using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace SyncDeck.Agent
{
    public sealed class DeckServer : IDisposable
    {
        private const int MaxRequestBytes = 65536;
        private readonly int _port;
        private readonly ActionStore _actions;
        private readonly ClientStore _clients;
        private readonly PairingManager _pairing;
        private readonly ActionExecutor _executor = new ActionExecutor();
        private readonly IconResolver _icons = new IconResolver();
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer { MaxJsonLength = MaxRequestBytes };
        private readonly ConcurrentDictionary<string, long> _nonces = new ConcurrentDictionary<string, long>();
        private TcpListener _listener;
        private Thread _acceptThread;
        private volatile bool _running;

        public event EventHandler PhonePaired;
        public event EventHandler ActionsChanged;

        public DeckServer(int port, ActionStore actions, ClientStore clients, PairingManager pairing)
        {
            _port = port;
            _actions = actions;
            _clients = clients;
            _pairing = pairing;
        }

        public void Start()
        {
            if (_running) return;
            _listener = new TcpListener(IPAddress.Any, _port);
            _listener.Start(20);
            _running = true;
            _acceptThread = new Thread(AcceptLoop) { IsBackground = true, Name = "SyncDeck HTTP" };
            _acceptThread.Start();
        }

        public void Stop()
        {
            _running = false;
            try { if (_listener != null) _listener.Stop(); } catch { }
        }

        private void AcceptLoop()
        {
            while (_running)
            {
                try
                {
                    TcpClient client = _listener.AcceptTcpClient();
                    ThreadPool.QueueUserWorkItem(HandleClient, client);
                }
                catch (SocketException) { if (!_running) return; }
                catch { if (!_running) return; }
            }
        }

        private void HandleClient(object state)
        {
            using (TcpClient client = (TcpClient)state)
            {
                try
                {
                    client.ReceiveTimeout = 7000;
                    client.SendTimeout = 7000;
                    IPEndPoint remote = client.Client.RemoteEndPoint as IPEndPoint;
                    if (remote == null || !NetworkInfo.IsPrivateOrLoopback(remote.Address))
                    {
                        WriteResponse(client.GetStream(), 403, Error("Acesso permitido somente pela rede local.", "network_denied"));
                        return;
                    }
                    HttpRequestData request = ReadRequest(client.GetStream());
                    Route(client.GetStream(), request);
                }
                catch (InvalidDataException ex)
                {
                    try { WriteResponse(client.GetStream(), 400, Error(ex.Message, "bad_request")); } catch { }
                }
                catch
                {
                    try { WriteResponse(client.GetStream(), 500, Error("Erro interno do agente.", "server_error")); } catch { }
                }
            }
        }

        private void Route(NetworkStream stream, HttpRequestData request)
        {
            if (request.Method == "GET" && request.Path == "/api/status")
            {
                PairingStatus pairing = _pairing.GetStatus();
                WriteResponse(stream, 200, Success(new
                {
                    name = Environment.MachineName,
                    version = "0.3.0",
                    serverTime = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                    pairedDevices = _clients.Count,
                    pairing = pairing
                }, "Agente disponível."));
                return;
            }

            if (request.Method == "POST" && request.Path == "/api/pair")
            {
                PairRequest pairRequest = Deserialize<PairRequest>(request.BodyText);
                PairPayload payload;
                string error;
                if (!_pairing.TryComplete(pairRequest == null ? null : pairRequest.Payload, out payload, out error))
                {
                    WriteResponse(stream, 401, Error(error, "pair_failed"));
                    return;
                }
                byte[] secret;
                if (!PairingManager.TryBase64UrlDecode(payload.Secret, out secret))
                {
                    WriteResponse(stream, 400, Error("Chave inválida.", "pair_failed"));
                    return;
                }
                _clients.AddOrReplace(payload.ClientId, payload.DeviceName, secret);
                WriteResponse(stream, 200, Success(new { clientId = payload.ClientId }, "Celular pareado."));
                Raise(PhonePaired);
                return;
            }

            string authError;
            AuthContext auth;
            if (!Authenticate(request, out auth, out authError))
            {
                WriteResponse(stream, 401, Error(authError, "unauthorized"));
                return;
            }

            const string iconPrefix = "/api/icons/";
            if (request.Method == "GET" && request.Path.StartsWith(iconPrefix, StringComparison.Ordinal))
            {
                string actionId = Uri.UnescapeDataString(request.Path.Substring(iconPrefix.Length));
                ActionDefinition iconAction = _actions.Find(actionId);
                if (iconAction == null)
                {
                    WriteSignedResponse(stream, 404, Error("Botão não encontrado.", "not_found"), auth);
                    return;
                }
                byte[] icon = _icons.Resolve(iconAction);
                if (icon == null || icon.Length == 0)
                {
                    WriteSignedResponse(stream, 500, Error("Não foi possível obter a imagem do aplicativo.", "icon_failed"), auth);
                    return;
                }
                WriteSignedBinaryResponse(stream, 200, icon, "image/png", auth);
                return;
            }

            if (request.Method == "GET" && request.Path == "/api/actions")
            {
                ActionDefinition[] definitions = _actions.Load().Where(x => x.Enabled).ToArray();
                Dictionary<string, ActionState> states = _executor.GetStates(definitions)
                    .ToDictionary(x => x.Id, StringComparer.OrdinalIgnoreCase);
                PublicAction[] actions = definitions.Select(x => new PublicAction
                {
                    Id = x.Id,
                    Label = x.Label,
                    Type = x.Type,
                    Icon = x.Icon,
                    ImageKey = _icons.ImageKey(x),
                    Color = x.Color,
                    Confirm = x.Confirm,
                    Closable = x.Closable,
                    IsOpen = states[x.Id].IsOpen,
                    WindowCount = states[x.Id].WindowCount
                }).ToArray();
                WriteSignedResponse(stream, 200, Success(actions, actions.Length + " botões carregados."), auth);
                return;
            }

            if (request.Method == "GET" && request.Path == "/api/actions/state")
            {
                ActionDefinition[] definitions = _actions.Load().Where(x => x.Enabled).ToArray();
                ActionState[] states = _executor.GetStates(definitions);
                WriteSignedResponse(stream, 200, Success(states, "Estado das janelas atualizado."), auth);
                return;
            }

            if (request.Method == "GET" && request.Path == "/api/actions/edit")
            {
                WriteSignedResponse(stream, 200, Success(_actions.Load(), "Configuração carregada."), auth);
                return;
            }

            if (request.Method == "POST" && request.Path == "/api/execute")
            {
                ExecuteRequest execute = Deserialize<ExecuteRequest>(request.BodyText);
                if (execute == null || string.IsNullOrWhiteSpace(execute.ActionId))
                {
                    WriteSignedResponse(stream, 400, Error("Ação ausente.", "invalid_action"), auth);
                    return;
                }
                ActionDefinition action = _actions.Find(execute.ActionId);
                string operation = string.IsNullOrWhiteSpace(execute.Operation) ? "open" : execute.Operation.Trim().ToLowerInvariant();
                if (operation != "open" && operation != "close" && operation != "close-all")
                {
                    WriteSignedResponse(stream, 400, Error("Operação inválida.", "invalid_operation"), auth);
                    return;
                }
                bool closeOperation = operation == "close" || operation == "close-all";
                if (action != null && (action.Confirm || closeOperation) && !execute.Confirmed)
                {
                    WriteSignedResponse(stream, 409, Error("Essa ação precisa de confirmação.", "confirmation_required"), auth);
                    return;
                }
                ExecutionResult result = _executor.Execute(action, operation);
                WriteSignedResponse(stream, result.Ok ? 200 : 422,
                    result.Ok ? Success(null, result.Message) : Error(result.Message, "execution_failed"), auth);
                return;
            }

            if (request.Method == "POST" && request.Path == "/api/actions/save")
            {
                SaveActionRequest save = Deserialize<SaveActionRequest>(request.BodyText);
                try
                {
                    if (save == null || save.Action == null) throw new InvalidOperationException("Configuração ausente.");
                    _actions.Upsert(save.Action);
                    WriteSignedResponse(stream, 200, Success(null, "Botão salvo."), auth);
                    Raise(ActionsChanged);
                }
                catch (Exception ex)
                {
                    WriteSignedResponse(stream, 400, Error(ex.Message, "invalid_action"), auth);
                }
                return;
            }

            if (request.Method == "POST" && request.Path == "/api/actions/delete")
            {
                DeleteActionRequest delete = Deserialize<DeleteActionRequest>(request.BodyText);
                if (delete == null || string.IsNullOrWhiteSpace(delete.ActionId) || !_actions.Delete(delete.ActionId))
                {
                    WriteSignedResponse(stream, 404, Error("Botão não encontrado.", "not_found"), auth);
                    return;
                }
                WriteSignedResponse(stream, 200, Success(null, "Botão excluído."), auth);
                Raise(ActionsChanged);
                return;
            }

            WriteSignedResponse(stream, 404, Error("Rota não encontrada.", "not_found"), auth);
        }

        private bool Authenticate(HttpRequestData request, out AuthContext auth, out string error)
        {
            auth = null;
            error = "Não autorizado.";
            string clientId, timestampText, nonce, signature;
            if (!request.Headers.TryGetValue("X-SyncDeck-Client", out clientId) ||
                !request.Headers.TryGetValue("X-SyncDeck-Timestamp", out timestampText) ||
                !request.Headers.TryGetValue("X-SyncDeck-Nonce", out nonce) ||
                !request.Headers.TryGetValue("X-SyncDeck-Signature", out signature)) return false;

            byte[] secret;
            long timestamp;
            if (!_clients.TryGetSecret(clientId, out secret) || !long.TryParse(timestampText, out timestamp)) return false;
            long now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            if (Math.Abs(now - timestamp) > 90)
            {
                error = "O relógio do celular está fora de sincronia.";
                return false;
            }
            if (string.IsNullOrWhiteSpace(nonce) || nonce.Length < 12 || nonce.Length > 80) return false;
            string nonceKey = clientId + ":" + nonce;
            string expected = RequestSigner.Sign(secret, request.Method, request.Path, timestampText, nonce, request.BodyBytes);
            if (!RequestSigner.FixedTimeEquals(expected, signature))
            {
                error = "Assinatura inválida.";
                return false;
            }
            if (!_nonces.TryAdd(nonceKey, now + 180))
            {
                error = "Solicitação repetida.";
                return false;
            }
            PruneNonces(now);
            auth = new AuthContext { Secret = secret, Nonce = nonce };
            return true;
        }

        private void PruneNonces(long now)
        {
            if (_nonces.Count < 300) return;
            foreach (KeyValuePair<string, long> item in _nonces)
            {
                long ignored;
                if (item.Value < now) _nonces.TryRemove(item.Key, out ignored);
            }
        }

        private T Deserialize<T>(string text) where T : class
        {
            try { return _json.Deserialize<T>(text ?? string.Empty); }
            catch { return null; }
        }

        private string Success(object data, string message)
        {
            return _json.Serialize(new ApiEnvelope { Ok = true, Data = data, Message = message });
        }

        private string Error(string message, string code)
        {
            return _json.Serialize(new ApiEnvelope { Ok = false, Message = message, Code = code });
        }

        private static HttpRequestData ReadRequest(NetworkStream stream)
        {
            MemoryStream memory = new MemoryStream();
            byte[] buffer = new byte[4096];
            int headerEnd = -1;
            int contentLength = 0;
            while (memory.Length < MaxRequestBytes)
            {
                int read = stream.Read(buffer, 0, buffer.Length);
                if (read <= 0) break;
                memory.Write(buffer, 0, read);
                byte[] current = memory.ToArray();
                if (headerEnd < 0)
                {
                    headerEnd = FindHeaderEnd(current);
                    if (headerEnd >= 0)
                    {
                        string headersOnly = Encoding.ASCII.GetString(current, 0, headerEnd);
                        contentLength = ParseContentLength(headersOnly);
                        if (contentLength < 0 || contentLength > MaxRequestBytes) throw new InvalidDataException("Corpo da requisição inválido.");
                    }
                }
                if (headerEnd >= 0 && memory.Length >= headerEnd + 4 + contentLength) break;
            }
            byte[] bytes = memory.ToArray();
            if (headerEnd < 0) throw new InvalidDataException("Cabeçalho HTTP incompleto.");
            if (bytes.Length < headerEnd + 4 + contentLength) throw new InvalidDataException("Corpo HTTP incompleto.");

            string headerText = Encoding.ASCII.GetString(bytes, 0, headerEnd);
            string[] lines = headerText.Split(new[] { "\r\n" }, StringSplitOptions.None);
            string[] first = lines[0].Split(' ');
            if (first.Length < 2) throw new InvalidDataException("Linha HTTP inválida.");
            string method = first[0].ToUpperInvariant();
            if (method != "GET" && method != "POST") throw new InvalidDataException("Método HTTP não suportado.");
            string rawPath = first[1];
            string path = rawPath.Split('?')[0];
            if (!path.StartsWith("/api/", StringComparison.Ordinal) && path != "/api/status")
                throw new InvalidDataException("Caminho inválido.");

            Dictionary<string, string> headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            for (int i = 1; i < lines.Length; i++)
            {
                int separator = lines[i].IndexOf(':');
                if (separator > 0) headers[lines[i].Substring(0, separator).Trim()] = lines[i].Substring(separator + 1).Trim();
            }
            byte[] body = new byte[contentLength];
            if (contentLength > 0) Buffer.BlockCopy(bytes, headerEnd + 4, body, 0, contentLength);
            return new HttpRequestData { Method = method, Path = path, Headers = headers, BodyBytes = body };
        }

        private static int FindHeaderEnd(byte[] bytes)
        {
            for (int i = 0; i <= bytes.Length - 4; i++)
                if (bytes[i] == 13 && bytes[i + 1] == 10 && bytes[i + 2] == 13 && bytes[i + 3] == 10) return i;
            return -1;
        }

        private static int ParseContentLength(string headerText)
        {
            foreach (string line in headerText.Split(new[] { "\r\n" }, StringSplitOptions.None))
            {
                int separator = line.IndexOf(':');
                if (separator > 0 && string.Equals(line.Substring(0, separator).Trim(), "Content-Length", StringComparison.OrdinalIgnoreCase))
                {
                    int value;
                    return int.TryParse(line.Substring(separator + 1).Trim(), out value) ? value : -1;
                }
            }
            return 0;
        }

        private static void WriteResponse(NetworkStream stream, int statusCode, string json)
        {
            byte[] body = Encoding.UTF8.GetBytes(json ?? "{}");
            WriteResponseBytes(stream, statusCode, body, null);
        }

        private static void WriteSignedResponse(NetworkStream stream, int statusCode, string json, AuthContext auth)
        {
            byte[] body = Encoding.UTF8.GetBytes(json ?? "{}");
            string signature = RequestSigner.SignResponse(auth.Secret, statusCode, auth.Nonce, body);
            WriteResponseBytes(stream, statusCode, body, signature);
        }

        private static void WriteResponseBytes(NetworkStream stream, int statusCode, byte[] body, string signature)
        {
            WriteResponseBytes(stream, statusCode, body, signature, "application/json; charset=utf-8");
        }

        private static void WriteSignedBinaryResponse(NetworkStream stream, int statusCode, byte[] body,
            string contentType, AuthContext auth)
        {
            string signature = RequestSigner.SignResponse(auth.Secret, statusCode, auth.Nonce, body);
            WriteResponseBytes(stream, statusCode, body, signature, contentType);
        }

        private static void WriteResponseBytes(NetworkStream stream, int statusCode, byte[] body, string signature,
            string contentType)
        {
            string status = statusCode == 200 ? "OK" : statusCode == 400 ? "Bad Request" :
                statusCode == 401 ? "Unauthorized" : statusCode == 403 ? "Forbidden" :
                statusCode == 404 ? "Not Found" : statusCode == 409 ? "Conflict" :
                statusCode == 422 ? "Unprocessable Entity" : "Internal Server Error";
            string header = "HTTP/1.1 " + statusCode + " " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.Length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                (string.IsNullOrWhiteSpace(signature) ? string.Empty : "X-SyncDeck-Response-Signature: " + signature + "\r\n") +
                "Connection: close\r\n\r\n";
            byte[] head = Encoding.ASCII.GetBytes(header);
            stream.Write(head, 0, head.Length);
            stream.Write(body, 0, body.Length);
            stream.Flush();
        }

        private void Raise(EventHandler handler)
        {
            if (handler != null) handler(this, EventArgs.Empty);
        }

        public void Dispose()
        {
            Stop();
        }
    }

    internal static class NetworkInfo
    {
        public static string[] LocalIPv4Addresses()
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(x => x.OperationalStatus == OperationalStatus.Up && x.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .SelectMany(x => x.GetIPProperties().UnicastAddresses)
                .Where(x => x.Address.AddressFamily == AddressFamily.InterNetwork && IsPrivateOrLoopback(x.Address))
                .Select(x => x.Address.ToString())
                .Distinct().ToArray();
        }

        public static bool IsPrivateOrLoopback(IPAddress address)
        {
            if (IPAddress.IsLoopback(address)) return true;
            byte[] bytes = address.GetAddressBytes();
            if (bytes.Length != 4) return false;
            return bytes[0] == 10 ||
                   (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) ||
                   (bytes[0] == 192 && bytes[1] == 168) ||
                   (bytes[0] == 169 && bytes[1] == 254);
        }
    }
}
