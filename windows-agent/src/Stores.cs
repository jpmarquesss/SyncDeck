using Microsoft.Win32;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;

namespace SyncDeck.Agent
{
    internal static class DataPaths
    {
        public static readonly string Root = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SyncDeck");
        public static readonly string Actions = Path.Combine(Root, "actions.json");
        public static readonly string Clients = Path.Combine(Root, "clients.json");
        public static readonly string ClientsBackup = Path.Combine(Root, "clients.backup.json");
        public static readonly string Settings = Path.Combine(Root, "settings.json");
        public static readonly string ActionsMigration031 = Path.Combine(Root, "actions-v0.3.1.migrated");

        public static void Ensure()
        {
            Directory.CreateDirectory(Root);
        }
    }

    public sealed class ActionStore
    {
        private readonly object _gate = new object();
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();

        public ActionStore()
        {
            DataPaths.Ensure();
            if (!File.Exists(DataPaths.Actions))
            {
                Save(CreateDefaults());
            }
            Apply031LayoutMigration();
        }

        public List<ActionDefinition> Load()
        {
            lock (_gate)
            {
                try
                {
                    string text = File.ReadAllText(DataPaths.Actions, Encoding.UTF8);
                    List<ActionDefinition> items = _json.Deserialize<List<ActionDefinition>>(text);
                    if (items == null)
                    {
                        return new List<ActionDefinition>();
                    }
                    return items.Select(Normalize).ToList();
                }
                catch
                {
                    return new List<ActionDefinition>();
                }
            }
        }

        public void Save(IEnumerable<ActionDefinition> actions)
        {
            lock (_gate)
            {
                List<ActionDefinition> cleaned = actions.Select(Normalize).ToList();
                ValidateAll(cleaned);
                string temp = DataPaths.Actions + ".tmp";
                File.WriteAllText(temp, PrettyJson.Serialize(cleaned), new UTF8Encoding(false));
                if (File.Exists(DataPaths.Actions))
                {
                    File.Replace(temp, DataPaths.Actions, null);
                }
                else
                {
                    File.Move(temp, DataPaths.Actions);
                }
            }
        }

        public void Upsert(ActionDefinition action)
        {
            ActionDefinition cleaned = Normalize(action);
            Validate(cleaned);
            lock (_gate)
            {
                List<ActionDefinition> all = Load();
                int index = all.FindIndex(x => string.Equals(x.Id, cleaned.Id, StringComparison.OrdinalIgnoreCase));
                if (index >= 0)
                {
                    all[index] = cleaned;
                }
                else
                {
                    all.Add(cleaned);
                }
                Save(all);
            }
        }

        public bool Delete(string id)
        {
            lock (_gate)
            {
                List<ActionDefinition> all = Load();
                int removed = all.RemoveAll(x => string.Equals(x.Id, id, StringComparison.OrdinalIgnoreCase));
                if (removed > 0)
                {
                    Save(all);
                    return true;
                }
                return false;
            }
        }

        public ActionDefinition Find(string id)
        {
            return Load().FirstOrDefault(x => x.Enabled && string.Equals(x.Id, id, StringComparison.OrdinalIgnoreCase));
        }

        public static string Slugify(string value)
        {
            string input = (value ?? string.Empty).Trim().ToLowerInvariant();
            string normalized = input.Normalize(NormalizationForm.FormD);
            StringBuilder builder = new StringBuilder();
            foreach (char c in normalized)
            {
                System.Globalization.UnicodeCategory category =
                    System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c);
                if (category != System.Globalization.UnicodeCategory.NonSpacingMark)
                {
                    builder.Append(c);
                }
            }
            string slug = Regex.Replace(builder.ToString().Normalize(NormalizationForm.FormC), "[^a-z0-9]+", "-").Trim('-');
            return string.IsNullOrWhiteSpace(slug) ? "acao-" + Guid.NewGuid().ToString("N").Substring(0, 8) : slug;
        }

        public static void Validate(ActionDefinition action)
        {
            if (action == null) throw new InvalidOperationException("Ação ausente.");
            if (!Regex.IsMatch(action.Id ?? string.Empty, "^[a-z0-9][a-z0-9-]{1,63}$"))
                throw new InvalidOperationException("O identificador deve ter de 2 a 64 caracteres: letras, números e hífen.");
            if (string.IsNullOrWhiteSpace(action.Label) || action.Label.Length > 40)
                throw new InvalidOperationException("O nome da ação deve ter entre 1 e 40 caracteres.");
            string[] types = { "app", "url", "path", "command", "hotkey" };
            if (!types.Contains(action.Type))
                throw new InvalidOperationException("Tipo de ação inválido.");
            if (string.IsNullOrWhiteSpace(action.Target) || action.Target.Length > 1000)
                throw new InvalidOperationException("Informe um destino válido.");
            if (action.Type == "url")
            {
                Uri uri;
                if (!Uri.TryCreate(action.Target, UriKind.Absolute, out uri) ||
                    (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
                    throw new InvalidOperationException("Links devem começar com http:// ou https://.");
            }
            if (action.Type == "command") action.Confirm = true;
        }

        private static void ValidateAll(List<ActionDefinition> actions)
        {
            HashSet<string> ids = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (ActionDefinition action in actions)
            {
                Validate(action);
                if (!ids.Add(action.Id)) throw new InvalidOperationException("Existem ações com o mesmo identificador.");
            }
        }

        private static ActionDefinition Normalize(ActionDefinition source)
        {
            ActionDefinition action = source == null ? new ActionDefinition() : source.Clone();
            action.Id = string.IsNullOrWhiteSpace(action.Id) ? Slugify(action.Label) : action.Id.Trim().ToLowerInvariant();
            action.Label = (action.Label ?? string.Empty).Trim();
            action.Type = (action.Type ?? "app").Trim().ToLowerInvariant();
            action.Target = (action.Target ?? string.Empty).Trim();
            action.Arguments = (action.Arguments ?? string.Empty).Trim();
            action.WorkingDirectory = (action.WorkingDirectory ?? string.Empty).Trim();
            action.ProcessNames = CleanArray(action.ProcessNames);
            action.AppNames = CleanArray(action.AppNames);
            action.FallbackUrl = (action.FallbackUrl ?? string.Empty).Trim();
            action.Icon = string.IsNullOrWhiteSpace(action.Icon) ? "app" : action.Icon.Trim().ToLowerInvariant();
            action.Color = Regex.IsMatch(action.Color ?? string.Empty, "^#[0-9a-fA-F]{6}$") ? action.Color.ToUpperInvariant() : "#697386";
            return action;
        }

        private static string[] CleanArray(string[] values)
        {
            return (values ?? new string[0])
                .Where(x => !string.IsNullOrWhiteSpace(x))
                .Select(x => x.Trim())
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .Take(12)
                .ToArray();
        }

        private static List<ActionDefinition> CreateDefaults()
        {
            return new List<ActionDefinition>
            {
                App("chrome", "Chrome", "chrome.exe", new[] { "chrome" }, new[] { "Google Chrome" }, "chrome", "#4285F4"),
                App("whatsapp", "WhatsApp", "whatsapp:", new[] { "WhatsApp" }, new[] { "WhatsApp" }, "whatsapp", "#25D366"),
                App("outlook", "Outlook (novo)", "olk.exe", new[] { "olk", "HxOutlook" }, new[] { "Outlook (new)", "Outlook (novo)", "Outlook" }, "outlook", "#1473E6"),
                App("explorer", "Explorador", "explorer.exe", new[] { "explorer" }, new[] { "Explorador de Arquivos", "File Explorer" }, "folder", "#F5B82E"),
                App("cmd", "Prompt de Comando", "cmd.exe", new[] { "cmd" }, new string[0], "terminal", "#64748B"),
                new ActionDefinition
                {
                    Id = "codex", Label = "ChatGPT · Codex", Type = "app",
                    Target = "https://chatgpt.com/codex/open-app",
                    FallbackUrl = "https://chatgpt.com/codex/",
                    ProcessNames = new[] { "ChatGPT" }, AppNames = new[] { "ChatGPT" },
                    Icon = "codex", Color = "#10A37F", Closable = true, Enabled = true
                },
                App("calculator", "Calculadora", "calc.exe", new[] { "CalculatorApp", "Calculator" }, new[] { "Calculadora", "Calculator" }, "calculator", "#F59E0B"),
                ChatGptWeb(),
                ShutdownPc()
            };
        }

        private void Apply031LayoutMigration()
        {
            if (File.Exists(DataPaths.ActionsMigration031)) return;
            lock (_gate)
            {
                List<ActionDefinition> actions = Load();
                actions.RemoveAll(IsRemovedFrom031Layout);
                if (!actions.Any(x => string.Equals(x.Id, "chatgpt-web", StringComparison.OrdinalIgnoreCase)))
                    actions.Add(ChatGptWeb());
                if (!actions.Any(x => string.Equals(x.Id, "shutdown-pc", StringComparison.OrdinalIgnoreCase)))
                    actions.Add(ShutdownPc());
                Save(actions);
                File.WriteAllText(DataPaths.ActionsMigration031,
                    "Migração do painel aplicada em " + DateTime.UtcNow.ToString("o"), new UTF8Encoding(false));
            }
        }

        private static bool IsRemovedFrom031Layout(ActionDefinition action)
        {
            if (action == null) return false;
            string id = (action.Id ?? string.Empty).Trim();
            string label = (action.Label ?? string.Empty).Trim();
            return string.Equals(id, "downloads", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(id, "android-studio", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(id, "android-app", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(label, "Downloads", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(label, "Pasta Downloads", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(label, "Android Studio", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(label, "Android App", StringComparison.OrdinalIgnoreCase);
        }

        private static ActionDefinition ChatGptWeb()
        {
            return new ActionDefinition
            {
                Id = "chatgpt-web", Label = "ChatGPT", Type = "url", Target = "https://chatgpt.com/",
                Arguments = "chrome", ProcessNames = new string[0], AppNames = new string[0],
                Icon = "codex", Color = "#10A37F", Confirm = false, Closable = false, Enabled = true
            };
        }

        private static ActionDefinition ShutdownPc()
        {
            return new ActionDefinition
            {
                Id = "shutdown-pc", Label = "Desligar PC", Type = "command", Target = "shutdown.exe",
                Arguments = "/s /t 5", ProcessNames = new string[0], AppNames = new string[0],
                Icon = "power", Color = "#EF4444", Confirm = true, Closable = false, Enabled = true
            };
        }

        private static ActionDefinition App(string id, string label, string target, string[] processes, string[] appNames, string icon, string color)
        {
            return new ActionDefinition
            {
                Id = id, Label = label, Type = "app", Target = target,
                ProcessNames = processes, AppNames = appNames, Icon = icon, Color = color,
                Closable = true, Enabled = true
            };
        }
    }

    public sealed class ClientStore
    {
        private readonly object _gate = new object();
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("SyncDeck.ClientSecret.v1");

        public ClientStore()
        {
            DataPaths.Ensure();
        }

        public int Count { get { return LoadRecords().Count; } }

        public void AddOrReplace(string clientId, string deviceName, byte[] secret)
        {
            lock (_gate)
            {
                List<ClientRecord> records = LoadRecords();
                records.RemoveAll(x => string.Equals(x.ClientId, clientId, StringComparison.OrdinalIgnoreCase));
                records.Add(new ClientRecord
                {
                    ClientId = clientId,
                    DeviceName = string.IsNullOrWhiteSpace(deviceName) ? "Android" : deviceName.Trim(),
                    ProtectedSecret = Convert.ToBase64String(ProtectedData.Protect(secret, Entropy, DataProtectionScope.CurrentUser)),
                    PairedAtUtc = DateTime.UtcNow.ToString("o")
                });
                while (records.Count > 5) records.RemoveAt(0);
                SaveRecords(records);
            }
        }

        public bool TryGetSecret(string clientId, out byte[] secret)
        {
            secret = null;
            ClientRecord record = LoadRecords().FirstOrDefault(x =>
                string.Equals(x.ClientId, clientId, StringComparison.OrdinalIgnoreCase));
            if (record == null) return false;
            try
            {
                secret = ProtectedData.Unprotect(Convert.FromBase64String(record.ProtectedSecret), Entropy, DataProtectionScope.CurrentUser);
                return secret != null && secret.Length == 32;
            }
            catch { return false; }
        }

        public void RevokeAll()
        {
            lock (_gate)
            {
                if (File.Exists(DataPaths.Clients)) File.Delete(DataPaths.Clients);
                if (File.Exists(DataPaths.ClientsBackup)) File.Delete(DataPaths.ClientsBackup);
            }
        }

        private List<ClientRecord> LoadRecords()
        {
            lock (_gate)
            {
                List<ClientRecord> records;
                if (TryLoadRecords(DataPaths.Clients, out records)) return records;
                if (TryLoadRecords(DataPaths.ClientsBackup, out records))
                {
                    try { SaveRecords(records); } catch { }
                    return records;
                }
                return new List<ClientRecord>();
            }
        }

        private void SaveRecords(List<ClientRecord> records)
        {
            string content = PrettyJson.Serialize(records);
            string temp = DataPaths.Clients + ".tmp";
            using (FileStream stream = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None))
            using (StreamWriter writer = new StreamWriter(stream, new UTF8Encoding(false)))
            {
                writer.Write(content);
                writer.Flush();
                stream.Flush(true);
            }
            if (File.Exists(DataPaths.Clients))
                File.Replace(temp, DataPaths.Clients, DataPaths.ClientsBackup);
            else
                File.Move(temp, DataPaths.Clients);
            File.Copy(DataPaths.Clients, DataPaths.ClientsBackup, true);
        }

        private bool TryLoadRecords(string path, out List<ClientRecord> records)
        {
            records = null;
            if (!File.Exists(path)) return false;
            try
            {
                records = _json.Deserialize<List<ClientRecord>>(File.ReadAllText(path, Encoding.UTF8));
                return records != null;
            }
            catch { return false; }
        }
    }

    public sealed class SettingsStore
    {
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();

        public AgentSettings Load()
        {
            DataPaths.Ensure();
            try
            {
                if (File.Exists(DataPaths.Settings))
                {
                    AgentSettings settings = _json.Deserialize<AgentSettings>(File.ReadAllText(DataPaths.Settings));
                    if (settings != null)
                    {
                        if (settings.Port < 1024 || settings.Port > 65535) settings.Port = 47321;
                        return settings;
                    }
                }
            }
            catch { }
            return new AgentSettings { Port = 47321, StartupConfigured = false };
        }

        public void Save(AgentSettings settings)
        {
            DataPaths.Ensure();
            File.WriteAllText(DataPaths.Settings, PrettyJson.Serialize(settings), new UTF8Encoding(false));
        }
    }

    internal static class StartupManager
    {
        private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string ValueName = "SyncDeckAgent";

        public static bool IsEnabled()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunKey, false))
            {
                return key != null && key.GetValue(ValueName) != null;
            }
        }

        public static void SetEnabled(bool enabled)
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RunKey))
            {
                if (enabled)
                    key.SetValue(ValueName, "\"" + System.Windows.Forms.Application.ExecutablePath + "\"");
                else
                    key.DeleteValue(ValueName, false);
            }
        }
    }

    internal static class PrettyJson
    {
        public static string Serialize(object value)
        {
            JavaScriptSerializer serializer = new JavaScriptSerializer();
            string json = serializer.Serialize(value);
            StringBuilder result = new StringBuilder();
            bool quoted = false;
            bool escaped = false;
            int depth = 0;
            foreach (char c in json)
            {
                if (escaped) { result.Append(c); escaped = false; continue; }
                if (c == '\\' && quoted) { result.Append(c); escaped = true; continue; }
                if (c == '"') { quoted = !quoted; result.Append(c); continue; }
                if (quoted) { result.Append(c); continue; }
                if (c == '{' || c == '[')
                {
                    result.Append(c).AppendLine();
                    depth++;
                    result.Append(new string(' ', depth * 2));
                }
                else if (c == '}' || c == ']')
                {
                    result.AppendLine();
                    depth--;
                    result.Append(new string(' ', depth * 2)).Append(c);
                }
                else if (c == ',')
                {
                    result.Append(c).AppendLine().Append(new string(' ', depth * 2));
                }
                else if (c == ':') result.Append(": ");
                else result.Append(c);
            }
            return result.ToString();
        }
    }
}
