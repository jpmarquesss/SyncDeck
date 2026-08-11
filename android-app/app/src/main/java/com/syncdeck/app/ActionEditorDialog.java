package com.syncdeck.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public final class ActionEditorDialog {
    public interface Listener {
        void onSave(SyncAction action);
        void onDelete(SyncAction action);
    }

    private static final String[] TYPE_VALUES = {"app", "url", "path", "command", "hotkey"};
    private static final String[] TYPE_LABELS = {"Aplicativo", "Site", "Pasta ou arquivo", "Comando", "Atalho de teclado"};
    private static final String[] ICON_VALUES = {"app", "chrome", "whatsapp", "outlook", "folder", "terminal", "codex", "calculator", "download", "globe", "power", "settings"};
    private static final String[] COLORS = {"#4285F4", "#25D366", "#1473E6", "#F5B82E", "#8B5CF6", "#EC4899", "#F97316", "#10A37F", "#64748B"};

    private final Activity activity;
    private final SyncAction original;
    private final Listener listener;
    private EditText label, id, target, arguments, processes, appNames, fallback, color;
    private Spinner type, icon;
    private CheckBox enabled, closable, confirm;

    public ActionEditorDialog(Activity activity, SyncAction action, Listener listener) {
        this.activity = activity;
        this.original = action;
        this.listener = listener;
    }

    public void show() {
        SyncAction value = original == null ? new SyncAction() : original.copy();
        ScrollView scroll = new ScrollView(activity);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(18));
        scroll.addView(form);

        label = field(form, "Nome do botão", value.label, false);
        id = field(form, "Identificador", value.id, false);
        type = spinner(form, "Tipo", TYPE_LABELS, indexOf(TYPE_VALUES, value.type));
        target = field(form, "Destino", value.target, false);
        arguments = field(form, "Argumentos opcionais", value.arguments, false);
        processes = field(form, "Processos para trazer/fechar, separados por vírgula", join(value.processNames), false);
        appNames = field(form, "Nomes no menu Iniciar, separados por vírgula", join(value.appNames), false);
        fallback = field(form, "Link alternativo", value.fallbackUrl, false);
        icon = spinner(form, "Ícone alternativo (se o Windows não encontrar a imagem)", ICON_VALUES, indexOf(ICON_VALUES, value.icon));
        color = field(form, "Cor do ícone alternativo", value.color, false);
        addPalette(form);

        enabled = check(form, "Botão ativo", value.enabled);
        closable = check(form, "Mostrar opção para fechar a janela", value.closable);
        confirm = check(form, "Pedir confirmação antes de executar", value.confirm);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(original == null ? "Novo botão" : "Editar botão")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null);
        if (original != null) builder.setNeutralButton("Excluir", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(63, 201, 139));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save(dialog, value));
            if (original != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.rgb(235, 91, 91));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> confirmDelete(dialog));
            }
        });
        dialog.getWindow();
        dialog.show();
    }

    private void save(AlertDialog dialog, SyncAction value) {
        value.label = label.getText().toString().trim();
        value.id = id.getText().toString().trim();
        value.type = TYPE_VALUES[type.getSelectedItemPosition()];
        value.target = target.getText().toString().trim();
        value.arguments = arguments.getText().toString().trim();
        value.processNames = SyncAction.split(processes.getText().toString());
        value.appNames = SyncAction.split(appNames.getText().toString());
        value.fallbackUrl = fallback.getText().toString().trim();
        value.icon = ICON_VALUES[icon.getSelectedItemPosition()];
        value.color = color.getText().toString().trim().toUpperCase();
        value.enabled = enabled.isChecked();
        value.closable = closable.isChecked();
        value.confirm = confirm.isChecked() || "command".equals(value.type);

        if (value.label.isEmpty() || value.label.length() > 40) { toast("Informe um nome de até 40 caracteres."); return; }
        if (value.target.isEmpty()) { toast("Informe o destino do botão."); return; }
        if (!value.color.matches("^#[0-9A-Fa-f]{6}$")) { toast("Use uma cor como #4285F4."); return; }
        if ("url".equals(value.type) && !(value.target.startsWith("https://") || value.target.startsWith("http://"))) {
            toast("O site deve começar com https:// ou http://."); return;
        }
        value.id = value.normalizeId();
        dialog.dismiss();
        listener.onSave(value);
    }

    private void confirmDelete(AlertDialog editor) {
        new AlertDialog.Builder(activity)
                .setTitle("Excluir botão?")
                .setMessage("O botão “" + original.label + "” será removido do celular e do PC.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> { editor.dismiss(); listener.onDelete(original); })
                .show();
    }

    private EditText field(LinearLayout parent, String caption, String value, boolean number) {
        parent.addView(caption(caption));
        EditText input = new EditText(activity);
        input.setText(value == null ? "" : value);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(110, 115, 126));
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        input.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{}}, new int[]{Color.rgb(70, 74, 86)}));
        parent.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return input;
    }

    private Spinner spinner(LinearLayout parent, String caption, String[] values, int selected) {
        parent.addView(caption(caption));
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected));
        parent.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return spinner;
    }

    private CheckBox check(LinearLayout parent, String text, boolean checked) {
        CheckBox box = new CheckBox(activity);
        box.setText(text); box.setTextColor(Color.WHITE); box.setChecked(checked);
        box.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(63, 201, 139), Color.rgb(95, 99, 111)}));
        parent.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        return box;
    }

    private TextView caption(String value) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextColor(Color.rgb(160, 165, 176)); text.setTextSize(12);
        text.setPadding(0, dp(14), 0, 0);
        return text;
    }

    private void addPalette(LinearLayout parent) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String value : COLORS) {
            View dot = new View(activity);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.parseColor(value)); background.setShape(GradientDrawable.OVAL);
            dot.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
            params.setMargins(0, dp(8), dp(10), dp(8));
            row.addView(dot, params);
            dot.setOnClickListener(view -> color.setText(value));
        }
        scroll.addView(row);
        parent.addView(scroll);
    }

    private void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static int indexOf(String[] values, String value) { int index = Arrays.asList(values).indexOf(value); return index < 0 ? 0 : index; }
    private static String join(String[] values) { return values == null ? "" : String.join(", ", values); }
}
