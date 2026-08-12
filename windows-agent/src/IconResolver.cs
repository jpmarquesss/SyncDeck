using Microsoft.Win32;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

namespace SyncDeck.Agent
{
    public sealed class IconResolver
    {
        private const uint SHGFI_ICON = 0x000000100;
        private const uint SHGFI_PIDL = 0x000000008;
        private const uint SHGFI_LARGEICON = 0x000000000;
        private const uint SHGFI_USEFILEATTRIBUTES = 0x000000010;
        private const uint FILE_ATTRIBUTE_NORMAL = 0x00000080;
        private const uint FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
        private const int IconSize = 128;

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct SHFILEINFO
        {
            public IntPtr hIcon;
            public int iIcon;
            public uint dwAttributes;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string szDisplayName;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 80)] public string szTypeName;
        }

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern uint PrivateExtractIcons(string fileName, int iconIndex, int width, int height,
            IntPtr[] icons, uint[] iconIds, uint iconCount, uint flags);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool DestroyIcon(IntPtr icon);

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SHGetFileInfo(string path, uint fileAttributes, ref SHFILEINFO info,
            uint infoSize, uint flags);

        [DllImport("shell32.dll", EntryPoint = "SHGetFileInfo", CharSet = CharSet.Unicode)]
        private static extern IntPtr SHGetFileInfoFromPidl(IntPtr pidl, uint fileAttributes, ref SHFILEINFO info,
            uint infoSize, uint flags);

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern int SHParseDisplayName(string name, IntPtr bindingContext, out IntPtr pidl,
            uint attributesIn, out uint attributesOut);

        private readonly ConcurrentDictionary<string, byte[]> _cache =
            new ConcurrentDictionary<string, byte[]>(StringComparer.OrdinalIgnoreCase);

        public string ImageKey(ActionDefinition action)
        {
            if (action == null) return "missing";
            string material = "v2\n" + action.Id + "\n" + action.Label + "\n" + action.Type + "\n" +
                action.Target + "\n" + action.Arguments + "\n" + action.WorkingDirectory + "\n" +
                action.FallbackUrl + "\n" + action.Icon + "\n" + action.Color + "\n" +
                string.Join("|", action.ProcessNames ?? new string[0]) + "\n" +
                string.Join("|", action.AppNames ?? new string[0]);
            using (SHA256 hash = SHA256.Create())
            {
                byte[] value = hash.ComputeHash(Encoding.UTF8.GetBytes(material));
                StringBuilder key = new StringBuilder(20);
                for (int i = 0; i < 10; i++) key.Append(value[i].ToString("x2"));
                return key.ToString();
            }
        }

        public byte[] Resolve(ActionDefinition action)
        {
            if (action == null) return null;
            string key = ImageKey(action);
            byte[] cached;
            if (_cache.TryGetValue(key, out cached)) return cached;

            byte[] resolved = ResolveCore(action);
            if (resolved == null || resolved.Length == 0) resolved = CreateFallback(action);
            if (resolved != null && resolved.Length > 0) _cache[key] = resolved;
            return resolved;
        }

        private static byte[] ResolveCore(ActionDefinition action)
        {
            byte[] themed = CreateThemedIcon(action);
            if (themed != null) return themed;

            string target = ResolveFileTarget(action.Target);
            byte[] image = IconFromEmbeddedResource(target, IsWindowsAppAlias(target));
            if (image != null) return image;

            string running = ResolveRunningExecutable(action.ProcessNames);
            image = IconFromEmbeddedResource(running, false);
            if (image != null) return image;

            string appId = FindStartAppId(action.AppNames, action.Label);
            image = IconFromAppId(appId);
            if (image != null) return image;

            string protocol = ResolveProtocolExecutable(action.Target);
            image = IconFromEmbeddedResource(protocol, false);
            if (image != null) return image;

            image = IconFromPath(target);
            if (image != null) return image;
            image = IconFromPath(running);
            if (image != null) return image;
            image = IconFromPath(protocol);
            if (image != null) return image;

            string expanded = Environment.ExpandEnvironmentVariables(action.Target ?? string.Empty);
            if (action.Type == "path" && !string.IsNullOrWhiteSpace(expanded))
            {
                image = IconFromShell(expanded, Directory.Exists(expanded), false);
                if (image != null) return image;
            }

            if (action.Type == "url")
            {
                image = IconFromShell(".html", false, true);
                if (image != null) return image;
            }
            return null;
        }

        private static byte[] CreateThemedIcon(ActionDefinition action)
        {
            string icon = action == null ? string.Empty : (action.Icon ?? string.Empty).Trim().ToLowerInvariant();
            if (icon != "power" && icon != "codex") return null;
            try
            {
                using (Bitmap canvas = new Bitmap(IconSize, IconSize, PixelFormat.Format32bppArgb))
                using (Graphics graphics = Graphics.FromImage(canvas))
                using (MemoryStream memory = new MemoryStream())
                {
                    graphics.Clear(Color.Transparent);
                    graphics.SmoothingMode = SmoothingMode.AntiAlias;
                    Color color;
                    try { color = ColorTranslator.FromHtml(action.Color ?? (icon == "power" ? "#EF4444" : "#10A37F")); }
                    catch { color = icon == "power" ? Color.FromArgb(239, 68, 68) : Color.FromArgb(16, 163, 127); }
                    using (SolidBrush background = new SolidBrush(Color.FromArgb(245, color)))
                    using (GraphicsPath shape = RoundedRectangle(new Rectangle(4, 4, 120, 120), 28))
                        graphics.FillPath(background, shape);

                    if (icon == "power")
                    {
                        using (Pen pen = new Pen(Color.White, 11F))
                        {
                            pen.StartCap = LineCap.Round;
                            pen.EndCap = LineCap.Round;
                            graphics.DrawArc(pen, 29, 30, 70, 70, -43, 266);
                            graphics.DrawLine(pen, 64, 20, 64, 61);
                        }
                    }
                    else
                    {
                        Point[] sparkle =
                        {
                            new Point(64, 17), new Point(74, 50), new Point(108, 64), new Point(74, 78),
                            new Point(64, 111), new Point(54, 78), new Point(20, 64), new Point(54, 50)
                        };
                        using (SolidBrush foreground = new SolidBrush(Color.White))
                            graphics.FillPolygon(foreground, sparkle);
                    }
                    canvas.Save(memory, ImageFormat.Png);
                    return memory.ToArray();
                }
            }
            catch { return null; }
        }

        private static byte[] IconFromEmbeddedResource(string path, bool skipExecutable)
        {
            if (string.IsNullOrWhiteSpace(path)) return null;
            try
            {
                if (Directory.Exists(path)) return IconFromShell(path, true, false);
                if (!File.Exists(path) || skipExecutable) return null;
                return ExtractExecutableIcon(path);
            }
            catch { return null; }
        }

        private static bool IsWindowsAppAlias(string path)
        {
            return !string.IsNullOrWhiteSpace(path) &&
                path.IndexOf("\\Microsoft\\WindowsApps\\", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static byte[] IconFromPath(string path)
        {
            if (string.IsNullOrWhiteSpace(path)) return null;
            try
            {
                if (Directory.Exists(path)) return IconFromShell(path, true, false);
                if (!File.Exists(path)) return null;
                byte[] image = ExtractExecutableIcon(path);
                return image ?? IconFromShell(path, false, false);
            }
            catch { return null; }
        }

        private static byte[] ExtractExecutableIcon(string path)
        {
            IntPtr[] handles = { IntPtr.Zero };
            uint[] identifiers = { 0 };
            try
            {
                uint count = PrivateExtractIcons(path, 0, IconSize, IconSize, handles, identifiers, 1, 0);
                if (count == 0 || count == uint.MaxValue || handles[0] == IntPtr.Zero) return null;
                return IconHandleToPng(handles[0]);
            }
            catch { return null; }
            finally
            {
                if (handles[0] != IntPtr.Zero) DestroyIcon(handles[0]);
            }
        }

        private static byte[] IconFromAppId(string appId)
        {
            if (string.IsNullOrWhiteSpace(appId)) return null;
            IntPtr pidl = IntPtr.Zero;
            try
            {
                uint ignored;
                int result = SHParseDisplayName("shell:AppsFolder\\" + appId.Trim(), IntPtr.Zero, out pidl, 0, out ignored);
                if (result != 0 || pidl == IntPtr.Zero) return null;
                SHFILEINFO info = new SHFILEINFO();
                SHGetFileInfoFromPidl(pidl, 0, ref info, (uint)Marshal.SizeOf(typeof(SHFILEINFO)),
                    SHGFI_PIDL | SHGFI_ICON | SHGFI_LARGEICON);
                if (info.hIcon == IntPtr.Zero) return null;
                try { return IconHandleToPng(info.hIcon); }
                finally { DestroyIcon(info.hIcon); }
            }
            catch { return null; }
            finally
            {
                if (pidl != IntPtr.Zero) Marshal.FreeCoTaskMem(pidl);
            }
        }

        private static byte[] IconFromShell(string path, bool directory, bool useAttributes)
        {
            if (string.IsNullOrWhiteSpace(path)) return null;
            try
            {
                SHFILEINFO info = new SHFILEINFO();
                uint flags = SHGFI_ICON | SHGFI_LARGEICON;
                if (useAttributes) flags |= SHGFI_USEFILEATTRIBUTES;
                uint attributes = directory ? FILE_ATTRIBUTE_DIRECTORY : FILE_ATTRIBUTE_NORMAL;
                SHGetFileInfo(path, attributes, ref info, (uint)Marshal.SizeOf(typeof(SHFILEINFO)), flags);
                if (info.hIcon == IntPtr.Zero) return null;
                try { return IconHandleToPng(info.hIcon); }
                finally { DestroyIcon(info.hIcon); }
            }
            catch { return null; }
        }

        private static byte[] IconHandleToPng(IntPtr handle)
        {
            if (handle == IntPtr.Zero) return null;
            using (Icon icon = (Icon)Icon.FromHandle(handle).Clone())
            using (Bitmap source = icon.ToBitmap())
            using (Bitmap canvas = new Bitmap(IconSize, IconSize, PixelFormat.Format32bppArgb))
            using (Graphics graphics = Graphics.FromImage(canvas))
            using (MemoryStream memory = new MemoryStream())
            {
                graphics.Clear(Color.Transparent);
                graphics.CompositingMode = CompositingMode.SourceOver;
                graphics.CompositingQuality = CompositingQuality.HighQuality;
                graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
                graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
                graphics.SmoothingMode = SmoothingMode.HighQuality;

                float scale = Math.Min((float)IconSize / source.Width, (float)IconSize / source.Height);
                int width = Math.Max(1, (int)Math.Round(source.Width * scale));
                int height = Math.Max(1, (int)Math.Round(source.Height * scale));
                int left = (IconSize - width) / 2;
                int top = (IconSize - height) / 2;
                graphics.DrawImage(source, new Rectangle(left, top, width, height));
                canvas.Save(memory, ImageFormat.Png);
                return memory.ToArray();
            }
        }

        private static byte[] CreateFallback(ActionDefinition action)
        {
            try
            {
                using (Bitmap canvas = new Bitmap(IconSize, IconSize, PixelFormat.Format32bppArgb))
                using (Graphics graphics = Graphics.FromImage(canvas))
                using (MemoryStream memory = new MemoryStream())
                {
                    graphics.SmoothingMode = SmoothingMode.AntiAlias;
                    Color color;
                    try { color = ColorTranslator.FromHtml(action.Color ?? "#697386"); }
                    catch { color = Color.FromArgb(105, 115, 134); }
                    using (SolidBrush background = new SolidBrush(Color.FromArgb(235, color)))
                    using (GraphicsPath shape = RoundedRectangle(new Rectangle(4, 4, 120, 120), 28))
                        graphics.FillPath(background, shape);

                    string initial = string.IsNullOrWhiteSpace(action.Label) ? "•" : action.Label.Trim().Substring(0, 1).ToUpperInvariant();
                    using (Font font = new Font("Segoe UI", 56F, FontStyle.Bold, GraphicsUnit.Pixel))
                    using (SolidBrush foreground = new SolidBrush(Color.White))
                    using (StringFormat format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
                        graphics.DrawString(initial, font, foreground, new RectangleF(4, 1, 120, 120), format);
                    canvas.Save(memory, ImageFormat.Png);
                    return memory.ToArray();
                }
            }
            catch { return null; }
        }

        private static GraphicsPath RoundedRectangle(Rectangle rectangle, int radius)
        {
            int diameter = radius * 2;
            GraphicsPath path = new GraphicsPath();
            path.AddArc(rectangle.Left, rectangle.Top, diameter, diameter, 180, 90);
            path.AddArc(rectangle.Right - diameter, rectangle.Top, diameter, diameter, 270, 90);
            path.AddArc(rectangle.Right - diameter, rectangle.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(rectangle.Left, rectangle.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }

        private static string ResolveFileTarget(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return null;
            string target = Environment.ExpandEnvironmentVariables(value.Trim().Trim('"'));
            Uri uri;
            if (Uri.TryCreate(target, UriKind.Absolute, out uri) && uri.Scheme != Uri.UriSchemeFile) return null;
            if (File.Exists(target) || Directory.Exists(target)) return target;

            string name;
            try { name = Path.GetFileName(target); }
            catch { return null; }
            if (string.IsNullOrWhiteSpace(name)) return null;

            string[] registryPaths =
            {
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\" + name,
                @"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths\" + name
            };
            foreach (RegistryKey root in new[] { Registry.CurrentUser, Registry.LocalMachine })
            {
                foreach (string keyPath in registryPaths)
                {
                    try
                    {
                        using (RegistryKey key = root.OpenSubKey(keyPath))
                        {
                            string path = key == null ? null : key.GetValue(null) as string;
                            path = Environment.ExpandEnvironmentVariables((path ?? string.Empty).Trim().Trim('"'));
                            if (File.Exists(path)) return path;
                        }
                    }
                    catch { }
                }
            }

            foreach (string folder in new[] { Environment.SystemDirectory, Environment.GetFolderPath(Environment.SpecialFolder.Windows) })
            {
                try
                {
                    string path = Path.Combine(folder, name);
                    if (File.Exists(path)) return path;
                }
                catch { }
            }

            string environmentPath = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
            foreach (string folder in environmentPath.Split(';'))
            {
                try
                {
                    string path = Path.Combine(folder.Trim().Trim('"'), name);
                    if (File.Exists(path)) return path;
                }
                catch { }
            }
            return null;
        }

        private static string ResolveRunningExecutable(string[] processNames)
        {
            foreach (string raw in processNames ?? new string[0])
            {
                string name;
                try { name = Path.GetFileNameWithoutExtension(raw == null ? string.Empty : raw.Trim()); }
                catch { continue; }
                if (string.IsNullOrWhiteSpace(name)) continue;
                Process[] processes;
                try { processes = Process.GetProcessesByName(name); }
                catch { continue; }
                string found = null;
                foreach (Process process in processes)
                {
                    try
                    {
                        string path = process.MainModule == null ? null : process.MainModule.FileName;
                        if (found == null && File.Exists(path)) found = path;
                    }
                    catch { }
                    finally { process.Dispose(); }
                }
                if (found != null) return found;
            }
            return null;
        }

        private static string ResolveProtocolExecutable(string target)
        {
            if (string.IsNullOrWhiteSpace(target)) return null;
            int separator = target.IndexOf(':');
            if (separator <= 0 || target.Contains("://")) return null;
            string protocol = target.Substring(0, separator);
            string[] keys =
            {
                @"Software\Classes\" + protocol + @"\shell\open\command",
                protocol + @"\shell\open\command"
            };
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(keys[0]))
                {
                    string value = key == null ? null : key.GetValue(null) as string;
                    string path = ExecutableFromCommand(value);
                    if (File.Exists(path)) return path;
                }
            }
            catch { }
            try
            {
                using (RegistryKey key = Registry.ClassesRoot.OpenSubKey(keys[1]))
                {
                    string value = key == null ? null : key.GetValue(null) as string;
                    string path = ExecutableFromCommand(value);
                    if (File.Exists(path)) return path;
                }
            }
            catch { }
            return null;
        }

        private static string ExecutableFromCommand(string command)
        {
            if (string.IsNullOrWhiteSpace(command)) return null;
            string expanded = Environment.ExpandEnvironmentVariables(command.Trim());
            string path;
            if (expanded.StartsWith("\"", StringComparison.Ordinal))
            {
                int end = expanded.IndexOf('"', 1);
                path = end > 1 ? expanded.Substring(1, end - 1) : null;
            }
            else
            {
                int marker = expanded.IndexOf(".exe", StringComparison.OrdinalIgnoreCase);
                path = marker >= 0 ? expanded.Substring(0, marker + 4).Trim() : null;
            }
            return string.IsNullOrWhiteSpace(path) ? null : path;
        }

        private static string FindStartAppId(string[] configuredNames, string label)
        {
            List<string> names = (configuredNames ?? new string[0])
                .Where(x => !string.IsNullOrWhiteSpace(x)).Select(x => x.Trim()).ToList();
            if (!string.IsNullOrWhiteSpace(label)) names.Add(label.Trim());
            names = names.Distinct(StringComparer.OrdinalIgnoreCase).Take(12).ToList();
            if (names.Count == 0) return null;

            try
            {
                string array = string.Join(",", names.Select(x => "'" + x.Replace("'", "''") + "'"));
                string script = "$names=@(" + array + ");$apps=Get-StartApps;foreach($wanted in $names){" +
                    "$a=$apps|Where-Object{$_.Name -eq $wanted}|Select-Object -First 1;" +
                    "if(!$a){$a=$apps|Where-Object{$_.Name -like ('*'+$wanted+'*')}|Select-Object -First 1};" +
                    "if($a){[Console]::OutputEncoding=[Text.Encoding]::UTF8;Write-Output $a.AppID;break}}";
                string encoded = Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
                ProcessStartInfo start = new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = "-NoProfile -NonInteractive -WindowStyle Hidden -EncodedCommand " + encoded,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8
                };
                using (Process process = Process.Start(start))
                {
                    if (process == null) return null;
                    if (!process.WaitForExit(5000))
                    {
                        try { process.Kill(); } catch { }
                        return null;
                    }
                    string output = process.StandardOutput.ReadToEnd();
                    return output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
                        .Select(x => x.Trim()).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x));
                }
            }
            catch { return null; }
        }
    }
}
