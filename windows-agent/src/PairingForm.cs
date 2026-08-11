using System;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    public sealed class PairingForm : Form
    {
        private readonly PairingManager _pairing;
        private readonly int _port;
        private readonly Label _code;
        private readonly Label _countdown;
        private readonly Timer _timer;

        public PairingForm(PairingManager pairing, int port)
        {
            _pairing = pairing;
            _port = port;
            Text = "SyncDeck — Parear celular";
            Width = 500;
            Height = 465;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(17, 19, 24);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10F);

            Label title = Label("Conectar o Android", 24F, FontStyle.Bold);
            title.Location = new Point(28, 24); title.AutoSize = true;
            Controls.Add(title);

            Label instructions = Label("No aplicativo, informe o endereço abaixo, compare a impressão digital e digite o código.", 10F, FontStyle.Regular);
            instructions.Location = new Point(31, 72); instructions.Size = new Size(425, 48);
            Controls.Add(instructions);

            string[] ips = NetworkInfo.LocalIPv4Addresses();
            string address = ips.Length == 0 ? "IP local não encontrado" : string.Join("  ou  ", ips.Select(x => x + ":" + _port));
            AddCaption("ENDEREÇO DO PC", 126);
            AddValue(address, 149, 11F);

            AddCaption("IMPRESSÃO DIGITAL", 199);
            AddValue(_pairing.Fingerprint, 222, 13F);

            AddCaption("CÓDIGO DE 6 DÍGITOS", 272);
            _code = AddValue(_pairing.CurrentCode, 295, 29F);
            _code.ForeColor = Color.FromArgb(118, 231, 180);

            _countdown = Label(string.Empty, 9F, FontStyle.Regular);
            _countdown.Location = new Point(32, 355); _countdown.AutoSize = true;
            _countdown.ForeColor = Color.FromArgb(170, 175, 188);
            Controls.Add(_countdown);

            Button renew = new Button
            {
                Text = "Gerar novo código",
                Location = new Point(305, 347),
                Size = new Size(150, 38),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(39, 43, 52),
                ForeColor = Color.White
            };
            renew.FlatAppearance.BorderColor = Color.FromArgb(75, 80, 92);
            renew.Click += delegate { _pairing.Begin(); _code.Text = _pairing.CurrentCode; UpdateCountdown(); };
            Controls.Add(renew);

            _timer = new Timer { Interval = 1000 };
            _timer.Tick += delegate { UpdateCountdown(); };
            _timer.Start();
            UpdateCountdown();
        }

        public void ShowPaired()
        {
            _timer.Stop();
            _code.Text = "PAREADO";
            _countdown.Text = "Conexão concluída. Esta janela pode ser fechada.";
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            _timer.Stop();
            _timer.Dispose();
            base.OnFormClosed(e);
        }

        private void UpdateCountdown()
        {
            TimeSpan remaining = _pairing.ExpiresUtc - DateTime.UtcNow;
            if (remaining.TotalSeconds <= 0)
            {
                _code.Text = "EXPIRADO";
                _countdown.Text = "Gere um novo código para continuar.";
            }
            else _countdown.Text = "Expira em " + Math.Ceiling(remaining.TotalSeconds) + " segundos.";
        }

        private void AddCaption(string text, int y)
        {
            Label label = Label(text, 8.5F, FontStyle.Bold);
            label.Location = new Point(32, y); label.AutoSize = true;
            label.ForeColor = Color.FromArgb(135, 143, 158);
            Controls.Add(label);
        }

        private Label AddValue(string text, int y, float size)
        {
            Label label = Label(text, size, FontStyle.Bold);
            label.Location = new Point(31, y); label.AutoSize = true;
            Controls.Add(label);
            return label;
        }

        private static Label Label(string text, float size, FontStyle style)
        {
            return new Label { Text = text, Font = new Font("Segoe UI", size, style), ForeColor = Color.White, BackColor = Color.Transparent };
        }
    }
}
