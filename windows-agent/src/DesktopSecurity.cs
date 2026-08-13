using System;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    public enum DesktopDecision
    {
        Approved,
        Denied,
        Busy,
        Unavailable
    }

    public sealed class DesktopSecurity
    {
        private readonly Control _dispatcher;
        private int _dialogOpen;

        public DesktopSecurity(Control dispatcher)
        {
            _dispatcher = dispatcher;
        }

        public DesktopDecision ApproveExecution(AuthContext auth, ActionDefinition action, string operation)
        {
            string title = operation == "open" ? "Executar ação protegida" : "Alterar janela";
            string detail = "Celular: " + SafeDevice(auth) + "\n" +
                "Ação: " + (action == null ? "Ação desconhecida" : Visible(action.Label)) + "\n" +
                "Tipo: " + (action == null ? "-" : Visible(action.Type)) + "\n" +
                "Destino: " + (action == null ? "-" : Visible(action.Target)) +
                (action == null || string.IsNullOrWhiteSpace(action.Arguments) ? string.Empty : "\nArgumentos: " + Visible(action.Arguments)) +
                (action == null || string.IsNullOrWhiteSpace(action.WorkingDirectory) ? string.Empty : "\nPasta de trabalho: " + Visible(action.WorkingDirectory));
            return Ask(title, detail, "Autorizar uma vez");
        }

        public DesktopDecision ApproveSave(AuthContext auth, ActionDefinition action)
        {
            string detail = "Celular: " + SafeDevice(auth) + "\n" +
                "Novo botão: " + Visible(action.Label) + "\n" +
                "Tipo: " + Visible(action.Type) + "\n" +
                "Destino: " + Visible(action.Target) +
                (string.IsNullOrWhiteSpace(action.Arguments) ? string.Empty : "\nArgumentos: " + Visible(action.Arguments)) +
                (string.IsNullOrWhiteSpace(action.WorkingDirectory) ? string.Empty : "\nPasta de trabalho: " + Visible(action.WorkingDirectory)) +
                (string.IsNullOrWhiteSpace(action.FallbackUrl) ? string.Empty : "\nLink alternativo: " + Visible(action.FallbackUrl));
            return Ask("Autorizar novo botão", detail, "Autorizar e salvar");
        }

        public PickedPath PickPath(AuthContext auth, string kind)
        {
            if (Interlocked.CompareExchange(ref _dialogOpen, 1, 0) != 0)
                throw new InvalidOperationException("Já existe uma solicitação aguardando no PC.");
            try
            {
                return Invoke(delegate
                {
                    if (string.Equals(kind, "folder", StringComparison.OrdinalIgnoreCase))
                    {
                        using (FolderBrowserDialog picker = new FolderBrowserDialog())
                        {
                            picker.Description = "O celular " + SafeDevice(auth) + " quer criar um botão. Escolha a pasta.";
                            picker.ShowNewFolderButton = false;
                            if (picker.ShowDialog() != DialogResult.OK || string.IsNullOrWhiteSpace(picker.SelectedPath)) return null;
                            return new PickedPath
                            {
                                Label = new DirectoryInfo(picker.SelectedPath).Name,
                                Type = "path",
                                Target = picker.SelectedPath,
                                ProcessNames = new string[0],
                                AppNames = new string[0],
                                Icon = "folder",
                                Color = "#F5B82E"
                            };
                        }
                    }

                    using (OpenFileDialog picker = new OpenFileDialog())
                    {
                        picker.Title = "Escolha o arquivo para o botão do SyncDeck";
                        picker.CheckFileExists = true;
                        picker.Multiselect = false;
                        picker.Filter = "Todos os arquivos (*.*)|*.*";
                        if (picker.ShowDialog() != DialogResult.OK || string.IsNullOrWhiteSpace(picker.FileName)) return null;
                        return new PickedPath
                        {
                            Label = Path.GetFileNameWithoutExtension(picker.FileName),
                            Type = "path",
                            Target = picker.FileName,
                            ProcessNames = new string[0],
                            AppNames = new string[0],
                            Icon = "app",
                            Color = "#8B5CF6"
                        };
                    }
                });
            }
            finally
            {
                Interlocked.Exchange(ref _dialogOpen, 0);
            }
        }

        private DesktopDecision Ask(string title, string detail, string approveText)
        {
            if (Interlocked.CompareExchange(ref _dialogOpen, 1, 0) != 0) return DesktopDecision.Busy;
            try
            {
                bool approved = Invoke(delegate
                {
                    using (ApprovalDialog dialog = new ApprovalDialog(title, detail, approveText))
                        return dialog.ShowDialog() == DialogResult.Yes;
                });
                return approved ? DesktopDecision.Approved : DesktopDecision.Denied;
            }
            catch
            {
                return DesktopDecision.Unavailable;
            }
            finally
            {
                Interlocked.Exchange(ref _dialogOpen, 0);
            }
        }

        private T Invoke<T>(Func<T> action)
        {
            if (_dispatcher == null || _dispatcher.IsDisposed) throw new InvalidOperationException("Interface do agente indisponível.");
            if (_dispatcher.InvokeRequired) return (T)_dispatcher.Invoke(action);
            return action();
        }

        private static string SafeDevice(AuthContext auth)
        {
            string device = auth == null || string.IsNullOrWhiteSpace(auth.DeviceName) ? "celular pareado" : Visible(auth.DeviceName.Trim());
            string address = auth == null || string.IsNullOrWhiteSpace(auth.RemoteAddress) ? string.Empty : " (" + Visible(auth.RemoteAddress) + ")";
            return device + address;
        }

        private static string Visible(string value)
        {
            return (value ?? string.Empty).Replace("\r", "\\r").Replace("\n", "\\n").Replace("\t", "\\t");
        }
    }

    internal sealed class ApprovalDialog : Form
    {
        private readonly Button _approve;
        private readonly System.Windows.Forms.Timer _timer;
        private int _remaining = 45;

        public ApprovalDialog(string title, string detail, string approveText)
        {
            Text = "SyncDeck — Confirmação de segurança";
            Width = 650;
            Height = 500;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            TopMost = true;
            BackColor = Color.FromArgb(18, 20, 26);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10F);

            Controls.Add(new Label
            {
                Text = title,
                Location = new Point(26, 22),
                Size = new Size(540, 34),
                Font = new Font("Segoe UI Semibold", 17F),
                ForeColor = Color.White
            });
            Controls.Add(new Label
            {
                Text = "Uma ação sensível foi solicitada pelo celular. Confira os dados antes de permitir.",
                Location = new Point(28, 64),
                Size = new Size(530, 42),
                ForeColor = Color.FromArgb(177, 184, 199)
            });
            Controls.Add(new TextBox
            {
                Text = detail,
                Location = new Point(28, 116),
                Size = new Size(578, 235),
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Both,
                WordWrap = false,
                BorderStyle = BorderStyle.FixedSingle,
                BackColor = Color.FromArgb(31, 35, 44),
                ForeColor = Color.FromArgb(236, 239, 245),
                Font = new Font("Consolas", 9.5F)
            });

            Button deny = new Button
            {
                Text = "Negar",
                Location = new Point(358, 390),
                Size = new Size(116, 42),
                DialogResult = DialogResult.No,
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(48, 52, 63),
                ForeColor = Color.White
            };
            deny.FlatAppearance.BorderSize = 0;
            Controls.Add(deny);

            _approve = new Button
            {
                Text = approveText + " (45s)",
                Location = new Point(486, 390),
                Size = new Size(120, 42),
                DialogResult = DialogResult.Yes,
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(32, 153, 105),
                ForeColor = Color.White
            };
            _approve.FlatAppearance.BorderSize = 0;
            Controls.Add(_approve);

            AcceptButton = deny;
            CancelButton = deny;
            _timer = new System.Windows.Forms.Timer { Interval = 1000 };
            _timer.Tick += delegate
            {
                _remaining--;
                _approve.Text = approveText + " (" + _remaining + "s)";
                if (_remaining <= 0) { DialogResult = DialogResult.No; Close(); }
            };
            Shown += delegate { Activate(); deny.Select(); _timer.Start(); };
            FormClosed += delegate { _timer.Stop(); _timer.Dispose(); };
        }
    }
}
