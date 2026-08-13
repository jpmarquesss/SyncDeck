import SwiftUI

struct ConnectionView: View {
    @EnvironmentObject private var model: DeckViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var host = ""
    @State private var port = "47321"
    @State private var code = ""
    @State private var status: ServerStatus?
    @State private var result = "Primeiro verifique o PC. A impressão digital precisa ser igual nas duas telas."
    @State private var working = false
    @State private var confirmUnpair = false

    var body: some View {
        NavigationView {
            Form {
                Section {
                    Text("No Windows, clique no ícone do SyncDeck perto do relógio e escolha “Parear celular”.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Section("Endereço do PC") {
                    TextField("IP, por exemplo 192.168.0.10", text: $host)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                    TextField("Porta", text: $port)
                        .keyboardType(.numberPad)
                    Button(working ? "Verificando…" : "Verificar PC") { verify() }
                        .disabled(working)
                }

                Section("Pareamento") {
                    Text(result)
                        .font(.footnote)
                        .foregroundStyle(status == nil ? Color.secondary : Color.white.opacity(0.88))
                        .textSelection(.enabled)
                    TextField("Código de 6 números", text: $code)
                        .keyboardType(.numberPad)
                        .onChange(of: code) { value in
                            code = String(value.filter(\.isNumber).prefix(6))
                        }
                    Button(working ? "Pareando…" : "Parear iPhone") { pair() }
                        .disabled(working || status == nil || code.count != 6 || status?.pairingAvailable != true)
                }

                if model.paired {
                    Section {
                        Button("Desparear este iPhone", role: .destructive) { confirmUnpair = true }
                    }
                }

                Section("Permissão do iPhone") {
                    Text("Na primeira verificação, permita que o SyncDeck encontre dispositivos na Rede Local. Se negar, ative em Ajustes › Privacidade e Segurança › Rede Local.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Conectar ao PC")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
        .navigationViewStyle(.stack)
        .task {
            let endpoint = await model.endpointSnapshot()
            if host.isEmpty { host = endpoint.host }
            port = String(endpoint.port)
        }
        .alert("Desparear iPhone?", isPresented: $confirmUnpair) {
            Button("Cancelar", role: .cancel) {}
            Button("Desparear", role: .destructive) {
                Task {
                    await model.unpair()
                    status = nil
                    result = "Pareamento removido deste iPhone."
                }
            }
        } message: {
            Text("Será necessário gerar um novo código no Windows.")
        }
    }

    private func parsedEndpoint() -> (String, Int)? {
        var selectedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        var selectedPort = Int(port.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 47_321
        if let separator = selectedHost.lastIndex(of: ":") {
            let suffix = selectedHost[selectedHost.index(after: separator)...]
            if let embedded = Int(suffix), (1_024...65_535).contains(embedded) {
                selectedPort = embedded
                selectedHost = String(selectedHost[..<separator])
                host = selectedHost
                port = String(selectedPort)
            }
        }
        guard !selectedHost.isEmpty else {
            result = "Informe o IP privado mostrado no agente do Windows."
            return nil
        }
        return (selectedHost, selectedPort)
    }

    private func verify() {
        guard let endpoint = parsedEndpoint() else { return }
        working = true
        result = "Conectando ao agente…"
        status = nil
        Task {
            defer { working = false }
            do {
                let verified = try await model.verify(host: endpoint.0, port: endpoint.1)
                status = verified
                result = "PC: \(verified.name)\nImpressão digital: \(verified.fingerprint)\n" +
                    (verified.pairingAvailable
                     ? "Código disponível por 5 minutos."
                     : "Abra “Parear celular” novamente no Windows.")
            } catch {
                result = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }

    private func pair() {
        guard let status else { return }
        working = true
        Task {
            defer { working = false }
            do {
                try await model.pair(status: status, code: code)
                dismiss()
            } catch {
                result = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
