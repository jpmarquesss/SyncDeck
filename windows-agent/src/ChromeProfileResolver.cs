using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;

namespace SyncDeck.Agent
{
    internal static class ChromeProfileResolver
    {
        public static string ArgumentsFor(ActionDefinition action)
        {
            string arguments = action == null ? string.Empty : (action.Arguments ?? string.Empty).Trim();
            if (!IsChrome(action) || arguments.IndexOf("--profile-directory", StringComparison.OrdinalIgnoreCase) >= 0)
                return arguments;

            string profile = ResolveActiveProfile();
            if (string.IsNullOrWhiteSpace(profile)) return arguments;
            string selection = "--profile-directory=\"" + profile.Replace("\"", string.Empty) + "\"";
            return string.IsNullOrWhiteSpace(arguments) ? selection : arguments + " " + selection;
        }

        private static bool IsChrome(ActionDefinition action)
        {
            if (action == null) return false;
            string target = action.Target ?? string.Empty;
            if (target.EndsWith("chrome.exe", StringComparison.OrdinalIgnoreCase))
            {
                try { if (Path.GetFileName(target).Equals("chrome.exe", StringComparison.OrdinalIgnoreCase)) return true; }
                catch { }
            }
            return (action.ProcessNames ?? new string[0]).Any(x =>
            {
                try { return Path.GetFileNameWithoutExtension(x ?? string.Empty).Equals("chrome", StringComparison.OrdinalIgnoreCase); }
                catch { return string.Equals((x ?? string.Empty).Trim(), "chrome", StringComparison.OrdinalIgnoreCase); }
            });
        }

        private static string ResolveActiveProfile()
        {
            string userData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Google", "Chrome", "User Data");
            string localState = Path.Combine(userData, "Local State");
            try
            {
                if (File.Exists(localState))
                {
                    JavaScriptSerializer serializer = new JavaScriptSerializer();
                    IDictionary<string, object> root = serializer.DeserializeObject(File.ReadAllText(localState, Encoding.UTF8)) as IDictionary<string, object>;
                    object profileValue;
                    IDictionary<string, object> profile = root != null && root.TryGetValue("profile", out profileValue)
                        ? profileValue as IDictionary<string, object> : null;
                    if (profile != null)
                    {
                        object activeValue;
                        if (profile.TryGetValue("last_active_profiles", out activeValue))
                        {
                            IEnumerable activeProfiles = activeValue as IEnumerable;
                            if (activeProfiles != null)
                            {
                                foreach (object item in activeProfiles)
                                {
                                    string candidate = ValidProfile(Convert.ToString(item), userData);
                                    if (candidate != null) return candidate;
                                }
                            }
                        }

                        object lastUsed;
                        if (profile.TryGetValue("last_used", out lastUsed))
                        {
                            string candidate = ValidProfile(Convert.ToString(lastUsed), userData);
                            if (candidate != null) return candidate;
                        }
                    }
                }
            }
            catch { }

            return Directory.Exists(Path.Combine(userData, "Default")) ? "Default" : null;
        }

        private static string ValidProfile(string value, string userData)
        {
            string candidate = (value ?? string.Empty).Trim();
            if (candidate.Length == 0 || candidate.Length > 80 || candidate.IndexOfAny(new[] { '\\', '/', '"' }) >= 0)
                return null;
            return Directory.Exists(Path.Combine(userData, candidate)) ? candidate : null;
        }
    }
}
