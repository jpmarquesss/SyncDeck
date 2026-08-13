using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace SyncDeck.Agent
{
    public sealed class DeckServer : IDisposable
    {
        private const int MaxRequestBytes = 65536;
        private const string EncryptionProtocol = "aes-256-cbc-v1";
        private readonly int _port;
        private readonly ActionStore _actions;
        private readonly ClientStore _clients;
        private readonly PairingManager _pairing;
        private readonly DesktopSecurity _desktop;
        private readonly ActionExecutor _executor = new ActionExecutor();
        private readonly IconResolver _icons = new IconResolver();
        private readonly CatalogService _catalog = new CatalogService();
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer { MaxJsonLength = MaxRequestBytes };
        private readonly ConcurrentDictionary<string, long> _nonces = new ConcurrentDictionary<string, long>();
        private readonly ConcurrentDictionary<string, RateWindow> _rates = new ConcurrentDictionary<string, RateWindow>();
        private TcpListener _listener;
        private Thread _acceptThread;
        private volatile bool _running;
        private int _activeConnections;

        public event EventHandler PhonePaired;
        public event EventHandler ActionsChanged;

        public DeckServer(int port, ActionStore actions, ClientStore clients, PairingManager pairing, DesktopSecurity desktop)
        {
            _port = port;
            _actions = actions;
            _clients = clients;
            _pairing = pairing;
            _desktop = desktop;
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
            TcpClient client = (TcpClient)state;
            if (Interlocked.Increment(ref _activeConnections) > 16)
            {
                try { using (client) WriteResponse(client.GetStream(), 429, Error("Muitas conexões simultâneas.", "rate_limited")); }
                catch { try { client.Dispose(); } catch { } }
                finally { Interlocked.Decrement(ref _activeConnections); }
                return;
            }
            try
            {
                using (client)
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
                        IPEndPoint local = client.Client.LocalEndPoint as IPEndPoint;
                        Route(client.GetStream(), request, local == null ? null : local.Address, remote.Address);
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
            finally { Interlocked.Decrement(ref _activeConnections); }
        }

        private void Route(NetworkStream stream, HttpRequestData request, IPAddress localAddress, IPAddress remoteAddress)
        {
            if (request.Method == "GET" && request.Path == "/api/status")
            {
                PairingStatus pairing = _pairing.GetStatus();
                WriteResponse(stream, 200, Success(new
                {
                    name = Environment.MachineName,
                    version = "1.0.1",
                    serverTime = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                    pairedDevices = _clients.Count,
                    security = new { protocol = 2, encryptedPayloads = true, desktopApproval = true },
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
            if (!Authenticate(request, remoteAddress, out auth, out authError))
            {
                WriteResponse(stream, 401, Error(authError, "unauthorized"));
                return;
            }

            if (request.Method == "GET" && request.Path == "/api/wake-config")
            {
                WakeConfiguration wake = NetworkInfo.GetWakeConfiguration(localAddress);
                if (wake == null)
                {
                    WriteSignedResponse(stream, 422,
                        Error("Não foi possível identificar a placa de rede usada pelo SyncDeck.", "wake_unavailable"), auth);
                    return;
                }
                WriteSignedResponse(stream, 200, Success(wake,
                    "Wake-on-LAN configurado para " + wake.InterfaceName + "."), auth);
                return;
            }

            if (request.Method == "GET" && request.Path == "/api/catalog/apps")
            {
                CatalogApplication[] applications = _catalog.Applications(auth.ClientId);
                WriteSignedResponse(stream, 200, Success(applications,
                    applications.Length + " aplicativos encontrados no Windows."), auth);
                return;
            }

            if (request.Method == "POST" && request.Path == "/api/catalog/pick")
            {
                PickPathRequest pick = Deserialize<PickPathRequest>(request.BodyText);
                string kind = pick == null ? string.Empty : (pick.Kind ?? string.Empty).Trim().ToLowerInvariant();
                if (kind != "file" && kind != "folder")
                {
                    WriteSignedResponse(stream, 400, Error("Escolha arquivo ou pasta.", "invalid_picker"), auth);
                    return;
                }
                try
                {
                    PickedPath selected = _desktop.PickPath(auth, kind);
                    if (selected == null)
                    {
                        WriteSignedResponse(stream, 409, Error("A escolha foi cancelada no PC.", "picker_cancelled"), auth);
                        return;
                    }
                    selected.SelectionToken = _catalog.TrustPath(auth.ClientId, selected.Target);
                    WriteSignedResponse(stream, 200, Success(selected, "Local escolhido no PC."), auth);
                }
                catch (Exception ex)
                {
                    WriteSignedResponse(stream, 409, Error(ex.Message, "desktop_busy"), auth);
                }
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
                if (action != null && operation == "open" &&
                    (string.Equals(action.Type, "command", StringComparison.OrdinalIgnoreCase) ||
                     string.Equals(action.Type, "hotkey", StringComparison.OrdinalIgnoreCase)))
                {
                    DesktopDecision decision = _desktop.ApproveExecution(auth, action, operation);
                    if (decision != DesktopDecision.Approved)
                    {
                        WriteSignedResponse(stream, decision == DesktopDecision.Busy ? 409 : 403,
                            Error(DecisionMessage(decision), "desktop_approval_required"), auth);
                        return;
                    }
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
                    ActionStore.Validate(save.Action);
                    ActionDefinition existing = _actions.Load().FirstOrDefault(x =>
                        string.Equals(x.Id, save.Action.Id, StringComparison.OrdinalIgnoreCase));
                    bool executionChanged = existing == null ||
                        !string.Equals(existing.Type, save.Action.Type, StringComparison.OrdinalIgnoreCase) ||
                        !string.Equals(existing.Target, save.Action.Target, StringComparison.OrdinalIgnoreCase) ||
                        !string.Equals(existing.Arguments ?? string.Empty, save.Action.Arguments ?? string.Empty, StringComparison.Ordinal) ||
                        !string.Equals(existing.WorkingDirectory ?? string.Empty, save.Action.WorkingDirectory ?? string.Empty, StringComparison.OrdinalIgnoreCase) ||
                        !string.Equals(existing.FallbackUrl ?? string.Empty, save.Action.FallbackUrl ?? string.Empty, StringComparison.OrdinalIgnoreCase);
                    bool trustedSelection = executionChanged &&
                        string.IsNullOrWhiteSpace(save.Action.Arguments) &&
                        string.IsNullOrWhiteSpace(save.Action.WorkingDirectory) &&
                        string.IsNullOrWhiteSpace(save.Action.FallbackUrl) &&
                        _catalog.Consume(auth.ClientId, save.SelectionToken, save.Action.Type, save.Action.Target);
                    bool isLink = string.Equals(save.Action.Type, "url", StringComparison.OrdinalIgnoreCase);
                    bool sensitive = string.Equals(save.Action.Type, "command", StringComparison.OrdinalIgnoreCase) ||
                        string.Equals(save.Action.Type, "hotkey", StringComparison.OrdinalIgnoreCase);
                    if (executionChanged && (sensitive || (!isLink && !trustedSelection)))
                    {
                        DesktopDecision decision = _desktop.ApproveSave(auth, save.Action);
                        if (decision != DesktopDecision.Approved)
                        {
                            WriteSignedResponse(stream, decision == DesktopDecision.Busy ? 409 : 403,
                                Error(DecisionMessage(decision), "desktop_approval_required"), auth);
                            return;
                        }
                    }
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

        private bool Authenticate(HttpRequestData request, IPAddress remoteAddress, out AuthContext auth, out string error)
        {
            auth = null;
            error = "Não autorizado.";
            string clientId, timestampText, nonce, signature;
            if (!request.Headers.TryGetValue("X-SyncDeck-Client", out clientId) ||
                !request.Headers.TryGetValue("X-SyncDeck-Timestamp", out timestampText) ||
                !request.Headers.TryGetValue("X-SyncDeck-Nonce", out nonce) ||
                !request.Headers.TryGetValue("X-SyncDeck-Signature", out signature)) return false;

            byte[] secret;
            ClientRecord client;
            long timestamp;
            if (!_clients.TryGetClient(clientId, out client, out secret) || !long.TryParse(timestampText, out timestamp)) return false;
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
            if (!AllowRequest(clientId, now))
            {
                error = "Muitas solicitações. Aguarde alguns segundos.";
                return false;
            }
            string encryption;
            bool encrypted = request.Headers.TryGetValue("X-SyncDeck-Encryption", out encryption) &&
                string.Equals(encryption, EncryptionProtocol, StringComparison.OrdinalIgnoreCase);
            if (encrypted && request.BodyBytes != null && request.BodyBytes.Length > 0)
            {
                try { request.BodyBytes = PayloadCipher.Decrypt(secret, request.BodyBytes, MaxRequestBytes); }
                catch
                {
                    error = "Não foi possível descriptografar a solicitação.";
                    return false;
                }
            }
            auth = new AuthContext
            {
                Secret = secret,
                Nonce = nonce,
                ClientId = clientId,
                DeviceName = client.DeviceName,
                RemoteAddress = remoteAddress == null ? string.Empty : remoteAddress.ToString(),
                Encrypted = encrypted
            };
            return true;
        }

        private bool AllowRequest(string clientId, long now)
        {
            RateWindow window = _rates.GetOrAdd(clientId, ignored => new RateWindow { StartedAt = now, Count = 0 });
            lock (window)
            {
                if (now - window.StartedAt >= 60) { window.StartedAt = now; window.Count = 0; }
                window.Count++;
                return window.Count <= 120;
            }
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

        private static string DecisionMessage(DesktopDecision decision)
        {
            if (decision == DesktopDecision.Busy) return "Já existe outra confirmação aberta no PC.";
            if (decision == DesktopDecision.Unavailable) return "O agente não conseguiu mostrar a confirmação no PC.";
            return "A ação foi negada ou expirou no PC.";
        }

        private static HttpRequestData ReadRequest(NetworkStream stream)
        {
            MemoryStream memory = new MemoryStream();
            byte[] buffer = new byte[4096];
            int headerEnd = -1;
            int contentLength = 0;
            while (memory.Length < MaxRequestBytes)
            {
                int remaining = MaxRequestBytes - (int)memory.Length;
                int read = stream.Read(buffer, 0, Math.Min(buffer.Length, remaining));
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
                        if (headerEnd + 4 + contentLength > MaxRequestBytes)
                            throw new InvalidDataException("Requisição muito grande.");
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
            if (auth.Encrypted) body = PayloadCipher.Encrypt(auth.Secret, body);
            string signature = RequestSigner.SignResponse(auth.Secret, statusCode, auth.Nonce, body);
            WriteResponseBytes(stream, statusCode, body, signature, "application/json; charset=utf-8", auth.Encrypted);
        }

        private static void WriteResponseBytes(NetworkStream stream, int statusCode, byte[] body, string signature)
        {
            WriteResponseBytes(stream, statusCode, body, signature, "application/json; charset=utf-8", false);
        }

        private static void WriteSignedBinaryResponse(NetworkStream stream, int statusCode, byte[] body,
            string contentType, AuthContext auth)
        {
            if (auth.Encrypted) body = PayloadCipher.Encrypt(auth.Secret, body);
            string signature = RequestSigner.SignResponse(auth.Secret, statusCode, auth.Nonce, body);
            WriteResponseBytes(stream, statusCode, body, signature, contentType, auth.Encrypted);
        }

        private static void WriteResponseBytes(NetworkStream stream, int statusCode, byte[] body, string signature,
            string contentType, bool encrypted)
        {
            string status = statusCode == 200 ? "OK" : statusCode == 400 ? "Bad Request" :
                statusCode == 401 ? "Unauthorized" : statusCode == 403 ? "Forbidden" :
                statusCode == 404 ? "Not Found" : statusCode == 409 ? "Conflict" :
                statusCode == 422 ? "Unprocessable Entity" : statusCode == 429 ? "Too Many Requests" :
                "Internal Server Error";
            string header = "HTTP/1.1 " + statusCode + " " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.Length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                (encrypted ? "X-SyncDeck-Encryption: " + EncryptionProtocol + "\r\n" : string.Empty) +
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

        private sealed class RateWindow
        {
            public long StartedAt;
            public int Count;
        }
    }

}
