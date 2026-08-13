package com.syncdeck.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicLong

enum class ConnectionTone { ONLINE, OFFLINE, WORKING }

data class UiNotice(val id: Long, val text: String)

data class DeckUiState(
    val actions: List<SyncAction> = emptyList(),
    val status: String = "Preparando o SyncDeck…",
    val tone: ConnectionTone = ConnectionTone.WORKING,
    val pcName: String = "Seu PC em um toque",
    val loading: Boolean = false,
    val computerOnline: Boolean = false,
    val showConnection: Boolean = false,
    val showWizard: Boolean = false,
    val editingAction: SyncAction? = null,
    val pendingOpen: SyncAction? = null,
    val pendingClose: SyncAction? = null,
    val menuAction: SyncAction? = null,
    val pendingDelete: SyncAction? = null,
    val busyActionIds: Set<String> = emptySet(),
    val notice: UiNotice? = null,
)

class DeckController(context: Context) {
    private val api = ApiClient(context.applicationContext)
    private val _state = mutableStateOf(DeckUiState())
    val state: State<DeckUiState> get() = _state
    private val handler = Handler(Looper.getMainLooper())
    private val noticeIds = AtomicLong()
    private var resumed = false
    private var firstResume = true
    private var stateRequestRunning = false
    private var wakeRequestRunning = false
    private val statePoll = Runnable { refreshActionStates() }

    val configured: Boolean get() = api.isConfigured
    val paired: Boolean get() = api.isPaired
    val host: String get() = api.host
    val port: Int get() = api.port

    fun start() {
        if (api.isConfigured) refresh()
        else update { it.copy(showConnection = true, loading = false, status = "Conecte ao agente do Windows", tone = ConnectionTone.OFFLINE) }
    }

    fun onResume() {
        resumed = true
        if (firstResume) { firstResume = false; return }
        if (api.isPaired) refresh()
    }

    fun onPause() {
        resumed = false
        handler.removeCallbacks(statePoll)
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        api.shutdown()
    }

    fun refresh() {
        if (_state.value.loading) return
        if (!api.isConfigured) {
            update { it.copy(showConnection = true, status = "Configure o endereço do PC", tone = ConnectionTone.OFFLINE) }
            return
        }
        if (api.isPaired && api.hasWakeConfig && _state.value.actions.isEmpty())
            update { it.copy(actions = listOf(wakeAction(false)), computerOnline = false) }
        update { it.copy(loading = true, status = if (api.isPaired) "Localizando PC…" else "Verificando conexão…", tone = ConnectionTone.WORKING) }
        api.getStatusWithRecovery(callback(
            success = { status ->
                if (status.protocol < 2) {
                    update { it.copy(loading = false, status = "Agente do Windows desatualizado", tone = ConnectionTone.OFFLINE) }
                    notice("Instale o agente SyncDeck 1.0 no PC para usar a conexão criptografada.")
                    return@callback
                }
                if (status.endpointRecovered) notice("PC encontrado automaticamente em ${status.host}.")
                update { it.copy(pcName = status.name, computerOnline = true) }
                if (!api.isPaired) {
                    update { it.copy(loading = false, showConnection = true, status = "Pareamento necessário", tone = ConnectionTone.OFFLINE) }
                } else loadActions()
            },
            error = { showOfflineActions(it) },
        ))
    }

    private fun loadActions() {
        api.getActions(editable = false, callback(
            success = { loaded ->
                api.refreshWakeConfig(callback(
                    success = { finishOnlineActions(loaded) },
                    error = { error ->
                        finishOnlineActions(loaded)
                        if (!api.hasWakeConfig) notice(error)
                    },
                ))
            },
            error = { error ->
                update { it.copy(loading = false, status = "PC localizado, mas recusou a conexão", tone = ConnectionTone.OFFLINE) }
                notice(error)
            },
        ))
    }

    private fun finishOnlineActions(loaded: List<SyncAction>) {
        val actions = loaded.filterNot(::isWakeAction).toMutableList()
        if (api.hasWakeConfig) actions += wakeAction(true)
        update {
            it.copy(
                actions = actions,
                loading = false,
                computerOnline = true,
                status = "Conectado com proteção ativa",
                tone = ConnectionTone.ONLINE,
            )
        }
        startStatePolling(900)
    }

    private fun showOfflineActions(error: String) {
        handler.removeCallbacks(statePoll)
        val actions = if (api.hasWakeConfig) listOf(wakeAction(false)) else emptyList()
        update {
            it.copy(
                actions = actions,
                loading = false,
                computerOnline = false,
                status = if (api.hasWakeConfig) "PC desligado — pronto para ligar" else "PC indisponível",
                tone = ConnectionTone.OFFLINE,
            )
        }
        if (!api.hasWakeConfig && error.isNotBlank()) notice(error)
    }

    private fun startStatePolling(delay: Long) {
        handler.removeCallbacks(statePoll)
        if (resumed && _state.value.computerOnline) handler.postDelayed(statePoll, delay)
    }

    private fun refreshActionStates() {
        if (!resumed || !_state.value.computerOnline || stateRequestRunning) return
        stateRequestRunning = true
        api.getActionStates(callback(
            success = { states ->
                stateRequestRunning = false
                val byId = states.associateBy { it.id.lowercase() }
                update { current ->
                    current.copy(actions = current.actions.map { action ->
                        if (isWakeAction(action)) action.copy(isOpen = current.computerOnline, windowCount = if (current.computerOnline) 1 else 0)
                        else byId[action.id.lowercase()]?.let { action.copy(isOpen = it.isOpen, windowCount = it.windowCount) } ?: action
                    })
                }
                startStatePolling(2_500)
            },
            error = {
                stateRequestRunning = false
                startStatePolling(4_500)
            },
        ))
    }

    fun requestOpen(action: SyncAction) {
        if (action.id in _state.value.busyActionIds) return
        closeMenu()
        if (action.confirm || isWakeAction(action)) update { it.copy(pendingOpen = action) }
        else execute(action, "open", confirmed = false)
    }

    fun confirmOpen() {
        val action = _state.value.pendingOpen ?: return
        update { it.copy(pendingOpen = null) }
        execute(action, "open", confirmed = true)
    }

    fun dismissOpen() = update { it.copy(pendingOpen = null) }

    fun requestClose(action: SyncAction) {
        if (action.id in _state.value.busyActionIds) return
        closeMenu()
        update { it.copy(pendingClose = action) }
    }

    fun confirmClose(closeAll: Boolean) {
        val action = _state.value.pendingClose ?: return
        update { it.copy(pendingClose = null) }
        execute(action, if (closeAll) "close-all" else "close", confirmed = true)
    }

    fun dismissClose() = update { it.copy(pendingClose = null) }

    private fun execute(action: SyncAction, operation: String, confirmed: Boolean) {
        if (isWakeAction(action)) { executeWake(action); return }
        setBusy(action.id, true)
        api.execute(action, operation, confirmed, callback(
            success = {
                setBusy(action.id, false)
                notice(
                    when {
                        action.id == "shutdown-pc" -> "Desligamento autorizado e iniciado no PC."
                        operation.startsWith("close") -> "Pedido para fechar enviado ao Windows."
                        else -> "${action.label} aberto no PC."
                    },
                )
                handler.removeCallbacks(statePoll)
                handler.postDelayed(statePoll, if (operation.startsWith("close")) 750 else 500)
            },
            error = {
                setBusy(action.id, false)
                notice(it)
            },
        ))
    }

    private fun executeWake(action: SyncAction) {
        if (wakeRequestRunning) return
        wakeRequestRunning = true
        setBusy(action.id, true)
        update { it.copy(status = "Enviando sinal para ligar…", tone = ConnectionTone.WORKING) }
        api.wakeComputer(callback(
            success = {
                wakeRequestRunning = false
                setBusy(action.id, false)
                update { it.copy(status = "Sinal enviado — aguarde o Windows", tone = ConnectionTone.WORKING) }
                notice("Sinal para ligar enviado ao PC.")
                handler.postDelayed({ if (resumed) refresh() }, 15_000)
            },
            error = {
                wakeRequestRunning = false
                setBusy(action.id, false)
                update { state -> state.copy(status = "Não foi possível enviar o sinal", tone = ConnectionTone.OFFLINE) }
                notice(it)
            },
        ))
    }

    fun showConnection() = update { it.copy(showConnection = true) }
    fun hideConnection() = update { it.copy(showConnection = false) }

    fun verifyEndpoint(host: String, port: Int, success: (AgentStatus) -> Unit, error: (String) -> Unit) {
        try { api.setEndpoint(host, port) }
        catch (exception: Exception) { error(exception.message ?: "Endereço inválido."); return }
        api.getStatus(callback(success, error))
    }

    fun pair(status: AgentStatus, code: String, success: () -> Unit, error: (String) -> Unit) {
        api.pair(status, code, callback(
            success = {
                hideConnection()
                notice("Celular pareado com segurança.")
                success()
                refresh()
            },
            error = error,
        ))
    }

    fun unpair() {
        api.clearPairing()
        handler.removeCallbacks(statePoll)
        update {
            DeckUiState(
                showConnection = true,
                status = "Pareamento removido",
                tone = ConnectionTone.OFFLINE,
                pcName = it.pcName,
            )
        }
        notice("Pareamento removido deste celular.")
    }

    fun showAddWizard() {
        if (!api.isPaired) { notice("Pareie o celular antes de adicionar botões."); showConnection(); return }
        update { it.copy(showWizard = true, editingAction = null) }
    }

    fun editAction(action: SyncAction) {
        closeMenu()
        update { it.copy(loading = true) }
        api.getActions(editable = true, callback(
            success = { values ->
                val editable = values.firstOrNull { it.id.equals(action.id, ignoreCase = true) }
                if (editable == null) {
                    update { it.copy(loading = false) }
                    notice("Botão não encontrado.")
                } else update { it.copy(loading = false, showWizard = true, editingAction = editable) }
            },
            error = {
                update { state -> state.copy(loading = false) }
                notice(it)
            },
        ))
    }

    fun hideWizard() = update { it.copy(showWizard = false, editingAction = null) }

    fun loadApplications(success: (List<CatalogApplication>) -> Unit, error: (String) -> Unit) =
        api.getApplications(callback(success, error))

    fun pickPath(kind: String, success: (PickedPath) -> Unit, error: (String) -> Unit) =
        api.pickPath(kind, callback(success, error))

    fun saveAction(action: SyncAction, success: () -> Unit, error: (String) -> Unit) {
        api.saveAction(action, callback(
            success = {
                hideWizard()
                notice("Botão salvo no SyncDeck.")
                success()
                loadActions()
            },
            error = error,
        ))
    }

    fun requestDelete(action: SyncAction) {
        closeMenu()
        update { it.copy(pendingDelete = action) }
    }

    fun dismissDelete() = update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val action = _state.value.pendingDelete ?: return
        update { it.copy(pendingDelete = null, loading = true) }
        api.deleteAction(action, callback(
            success = {
                notice("Botão excluído.")
                loadActions()
            },
            error = {
                update { state -> state.copy(loading = false) }
                notice(it)
            },
        ))
    }

    fun openMenu(action: SyncAction) = update { it.copy(menuAction = action) }
    fun closeMenu() = update { it.copy(menuAction = null) }

    fun loadIcon(action: SyncAction, success: (Bitmap) -> Unit) {
        if (isWakeAction(action)) return
        api.getActionIcon(action, callback(success = success, error = {}))
    }

    fun consumeNotice(id: Long) {
        update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    private fun notice(text: String) {
        if (text.isBlank()) return
        update { it.copy(notice = UiNotice(noticeIds.incrementAndGet(), text)) }
    }

    private fun setBusy(id: String, busy: Boolean) = update { current ->
        val values = current.busyActionIds.toMutableSet()
        if (busy) values += id else values -= id
        current.copy(busyActionIds = values)
    }

    private fun wakeAction(pcOnline: Boolean) = SyncAction(
        id = WAKE_ACTION_ID,
        label = "Ligar PC",
        type = "wake",
        icon = "power",
        imageKey = "local-wake-v2",
        color = "#22C55E",
        confirm = true,
        closable = false,
        isOpen = pcOnline,
        windowCount = if (pcOnline) 1 else 0,
    )

    private fun update(change: (DeckUiState) -> DeckUiState) {
        _state.value = change(_state.value)
    }

    private fun <T> callback(success: (T) -> Unit, error: (String) -> Unit) = object : ApiClient.Callback<T> {
        override fun onSuccess(value: T, message: String) = success(value)
        override fun onError(message: String) = error(message)
    }

    companion object {
        const val WAKE_ACTION_ID = "wake-pc"
        fun isWakeAction(action: SyncAction) = action.id == WAKE_ACTION_ID || action.type == "wake"
    }
}
