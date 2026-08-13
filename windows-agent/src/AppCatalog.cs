using Microsoft.Win32;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;

namespace SyncDeck.Agent
{
    public sealed class CatalogService
    {
        private sealed class TrustedSelection
        {
            public string ClientId;
            public string Type;
            public string Target;
            public long ExpiresAt;
        }

        private readonly ConcurrentDictionary<string, TrustedSelection> _selections =
            new ConcurrentDictionary<string, TrustedSelection>(StringComparer.Ordinal);

        public CatalogApplication[] Applications(string clientId)
        {
            List<CatalogApplication> applications = RegistryApplications();
            applications.AddRange(StartApplications());
            CatalogApplication[] result = applications
                .Where(x => x != null && !string.IsNullOrWhiteSpace(x.Name) && !string.IsNullOrWhiteSpace(x.Target))
                .GroupBy(x => x.Name.Trim(), StringComparer.OrdinalIgnoreCase)
                .Select(x => x.First())
                .OrderBy(x => x.Name, StringComparer.CurrentCultureIgnoreCase)
                .Take(100)
                .ToArray();
            foreach (CatalogApplication app in result)
                app.SelectionToken = Remember(clientId, "app", app.Target);
            Prune();
            return result;
        }

        public string TrustPath(string clientId, string target)
        {
            return Remember(clientId, "path", target);
        }

        public bool Consume(string clientId, string token, string type, string target)
        {
            if (string.IsNullOrWhiteSpace(clientId) || string.IsNullOrWhiteSpace(token)) return false;
            TrustedSelection selection;
            if (!_selections.TryRemove(token, out selection) || selection == null) return false;
            long now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            return selection.ExpiresAt >= now &&
                string.Equals(selection.ClientId, clientId, StringComparison.OrdinalIgnoreCase) &&
                string.Equals(selection.Type, type, StringComparison.OrdinalIgnoreCase) &&
                string.Equals(NormalizeTarget(selection.Target), NormalizeTarget(target), StringComparison.OrdinalIgnoreCase);
        }

        private string Remember(string clientId, string type, string target)
        {
            string token = PairingManager.Base64UrlEncode(Guid.NewGuid().ToByteArray());
            _selections[token] = new TrustedSelection
            {
                ClientId = clientId,
                Type = type,
                Target = target,
                ExpiresAt = DateTimeOffset.UtcNow.AddMinutes(5).ToUnixTimeSeconds()
            };
            return token;
        }

        private void Prune()
        {
            if (_selections.Count < 200) return;
            long now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            foreach (KeyValuePair<string, TrustedSelection> item in _selections)
            {
                TrustedSelection ignored;
                if (item.Value.ExpiresAt < now) _selections.TryRemove(item.Key, out ignored);
            }
            if (_selections.Count <= 500) return;
            foreach (string key in _selections.Keys.Take(_selections.Count - 400))
            {
                TrustedSelection ignored;
                _selections.TryRemove(key, out ignored);
            }
        }

        private static List<CatalogApplication> RegistryApplications()
        {
            List<CatalogApplication> result = new List<CatalogApplication>();
            foreach (RegistryKey root in new[] { Registry.CurrentUser, Registry.LocalMachine })
            {
                foreach (string path in new[]
                {
                    @"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths",
                    @"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths"
                })
                {
                    try
                    {
                        using (RegistryKey apps = root.OpenSubKey(path))
                        {
                            if (apps == null) continue;
                            foreach (string child in apps.GetSubKeyNames())
                            {
                                try
                                {
                                    using (RegistryKey key = apps.OpenSubKey(child))
                                    {
                                        string executable = Convert.ToString(key == null ? null : key.GetValue(null));
                                        executable = Environment.ExpandEnvironmentVariables((executable ?? string.Empty).Trim().Trim('"'));
                                        if (!File.Exists(executable)) continue;
                                        FileVersionInfo version = FileVersionInfo.GetVersionInfo(executable);
                                        string name = First(version.ProductName, version.FileDescription, Path.GetFileNameWithoutExtension(executable));
                                        if (IsUtility(name, executable)) continue;
                                        string process = Path.GetFileNameWithoutExtension(executable);
                                        result.Add(new CatalogApplication
                                        {
                                            Name = name,
                                            Target = executable,
                                            ProcessNames = new[] { process },
                                            AppNames = new[] { name },
                                            Icon = "app",
                                            Color = "#64748B"
                                        });
                                    }
                                }
                                catch { }
                            }
                        }
                    }
                    catch { }
                }
            }
            return result;
        }

        private static List<CatalogApplication> StartApplications()
        {
            List<CatalogApplication> result = new List<CatalogApplication>();
            try
            {
                string script = "Get-StartApps | Sort-Object Name | ForEach-Object { ($_.Name -replace \"`t\",\" \") + \"`t\" + $_.AppID }";
                string encoded = Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
                using (Process process = new Process())
                {
                    process.StartInfo = new ProcessStartInfo
                    {
                        FileName = "powershell.exe",
                        Arguments = "-NoProfile -NonInteractive -EncodedCommand " + encoded,
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardOutput = true,
                        RedirectStandardError = true,
                        StandardOutputEncoding = Encoding.UTF8
                    };
                    process.Start();
                    string output = process.StandardOutput.ReadToEnd();
                    if (!process.WaitForExit(5000)) { try { process.Kill(); } catch { } return result; }
                    foreach (string line in output.Split(new[] { "\r\n", "\n" }, StringSplitOptions.RemoveEmptyEntries))
                    {
                        int separator = line.IndexOf('\t');
                        if (separator <= 0 || separator >= line.Length - 1) continue;
                        string name = line.Substring(0, separator).Trim();
                        string appId = line.Substring(separator + 1).Trim();
                        if (name.Length == 0 || appId.Length == 0 || IsUtility(name, appId)) continue;
                        result.Add(new CatalogApplication
                        {
                            Name = name,
                            Target = "shell:AppsFolder\\" + appId,
                            ProcessNames = new string[0],
                            AppNames = new[] { name },
                            Icon = "app",
                            Color = "#64748B"
                        });
                    }
                }
            }
            catch { }
            return result;
        }

        private static bool IsUtility(string name, string target)
        {
            string value = (name + " " + target).ToLowerInvariant();
            string[] ignored = { "uninstall", "desinstal", "setup", "installer", "update helper", "crash handler" };
            return ignored.Any(value.Contains);
        }

        private static string First(params string[] values)
        {
            return values.FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "Aplicativo";
        }

        private static string NormalizeTarget(string value)
        {
            return Environment.ExpandEnvironmentVariables((value ?? string.Empty).Trim().Trim('"')).TrimEnd('\\');
        }
    }
}
