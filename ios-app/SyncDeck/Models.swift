import Foundation

struct SyncAction: Identifiable, Equatable {
    var id: String
    var label: String
    var type: String
    var target: String
    var arguments: String
    var workingDirectory: String
    var processNames: [String]
    var appNames: [String]
    var fallbackURL: String
    var icon: String
    var imageKey: String
    var color: String
    var confirm: Bool
    var closable: Bool
    var enabled: Bool
    var isOpen: Bool
    var windowCount: Int

    init(
        id: String = "",
        label: String = "",
        type: String = "app",
        target: String = "",
        arguments: String = "",
        workingDirectory: String = "",
        processNames: [String] = [],
        appNames: [String] = [],
        fallbackURL: String = "",
        icon: String = "app",
        imageKey: String = "",
        color: String = "#697386",
        confirm: Bool = false,
        closable: Bool = true,
        enabled: Bool = true,
        isOpen: Bool = false,
        windowCount: Int = 0
    ) {
        self.id = id
        self.label = label
        self.type = type
        self.target = target
        self.arguments = arguments
        self.workingDirectory = workingDirectory
        self.processNames = processNames
        self.appNames = appNames
        self.fallbackURL = fallbackURL
        self.icon = icon
        self.imageKey = imageKey
        self.color = color
        self.confirm = confirm
        self.closable = closable
        self.enabled = enabled
        self.isOpen = isOpen
        self.windowCount = windowCount
    }

    init(dictionary: [String: Any]) {
        id = dictionary.string("Id", "id")
        label = dictionary.string("Label", "label")
        type = dictionary.string("Type", "type", fallback: "app")
        target = dictionary.string("Target", "target")
        arguments = dictionary.string("Arguments", "arguments")
        workingDirectory = dictionary.string("WorkingDirectory", "workingDirectory")
        processNames = dictionary.strings("ProcessNames", "processNames")
        appNames = dictionary.strings("AppNames", "appNames")
        fallbackURL = dictionary.string("FallbackUrl", "fallbackUrl")
        icon = dictionary.string("Icon", "icon", fallback: "app")
        imageKey = dictionary.string("ImageKey", "imageKey")
        color = dictionary.string("Color", "color", fallback: "#697386")
        confirm = dictionary.bool("Confirm", "confirm")
        closable = dictionary.bool("Closable", "closable")
        enabled = dictionary.bool("Enabled", "enabled", fallback: true)
        isOpen = dictionary.bool("IsOpen", "isOpen")
        windowCount = dictionary.int("WindowCount", "windowCount")
    }

    static var newAction: SyncAction {
        SyncAction(
            id: "acao-\(UUID().uuidString.prefix(8).lowercased())",
            label: "Novo botão",
            color: "#58D89B"
        )
    }

    static func wake(online: Bool) -> SyncAction {
        SyncAction(
            id: "wake-pc",
            label: "Ligar PC",
            type: "wake",
            icon: "power",
            imageKey: "local-wake-v1",
            color: "#22C55E",
            confirm: true,
            closable: false,
            enabled: true,
            isOpen: online,
            windowCount: online ? 1 : 0
        )
    }

    var isWakeAction: Bool { id == "wake-pc" || type == "wake" }

    var isShutdownAction: Bool {
        id == "shutdown-pc" ||
            (type == "command" && target.lowercased().contains("shutdown"))
    }

    var normalizedID: String {
        let lowered = id.trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "pt_BR"))
            .lowercased()
        var value = String(lowered.unicodeScalars.map { scalar -> Character in
            let number = scalar.value
            let allowed = (number >= 97 && number <= 122) || (number >= 48 && number <= 57) || number == 45
            return allowed ? Character(String(scalar)) : "-"
        })
        while value.contains("--") { value = value.replacingOccurrences(of: "--", with: "-") }
        value = value.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        if value.count < 2 { value = "acao-\(UUID().uuidString.prefix(8).lowercased())" }
        return String(value.prefix(64))
    }

    func savingDictionary() -> [String: Any] {
        [
            "Id": normalizedID,
            "Label": label.trimmingCharacters(in: .whitespacesAndNewlines),
            "Type": type,
            "Target": target.trimmingCharacters(in: .whitespacesAndNewlines),
            "Arguments": arguments.trimmingCharacters(in: .whitespacesAndNewlines),
            "WorkingDirectory": workingDirectory.trimmingCharacters(in: .whitespacesAndNewlines),
            "ProcessNames": processNames,
            "AppNames": appNames,
            "FallbackUrl": fallbackURL.trimmingCharacters(in: .whitespacesAndNewlines),
            "Icon": icon,
            "Color": color.uppercased(),
            "Confirm": confirm || type == "command",
            "Closable": closable,
            "Enabled": enabled
        ]
    }

    static func splitList(_ text: String) -> [String] {
        text.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}

struct ActionState {
    let id: String
    let isOpen: Bool
    let windowCount: Int

    init(dictionary: [String: Any]) {
        id = dictionary.string("Id", "id")
        isOpen = dictionary.bool("IsOpen", "isOpen")
        windowCount = dictionary.int("WindowCount", "windowCount")
    }
}

struct ServerStatus {
    var name = "PC Windows"
    var host = ""
    var fingerprint = ""
    var modulus = ""
    var exponent = ""
    var pairingAvailable = false
    var expiresAt: Int64 = 0
    var serverTime: Int64 = 0
    var pairedDevices = 0
    var endpointRecovered = false
}

struct WakeConfiguration {
    let macAddress: String
    let broadcastAddress: String
    let port: Int
    let interfaceName: String
}

struct EditorPresentation: Identifiable {
    let id = UUID()
    let action: SyncAction
    let isNew: Bool
}

struct ConfirmationPrompt: Identifiable {
    enum Command {
        case execute(SyncAction, String, Bool)
        case wake
    }

    let id = UUID()
    let title: String
    let message: String
    let confirmTitle: String
    let destructive: Bool
    let command: Command
}

enum SyncDeckError: LocalizedError {
    case friendly(String)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .friendly(let message): return message
        case .invalidResponse: return "O agente não respondeu corretamente."
        }
    }
}

extension Dictionary where Key == String, Value == Any {
    func value(_ primary: String, _ alternate: String) -> Any? {
        self[primary] ?? self[alternate]
    }

    func string(_ primary: String, _ alternate: String, fallback: String = "") -> String {
        let result = value(primary, alternate) as? String ?? fallback
        return result.isEmpty ? fallback : result
    }

    func bool(_ primary: String, _ alternate: String, fallback: Bool = false) -> Bool {
        if let value = value(primary, alternate) as? Bool { return value }
        if let value = value(primary, alternate) as? NSNumber { return value.boolValue }
        return fallback
    }

    func int(_ primary: String, _ alternate: String, fallback: Int = 0) -> Int {
        if let value = value(primary, alternate) as? Int { return value }
        if let value = value(primary, alternate) as? NSNumber { return value.intValue }
        return fallback
    }

    func int64(_ primary: String, _ alternate: String, fallback: Int64 = 0) -> Int64 {
        if let value = value(primary, alternate) as? Int64 { return value }
        if let value = value(primary, alternate) as? NSNumber { return value.int64Value }
        return fallback
    }

    func strings(_ primary: String, _ alternate: String) -> [String] {
        value(primary, alternate) as? [String] ?? []
    }
}
