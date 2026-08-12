package com.syncdeck.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(11, 12, 15);
    private static final int MUTED = Color.rgb(145, 150, 162);
    private static final int ACCENT = Color.rgb(88, 216, 155);
    private ApiClient api;
    private TextView statusDot, statusText, subtitle, emptyText;
    private GridLayout grid;
    private ProgressBar progress;
    private TextView addButton;
    private List<SyncAction> actions = new ArrayList<>();
    private final Map<String, Bitmap> iconMemory = new HashMap<>();
    private final Map<String, View> actionCards = new HashMap<>();
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable statePoll = () -> refreshActionStates();
    private boolean landscape;
    private int landscapeCardHeight;
    private boolean firstResume = true;
    private boolean resumed;
    private boolean stateRequestRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        api = new ApiClient(this);
        buildInterface();
        configureWindow();
        if (api.isConfigured()) refresh();
        else grid.postDelayed(this::showConnectionDialog, 300);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (firstResume) { firstResume = false; return; }
        if (api.isPaired()) refresh();
    }

    @Override
    protected void onPause() {
        resumed = false;
        stateHandler.removeCallbacks(statePoll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stateHandler.removeCallbacksAndMessages(null);
        api.shutdown();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && landscape) configureWindow();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(appBackground());
        root.setPadding(landscape ? dp(6) : dp(18), landscape ? dp(6) : dp(18),
                landscape ? dp(6) : dp(18), landscape ? dp(6) : 0);

        if (!landscape) {
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout titles = new LinearLayout(this);
            titles.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("SyncDeck", 28, Color.WHITE, Typeface.BOLD);
            subtitle = text("Seu PC em um toque", 13, MUTED, Typeface.NORMAL);
            titles.addView(title);
            titles.addView(subtitle);
            top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            addButton = roundIcon("＋");
            addButton.setContentDescription("Adicionar botão");
            addButton.setOnClickListener(view -> openEditor(null));
            top.addView(addButton, squareParams(44, 8));

            TextView refreshButton = roundIcon("↻");
            refreshButton.setContentDescription("Atualizar");
            refreshButton.setOnClickListener(view -> refresh());
            top.addView(refreshButton, squareParams(44, 8));

            TextView settings = roundIcon("•••");
            settings.setContentDescription("Conexão");
            settings.setOnClickListener(view -> showConnectionDialog());
            top.addView(settings, squareParams(44, 0));
            root.addView(top);

            LinearLayout status = new LinearLayout(this);
            status.setOrientation(LinearLayout.HORIZONTAL);
            status.setGravity(Gravity.CENTER_VERTICAL);
            status.setPadding(0, dp(13), 0, dp(13));
            statusDot = text("●", 12, Color.rgb(109, 115, 128), Typeface.NORMAL);
            statusText = text("Verificando conexão", 12, MUTED, Typeface.NORMAL);
            status.addView(statusDot);
            LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            statusTextParams.leftMargin = dp(7);
            status.addView(statusText, statusTextParams);
            status.setOnClickListener(view -> refresh());
            root.addView(status);
        }

        FrameLayout content = new FrameLayout(this);
        content.setClipChildren(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(!landscape);
        grid = new GridLayout(this);
        grid.setColumnCount(landscape ? 3 : 2);
        grid.setUseDefaultMargins(false);
        grid.setClipChildren(false);
        grid.setPadding(0, 0, 0, landscape ? 0 : dp(28));
        scroll.addView(grid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyText = text(landscape ? "Gire o celular para configurar a conexão." :
                "Conecte o SyncDeck ao agente do Windows para carregar seus botões.", 15, MUTED, Typeface.NORMAL);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(28), dp(40), dp(28), dp(40));
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        content.addView(emptyText, emptyParams);

        progress = new ProgressBar(this);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        content.addView(progress, progressParams);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        setLoading(false);
        setOnline(false, "Ainda não conectado");
    }

    private void refresh() {
        if (!api.isConfigured()) { setOnline(false, "Configure o endereço do PC"); showConnectionDialog(); return; }
        setLoading(true);
        setOnline(false, api.isPaired() ? "Localizando PC…" : "Verificando conexão");
        api.getStatusWithRecovery(new ApiClient.Callback<ApiClient.Status>() {
            @Override public void onSuccess(ApiClient.Status value, String message) {
                if (subtitle != null) subtitle.setText(value.name);
                if (value.endpointRecovered) showMessage("PC encontrado automaticamente em " + value.host + ".");
                if (!api.isPaired()) {
                    setLoading(false);
                    setOnline(false, "Pareamento necessário");
                    showConnectionDialog();
                    return;
                }
                loadActions();
            }
            @Override public void onError(String message) {
                setLoading(false); setOnline(false, "PC indisponível"); showMessage(message);
            }
        });
    }

    private void loadActions() {
        api.getActions(false, new ApiClient.Callback<List<SyncAction>>() {
            @Override public void onSuccess(List<SyncAction> value, String message) {
                actions = value;
                renderActions();
                setLoading(false);
                setOnline(true, "Conectado ao PC");
                startStatePolling(900);
            }
            @Override public void onError(String message) {
                setLoading(false); setOnline(false, "Conexão recusada"); showMessage(message);
            }
        });
    }

    private void renderActions() {
        grid.removeAllViews();
        actionCards.clear();
        if (landscape) {
            int rows = Math.max(1, (actions.size() + 2) / 3);
            int visibleRows = Math.min(3, rows);
            float density = getResources().getDisplayMetrics().density;
            int screenHeight = Math.round(getResources().getDisplayMetrics().heightPixels / density);
            int height = (screenHeight - 12 - visibleRows * 10) / visibleRows;
            landscapeCardHeight = dp(Math.max(92, Math.min(168, height)));
        }
        for (SyncAction action : actions) {
            View card = actionCard(action);
            actionCards.put(action.id, card);
            grid.addView(card, cardParams());
        }
        emptyText.setVisibility(actions.isEmpty() ? View.VISIBLE : View.GONE);
        if (actions.isEmpty()) emptyText.setText(landscape ? "Nenhum aplicativo cadastrado. Gire o celular para adicionar." :
                "Nenhum botão cadastrado. Toque em ＋ para criar o primeiro.");
    }

    private View actionCard(SyncAction action) {
        FrameLayout card = new FrameLayout(this);
        card.setPadding(landscape ? dp(8) : dp(15), landscape ? dp(8) : dp(14),
                landscape ? dp(8) : dp(13), landscape ? dp(8) : dp(13));
        applyCardStyle(card, action);
        card.setForeground(selectableForeground());
        updateCardDescription(card, action);

        if (landscape) {
            int heightDp = Math.round(landscapeCardHeight / getResources().getDisplayMetrics().density);
            int logoSize = heightDp >= 140 ? 92 : heightDp >= 100 ? 76 : 64;
            FrameLayout logo = actionImage(action, logoSize, false);
            card.addView(logo, new FrameLayout.LayoutParams(dp(logoSize), dp(logoSize), Gravity.CENTER));
        } else {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.START);
            content.addView(actionImage(action, 58, true), new LinearLayout.LayoutParams(dp(58), dp(58)));

            TextView name = text(action.label, 15, Color.rgb(246, 247, 249), Typeface.BOLD);
            name.setGravity(Gravity.BOTTOM);
            name.setMaxLines(2);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            nameParams.topMargin = dp(13);
            content.addView(name, nameParams);
            card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (action.closable) {
                TextView close = text("×", 18, Color.rgb(190, 194, 202), Typeface.NORMAL);
                close.setGravity(Gravity.CENTER);
                close.setBackground(roundRect(Color.argb(24, 255, 255, 255), 14, Color.TRANSPARENT, 0));
                FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(31), dp(31), Gravity.TOP | Gravity.END);
                card.addView(close, closeParams);
                close.setOnClickListener(view -> confirmClose(action, card));
            }
        }

        card.setOnClickListener(view -> runOpen(action, card));
        card.setOnLongClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (landscape) showActionMenu(action, card);
            else loadEditable(action.id);
            return true;
        });
        return card;
    }

    private FrameLayout actionImage(SyncAction action, int size, boolean roundedFallback) {
        FrameLayout holder = new FrameLayout(this);
        TextView fallback = text(glyph(action.icon), glyphSize(action.icon), Color.WHITE, Typeface.BOLD);
        fallback.setGravity(Gravity.CENTER);
        int color = safeColor(action.color);
        fallback.setBackground(roundRect(Color.argb(74, Color.red(color), Color.green(color), Color.blue(color)),
                roundedFallback ? 17 : 22, Color.argb(35, 255, 255, 255), 1));
        holder.addView(fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Imagem de " + action.label);
        holder.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadActionImage(action, image, fallback);
        return holder;
    }

    private void loadActionImage(SyncAction action, ImageView image, TextView fallback) {
        String token = action.id + ":" + (action.imageKey == null ? "" : action.imageKey);
        image.setTag(token);
        Bitmap remembered = iconMemory.get(token);
        if (remembered != null) {
            image.setImageBitmap(remembered);
            fallback.setVisibility(View.INVISIBLE);
        }
        api.getActionIcon(action, new ApiClient.Callback<Bitmap>() {
            @Override public void onSuccess(Bitmap value, String message) {
                if (value == null || isFinishing() || isDestroyed()) return;
                iconMemory.put(token, value);
                if (!token.equals(image.getTag())) return;
                image.setImageBitmap(value);
                fallback.setVisibility(View.INVISIBLE);
            }
            @Override public void onError(String message) {
                if (token.equals(image.getTag()) && image.getDrawable() == null) fallback.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showActionMenu(SyncAction action, View card) {
        List<String> labels = new ArrayList<>();
        List<Runnable> commands = new ArrayList<>();
        labels.add("Abrir no PC");
        commands.add(() -> runOpen(action, card));
        if (action.closable) {
            labels.add("Fechar janela");
            commands.add(() -> confirmClose(action, card));
        }
        labels.add("Editar botão");
        commands.add(() -> loadEditable(action.id));
        new AlertDialog.Builder(this)
                .setTitle(action.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> commands.get(which).run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runOpen(SyncAction action, View card) {
        if (action.confirm) {
            boolean shutdown = isShutdownAction(action);
            new AlertDialog.Builder(this)
                    .setTitle(shutdown ? "Desligar o PC?" : "Executar “" + action.label + "”?")
                    .setMessage(shutdown
                            ? "Salve seu trabalho. O Windows começará a desligar em 5 segundos."
                            : "Essa ação foi marcada como sensível.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton(shutdown ? "Desligar" : "Executar",
                            (dialog, which) -> execute(action, "open", true, card))
                    .show();
        } else execute(action, "open", false, card);
    }

    private void confirmClose(SyncAction action, View card) {
        if (action.windowCount > 1) {
            String[] choices = {
                    "Fechar somente a janela mais recente",
                    "Fechar todas as " + action.windowCount + " janelas"
            };
            new AlertDialog.Builder(this)
                    .setTitle(action.windowCount + " janelas abertas")
                    .setMessage("O que deseja fazer com “" + action.label + "”?")
                    .setItems(choices, (dialog, which) -> execute(action, which == 0 ? "close" : "close-all", true, card))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Fechar “" + action.label + "”?")
                .setMessage(action.isOpen ? "A janela receberá um pedido normal para fechar." :
                        "O estado pode ter mudado. O SyncDeck verificará novamente no Windows.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Fechar", (dialog, which) -> execute(action, "close", true, card))
                .show();
    }

    private void execute(SyncAction action, String operation, boolean confirmed, View card) {
        animateCommand(card, !operation.startsWith("close"));
        api.execute(action, operation, confirmed, new ApiClient.Callback<Boolean>() {
            @Override public void onSuccess(Boolean value, String message) {
                if (card != null) card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showMessage(isShutdownAction(action) ? "Desligamento iniciado no PC." :
                        operation.startsWith("close") ? "Comando para fechar enviado." : action.label + " aberto no PC.");
                stateHandler.removeCallbacks(statePoll);
                stateHandler.postDelayed(statePoll, operation.startsWith("close") ? 700 : 450);
            }
            @Override public void onError(String message) { setOnline(false, "Falha ao executar"); showMessage(message); }
        });
    }

    private void loadEditable(String id) {
        setLoading(true);
        api.getActions(true, new ApiClient.Callback<List<SyncAction>>() {
            @Override public void onSuccess(List<SyncAction> value, String message) {
                setLoading(false);
                for (SyncAction item : value) if (item.id.equalsIgnoreCase(id)) { openEditor(item); return; }
                showMessage("Botão não encontrado.");
            }
            @Override public void onError(String message) { setLoading(false); showMessage(message); }
        });
    }

    private void openEditor(SyncAction action) {
        if (!api.isPaired()) { showMessage("Pareie o celular antes de editar botões."); return; }
        new ActionEditorDialog(this, action, new ActionEditorDialog.Listener() {
            @Override public void onSave(SyncAction value) {
                setLoading(true);
                api.saveAction(value, new ApiClient.Callback<Boolean>() {
                    @Override public void onSuccess(Boolean result, String message) { showMessage("Botão salvo."); loadActions(); }
                    @Override public void onError(String message) { setLoading(false); showMessage(message); }
                });
            }
            @Override public void onDelete(SyncAction value) {
                setLoading(true);
                api.deleteAction(value, new ApiClient.Callback<Boolean>() {
                    @Override public void onSuccess(Boolean result, String message) { showMessage("Botão excluído."); loadActions(); }
                    @Override public void onError(String message) { setLoading(false); showMessage(message); }
                });
            }
        }).show();
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(4), dp(24), dp(4));

        TextView help = text("No Windows, clique no ícone do SyncDeck perto do relógio e escolha “Parear celular”.", 13, MUTED, Typeface.NORMAL);
        help.setPadding(0, 0, 0, dp(12));
        form.addView(help);
        EditText host = input("IP sem a porta (ex.: 192.168.0.10)", api.getHost(), false);
        EditText port = input("Porta", Integer.toString(api.getPort()), true);
        EditText code = input("Código de 6 números", "", true);
        code.setLetterSpacing(.12f);
        form.addView(host); form.addView(port); form.addView(code);
        TextView result = text("Primeiro verifique o PC. A impressão digital precisa ser igual nas duas telas.", 12, MUTED, Typeface.NORMAL);
        result.setPadding(0, dp(15), 0, dp(8));
        form.addView(result);

        final ApiClient.Status[] verified = {null};
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Conectar ao PC")
                .setView(form)
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Verificar PC", null)
                .setNeutralButton(api.isPaired() ? "Desparear" : "Parear", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            Button verify = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button pair = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            verify.setTextColor(ACCENT);
            pair.setTextColor(api.isPaired() ? Color.rgb(232, 103, 103) : ACCENT);
            verify.setOnClickListener(view -> {
                try {
                    String address = host.getText().toString().trim();
                    int selectedPort = Integer.parseInt(port.getText().toString().trim());
                    int separator = address.lastIndexOf(':');
                    if (separator > 0 && address.substring(separator + 1).matches("^[0-9]{4,5}$")) {
                        selectedPort = Integer.parseInt(address.substring(separator + 1));
                        address = address.substring(0, separator).trim();
                        host.setText(address);
                        port.setText(Integer.toString(selectedPort));
                    }
                    api.setEndpoint(address, selectedPort);
                } catch (Exception ex) { showMessage(ex.getMessage()); return; }
                verify.setEnabled(false); verify.setText("Verificando…"); result.setText("Conectando ao agente…");
                api.getStatus(new ApiClient.Callback<ApiClient.Status>() {
                    @Override public void onSuccess(ApiClient.Status value, String message) {
                        verified[0] = value;
                        verify.setEnabled(true); verify.setText("Verificar novamente");
                        result.setText("PC: " + value.name + "\nImpressão digital: " + value.fingerprint +
                                (value.pairingAvailable ? "\nCódigo disponível por 5 minutos." : "\nAbra “Parear celular” novamente no Windows."));
                        if (!api.isPaired()) { pair.setText("Parear"); pair.setTextColor(ACCENT); }
                        else { setOnline(true, "PC localizado"); }
                    }
                    @Override public void onError(String message) { verify.setEnabled(true); verify.setText("Verificar PC"); result.setText(message); }
                });
            });
            pair.setOnClickListener(view -> {
                if (api.isPaired()) {
                    new AlertDialog.Builder(this).setTitle("Desparear celular?")
                            .setMessage("Será necessário gerar um novo código no Windows.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Desparear", (d, w) -> { api.clearPairing(); pair.setText("Parear"); pair.setTextColor(ACCENT); result.setText("Pareamento removido deste celular."); })
                            .show();
                    return;
                }
                if (verified[0] == null) { showMessage("Verifique o PC primeiro."); return; }
                pair.setEnabled(false); pair.setText("Pareando…");
                api.pair(verified[0], code.getText().toString().trim(), new ApiClient.Callback<Boolean>() {
                    @Override public void onSuccess(Boolean value, String message) { dialog.dismiss(); showMessage("Celular pareado com segurança."); refresh(); }
                    @Override public void onError(String message) { pair.setEnabled(true); pair.setText("Parear"); result.setText(message); }
                });
            });
        });
        dialog.show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) emptyText.setVisibility(View.GONE);
        else if (actions.isEmpty()) emptyText.setVisibility(View.VISIBLE);
        if (addButton != null) addButton.setEnabled(!loading);
    }

    private void setOnline(boolean online, String message) {
        if (statusDot == null || statusText == null) return;
        statusDot.setTextColor(online ? ACCENT : Color.rgb(125, 130, 142));
        statusText.setText(message);
        statusText.setTextColor(online ? Color.rgb(173, 227, 201) : MUTED);
    }

    private void startStatePolling(long delayMillis) {
        stateHandler.removeCallbacks(statePoll);
        if (resumed && api.isPaired() && !actions.isEmpty()) stateHandler.postDelayed(statePoll, delayMillis);
    }

    private void refreshActionStates() {
        if (!resumed || !api.isPaired() || actions.isEmpty()) return;
        if (stateRequestRunning) { startStatePolling(700); return; }
        stateRequestRunning = true;
        api.getActionStates(new ApiClient.Callback<List<ApiClient.ActionState>>() {
            @Override public void onSuccess(List<ApiClient.ActionState> values, String message) {
                stateRequestRunning = false;
                Map<String, ApiClient.ActionState> states = new HashMap<>();
                for (ApiClient.ActionState state : values) states.put(state.id, state);
                for (SyncAction action : actions) {
                    ApiClient.ActionState state = states.get(action.id);
                    if (state == null) continue;
                    boolean changed = action.isOpen != state.isOpen || action.windowCount != state.windowCount;
                    action.isOpen = state.isOpen;
                    action.windowCount = state.windowCount;
                    View card = actionCards.get(action.id);
                    if (card != null) {
                        applyCardStyle(card, action);
                        updateCardDescription(card, action);
                        if (changed) animateStateChange(card, action.isOpen);
                    }
                }
                startStatePolling(2400);
            }

            @Override public void onError(String message) {
                stateRequestRunning = false;
                startStatePolling(4200);
            }
        });
    }

    private void animateCommand(View card, boolean opening) {
        if (card == null) return;
        card.animate().cancel();
        card.animate().scaleX(opening ? .95f : .92f).scaleY(opening ? .95f : .92f)
                .alpha(opening ? .82f : .68f).setDuration(100)
                .withEndAction(() -> card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(190));
    }

    private void animateStateChange(View card, boolean opened) {
        card.animate().cancel();
        if (opened) {
            card.setScaleX(.97f); card.setScaleY(.97f); card.setAlpha(.78f);
            card.animate().scaleX(1.025f).scaleY(1.025f).alpha(1f).setDuration(180)
                    .withEndAction(() -> card.animate().scaleX(1f).scaleY(1f).setDuration(150));
        } else {
            card.setAlpha(.62f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260);
        }
    }

    private void applyCardStyle(View card, SyncAction action) {
        int color = safeColor(action.color);
        int radius = landscape ? 20 : 24;
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                action.isOpen ? new int[]{
                        Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(220, 27, 30, 40),
                        Color.argb(48, Color.red(color), Color.green(color), Color.blue(color))
                } : new int[]{Color.argb(214, 32, 35, 45), Color.argb(198, 22, 24, 32), Color.argb(210, 27, 29, 39)});
        glass.setCornerRadius(dp(radius));
        glass.setStroke(dp(action.isOpen ? 2 : 1), action.isOpen
                ? Color.argb(220, Color.red(color), Color.green(color), Color.blue(color))
                : Color.argb(42, 255, 255, 255));

        if (action.isOpen) {
            GradientDrawable glow = new GradientDrawable();
            glow.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
            glow.setCornerRadius(dp(radius + 2));
            glow.setStroke(dp(2), Color.argb(105, Color.red(color), Color.green(color), Color.blue(color)));
            LayerDrawable layers = new LayerDrawable(new Drawable[]{glow, glass});
            layers.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
            card.setBackground(layers);
            card.setElevation(dp(11));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) card.setOutlineSpotShadowColor(color);
        } else {
            card.setBackground(glass);
            card.setElevation(dp(2));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                card.setOutlineSpotShadowColor(Color.argb(90, 0, 0, 0));
        }
    }

    private void updateCardDescription(View card, SyncAction action) {
        String state = action.isOpen ? ". Aberto no PC" + (action.windowCount > 1 ? ", " + action.windowCount + " janelas" : "") : ". Fechado";
        card.setContentDescription(action.label + state + ". Toque para abrir; segure para mostrar as opções.");
    }

    private Drawable appBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 9, 14), Color.rgb(16, 19, 31), Color.rgb(8, 11, 18), Color.rgb(11, 12, 15)});
    }

    private void configureWindow() {
        Window window = getWindow();
        View decorView = window.getDecorView();
        if (landscape) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                if (landscape) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            decorView.setSystemUiVisibility(landscape ?
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE :
                    View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private TextView roundIcon(String value) {
        TextView view = text(value, 20, Color.rgb(229, 231, 235), Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(220, 38, 41, 54), Color.argb(205, 24, 27, 36)});
        glass.setCornerRadius(dp(16));
        glass.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        view.setBackground(glass);
        view.setElevation(dp(3));
        return view;
    }

    private EditText input(String hint, String value, boolean numeric) {
        EditText input = new EditText(this);
        input.setHint(hint); input.setText(value); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(105, 110, 122));
        input.setSingleLine(true); input.setTextSize(16); input.setPadding(dp(12), 0, dp(12), 0);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        input.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(65, 69, 80)));
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return input;
    }

    private GridLayout.LayoutParams cardParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; params.height = landscape ? landscapeCardHeight : dp(154);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(landscape ? dp(4) : dp(5), landscape ? dp(4) : dp(5),
                landscape ? dp(4) : dp(5), landscape ? dp(4) : dp(5));
        return params;
    }

    private LinearLayout.LayoutParams squareParams(int size, int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
        params.leftMargin = dp(leftMargin);
        return params;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius));
        if (strokeWidth > 0) shape.setStroke(dp(strokeWidth), strokeColor);
        return shape;
    }

    private android.graphics.drawable.Drawable selectableForeground() {
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        android.content.res.TypedArray typed = obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable drawable = typed.getDrawable(0);
        typed.recycle();
        return drawable;
    }

    private static int safeColor(String value) {
        try { return Color.parseColor(value); } catch (Exception ignored) { return Color.rgb(105, 115, 134); }
    }

    private static boolean isShutdownAction(SyncAction action) {
        return action != null && ("shutdown-pc".equalsIgnoreCase(action.id) ||
                ("command".equalsIgnoreCase(action.type) && "power".equalsIgnoreCase(action.icon)));
    }

    private static String glyph(String icon) {
        if (icon == null) return "◆";
        switch (icon) {
            case "chrome": return "◎";
            case "whatsapp": return "W";
            case "outlook": return "O";
            case "folder": return "▰";
            case "terminal": return ">_";
            case "codex": return "✦";
            case "calculator": return "＋";
            case "download": return "↓";
            case "globe": return "◉";
            case "power": return "⏻";
            case "settings": return "⚙";
            default: return "◆";
        }
    }

    private static int glyphSize(String icon) { return "terminal".equals(icon) ? 18 : 28; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void showMessage(String value) { Toast.makeText(this, value == null ? "Não foi possível concluir." : value, Toast.LENGTH_SHORT).show(); }
}
