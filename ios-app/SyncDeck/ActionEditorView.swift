import SwiftUI

struct ActionEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var action: SyncAction
    @State private var processes: String
    @State private var appNames: String
    @State private var working = false
    @State private var errorMessage = ""
    @State private var confirmDelete = false
    let isNew: Bool
    let onSave: (SyncAction) async throws -> Void
    let onDelete: (SyncAction) async throws -> Void

    init(
        action: SyncAction,
        isNew: Bool,
        onSave: @escaping (SyncAction) async throws -> Void,
        onDelete: @escaping (SyncAction) async throws -> Void
    ) {
        _action = State(initialValue: action)
        _processes = State(initialValue: action.processNames.joined(separator: ", "))
        _appNames = State(initialValue: action.appNames.joined(separator: ", "))
        self.isNew = isNew
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Identificação") {
                    TextField("Nome do botão", text: $action.label)
                    TextField("Identificador", text: $action.id)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    Picker("Tipo", selection: $action.type) {
                        Text("Aplicativo").tag("app")
                        Text("Site").tag("url")
                        Text("Arquivo ou pasta").tag("path")
                        Text("Comando").tag("command")
                        Text("Atalho de teclado").tag("hotkey")
                    }
                }

                Section("Execução no Windows") {
                    TextField(targetPlaceholder, text: $action.target)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    TextField("Argumentos opcionais", text: $action.arguments)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    TextField("Pasta de trabalho opcional", text: $action.workingDirectory)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    TextField("URL alternativa opcional", text: $action.fallbackURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                }

                Section("Reconhecimento da janela") {
                    TextField("Processos, separados por vírgula", text: $processes)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    TextField("Nomes no Menu Iniciar", text: $appNames)
                    Text("Exemplo: chrome, WhatsApp. Não inclua .exe no nome do processo.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Visual") {
                    TextField("Cor hexadecimal, por exemplo #72F5AD", text: $action.color)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                    Picker("Símbolo de reserva", selection: $action.icon) {
                        Text("Aplicativo").tag("app")
                        Text("Navegador").tag("chrome")
                        Text("Mensagem").tag("chat")
                        Text("E-mail").tag("mail")
                        Text("Pasta").tag("folder")
                        Text("Terminal").tag("terminal")
                        Text("Link").tag("link")
                        Text("Calculadora").tag("calculator")
                        Text("Energia").tag("power")
                    }
                    HStack {
                        Text("Prévia")
                        Spacer()
                        Image(systemName: DeckSymbols.name(for: action.icon, type: action.type))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color(syncDeckHex: action.color), in: RoundedRectangle(cornerRadius: 13))
                    }
                }

                Section("Comportamento") {
                    Toggle("Pode fechar", isOn: $action.closable)
                    Toggle("Pedir confirmação", isOn: $action.confirm)
                        .disabled(action.type == "command")
                    Toggle("Botão ativo", isOn: $action.enabled)
                    if action.type == "command" {
                        Text("Comandos sempre exigem confirmação.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                if !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }

                if !isNew {
                    Section {
                        Button("Excluir botão", role: .destructive) { confirmDelete = true }
                            .disabled(working)
                    }
                }
            }
            .navigationTitle(isNew ? "Novo botão" : "Editar botão")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") { dismiss() }
                        .disabled(working)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(working ? "Salvando…" : "Salvar") { save() }
                        .font(.body.weight(.semibold))
                        .disabled(working)
                }
            }
        }
        .navigationViewStyle(.stack)
        .alert("Excluir “\(action.label)”?", isPresented: $confirmDelete) {
            Button("Cancelar", role: .cancel) {}
            Button("Excluir", role: .destructive) { delete() }
        } message: {
            Text("O botão será removido dos outros celulares e do agente Windows.")
        }
    }

    private var targetPlaceholder: String {
        switch action.type {
        case "url": return "https://exemplo.com"
        case "path": return "%USERPROFILE%\\Downloads"
        case "command": return "shutdown.exe"
        case "hotkey": return "^+{ESC}"
        default: return "Caminho do .exe ou protocolo"
        }
    }

    private func preparedAction() throws -> SyncAction {
        var result = action
        result.processNames = SyncAction.splitList(processes)
        result.appNames = SyncAction.splitList(appNames)
        result.id = result.normalizedID
        result.color = result.color.uppercased()
        result.confirm = result.confirm || result.type == "command"
        guard !result.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SyncDeckError.friendly("Informe o nome do botão.")
        }
        guard result.color.range(of: "^#[0-9A-F]{6}$", options: .regularExpression) != nil else {
            throw SyncDeckError.friendly("Use uma cor no formato #72F5AD.")
        }
        guard !result.target.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SyncDeckError.friendly("Informe o destino que será aberto no Windows.")
        }
        return result
    }

    private func save() {
        do {
            let result = try preparedAction()
            working = true
            errorMessage = ""
            Task {
                defer { working = false }
                do {
                    try await onSave(result)
                    dismiss()
                } catch {
                    errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                }
            }
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    private func delete() {
        working = true
        errorMessage = ""
        Task {
            defer { working = false }
            do {
                try await onDelete(action)
                dismiss()
            } catch {
                errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
