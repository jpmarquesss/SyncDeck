@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.syncdeck.app

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: DeckController
    private var landscapeUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = DeckController(this)
        setContent {
            SyncDeckTheme {
                SyncDeckApp(controller) { setLandscapeUi(it) }
            }
        }
        controller.start()
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }

    private fun setLandscapeUi(landscape: Boolean) {
        if (landscapeUi == landscape) return
        landscapeUi = landscape
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    if (landscape) {
                        hide(WindowInsets.Type.systemBars())
                        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else show(WindowInsets.Type.systemBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (landscape) {
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                } else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }
}

@Composable
private fun SyncDeckTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF72F5AD),
        onPrimary = Color(0xFF071B13),
        secondary = Color(0xFF86AFFF),
        background = Color(0xFF070A09),
        surface = Color(0xFF171A23),
        onSurface = Color(0xFFF5F7FB),
        error = Color(0xFFFF7F8D),
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@Composable
private fun SyncDeckApp(controller: DeckController, setLandscape: (Boolean) -> Unit) {
    val state by controller.state
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(landscape) { setLandscape(landscape) }
    DisposableEffect(Unit) { onDispose { setLandscape(false) } }
    state.notice?.let { notice ->
        LaunchedEffect(notice.id) {
            snackbar.showSnackbar(notice.text)
            controller.consumeNotice(notice.id)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF070A09), Color(0xFF101713), Color(0xFF070A09)),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(1400f, 2200f),
            ),
        ),
    ) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(12.dp)) },
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(
                    start = if (landscape) 8.dp else 17.dp,
                    end = if (landscape) 8.dp else 17.dp,
                    top = if (landscape) 8.dp else insets.calculateTopPadding() + 7.dp,
                    bottom = if (landscape) 8.dp else insets.calculateBottomPadding(),
                ),
            ) {
                if (!landscape) {
                    Header(
                        pcName = state.pcName,
                        onAdd = controller::showAddWizard,
                        onRefresh = controller::refresh,
                        onSettings = controller::showConnection,
                    )
                    StatusPill(state.status, state.tone, controller::refresh)
                    Spacer(Modifier.height(13.dp))
                }
                DeckGrid(
                    actions = state.actions,
                    busy = state.busyActionIds,
                    landscape = landscape,
                    onOpen = controller::requestOpen,
                    onClose = controller::requestClose,
                    onMenu = controller::openMenu,
                    loadIcon = controller::loadIcon,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color(0xE61B1F28), shadowElevation = 16.dp) {
                        CircularProgressIndicator(Modifier.padding(17.dp).size(30.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }

    state.pendingOpen?.let { action -> OpenConfirmation(action, controller::dismissOpen, controller::confirmOpen) }
    state.pendingClose?.let { action -> CloseConfirmation(action, controller::dismissClose, controller::confirmClose) }
    state.menuAction?.let { action -> ActionMenu(action, controller) }
    state.pendingDelete?.let { action -> DeleteConfirmation(action, controller::dismissDelete, controller::confirmDelete) }
    if (state.showConnection) ConnectionDialog(controller, controller::hideConnection)
    if (state.showWizard) ActionWizard(controller, state.editingAction, controller::hideWizard)
}

@Composable
private fun AmbientGlow() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF50AA78).copy(alpha = .13f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(70f, 120f),
                radius = 720f,
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF658DFF).copy(alpha = .08f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(1050f, 1050f),
                radius = 900f,
            ),
        ),
    )
}

@Composable
private fun Header(pcName: String, onAdd: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SyncDeckBrandMark(Modifier.size(25.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text("SyncDeck", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text(pcName, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HeaderButton("＋", "Adicionar botão", onAdd)
        Spacer(Modifier.width(8.dp))
        HeaderButton("↻", "Atualizar", onRefresh)
        Spacer(Modifier.width(8.dp))
        HeaderButton("•••", "Conexão", onSettings, compact = true)
    }
}

@Composable
private fun SyncDeckBrandMark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.graphicsLayer { rotationZ = -8f },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            BrandTile(Color(0xFF72F5AD))
            BrandTile(Color(0xFF50AA78))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            BrandTile(Color(0xFF50AA78))
            BrandTile(Color(0xFF72F5AD))
        }
    }
}

@Composable
private fun BrandTile(color: Color) {
    Box(
        Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(3.5.dp))
            .background(color),
    )
}

@Composable
private fun HeaderButton(text: String, description: String, onClick: () -> Unit, compact: Boolean = false) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = .065f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = if (compact) 15.sp else 25.sp, fontWeight = FontWeight.Medium, modifier = Modifier.graphicsLayer { this.alpha = 1f })
        }
    }
}

@Composable
private fun StatusPill(text: String, tone: ConnectionTone, onClick: () -> Unit) {
    val color = when (tone) {
        ConnectionTone.ONLINE -> Color(0xFF72F5AD)
        ConnectionTone.OFFLINE -> Color(0xFFFF8B91)
        ConnectionTone.WORKING -> Color(0xFF80AFFF)
    }
    Surface(
        modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(100.dp)).combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = .075f),
        border = BorderStroke(1.dp, color.copy(alpha = .17f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(text, modifier = Modifier.padding(start = 8.dp), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DeckGrid(
    actions: List<SyncAction>,
    busy: Set<String>,
    landscape: Boolean,
    onOpen: (SyncAction) -> Unit,
    onClose: (SyncAction) -> Unit,
    onMenu: (SyncAction) -> Unit,
    loadIcon: (SyncAction, (android.graphics.Bitmap) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("◇", fontSize = 42.sp, color = Color.White.copy(alpha = .25f))
                Text(if (landscape) "Gire o celular para configurar." else "Nenhum botão por aqui ainda.", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                if (!landscape) Text("Toque em ＋ para criar o primeiro.", color = Color.White.copy(alpha = .48f), textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (landscape) 3 else 2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (landscape) 4.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(if (landscape) 9.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(if (landscape) 9.dp else 11.dp),
    ) {
        items(actions, key = { it.id }) { action ->
            DeckCard(
                action = action,
                landscape = landscape,
                busy = action.id in busy,
                onOpen = { onOpen(action) },
                onClose = { onClose(action) },
                onMenu = { onMenu(action) },
                loadIcon = loadIcon,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCard(
    action: SyncAction,
    landscape: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onMenu: () -> Unit,
    loadIcon: (SyncAction, (android.graphics.Bitmap) -> Unit) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .965f else 1f, spring(stiffness = 680f), label = "card-scale")
    val openGlow by animateFloatAsState(if (action.isOpen) 1f else 0f, spring(stiffness = 430f), label = "open-glow")
    val shape = RoundedCornerShape(if (landscape) 24.dp else 27.dp)
    val accent = parseDeckColor(action.color)
    val borderBrush = Brush.linearGradient(
        listOf(
            accent.copy(alpha = .12f + .83f * openGlow),
            Color(0xFF75E9C1).copy(alpha = .045f + .655f * openGlow),
            accent.copy(alpha = .045f + .755f * openGlow),
        ),
    )

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (landscape) 1.32f else .88f)
            .scale(scale)
            .graphicsLayer {
                shadowElevation = 10f + 15f * openGlow
                this.shape = shape
                clip = false
                ambientShadowColor = if (openGlow > .01f) accent.copy(alpha = openGlow) else Color.Black
                spotShadowColor = if (openGlow > .01f) accent.copy(alpha = openGlow) else Color.Black
            }
            .border((1f + .6f * openGlow).dp, borderBrush, shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = .075f + .035f * openGlow),
                        accent.copy(alpha = .035f + .075f * openGlow),
                        Color(0xFF11141D).copy(alpha = .94f),
                    ),
                ),
                shape,
            )
            .clip(shape)
            .combinedClickable(
                onClick = {
                    pressed = true
                    onOpen()
                    scope.launch { delay(130); pressed = false }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenu()
                },
            )
            .padding(if (landscape) 12.dp else 15.dp),
    ) {
        if (landscape) {
            ActionLogo(action, busy, loadIcon, Modifier.align(Alignment.Center).fillMaxHeight(.72f).aspectRatio(1f))
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Top) {
                    ActionLogo(action, busy, loadIcon, Modifier.size(62.dp))
                    Spacer(Modifier.weight(1f))
                    if (action.closable) {
                        Surface(
                            modifier = Modifier.size(34.dp).clip(CircleShape).combinedClickable(onClick = onClose),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = .075f),
                        ) { Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White.copy(alpha = .7f), fontSize = 20.sp) } }
                    }
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(action.isOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Box(Modifier.size(6.dp).background(accent, CircleShape))
                        Text("ABERTO", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text(action.label, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ActionLogo(
    action: SyncAction,
    busy: Boolean,
    loadIcon: (SyncAction, (android.graphics.Bitmap) -> Unit) -> Unit,
    modifier: Modifier,
) {
    var bitmap by remember(action.id, action.imageKey) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(action.id, action.imageKey) { loadIcon(action) { bitmap = it } }
    val accent = parseDeckColor(action.color)
    Box(
        modifier.background(
            Brush.linearGradient(listOf(accent.copy(alpha = .25f), accent.copy(alpha = .08f))),
            RoundedCornerShape(20.dp),
        ).border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val ready = bitmap
        if (ready != null) {
            Image(
                bitmap = ready.asImageBitmap(),
                contentDescription = "Ícone de ${action.label}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
            )
        } else {
            Text(actionGlyph(action), color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (action.icon == "terminal") 18.sp else 28.sp)
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color(0xB5090B10), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.5.dp, color = accent)
            }
        }
    }
}

@Composable
private fun OpenConfirmation(action: SyncAction, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (DeckController.isWakeAction(action)) "Ligar o PC?" else "Executar “${action.label}”?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                when {
                    DeckController.isWakeAction(action) -> "O SyncDeck enviará o sinal de ligar pela rede local."
                    action.type == "command" || action.type == "hotkey" -> "Depois desta confirmação, o Windows também mostrará os detalhes e pedirá sua autorização."
                    else -> "Essa ação pede confirmação antes de continuar."
                },
            )
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } },
        confirmButton = { Button(onClick = confirm) { Text(if (DeckController.isWakeAction(action)) "Ligar" else "Continuar") } },
    )
}

@Composable
private fun CloseConfirmation(action: SyncAction, dismiss: () -> Unit, confirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (action.windowCount > 1) "${action.windowCount} janelas abertas" else "Fechar “${action.label}”?", fontWeight = FontWeight.Bold) },
        text = { Text(if (action.windowCount > 1) "Escolha se deseja fechar uma janela ou todas." else "O Windows enviará um pedido normal para a janela fechar.") },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } },
        confirmButton = {
            Row {
                if (action.windowCount > 1) TextButton(onClick = { confirm(false) }) { Text("Fechar uma") }
                Button(onClick = { confirm(action.windowCount > 1) }) { Text(if (action.windowCount > 1) "Fechar todas" else "Fechar") }
            }
        },
    )
}

@Composable
private fun ActionMenu(action: SyncAction, controller: DeckController) {
    AlertDialog(
        onDismissRequest = controller::closeMenu,
        title = { Text(action.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuRow(if (DeckController.isWakeAction(action)) "Ligar o PC" else "Abrir agora") { controller.requestOpen(action) }
                if (action.closable) MenuRow("Fechar janela") { controller.requestClose(action) }
                if (!DeckController.isWakeAction(action)) {
                    MenuRow("Editar botão") { controller.editAction(action) }
                    MenuRow("Excluir botão", destructive = true) { controller.requestDelete(action) }
                }
            }
        },
        confirmButton = { TextButton(onClick = controller::closeMenu) { Text("Fechar menu") } },
    )
}

@Composable
private fun MenuRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = .05f),
    ) { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else Color.White, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium) }
}

@Composable
private fun DeleteConfirmation(action: SyncAction, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Excluir “${action.label}”?", fontWeight = FontWeight.Bold) },
        text = { Text("O botão será removido do celular e do agente do Windows.") },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Excluir") }
        },
    )
}

@Composable
private fun ConnectionDialog(controller: DeckController, onDismiss: () -> Unit) {
    var host by remember(controller.host) { mutableStateOf(controller.host) }
    var port by remember(controller.port) { mutableStateOf(controller.port.toString()) }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf<AgentStatus?>(null) }
    var result by remember { mutableStateOf("No PC, abra o SyncDeck perto do relógio e escolha “Parear celular”.") }
    var fingerprintChecked by remember { mutableStateOf(false) }
    var unpairConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!loading && controller.configured) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).widthIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFA191C25),
            shadowElevation = 24.dp,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Conectar ao PC", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Somente na sua rede local", color = Color.White.copy(alpha = .52f))
                    }
                    TextButton(onClick = onDismiss, enabled = !loading && controller.configured) { Text("Fechar") }
                }
                Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                    Text("O endereço é salvo no celular e recuperado automaticamente se o roteador trocar o IP do PC.", modifier = Modifier.padding(13.dp), color = Color.White.copy(alpha = .67f), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(host, { host = it; verified = null; fingerprintChecked = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("IP privado do PC") }, placeholder = { Text("192.168.0.185") })
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5); verified = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Porta") })
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Código de 6 números") }, enabled = !controller.paired)

                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .045f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(result, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                        verified?.takeIf { it.fingerprint.isNotBlank() }?.let { status ->
                            Text(status.fingerprint, modifier = Modifier.padding(top = 9.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                                Checkbox(checked = fingerprintChecked, onCheckedChange = { fingerprintChecked = it })
                                Text("Confirmei que esse código é igual ao exibido no PC", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                AnimatedVisibility(loading) { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(28.dp), strokeWidth = 3.dp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = {
                            loading = true; result = "Verificando o agente…"; verified = null; fingerprintChecked = false
                            val selectedPort = port.toIntOrNull() ?: 0
                            controller.verifyEndpoint(
                                host.trim(), selectedPort,
                                success = { status ->
                                    verified = status; loading = false
                                    result = if (status.protocol < 2) "Agente encontrado, mas precisa ser atualizado para a versão 1.0."
                                    else "PC: ${status.name}\nProteção v${status.protocol} disponível.${if (status.pairingAvailable) " Código ativo por 5 minutos." else " Gere um novo código no Windows."}"
                                },
                                error = { message -> loading = false; result = message },
                            )
                        },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Verificar PC") }
                    if (!controller.paired) {
                        Button(
                            onClick = {
                                val status = verified ?: return@Button
                                loading = true
                                controller.pair(
                                    status, code,
                                    success = { loading = false },
                                    error = { message -> loading = false; result = message },
                                )
                            },
                            enabled = !loading && verified?.pairingAvailable == true &&
                                (verified?.protocol ?: 0) >= 2 && fingerprintChecked && code.length == 6,
                            modifier = Modifier.weight(1f),
                        ) { Text("Parear") }
                    }
                }
                if (controller.paired) TextButton(onClick = { unpairConfirm = true }, enabled = !loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Desparear este celular", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (unpairConfirm) AlertDialog(
        onDismissRequest = { unpairConfirm = false },
        title = { Text("Desparear celular?") },
        text = { Text("Será necessário gerar um novo código no Windows.") },
        dismissButton = { TextButton(onClick = { unpairConfirm = false }) { Text("Cancelar") } },
        confirmButton = { Button(onClick = { unpairConfirm = false; controller.unpair() }) { Text("Desparear") } },
    )
}

private fun actionGlyph(action: SyncAction) = when (action.icon) {
    "power" -> "⏻"
    "terminal" -> ">_"
    "folder" -> "▱"
    "globe" -> "◎"
    "calculator" -> "+"
    "codex" -> "✦"
    else -> action.label.take(1).uppercase().ifBlank { "•" }
}

private fun parseDeckColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF697386))
