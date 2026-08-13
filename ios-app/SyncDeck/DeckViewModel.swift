import Foundation
import UIKit

@MainActor
final class DeckViewModel: ObservableObject {
    @Published var actions: [SyncAction] = []
    @Published var icons: [String: UIImage] = [:]
    @Published var online = false
    @Published var paired = false
    @Published var loading = false
    @Published var statusText = "Ainda não conectado"
    @Published var computerName = "Seu PC em um toque"
    @Published var connectionPresented = false
    @Published var editor: EditorPresentation?
    @Published var confirmation: ConfirmationPrompt?
    @Published var multipleCloseAction: SyncAction?
    @Published var commandActionID = ""
    @Published var toastMessage = ""

    private let api = SyncDeckAPI()
    private var started = false
    private var active = true
    private var refreshRunning = false
    private var pollingTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?

    func start() {
        guard !started else { return }
        started = true
        refresh()
    }

    func sceneBecame(active: Bool) {
        self.active = active
        if active {
            refresh()
        } else {
            pollingTask?.cancel()
        }
    }

    func refresh() {
        guard !refreshRunning else { return }
        Task { await performRefresh() }
    }

    func openConnection() {
        connectionPresented = true
    }

    func endpointSnapshot() async -> (host: String, port: Int) {
        await api.endpoint()
    }

    func verify(host: String, port: Int) async throws -> ServerStatus {
        try await api.setEndpoint(host: host, port: port)
        let status = try await api.getStatus()
        paired = await api.isPaired()
        online = true
        statusText = paired ? "PC localizado" : "Pareamento necessário"
        computerName = status.name
        return status
    }

    func pair(status: ServerStatus, code: String) async throws {
        try await api.pair(status: status, code: code)
        paired = true
        connectionPresented = false
        showToast("iPhone pareado com segurança.")
        refresh()
    }

    func unpair() async {
        await api.clearPairing()
        paired = false
        online = false
        actions = []
        icons = [:]
        statusText = "Pareamento removido"
        showToast("Será necessário gerar um novo código no Windows.")
    }

    func requestOpen(_ action: SyncAction) {
        if action.isWakeAction {
            if online {
                showToast("O PC já está ligado e conectado.")
            } else {
                confirmation = ConfirmationPrompt(
                    title: "Ligar o PC?",
                    message: "O iPhone enviará o sinal Wake-on-LAN pela sua rede Wi-Fi.",
                    confirmTitle: "Ligar",
                    destructive: false,
                    command: .wake
                )
            }
            return
        }

        if action.confirm {
            confirmation = ConfirmationPrompt(
                title: action.isShutdownAction ? "Desligar o PC?" : "Executar “\(action.label)”?",
                message: action.isShutdownAction
                    ? "Salve seu trabalho. O Windows começará a desligar em 5 segundos."
                    : "Essa ação foi marcada como sensível.",
                confirmTitle: action.isShutdownAction ? "Desligar" : "Executar",
                destructive: action.isShutdownAction,
                command: .execute(action, "open", true)
            )
        } else {
            run(action: action, operation: "open", confirmed: false)
        }
    }

    func requestClose(_ action: SyncAction) {
        if action.windowCount > 1 {
            multipleCloseAction = action
            return
        }
        confirmation = ConfirmationPrompt(
            title: "Fechar “\(action.label)”?",
            message: action.isOpen
                ? "A janela receberá um pedido normal para fechar."
                : "O estado pode ter mudado. O SyncDeck verificará novamente no Windows.",
            confirmTitle: "Fechar",
            destructive: true,
            command: .execute(action, "close", true)
        )
    }

    func closeOne(_ action: SyncAction) {
        multipleCloseAction = nil
        run(action: action, operation: "close", confirmed: true)
    }

    func closeAll(_ action: SyncAction) {
        multipleCloseAction = nil
        run(action: action, operation: "close-all", confirmed: true)
    }

    func confirm(_ prompt: ConfirmationPrompt) {
        confirmation = nil
        switch prompt.command {
        case .execute(let action, let operation, let confirmed):
            run(action: action, operation: operation, confirmed: confirmed)
        case .wake:
            sendWakeSignal()
        }
    }

    func presentNewEditor() {
        guard paired else {
            showToast("Pareie o iPhone antes de editar botões.")
            return
        }
        editor = EditorPresentation(action: .newAction, isNew: true)
    }

    func presentEditor(for action: SyncAction) {
        guard !action.isWakeAction else { return }
        guard paired else {
            showToast("Pareie o iPhone antes de editar botões.")
            return
        }
        Task {
            loading = true
            defer { loading = false }
            do {
                let editable = try await api.getActions(editable: true)
                guard let complete = editable.first(where: { $0.id.caseInsensitiveCompare(action.id) == .orderedSame }) else {
                    throw SyncDeckError.friendly("Botão não encontrado.")
                }
                editor = EditorPresentation(action: complete, isNew: false)
            } catch {
                showToast(message(for: error))
            }
        }
    }

    func save(action: SyncAction) async throws {
        guard !action.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SyncDeckError.friendly("Informe o nome do botão.")
        }
        try await api.save(action: action)
        showToast("Botão salvo.")
        editor = nil
        await performRefresh(force: true)
    }

    func delete(action: SyncAction) async throws {
        try await api.delete(action: action)
        showToast("Botão excluído.")
        editor = nil
        await performRefresh(force: true)
    }

    private func performRefresh(force: Bool = false) async {
        if refreshRunning, !force { return }
        refreshRunning = true
        pollingTask?.cancel()
        loading = true
        paired = await api.isPaired()

        guard await api.isConfigured() else {
            refreshRunning = false
            loading = false
            online = false
            statusText = "Configure o endereço do PC"
            if active { connectionPresented = true }
            return
        }

        if paired, await api.hasWakeConfiguration(), actions.isEmpty {
            actions = [.wake(online: false)]
        }
        statusText = paired ? "Localizando PC…" : "Verificando conexão"

        do {
            let status = try await api.getStatusWithRecovery()
            online = true
            computerName = status.name
            if status.endpointRecovered { showToast("PC encontrado automaticamente em \(status.host).") }
            paired = await api.isPaired()
            guard paired else {
                loading = false
                refreshRunning = false
                statusText = "Pareamento necessário"
                connectionPresented = true
                return
            }

            var loaded = try await api.getActions(editable: false)
            do {
                _ = try await api.refreshWakeConfiguration()
            } catch {
                if !(await api.hasWakeConfiguration()) { showToast(message(for: error)) }
            }
            loaded.removeAll(where: { $0.isWakeAction })
            if await api.hasWakeConfiguration() { loaded.append(.wake(online: true)) }
            actions = loaded
            retainRelevantIcons()
            loadIcons(for: loaded)
            statusText = "Conectado ao PC"
            loading = false
            refreshRunning = false
            startPolling(after: 0.9)
        } catch {
            online = false
            let canWake = await api.hasWakeConfiguration()
            actions = canWake ? [.wake(online: false)] : []
            retainRelevantIcons()
            statusText = canWake
                ? "PC desligado ou indisponível — pronto para ligar"
                : "PC indisponível"
            loading = false
            refreshRunning = false
            if !canWake { showToast(message(for: error)) }
        }
    }

    private func run(action: SyncAction, operation: String, confirmed: Bool) {
        guard online else {
            showToast("O PC está indisponível.")
            return
        }
        commandActionID = action.id
        Task {
            defer {
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 260_000_000)
                    if commandActionID == action.id { commandActionID = "" }
                }
            }
            do {
                try await api.execute(action: action, operation: operation, confirmed: confirmed)
                let impact = UIImpactFeedbackGenerator(style: .light)
                impact.impactOccurred()
                if action.isShutdownAction {
                    showToast("Desligamento iniciado no PC.")
                } else if operation.hasPrefix("close") {
                    showToast("Comando para fechar enviado.")
                } else {
                    showToast("\(action.label) aberto no PC.")
                }
                try? await Task.sleep(nanoseconds: operation.hasPrefix("close") ? 700_000_000 : 450_000_000)
                await pollOnce()
            } catch {
                online = false
                statusText = "Falha ao executar"
                showToast(message(for: error))
            }
        }
    }

    private func sendWakeSignal() {
        commandActionID = "wake-pc"
        statusText = "Enviando sinal para ligar…"
        Task {
            defer { commandActionID = "" }
            do {
                try await api.wakeComputer()
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                statusText = "Sinal enviado — aguarde o Windows"
                showToast("Sinal para ligar enviado ao PC.")
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                if active { await performRefresh(force: true) }
            } catch {
                statusText = "Não foi possível enviar o sinal"
                showToast(message(for: error))
            }
        }
    }

    private func startPolling(after seconds: Double) {
        pollingTask?.cancel()
        guard active, paired, online else { return }
        pollingTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            while !Task.isCancelled, active, paired, online {
                await pollOnce()
                let interval = online ? 2.4 : 4.2
                try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
            }
        }
    }

    private func pollOnce() async {
        guard active, paired, online else { return }
        do {
            let states = try await api.getActionStates()
            let mapped = Dictionary(uniqueKeysWithValues: states.map { ($0.id.lowercased(), $0) })
            for index in actions.indices where !actions[index].isWakeAction {
                guard let state = mapped[actions[index].id.lowercased()] else { continue }
                actions[index].isOpen = state.isOpen
                actions[index].windowCount = state.windowCount
            }
        } catch {
            // Uma falha isolada não derruba o painel; o próximo ciclo tenta novamente.
        }
    }

    private func loadIcons(for values: [SyncAction]) {
        for action in values where !action.isWakeAction {
            let expectedToken = action.imageKey
            Task {
                do {
                    let image = try await api.actionIcon(for: action)
                    guard actions.contains(where: { $0.id == action.id && $0.imageKey == expectedToken }) else { return }
                    icons[action.id] = image
                } catch {
                    // O símbolo local continua visível quando o Windows não possui imagem.
                }
            }
        }
    }

    private func retainRelevantIcons() {
        let ids = Set(actions.map(\.id))
        icons = icons.filter { ids.contains($0.key) }
    }

    private func showToast(_ message: String) {
        guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        toastTask?.cancel()
        toastMessage = message
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 3_200_000_000)
            if !Task.isCancelled { toastMessage = "" }
        }
    }

    private func message(for error: Error) -> String {
        if let localized = error as? LocalizedError, let text = localized.errorDescription { return text }
        return error.localizedDescription.isEmpty ? "Não foi possível conectar ao PC." : error.localizedDescription
    }
}
