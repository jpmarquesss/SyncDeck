using System;
using System.Diagnostics;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    public sealed class AgentContext : ApplicationContext
    {
        private readonly NotifyIcon _tray;
        private readonly ToolStripMenuItem _startupItem;
        private readonly ActionStore _actions;
        private readonly ClientStore _clients;
        private readonly PairingManager _pairing;
        private readonly SettingsStore _settingsStore;
        private readonly AgentSettings _settings;
        private readonly DeckServer _server;
        private readonly Control _dispatcher;
        private readonly DesktopSecurity _desktopSecurity;
        private PairingForm _pairingForm;

        public AgentContext()
        {
            _settingsStore = new SettingsStore();
            _settings = _settingsStore.Load();
            _actions = new ActionStore();
            _clients = new ClientStore();
            _pairing = new PairingManager();
            _dispatcher = new Control();
            _dispatcher.CreateControl();
            _desktopSecurity = new DesktopSecurity(_dispatcher);

            ContextMenuStrip menu = new ContextMenuStrip();
            menu.Items.Add("Status e conexão", null, delegate { ShowStatus(); });
            menu.Items.Add("Parear celular", null, delegate { ShowPairing(); });
            menu.Items.Add("Editar botões", null, delegate { ShowEditor(); });
            menu.Items.Add(new ToolStripSeparator());
            _startupItem = new ToolStripMenuItem("Iniciar com o Windows") { CheckOnClick = true };
            menu.Items.Add(_startupItem);
            menu.Items.Add("Abrir pasta de configuração", null, delegate { Process.Start(DataPaths.Root); });
            menu.Items.Add("Revogar celulares pareados", null, delegate { RevokeDevices(); });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Sair", null, delegate { ExitAgent(); });

            _tray = new NotifyIcon
            {
                Icon = BrandResources.ApplicationIcon,
                Text = "SyncDeck — agente conectado",
                Visible = true,
                ContextMenuStrip = menu
            };
            _tray.DoubleClick += delegate { ShowStatus(); };

            try
            {
                _server = new DeckServer(_settings.Port, _actions, _clients, _pairing, _desktopSecurity);
                _server.PhonePaired += OnPhonePaired;
                _server.Start();
            }
            catch (Exception ex)
            {
                MessageBox.Show("O SyncDeck não conseguiu iniciar na porta " + _settings.Port + ".\n\n" + ex.Message,
                    "SyncDeck", MessageBoxButtons.OK, MessageBoxIcon.Error);
                ExitAgent();
                return;
            }

            ConfigureStartupDefault();
            if (StartupManager.IsEnabled())
            {
                try { StartupManager.SetEnabled(true); } catch { }
            }
            _startupItem.Checked = StartupManager.IsEnabled();
            _startupItem.CheckedChanged += StartupItemCheckedChanged;

            _tray.ShowBalloonTip(2500, "SyncDeck ativo", "O celular pode controlar este PC pela rede local.", ToolTipIcon.Info);
            if (_clients.Count == 0)
            {
                Timer timer = new Timer { Interval = 700 };
                timer.Tick += delegate { timer.Stop(); timer.Dispose(); ShowPairing(); };
                timer.Start();
            }
        }

        private void StartupItemCheckedChanged(object sender, EventArgs e)
        {
            ToggleStartup();
        }

        private void ConfigureStartupDefault()
        {
            if (_settings.StartupConfigured) return;
            try { StartupManager.SetEnabled(true); } catch { }
            _settings.StartupConfigured = true;
            _settingsStore.Save(_settings);
        }

        private void ToggleStartup()
        {
            try
            {
                StartupManager.SetEnabled(_startupItem.Checked);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Não foi possível alterar a inicialização automática.\n\n" + ex.Message,
                    "SyncDeck", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void ShowStatus()
        {
            string[] ips = NetworkInfo.LocalIPv4Addresses();
            string addresses = ips.Length == 0 ? "Nenhum IP local encontrado" :
                string.Join(Environment.NewLine, ips.Select(x => x + ":" + _settings.Port));
            MessageBox.Show(
                "Agente: conectado — versão 1.0.1\n" +
                "Celulares pareados: " + _clients.Count + "\n\n" +
                "Endereço para o aplicativo:\n" + addresses + "\n\n" +
                "A conexão aceita somente endereços da rede local.",
                "SyncDeck — Status", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private void ShowPairing()
        {
            if (_pairingForm != null && !_pairingForm.IsDisposed)
            {
                _pairingForm.Activate();
                return;
            }
            _pairing.Begin();
            _pairingForm = new PairingForm(_pairing, _settings.Port);
            _pairingForm.FormClosed += delegate { _pairingForm = null; };
            _pairingForm.Show();
            _pairingForm.Activate();
        }

        private void ShowEditor()
        {
            using (ConfigEditorForm editor = new ConfigEditorForm(_actions)) editor.ShowDialog();
        }

        private void RevokeDevices()
        {
            if (_clients.Count == 0)
            {
                MessageBox.Show("Não existem celulares pareados.", "SyncDeck", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            if (MessageBox.Show("Revogar todos os celulares? Eles precisarão ser pareados novamente.",
                "SyncDeck", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes)
            {
                _clients.RevokeAll();
                MessageBox.Show("Celulares revogados.", "SyncDeck", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        private void OnPhonePaired(object sender, EventArgs e)
        {
            if (_dispatcher != null && !_dispatcher.IsDisposed && _dispatcher.InvokeRequired)
            {
                _dispatcher.BeginInvoke(new Action(delegate { OnPhonePaired(sender, e); }));
                return;
            }
            if (_tray == null) return;
            _tray.ShowBalloonTip(2500, "Celular pareado", "A conexão segura com o SyncDeck foi concluída.", ToolTipIcon.Info);
            if (_pairingForm != null && !_pairingForm.IsDisposed)
                _pairingForm.BeginInvoke(new Action(delegate { _pairingForm.ShowPaired(); }));
        }

        private void ExitAgent()
        {
            if (_server != null) _server.Dispose();
            if (_pairing != null) _pairing.Dispose();
            if (_dispatcher != null) _dispatcher.Dispose();
            if (_tray != null) { _tray.Visible = false; _tray.Dispose(); }
            ExitThread();
        }
    }
}
