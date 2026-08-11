using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;

namespace SyncDeck.Agent
{
    internal sealed class WindowRecord
    {
        public IntPtr Handle { get; set; }
        public string Title { get; set; }
        public string ClassName { get; set; }
        public HashSet<string> ProcessNames { get; set; }
    }

    internal static class WindowInspector
    {
        private const uint GW_OWNER = 4;
        private const int DWMWA_CLOAKED = 14;

        private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

        [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
        [DllImport("user32.dll")] private static extern bool EnumChildWindows(IntPtr parent, EnumWindowsProc callback, IntPtr lParam);
        [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll")] private static extern IntPtr GetWindow(IntPtr hWnd, uint command);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
        [DllImport("user32.dll")] private static extern int GetWindowTextLength(IntPtr hWnd);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr hWnd, StringBuilder className, int maxCount);
        [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
        [DllImport("dwmapi.dll")] private static extern int DwmGetWindowAttribute(IntPtr hWnd, int attribute, out int value, int size);

        public static List<WindowRecord> Capture()
        {
            List<WindowRecord> windows = new List<WindowRecord>();
            Dictionary<uint, string> processCache = new Dictionary<uint, string>();
            EnumWindows(delegate(IntPtr handle, IntPtr ignored)
            {
                try
                {
                    if (!IsWindowVisible(handle) || GetWindow(handle, GW_OWNER) != IntPtr.Zero || IsCloaked(handle)) return true;
                    string className = ReadClassName(handle);
                    if (IsShellOrUtilityWindow(className)) return true;
                    string title = ReadTitle(handle);
                    if (string.IsNullOrWhiteSpace(title)) return true;

                    HashSet<string> processNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    AddProcessName(handle, processNames, processCache);
                    EnumChildWindows(handle, delegate(IntPtr child, IntPtr childIgnored)
                    {
                        AddProcessName(child, processNames, processCache);
                        return true;
                    }, IntPtr.Zero);
                    if (processNames.Count == 0) return true;

                    windows.Add(new WindowRecord
                    {
                        Handle = handle,
                        Title = title,
                        ClassName = className,
                        ProcessNames = processNames
                    });
                }
                catch { }
                return true;
            }, IntPtr.Zero);
            return windows;
        }

        public static List<WindowRecord> Match(ActionDefinition action, IList<WindowRecord> windows)
        {
            HashSet<string> expected = ProcessSet(action);
            string[] titleHints = ((action == null ? null : action.AppNames) ?? new string[0])
                .Concat(new[] { action == null ? string.Empty : action.Label })
                .Where(x => !string.IsNullOrWhiteSpace(x) && x.Trim().Length >= 3)
                .Select(x => x.Trim()).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();

            List<WindowRecord> matches = new List<WindowRecord>();
            foreach (WindowRecord window in windows ?? new List<WindowRecord>())
            {
                bool processMatch = expected.Count > 0 && window.ProcessNames.Overlaps(expected);
                bool framedApp = window.ProcessNames.Contains("ApplicationFrameHost") ||
                                 window.ProcessNames.Contains("WindowsTerminal") ||
                                 window.ProcessNames.Contains("OpenConsole");
                bool titleMatch = titleHints.Any(x => window.Title.IndexOf(x, StringComparison.OrdinalIgnoreCase) >= 0);
                if (processMatch || ((framedApp || expected.Count == 0) && titleMatch)) matches.Add(window);
            }
            return matches;
        }

        private static HashSet<string> ProcessSet(ActionDefinition action)
        {
            IEnumerable<string> configured = action == null || action.ProcessNames == null
                ? new string[0] : action.ProcessNames;
            HashSet<string> names = new HashSet<string>(configured
                .Where(x => !string.IsNullOrWhiteSpace(x))
                .Select(NormalizeProcessName)
                .Where(x => !string.IsNullOrWhiteSpace(x)), StringComparer.OrdinalIgnoreCase);

            if (action != null && (action.Type == "app" || action.Type == "command") &&
                !string.IsNullOrWhiteSpace(action.Target))
            {
                string target = Environment.ExpandEnvironmentVariables(action.Target.Trim());
                if (target.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                {
                    try
                    {
                        string executable = Path.GetFileNameWithoutExtension(target);
                        if (!string.IsNullOrWhiteSpace(executable)) names.Add(executable);
                    }
                    catch { }
                }
            }
            return names;
        }

        private static string NormalizeProcessName(string value)
        {
            string cleaned = (value ?? string.Empty).Trim();
            try { return Path.GetFileNameWithoutExtension(cleaned); }
            catch { return cleaned.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? cleaned.Substring(0, cleaned.Length - 4) : cleaned; }
        }

        private static void AddProcessName(IntPtr handle, HashSet<string> names, Dictionary<uint, string> processCache)
        {
            uint processId;
            GetWindowThreadProcessId(handle, out processId);
            if (processId == 0) return;
            string cached;
            if (processCache.TryGetValue(processId, out cached))
            {
                if (!string.IsNullOrWhiteSpace(cached)) names.Add(cached);
                return;
            }
            try
            {
                using (Process process = Process.GetProcessById((int)processId))
                {
                    cached = process.ProcessName;
                    processCache[processId] = cached ?? string.Empty;
                    if (!string.IsNullOrWhiteSpace(cached)) names.Add(cached);
                }
            }
            catch { processCache[processId] = string.Empty; }
        }

        private static string ReadTitle(IntPtr handle)
        {
            int length = Math.Min(1024, Math.Max(0, GetWindowTextLength(handle)) + 1);
            if (length <= 1) return string.Empty;
            StringBuilder text = new StringBuilder(length);
            GetWindowText(handle, text, text.Capacity);
            return text.ToString().Trim();
        }

        private static string ReadClassName(IntPtr handle)
        {
            StringBuilder value = new StringBuilder(256);
            GetClassName(handle, value, value.Capacity);
            return value.ToString();
        }

        private static bool IsCloaked(IntPtr handle)
        {
            try
            {
                int cloaked;
                return DwmGetWindowAttribute(handle, DWMWA_CLOAKED, out cloaked, sizeof(int)) == 0 && cloaked != 0;
            }
            catch { return false; }
        }

        private static bool IsShellOrUtilityWindow(string className)
        {
            string[] ignored = { "Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd", "DV2ControlHost" };
            return ignored.Any(x => string.Equals(x, className, StringComparison.OrdinalIgnoreCase));
        }
    }
}
