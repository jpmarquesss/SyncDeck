using Microsoft.Win32;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    public sealed class ActionExecutor
    {
        private const int SW_RESTORE = 9;
        private const uint WM_CLOSE = 0x0010;
        private const byte VK_MENU = 0x12;
        private const uint KEYEVENTF_KEYUP = 0x0002;

        [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hWnd);
        [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
        [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll", CharSet = CharSet.Auto)] private static extern bool PostMessage(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam);
        [DllImport("user32.dll")] private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

        public ExecutionResult Execute(ActionDefinition action, string operation)
        {
            if (action == null || !action.Enabled) return ExecutionResult.Failure("Ação não encontrada ou desativada.");
            try
            {
                if (string.Equals(operation, "close", StringComparison.OrdinalIgnoreCase))
                    return Close(action, false);
                if (string.Equals(operation, "close-all", StringComparison.OrdinalIgnoreCase))
                    return Close(action, true);

                if ((action.Type == "app" || action.Type == "command") && FocusExisting(action))
                    return ExecutionResult.Success(action.Label + " foi trazido para frente.");

                switch (action.Type)
                {
                    case "app": return LaunchApp(action);
                    case "url": return LaunchUrl(action);
                    case "path": return LaunchPath(action);
                    case "command": return LaunchCommand(action);
                    case "hotkey": return SendHotkey(action);
                    default: return ExecutionResult.Failure("Tipo de ação não suportado.");
                }
            }
            catch (Exception ex)
            {
                return ExecutionResult.Failure("Não foi possível executar: " + SafeMessage(ex.Message));
            }
        }

        private ExecutionResult LaunchApp(ActionDefinition action)
        {
            if (TryStart(action.Target, ChromeProfileResolver.ArgumentsFor(action), action.WorkingDirectory))
                return ExecutionResult.Success(action.Label + " foi aberto.");
            if (TryLaunchStartApp(action.AppNames))
                return ExecutionResult.Success(action.Label + " foi aberto.");
            if (!string.IsNullOrWhiteSpace(action.FallbackUrl))
                return LaunchUrl(new ActionDefinition { Target = action.FallbackUrl, Label = action.Label });
            return ExecutionResult.Failure("O aplicativo não foi encontrado. Edite o botão e confira o destino.");
        }

        private ExecutionResult LaunchUrl(ActionDefinition action)
        {
            string value = action == null ? string.Empty : action.Target;
            string label = action == null ? "Site" : action.Label;
            Uri uri;
            if (!Uri.TryCreate(value, UriKind.Absolute, out uri) ||
                (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
                return ExecutionResult.Failure("Link inválido.");
            string browser = action == null ? string.Empty : (action.Arguments ?? string.Empty).Trim();
            if (string.Equals(browser, "chrome", StringComparison.OrdinalIgnoreCase))
            {
                ActionDefinition chrome = new ActionDefinition
                {
                    Target = "chrome.exe",
                    Arguments = uri.AbsoluteUri,
                    ProcessNames = new[] { "chrome" }
                };
                if (TryStart(chrome.Target, ChromeProfileResolver.ArgumentsFor(chrome), string.Empty))
                    return ExecutionResult.Success(label + " foi aberto no Chrome.");
            }
            Process.Start(new ProcessStartInfo { FileName = uri.AbsoluteUri, UseShellExecute = true });
            return ExecutionResult.Success(label + " foi aberto.");
        }

        private ExecutionResult LaunchPath(ActionDefinition action)
        {
            string target = Environment.ExpandEnvironmentVariables(action.Target);
            if (!target.StartsWith("shell:", StringComparison.OrdinalIgnoreCase) &&
                !File.Exists(target) && !Directory.Exists(target))
                return ExecutionResult.Failure("A pasta ou arquivo não foi encontrado: " + target);
            Process.Start(new ProcessStartInfo { FileName = target, UseShellExecute = true });
            return ExecutionResult.Success(action.Label + " foi aberto.");
        }

        private ExecutionResult LaunchCommand(ActionDefinition action)
        {
            string executable = ResolveExecutable(Environment.ExpandEnvironmentVariables(action.Target));
            if (string.IsNullOrWhiteSpace(executable)) return ExecutionResult.Failure("Executável não encontrado.");
            ProcessStartInfo start = new ProcessStartInfo
            {
                FileName = executable,
                Arguments = action.Arguments ?? string.Empty,
                UseShellExecute = false,
                WorkingDirectory = ResolveWorkingDirectory(action.WorkingDirectory)
            };
            Process.Start(start);
            return ExecutionResult.Success(action.Label + " foi executado.");
        }

        private ExecutionResult SendHotkey(ActionDefinition action)
        {
            SendKeys.SendWait(action.Target);
            return ExecutionResult.Success("Atalho enviado.");
        }

        public ActionState[] GetStates(IEnumerable<ActionDefinition> actions)
        {
            List<WindowRecord> windows = WindowInspector.Capture();
            return (actions ?? new ActionDefinition[0]).Select(action =>
            {
                int count = WindowInspector.Match(action, windows).Count;
                return new ActionState { Id = action.Id, IsOpen = count > 0, WindowCount = count };
            }).ToArray();
        }

        private ExecutionResult Close(ActionDefinition action, bool closeAll)
        {
            if (!action.Closable) return ExecutionResult.Failure("Esse botão não permite fechar janelas.");
            List<WindowRecord> matches = WindowInspector.Match(action, WindowInspector.Capture());
            if (matches.Count == 0) return ExecutionResult.Failure(action.Label + " não possui uma janela aberta.");

            IntPtr foreground = GetForegroundWindow();
            WindowRecord focused = matches.FirstOrDefault(x => x.Handle == foreground);
            if (focused != null)
            {
                matches.Remove(focused);
                matches.Insert(0, focused);
            }

            int sent = 0;
            IEnumerable<WindowRecord> targets = closeAll ? (IEnumerable<WindowRecord>)matches : matches.Take(1);
            foreach (WindowRecord window in targets)
                if (PostMessage(window.Handle, WM_CLOSE, IntPtr.Zero, IntPtr.Zero)) sent++;
            if (sent == 0) return ExecutionResult.Failure("O Windows não permitiu fechar a janela de " + action.Label + ".");
            return ExecutionResult.Success(closeAll && sent > 1
                ? sent + " janelas de " + action.Label + " receberam o comando para fechar."
                : "A janela de " + action.Label + " recebeu o comando para fechar.");
        }

        private static bool FocusExisting(ActionDefinition action)
        {
            List<WindowRecord> matches = WindowInspector.Match(action, WindowInspector.Capture());
            if (matches.Count == 0) return false;
            IntPtr foreground = GetForegroundWindow();
            WindowRecord selected = matches.FirstOrDefault(x => x.Handle == foreground) ?? matches[0];
            IntPtr handle = selected.Handle;
            ShowWindow(handle, SW_RESTORE);
            keybd_event(VK_MENU, 0, 0, UIntPtr.Zero);
            keybd_event(VK_MENU, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
            SetForegroundWindow(handle);
            return true;
        }

        private static bool TryStart(string target, string arguments, string workingDirectory)
        {
            if (string.IsNullOrWhiteSpace(target)) return false;
            try
            {
                string expanded = Environment.ExpandEnvironmentVariables(target);
                string resolved = ResolveExecutable(expanded) ?? expanded;
                ProcessStartInfo start = new ProcessStartInfo
                {
                    FileName = resolved,
                    Arguments = arguments ?? string.Empty,
                    UseShellExecute = true,
                    WorkingDirectory = ResolveWorkingDirectory(workingDirectory)
                };
                Process.Start(start);
                return true;
            }
            catch { return false; }
        }

        private static string ResolveExecutable(string target)
        {
            if (string.IsNullOrWhiteSpace(target)) return null;
            if (File.Exists(target)) return target;
            if (target.Contains(":") && !target.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) return target;
            string name = Path.GetFileName(target);
            string[] registryPaths =
            {
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\" + name,
                @"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths\" + name
            };
            foreach (RegistryKey root in new[] { Registry.CurrentUser, Registry.LocalMachine })
            {
                foreach (string path in registryPaths)
                {
                    try
                    {
                        using (RegistryKey key = root.OpenSubKey(path))
                        {
                            string value = key == null ? null : key.GetValue(null) as string;
                            if (!string.IsNullOrWhiteSpace(value) && File.Exists(value)) return value;
                        }
                    }
                    catch { }
                }
            }
            string system = Path.Combine(Environment.SystemDirectory, name);
            if (File.Exists(system)) return system;
            return target;
        }

        private static string ResolveWorkingDirectory(string value)
        {
            string expanded = Environment.ExpandEnvironmentVariables(value ?? string.Empty);
            return Directory.Exists(expanded) ? expanded : Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }

        private static bool TryLaunchStartApp(string[] appNames)
        {
            string[] names = (appNames ?? new string[0]).Where(x => !string.IsNullOrWhiteSpace(x)).ToArray();
            if (names.Length == 0) return false;
            try
            {
                string array = string.Join(",", names.Select(x => "'" + x.Replace("'", "''") + "'"));
                string script = "$names=@(" + array + ");$apps=Get-StartApps;" +
                    "$a=$apps|Where-Object{$n=$_.Name;@($names|Where-Object{$n -like ('*'+$_+'*')}).Count -gt 0}|Select-Object -First 1;" +
                    "if($a){Start-Process ('shell:AppsFolder\\'+$a.AppID);exit 0}else{exit 2}";
                string encoded = Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
                using (Process process = Process.Start(new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = "-NoProfile -NonInteractive -WindowStyle Hidden -EncodedCommand " + encoded,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }))
                {
                    if (process == null) return false;
                    process.WaitForExit(4000);
                    return process.HasExited && process.ExitCode == 0;
                }
            }
            catch { return false; }
        }

        private static string SafeMessage(string message)
        {
            if (string.IsNullOrWhiteSpace(message)) return "erro desconhecido";
            return message.Length <= 180 ? message : message.Substring(0, 180);
        }
    }
}
