import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var model: DeckViewModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var landscape = false

    var body: some View {
        GeometryReader { proxy in
            let isLandscape = proxy.size.width > proxy.size.height
            ZStack {
                DeckBackground()

                VStack(spacing: isLandscape ? 6 : 14) {
                    if !isLandscape { header }
                    deck(proxy: proxy, landscape: isLandscape)
                }
                .padding(.horizontal, isLandscape ? 8 : 18)
                .padding(.top, isLandscape ? 6 : 10)
                .padding(.bottom, isLandscape ? 5 : 12)

                if model.loading {
                    ProgressView()
                        .tint(.green)
                        .scaleEffect(1.15)
                        .padding(22)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
                }

                if !model.toastMessage.isEmpty {
                    VStack {
                        Spacer()
                        Text(model.toastMessage)
                            .font(.footnote.weight(.semibold))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 11)
                            .background(.ultraThinMaterial, in: Capsule())
                            .overlay(Capsule().stroke(Color.white.opacity(0.12)))
                            .padding(.bottom, isLandscape ? 5 : 16)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                    .padding(.horizontal, 24)
                    .animation(.spring(response: 0.35), value: model.toastMessage)
                }
            }
            .onAppear { landscape = isLandscape }
            .onChange(of: isLandscape) { landscape = $0 }
        }
        .statusBar(hidden: landscape)
        .task { model.start() }
        .onChange(of: scenePhase) { phase in model.sceneBecame(active: phase == .active) }
        .sheet(isPresented: $model.connectionPresented) {
            ConnectionView()
                .environmentObject(model)
        }
        .sheet(item: $model.editor) { item in
            ActionEditorView(
                action: item.action,
                isNew: item.isNew,
                onSave: { try await model.save(action: $0) },
                onDelete: { try await model.delete(action: $0) }
            )
        }
        .alert(item: $model.confirmation) { prompt in
            Alert(
                title: Text(prompt.title),
                message: Text(prompt.message),
                primaryButton: prompt.destructive
                    ? .destructive(Text(prompt.confirmTitle)) { model.confirm(prompt) }
                    : .default(Text(prompt.confirmTitle)) { model.confirm(prompt) },
                secondaryButton: .cancel(Text("Cancelar"))
            )
        }
        .confirmationDialog(
            multipleCloseTitle,
            isPresented: Binding(
                get: { model.multipleCloseAction != nil },
                set: { if !$0 { model.multipleCloseAction = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let action = model.multipleCloseAction {
                Button("Fechar somente a janela mais recente", role: .destructive) {
                    model.closeOne(action)
                }
                Button("Fechar todas as \(action.windowCount) janelas", role: .destructive) {
                    model.closeAll(action)
                }
                Button("Cancelar", role: .cancel) { model.multipleCloseAction = nil }
            }
        } message: {
            if let action = model.multipleCloseAction {
                Text("O que deseja fazer com “\(action.label)”? ")
            }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                SyncDeckMark(size: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text("SyncDeck")
                        .font(.system(size: 27, weight: .bold, design: .rounded))
                    Text(model.computerName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                headerButton(symbol: "plus", label: "Adicionar botão") { model.presentNewEditor() }
                headerButton(symbol: "arrow.clockwise", label: "Atualizar") { model.refresh() }
                headerButton(symbol: "ellipsis", label: "Conexão") { model.openConnection() }
            }

            HStack(spacing: 8) {
                Circle()
                    .fill(model.online ? Color(red: 0.35, green: 0.85, blue: 0.61) : Color.gray.opacity(0.65))
                    .frame(width: 8, height: 8)
                    .shadow(color: model.online ? .green.opacity(0.8) : .clear, radius: 5)
                Text(model.statusText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(model.online ? Color(red: 0.68, green: 0.90, blue: 0.79) : .secondary)
                    .lineLimit(2)
                Spacer()
            }
            .contentShape(Rectangle())
            .onTapGesture { model.refresh() }
        }
    }

    private func headerButton(symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .bold))
                .frame(width: 44, height: 44)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(Color.white.opacity(0.1)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    @ViewBuilder
    private func deck(proxy: GeometryProxy, landscape: Bool) -> some View {
        if model.actions.isEmpty, !model.loading {
            VStack(spacing: 12) {
                Image(systemName: "rectangle.grid.2x2")
                    .font(.system(size: 38, weight: .light))
                    .foregroundStyle(.secondary)
                Text(landscape
                     ? "Gire o iPhone para configurar a conexão."
                     : "Conecte o SyncDeck ao agente do Windows para carregar seus botões.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 28)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            let columns = Array(
                repeating: GridItem(.flexible(), spacing: landscape ? 10 : 12),
                count: landscape ? 3 : 2
            )
            ScrollView(.vertical, showsIndicators: !landscape) {
                LazyVGrid(columns: columns, spacing: landscape ? 10 : 12) {
                    ForEach(model.actions) { action in
                        ActionCard(
                            action: action,
                            image: model.icons[action.id],
                            landscape: landscape,
                            height: cardHeight(proxy: proxy, landscape: landscape),
                            commanding: model.commandActionID == action.id,
                            onOpen: { model.requestOpen(action) },
                            onClose: { model.requestClose(action) },
                            onEdit: { model.presentEditor(for: action) }
                        )
                    }
                }
                .padding(.vertical, landscape ? 1 : 2)
            }
        }
    }

    private func cardHeight(proxy: GeometryProxy, landscape: Bool) -> CGFloat {
        guard landscape else { return 174 }
        let rows = max(1, min(3, Int(ceil(Double(model.actions.count) / 3.0))))
        let available = proxy.size.height - 12 - CGFloat(rows - 1) * 10
        return max(88, min(164, available / CGFloat(rows)))
    }

    private var multipleCloseTitle: String {
        guard let action = model.multipleCloseAction else { return "Janelas abertas" }
        return "\(action.windowCount) janelas abertas"
    }
}

private struct SyncDeckMark: View {
    let size: CGFloat

    var body: some View {
        VStack(spacing: size * 0.12) {
            HStack(spacing: size * 0.12) {
                tile(Color(red: 114.0 / 255.0, green: 245.0 / 255.0, blue: 173.0 / 255.0))
                tile(Color(red: 80.0 / 255.0, green: 170.0 / 255.0, blue: 120.0 / 255.0))
            }
            HStack(spacing: size * 0.12) {
                tile(Color(red: 80.0 / 255.0, green: 170.0 / 255.0, blue: 120.0 / 255.0))
                tile(Color(red: 114.0 / 255.0, green: 245.0 / 255.0, blue: 173.0 / 255.0))
            }
        }
        .rotationEffect(.degrees(-8))
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func tile(_ color: Color) -> some View {
        RoundedRectangle(cornerRadius: size * 0.13, style: .continuous)
            .fill(color)
            .frame(width: size * 0.44, height: size * 0.44)
    }
}

private struct ActionCard: View {
    let action: SyncAction
    let image: UIImage?
    let landscape: Bool
    let height: CGFloat
    let commanding: Bool
    let onOpen: () -> Void
    let onClose: () -> Void
    let onEdit: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: landscape ? 21 : 25, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: landscape ? 21 : 25, style: .continuous)
                        .fill(cardGradient)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: landscape ? 21 : 25, style: .continuous)
                        .stroke(action.isOpen ? accent.opacity(0.95) : Color.white.opacity(0.11), lineWidth: action.isOpen ? 2 : 1)
                )
                .shadow(color: action.isOpen ? accent.opacity(0.42) : .black.opacity(0.25), radius: action.isOpen ? 13 : 4, y: 4)

            if landscape {
                ActionIcon(action: action, image: image, size: min(92, height * 0.62))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            } else {
                VStack(alignment: .leading, spacing: 13) {
                    ActionIcon(action: action, image: image, size: 60)
                    Spacer(minLength: 4)
                    Text(action.label)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(.white.opacity(0.96))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .padding(15)

                if action.closable {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white.opacity(0.72))
                            .frame(width: 32, height: 32)
                            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 13))
                    }
                    .buttonStyle(.plain)
                    .padding(12)
                    .accessibilityLabel("Fechar \(action.label)")
                }
            }
        }
        .frame(height: height)
        .contentShape(RoundedRectangle(cornerRadius: landscape ? 21 : 25, style: .continuous))
        .scaleEffect(commanding ? 0.94 : 1)
        .opacity(commanding ? 0.72 : 1)
        .animation(.spring(response: 0.28, dampingFraction: 0.72), value: commanding)
        .animation(.spring(response: 0.38, dampingFraction: 0.78), value: action.isOpen)
        .onTapGesture(perform: onOpen)
        .contextMenu {
            Button(action: onOpen) {
                Label(action.isWakeAction ? (action.isOpen ? "PC já está ligado" : "Ligar o PC") : "Abrir no PC", systemImage: "play.fill")
            }
            if action.closable {
                Button(role: .destructive, action: onClose) {
                    Label("Fechar janela", systemImage: "xmark.square")
                }
            }
            if !action.isWakeAction {
                Button(action: onEdit) {
                    Label("Editar botão", systemImage: "slider.horizontal.3")
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
        .accessibilityHint("Toque para abrir; mantenha pressionado para ver as opções.")
    }

    private var accent: Color { Color(syncDeckHex: action.color) }

    private var cardGradient: LinearGradient {
        if action.isOpen {
            return LinearGradient(
                colors: [accent.opacity(0.30), Color(red: 0.10, green: 0.12, blue: 0.17).opacity(0.92), accent.opacity(0.13)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        return LinearGradient(
            colors: [Color.white.opacity(0.08), Color(red: 0.07, green: 0.08, blue: 0.12).opacity(0.92)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var accessibilityText: String {
        if action.isWakeAction { return action.isOpen ? "Ligar PC. O computador já está conectado." : "Ligar PC." }
        let state = action.isOpen ? "aberto" : "fechado"
        let windows = action.windowCount > 1 ? ", \(action.windowCount) janelas" : ""
        return "\(action.label), \(state)\(windows)"
    }
}

private struct ActionIcon: View {
    let action: SyncAction
    let image: UIImage?
    let size: CGFloat

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                Image(systemName: DeckSymbols.name(for: action.icon, type: action.type))
                    .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(
                        LinearGradient(
                            colors: [Color(syncDeckHex: action.color).opacity(0.82), Color(syncDeckHex: action.color).opacity(0.35)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        in: RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
                    )
                    .overlay(RoundedRectangle(cornerRadius: size * 0.25).stroke(Color.white.opacity(0.16)))
            }
        }
        .frame(width: size, height: size)
    }
}

private struct DeckBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.025, green: 0.03, blue: 0.055),
                    Color(red: 0.065, green: 0.075, blue: 0.13),
                    Color(red: 0.025, green: 0.035, blue: 0.065),
                    Color(red: 0.035, green: 0.038, blue: 0.055)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Circle()
                .fill(Color.purple.opacity(0.09))
                .frame(width: 360, height: 360)
                .blur(radius: 55)
                .offset(x: 145, y: -250)
            Circle()
                .fill(Color.green.opacity(0.07))
                .frame(width: 320, height: 320)
                .blur(radius: 60)
                .offset(x: -150, y: 280)
        }
        .ignoresSafeArea()
    }
}

enum DeckSymbols {
    static func name(for icon: String, type: String) -> String {
        switch icon.lowercased() {
        case "chrome", "browser", "web": return "globe"
        case "whatsapp", "chat": return "message.fill"
        case "outlook", "mail": return "envelope.fill"
        case "folder", "explorer", "downloads": return "folder.fill"
        case "terminal", "cmd", "command": return "terminal.fill"
        case "chatgpt", "sparkles": return "sparkles"
        case "calculator": return "plus.forwardslash.minus"
        case "power", "shutdown": return "power"
        case "link": return "link"
        default:
            switch type.lowercased() {
            case "url": return "safari.fill"
            case "path": return "folder.fill"
            case "command": return "terminal.fill"
            case "hotkey": return "keyboard.fill"
            default: return "square.grid.2x2.fill"
            }
        }
    }
}

extension Color {
    init(syncDeckHex value: String) {
        var text = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if text.hasPrefix("#") { text.removeFirst() }
        guard text.count == 6, let number = UInt64(text, radix: 16) else {
            self = Color(red: 0.41, green: 0.45, blue: 0.53)
            return
        }
        self = Color(
            red: Double((number >> 16) & 0xff) / 255,
            green: Double((number >> 8) & 0xff) / 255,
            blue: Double(number & 0xff) / 255
        )
    }
}
