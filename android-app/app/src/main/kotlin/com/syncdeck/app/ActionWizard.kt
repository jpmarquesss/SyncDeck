package com.syncdeck.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

private enum class WizardPage { TYPE, SOURCE, DETAILS }

private data class ActionKind(val type: String, val title: String, val description: String, val glyph: String, val color: Color)

private val actionKinds = listOf(
    ActionKind("app", "Programa", "Escolha na lista do Windows", "▦", Color(0xFF65A8FF)),
    ActionKind("url", "Site", "Abra uma página no navegador", "◎", Color(0xFF45D7B1)),
    ActionKind("path", "Pasta ou arquivo", "Escolha diretamente no PC", "▱", Color(0xFFF6BE54)),
    ActionKind("command", "Comando", "Modo avançado com aprovação no PC", ">_", Color(0xFFE98BFF)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionWizard(
    controller: DeckController,
    initial: SyncAction?,
    onDismiss: () -> Unit,
) {
    var page by remember(initial) { mutableStateOf(if (initial == null) WizardPage.TYPE else WizardPage.DETAILS) }
    var draft by remember(initial) { mutableStateOf(initial ?: SyncAction()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<CatalogApplication>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.96f)
                .fillMaxHeight(.94f)
                .widthIn(max = 760.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xF51A1D26),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            shadowElevation = 22.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                WizardHeader(
                    editing = initial != null,
                    page = page,
                    loading = loading,
                    onBack = {
                        error = ""
                        page = when (page) {
                            WizardPage.TYPE -> WizardPage.TYPE
                            WizardPage.SOURCE -> WizardPage.TYPE
                            WizardPage.DETAILS -> if (initial == null) WizardPage.TYPE else WizardPage.DETAILS
                        }
                    },
                    onClose = onDismiss,
                )
                HorizontalDivider(color = Color.White.copy(alpha = .07f))
                AnimatedContent(targetState = page, label = "wizard-page", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        WizardPage.TYPE -> TypePage { kind ->
                            error = ""
                            draft = defaultAction(kind.type)
                            if (kind.type == "app") {
                                page = WizardPage.SOURCE
                                loading = true
                                controller.loadApplications(
                                    success = { values -> catalog = values; loading = false },
                                    error = { message -> error = message; loading = false },
                                )
                            } else if (kind.type == "path") {
                                page = WizardPage.SOURCE
                            } else page = WizardPage.DETAILS
                        }
                        WizardPage.SOURCE -> if (draft.type == "app") {
                            ProgramPage(
                                catalog = catalog,
                                search = search,
                                loading = loading,
                                error = error,
                                onSearch = { search = it },
                                onSelect = { selected -> draft = selected.toAction(); page = WizardPage.DETAILS },
                                onRetry = {
                                    loading = true; error = ""
                                    controller.loadApplications(
                                        success = { values -> catalog = values; loading = false },
                                        error = { message -> error = message; loading = false },
                                    )
                                },
                            )
                        } else {
                            PathPage(
                                loading = loading,
                                error = error,
                                onPick = { kind ->
                                    loading = true; error = ""
                                    controller.pickPath(
                                        kind,
                                        success = { selected -> draft = selected.toAction(); loading = false; page = WizardPage.DETAILS },
                                        error = { message -> error = message; loading = false },
                                    )
                                },
                            )
                        }
                        WizardPage.DETAILS -> DetailsPage(
                            draft = draft,
                            editing = initial != null,
                            loading = loading,
                            error = error,
                            onChange = { draft = it; error = "" },
                            onSave = {
                                val validation = validateAction(draft)
                                if (validation != null) error = validation
                                else {
                                    loading = true; error = ""
                                    controller.saveAction(
                                        draft,
                                        success = { loading = false },
                                        error = { message -> loading = false; error = message },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(editing: Boolean, page: WizardPage, loading: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != WizardPage.TYPE && !(editing && page == WizardPage.DETAILS)) {
            TextButton(onClick = onBack, enabled = !loading) { Text("‹  Voltar") }
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (editing) "Editar botão" else "Novo botão", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                when (page) {
                    WizardPage.TYPE -> "1 de 3 · Escolha o que ele faz"
                    WizardPage.SOURCE -> "2 de 3 · Escolha no Windows"
                    WizardPage.DETAILS -> "3 de 3 · Revise e salve"
                },
                color = Color.White.copy(alpha = .55f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        TextButton(onClick = onClose, enabled = !loading) { Text("Fechar") }
    }
}

@Composable
private fun TypePage(onSelect: (ActionKind) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("O que você quer abrir?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("O SyncDeck vai mostrar somente as opções necessárias.", color = Color.White.copy(alpha = .62f), modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        }
        items(actionKinds) { kind ->
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { onSelect(kind) },
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = .055f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).background(kind.color.copy(alpha = .18f), RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(kind.glyph, color = kind.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(kind.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(kind.description, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("›", color = Color.White.copy(alpha = .45f), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun ProgramPage(
    catalog: List<CatalogApplication>,
    search: String,
    loading: Boolean,
    error: String,
    onSearch: (String) -> Unit,
    onSelect: (CatalogApplication) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Programas instalados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("A lista e as imagens vêm automaticamente do seu PC.", color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar programa") },
            placeholder = { Text("Ex.: Chrome, Outlook…") },
        )
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        if (error.isNotBlank()) {
            ErrorPanel(error, onRetry)
            return
        }
        val filtered = catalog.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (catalog.isEmpty()) "Nenhum programa foi encontrado no Windows." else "Nenhum resultado para essa busca.", color = Color.White.copy(alpha = .6f))
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.selectionToken }) { app ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect(app) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = .05f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Color(0xFF65A8FF).copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF82B9FF))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text(app.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Imagem automática do Windows", color = Color.White.copy(alpha = .47f), style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Adicionar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PathPage(loading: Boolean, error: String, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Escolha no seu PC", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Ao tocar abaixo, uma janela segura será aberta no Windows. O caminho não precisa ser digitado.",
            color = Color.White.copy(alpha = .62f),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (loading) {
            CircularProgressIndicator()
            Text("Aguardando sua escolha no PC…", modifier = Modifier.padding(top = 14.dp), color = Color.White.copy(alpha = .65f))
        } else {
            Button(onClick = { onPick("folder") }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Escolher uma pasta no PC") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onPick("file") }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Escolher um arquivo no PC") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun DetailsPage(
    draft: SyncAction,
    editing: Boolean,
    loading: Boolean,
    error: String,
    onChange: (SyncAction) -> Unit,
    onSave: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    var commandMode by remember(draft.type) { mutableStateOf(if (draft.type == "hotkey") "hotkey" else "command") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Text(if (editing) "Revise este botão" else "Quase pronto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(typeExplanation(draft.type), color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(top = 4.dp))
        }
        if (draft.type == "command" || draft.type == "hotkey") {
            item {
                SecurityPanel()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = commandMode == "command",
                        onClick = { commandMode = "command"; onChange(draft.copy(type = "command", confirm = true)) },
                        label = { Text("Executar comando") },
                    )
                    FilterChip(
                        selected = commandMode == "hotkey",
                        onClick = { commandMode = "hotkey"; onChange(draft.copy(type = "hotkey", confirm = true)) },
                        label = { Text("Atalho de teclado") },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onChange(draft.copy(label = it.take(40))) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nome do botão") },
                supportingText = { Text("É o texto que aparece no celular") },
            )
        }
        when (draft.type) {
            "url" -> {
                item {
                    OutlinedTextField(
                        value = draft.target,
                        onValueChange = { onChange(draft.copy(target = it.take(1000))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Endereço do site") },
                        placeholder = { Text("https://…") },
                    )
                }
                item {
                    Text("Onde abrir", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.arguments != "chrome",
                            onClick = { onChange(draft.copy(arguments = "")) },
                            label = { Text("Navegador padrão") },
                        )
                        FilterChip(
                            selected = draft.arguments == "chrome",
                            onClick = { onChange(draft.copy(arguments = "chrome")) },
                            label = { Text("Sempre no Chrome") },
                        )
                    }
                }
            }
            "command" -> {
                item {
                    OutlinedTextField(
                        value = draft.target,
                        onValueChange = { onChange(draft.copy(target = it.take(1000))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Programa ou comando") },
                        placeholder = { Text("Ex.: shutdown.exe") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.arguments,
                        onValueChange = { onChange(draft.copy(arguments = it.take(1000))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Argumentos, se precisar") },
                        placeholder = { Text("Ex.: /s /t 5") },
                    )
                }
            }
            "hotkey" -> item {
                OutlinedTextField(
                    value = draft.target,
                    onValueChange = { onChange(draft.copy(target = it.take(120))) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Teclas do atalho") },
                    placeholder = { Text("Ex.: ^%t") },
                    supportingText = { Text("Formato SendKeys do Windows") },
                )
            }
            else -> item {
                ReadOnlyDestination(draft)
            }
        }
        item {
            Text("Cor de apoio", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .7f))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("#4285F4", "#25D366", "#10A37F", "#8B5CF6", "#EC4899", "#F97316", "#F5B82E", "#64748B").forEach { value ->
                    val selected = draft.color.equals(value, ignoreCase = true)
                    Box(
                        Modifier
                            .size(if (selected) 38.dp else 34.dp)
                            .background(parseColor(value), CircleShape)
                            .clip(CircleShape)
                            .clickable { onChange(draft.copy(color = value)) },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (draft.type == "app") item {
            SettingRow(
                title = "Permitir fechar a janela",
                description = "Mostra a opção Fechar ao segurar o botão",
                checked = draft.closable,
                onChecked = { onChange(draft.copy(closable = it)) },
            )
        }
        if (draft.type != "command" && draft.type != "hotkey") item {
            SettingRow(
                title = "Confirmar antes de abrir",
                description = "Útil para ações que não podem ser desfeitas",
                checked = draft.confirm,
                onChecked = { onChange(draft.copy(confirm = it)) },
            )
        }
        item {
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar opções avançadas" else "Mostrar opções avançadas") }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.processNames.joinToString(", "),
                        onValueChange = { onChange(draft.copy(processNames = SyncAction.split(it))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Processos para detectar a janela") },
                    )
                    OutlinedTextField(
                        value = draft.appNames.joinToString(", "),
                        onValueChange = { onChange(draft.copy(appNames = SyncAction.split(it))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nomes no menu Iniciar") },
                    )
                    if (draft.type == "command") OutlinedTextField(
                        value = draft.workingDirectory,
                        onValueChange = { onChange(draft.copy(workingDirectory = it.take(500))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Pasta de trabalho") },
                    )
                }
            }
        }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) }
        item {
            Button(
                onClick = onSave,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF081611))
                    Spacer(Modifier.width(10.dp))
                    Text(if (draft.type == "command" || draft.type == "hotkey") "Confirme no PC…" else "Salvando…")
                } else Text(if (editing) "Salvar alterações" else "Adicionar ao meu SyncDeck")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReadOnlyDestination(draft: SyncAction) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = .05f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (draft.type == "app") "Programa escolhido" else "Local escolhido no PC", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelMedium)
            Text(draft.label.ifBlank { draft.target }, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            Text(draft.target, color = Color.White.copy(alpha = .42f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SecurityPanel() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFAE6AF7).copy(alpha = .1f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE98BFF).copy(alpha = .24f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("Proteção em duas etapas", fontWeight = FontWeight.Bold, color = Color(0xFFF1B5FF))
            Text("Ao salvar e sempre que executar, o Windows mostrará exatamente o que será feito e pedirá sua autorização.", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}

private fun defaultAction(type: String) = when (type) {
    "url" -> SyncAction(type = "url", target = "https://", icon = "globe", color = "#10A37F", closable = false)
    "path" -> SyncAction(type = "path", icon = "folder", color = "#F5B82E", closable = false)
    "command" -> SyncAction(type = "command", icon = "terminal", color = "#8B5CF6", confirm = true, closable = false)
    else -> SyncAction(type = "app", icon = "app", color = "#4285F4", closable = true)
}

private fun validateAction(value: SyncAction): String? {
    val action = value.normalizedForSave()
    if (action.label.isBlank() || action.label.length > 40) return "Informe um nome de até 40 caracteres."
    if (action.target.isBlank() || action.target.length > 1000) return "Escolha ou informe um destino válido."
    if (!action.color.matches("^#[0-9A-F]{6}$".toRegex())) return "Escolha uma cor válida."
    if (action.type == "url") {
        val valid = runCatching {
            val uri = URI(action.target)
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
        if (!valid) return "Digite um endereço completo, começando com https://."
    }
    return null
}

private fun typeExplanation(type: String) = when (type) {
    "app" -> "A imagem será buscada automaticamente no programa escolhido."
    "url" -> "Dê um nome simples e cole o endereço completo do site."
    "path" -> "O caminho foi escolhido de forma segura no Windows."
    "command", "hotkey" -> "Ações avançadas sempre exigem sua aprovação também no PC."
    else -> "Revise as informações antes de salvar."
}

private fun parseColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF64748B))
