using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    public sealed class ConfigEditorForm : Form
    {
        private readonly ActionStore _store;
        private readonly ListBox _list;
        private List<ActionDefinition> _actions;

        public ConfigEditorForm(ActionStore store)
        {
            _store = store;
            Text = "SyncDeck — Editar botões";
            Icon = BrandResources.ApplicationIcon;
            Width = 650; Height = 510;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(20, 22, 28);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10F);

            Label hint = new Label
            {
                Text = "Esses botões aparecem no celular. A ordem desta lista será mantida.",
                Location = new Point(22, 20), Size = new Size(590, 28), ForeColor = Color.FromArgb(180, 185, 195)
            };
            Controls.Add(hint);

            _list = new ListBox
            {
                Location = new Point(22, 53), Size = new Size(430, 370),
                BackColor = Color.FromArgb(31, 34, 42), ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle, Font = new Font("Segoe UI", 11F)
            };
            _list.DoubleClick += delegate { EditSelected(); };
            Controls.Add(_list);

            AddButton("Adicionar", 474, 53, delegate { AddNew(); });
            AddButton("Editar", 474, 103, delegate { EditSelected(); });
            AddButton("Excluir", 474, 153, delegate { DeleteSelected(); });
            AddButton("Subir", 474, 223, delegate { Move(-1); });
            AddButton("Descer", 474, 273, delegate { Move(1); });
            AddButton("Salvar", 474, 373, delegate { SaveAndClose(); }, true);

            LoadItems();
        }

        private void LoadItems()
        {
            _actions = _store.Load();
            RefreshList();
        }

        private void RefreshList()
        {
            int selected = _list.SelectedIndex;
            _list.Items.Clear();
            foreach (ActionDefinition action in _actions)
                _list.Items.Add((action.Enabled ? "●  " : "○  ") + action.Label + "   ·   " + TypeLabel(action.Type));
            if (_list.Items.Count > 0) _list.SelectedIndex = Math.Max(0, Math.Min(selected, _list.Items.Count - 1));
        }

        private void AddNew()
        {
            ActionDefinition action = new ActionDefinition
            {
                Id = "", Label = "Novo botão", Type = "app", Target = "",
                ProcessNames = new string[0], AppNames = new string[0],
                Icon = "app", Color = "#697386", Enabled = true, Closable = true
            };
            using (ActionEditorForm editor = new ActionEditorForm(action))
            {
                if (editor.ShowDialog() == DialogResult.OK) { _actions.Add(editor.Value); RefreshList(); _list.SelectedIndex = _actions.Count - 1; }
            }
        }

        private void EditSelected()
        {
            if (_list.SelectedIndex < 0) return;
            int index = _list.SelectedIndex;
            using (ActionEditorForm editor = new ActionEditorForm(_actions[index].Clone()))
            {
                if (editor.ShowDialog() == DialogResult.OK) { _actions[index] = editor.Value; RefreshList(); _list.SelectedIndex = index; }
            }
        }

        private void DeleteSelected()
        {
            if (_list.SelectedIndex < 0) return;
            if (MessageBox.Show("Excluir o botão '" + _actions[_list.SelectedIndex].Label + "'?", "SyncDeck",
                MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;
            _actions.RemoveAt(_list.SelectedIndex);
            RefreshList();
        }

        private void Move(int direction)
        {
            int from = _list.SelectedIndex;
            int to = from + direction;
            if (from < 0 || to < 0 || to >= _actions.Count) return;
            ActionDefinition item = _actions[from];
            _actions.RemoveAt(from); _actions.Insert(to, item);
            RefreshList(); _list.SelectedIndex = to;
        }

        private void SaveAndClose()
        {
            try
            {
                _store.Save(_actions);
                DialogResult = DialogResult.OK;
                Close();
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message, "Não foi possível salvar", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void AddButton(string text, int x, int y, EventHandler click, bool primary = false)
        {
            Button button = new Button
            {
                Text = text, Location = new Point(x, y), Size = new Size(130, 38),
                FlatStyle = FlatStyle.Flat, ForeColor = Color.White,
                BackColor = primary ? Color.FromArgb(80, 170, 120) : Color.FromArgb(43, 47, 57)
            };
            button.FlatAppearance.BorderSize = 0;
            button.Click += click;
            Controls.Add(button);
        }

        private static string TypeLabel(string value)
        {
            switch (value) { case "app": return "Aplicativo"; case "url": return "Site"; case "path": return "Pasta/arquivo"; case "command": return "Comando"; case "hotkey": return "Atalho"; default: return value; }
        }
    }

    public sealed class ActionEditorForm : Form
    {
        private readonly TextBox _id, _label, _target, _arguments, _processes, _apps, _fallback, _icon, _color;
        private readonly ComboBox _type;
        private readonly CheckBox _enabled, _closable, _confirm;
        public ActionDefinition Value { get; private set; }

        public ActionEditorForm(ActionDefinition action)
        {
            Value = action;
            Text = "SyncDeck — Botão";
            Icon = BrandResources.ApplicationIcon;
            Width = 610; Height = 650;
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            BackColor = Color.FromArgb(20, 22, 28); ForeColor = Color.White;
            Font = new Font("Segoe UI", 9.5F);

            _label = AddField("Nome", action.Label, 22, 22, 340);
            _id = AddField("Identificador", action.Id, 380, 22, 190);
            AddCaption("Tipo", 22, 88);
            _type = new ComboBox { Location = new Point(22, 110), Width = 180, DropDownStyle = ComboBoxStyle.DropDownList };
            _type.Items.AddRange(new object[] { "app", "url", "path", "command", "hotkey" });
            _type.SelectedItem = action.Type ?? "app";
            Controls.Add(_type);
            _target = AddField("Destino (executável, link, caminho ou teclas)", action.Target, 22, 156, 548);
            _arguments = AddField("Argumentos opcionais", action.Arguments, 22, 222, 548);
            _processes = AddField("Processos para trazer/fechar (separados por vírgula)", Join(action.ProcessNames), 22, 288, 548);
            _apps = AddField("Nomes no menu Iniciar (separados por vírgula)", Join(action.AppNames), 22, 354, 548);
            _fallback = AddField("Link alternativo", action.FallbackUrl, 22, 420, 548);
            _icon = AddField("Ícone alternativo", action.Icon, 22, 486, 170);
            _color = AddField("Cor alternativa", action.Color, 210, 486, 170);

            _enabled = AddCheck("Ativo", action.Enabled, 400, 486);
            _closable = AddCheck("Pode fechar", action.Closable, 400, 514);
            _confirm = AddCheck("Pedir confirmação", action.Confirm, 400, 542);

            Button cancel = Button("Cancelar", 330, 570, false); cancel.Click += delegate { DialogResult = DialogResult.Cancel; Close(); };
            Button save = Button("Salvar", 455, 570, true); save.Click += delegate { SaveValue(); };
            Controls.Add(cancel); Controls.Add(save);
        }

        private void SaveValue()
        {
            try
            {
                Value.Id = string.IsNullOrWhiteSpace(_id.Text) ? ActionStore.Slugify(_label.Text) : _id.Text.Trim().ToLowerInvariant();
                Value.Label = _label.Text.Trim();
                Value.Type = Convert.ToString(_type.SelectedItem);
                Value.Target = _target.Text.Trim();
                Value.Arguments = _arguments.Text.Trim();
                Value.ProcessNames = Split(_processes.Text);
                Value.AppNames = Split(_apps.Text);
                Value.FallbackUrl = _fallback.Text.Trim();
                Value.Icon = _icon.Text.Trim();
                Value.Color = _color.Text.Trim();
                Value.Enabled = _enabled.Checked;
                Value.Closable = _closable.Checked;
                Value.Confirm = _confirm.Checked;
                ActionStore.Validate(Value);
                DialogResult = DialogResult.OK; Close();
            }
            catch (Exception ex) { MessageBox.Show(ex.Message, "Revise o botão", MessageBoxButtons.OK, MessageBoxIcon.Warning); }
        }

        private TextBox AddField(string caption, string value, int x, int y, int width)
        {
            AddCaption(caption, x, y);
            TextBox box = new TextBox { Text = value ?? string.Empty, Location = new Point(x, y + 23), Width = width, BackColor = Color.FromArgb(34, 37, 45), ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle };
            Controls.Add(box); return box;
        }

        private void AddCaption(string text, int x, int y)
        {
            Controls.Add(new Label { Text = text, Location = new Point(x, y), AutoSize = true, ForeColor = Color.FromArgb(180, 185, 195) });
        }

        private void AddCaption(string text, int y) { AddCaption(text, 22, y); }
        private CheckBox AddCheck(string text, bool value, int x, int y)
        {
            CheckBox check = new CheckBox { Text = text, Checked = value, Location = new Point(x, y), AutoSize = true, ForeColor = Color.White };
            Controls.Add(check); return check;
        }
        private static Button Button(string text, int x, int y, bool primary)
        {
            Button button = new Button { Text = text, Location = new Point(x, y), Size = new Size(115, 38), FlatStyle = FlatStyle.Flat, ForeColor = Color.White, BackColor = primary ? Color.FromArgb(80, 170, 120) : Color.FromArgb(48, 52, 62) };
            button.FlatAppearance.BorderSize = 0; return button;
        }
        private static string[] Split(string value) { return (value ?? string.Empty).Split(',').Select(x => x.Trim()).Where(x => x.Length > 0).ToArray(); }
        private static string Join(string[] values) { return string.Join(", ", values ?? new string[0]); }
    }
}
