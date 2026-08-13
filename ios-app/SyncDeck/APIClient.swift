import Darwin
import Foundation
import UIKit

actor SyncDeckAPI {
    private enum Keys {
        static let host = "syncdeck.host"
        static let port = "syncdeck.port"
        static let clientID = "syncdeck.client-id"
        static let fingerprint = "syncdeck.server-fingerprint"
        static let wakeMAC = "syncdeck.wake-mac"
        static let wakeBroadcast = "syncdeck.wake-broadcast"
        static let wakePort = "syncdeck.wake-port"
        static let wakeInterface = "syncdeck.wake-interface"
    }

    private struct RawResponse {
        let status: Int
        let contentType: String
        let body: Data
    }

    private let defaults = UserDefaults.standard
    private let maximumResponseBytes = 262_144
    private let defaultPort = 47_321
    private let discoveryWorkers = 24
    private var clockOffsetSeconds: Int64 = 0
    private var pendingServerFingerprint = ""
    private let iconCache: URL

    init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        iconCache = base.appendingPathComponent("syncdeck-action-icons-v2", isDirectory: true)
        try? FileManager.default.createDirectory(at: iconCache, withIntermediateDirectories: true)
    }

    func endpoint() -> (host: String, port: Int) {
        (defaults.string(forKey: Keys.host) ?? "", storedPort())
    }

    func isConfigured() -> Bool {
        let value = endpoint()
        return Self.isPrivateIPv4(value.host) && (1_024...65_535).contains(value.port)
    }

    func isPaired() -> Bool {
        isConfigured() && !(defaults.string(forKey: Keys.clientID) ?? "").isEmpty && SecureStore.load() != nil
    }

    func hasWakeConfiguration() -> Bool {
        Self.normalizedMAC(defaults.string(forKey: Keys.wakeMAC) ?? "") != nil &&
            Self.validWakeAddress(defaults.string(forKey: Keys.wakeBroadcast) ?? "")
    }

    func setEndpoint(host: String, port: Int) throws {
        let cleaned = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.isPrivateIPv4(cleaned) else {
            throw SyncDeckError.friendly("Use um IP privado, como 192.168.0.10.")
        }
        guard (1_024...65_535).contains(port) else {
            throw SyncDeckError.friendly("Porta inválida.")
        }
        defaults.set(cleaned, forKey: Keys.host)
        defaults.set(port, forKey: Keys.port)
    }

    func clearPairing() {
        SecureStore.clear()
        for key in [
            Keys.clientID,
            Keys.fingerprint,
            Keys.wakeMAC,
            Keys.wakeBroadcast,
            Keys.wakePort,
            Keys.wakeInterface
        ] {
            defaults.removeObject(forKey: key)
        }
        pendingServerFingerprint = ""
    }

    func getStatus() async throws -> ServerStatus {
        let value = endpoint()
        let status = try await fetchStatus(at: value.host, port: value.port, timeout: 7)
        observe(status)
        return status
    }

    func getStatusWithRecovery() async throws -> ServerStatus {
        do {
            return try await getStatus()
        } catch {
            let original = error
            if let recovered = await discoverPairedComputer() {
                observe(recovered)
                return recovered
            }
            throw original
        }
    }

    func pair(status: ServerStatus, code: String) async throws {
        guard status.pairingAvailable else {
            throw SyncDeckError.friendly("Gere um código de pareamento no Windows.")
        }
        guard code.range(of: "^[0-9]{6}$", options: .regularExpression) != nil else {
            throw SyncDeckError.friendly("Digite o código de 6 números.")
        }
        guard !status.modulus.isEmpty, !status.exponent.isEmpty else {
            throw SyncDeckError.friendly("O agente não forneceu uma chave de pareamento válida.")
        }

        let clientID = UUID().uuidString.lowercased()
        let secret = try SyncDeckCrypto.randomBytes(count: 32)
        let payload: [String: Any] = [
            "Code": code,
            "ClientId": clientID,
            "DeviceName": UIDevice.current.name.isEmpty ? "iPhone" : UIDevice.current.name,
            "Secret": SyncDeckCrypto.base64URL(secret)
        ]
        let payloadData = try JSONSerialization.data(withJSONObject: payload)
        let encrypted = try SyncDeckCrypto.encryptPairingPayload(
            payloadData,
            modulus: status.modulus,
            exponent: status.exponent
        )
        _ = try await requestJSON(
            method: "POST",
            path: "/api/pair",
            object: ["Payload": encrypted.base64EncodedString()],
            signed: false
        )

        do {
            try SecureStore.save(secret: secret)
            defaults.set(clientID, forKey: Keys.clientID)
            defaults.set(status.fingerprint, forKey: Keys.fingerprint)
            pendingServerFingerprint = ""
        } catch {
            SecureStore.clear()
            defaults.removeObject(forKey: Keys.clientID)
            throw error
        }
    }

    func getActions(editable: Bool) async throws -> [SyncAction] {
        let envelope = try await requestJSON(
            method: "GET",
            path: editable ? "/api/actions/edit" : "/api/actions",
            object: nil,
            signed: true
        )
        guard let list = envelope["Data"] as? [[String: Any]] else {
            throw SyncDeckError.invalidResponse
        }
        return list.map(SyncAction.init(dictionary:))
    }

    func getActionStates() async throws -> [ActionState] {
        let envelope = try await requestJSON(
            method: "GET",
            path: "/api/actions/state",
            object: nil,
            signed: true
        )
        guard let list = envelope["Data"] as? [[String: Any]] else {
            throw SyncDeckError.invalidResponse
        }
        return list.map(ActionState.init(dictionary:))
    }

    func refreshWakeConfiguration() async throws -> WakeConfiguration {
        let envelope = try await requestJSON(
            method: "GET",
            path: "/api/wake-config",
            object: nil,
            signed: true
        )
        guard let data = envelope["Data"] as? [String: Any] else {
            throw SyncDeckError.invalidResponse
        }

        let macText = data.string("MacAddress", "macAddress")
        let broadcast = data.string("BroadcastAddress", "broadcastAddress")
        let port = data.int("Port", "port", fallback: 9)
        let interface = data.string("InterfaceName", "interfaceName", fallback: "Rede Ethernet")
        guard let mac = Self.normalizedMAC(macText),
              Self.validWakeAddress(broadcast),
              (1...65_535).contains(port) else {
            throw SyncDeckError.friendly("O PC enviou uma configuração Wake-on-LAN inválida.")
        }

        defaults.set(mac, forKey: Keys.wakeMAC)
        defaults.set(broadcast, forKey: Keys.wakeBroadcast)
        defaults.set(port, forKey: Keys.wakePort)
        defaults.set(interface, forKey: Keys.wakeInterface)
        return WakeConfiguration(
            macAddress: mac,
            broadcastAddress: broadcast,
            port: port,
            interfaceName: interface
        )
    }

    func wakeComputer() throws {
        let macText = defaults.string(forKey: Keys.wakeMAC) ?? ""
        let savedBroadcast = defaults.string(forKey: Keys.wakeBroadcast) ?? ""
        let port = defaults.object(forKey: Keys.wakePort) == nil ? 9 : defaults.integer(forKey: Keys.wakePort)
        guard let normalized = Self.normalizedMAC(macText),
              Self.validWakeAddress(savedBroadcast),
              (1...65_535).contains(port) else {
            throw SyncDeckError.friendly("Ligue o PC normalmente uma vez para o SyncDeck salvar a placa de rede.")
        }

        var mac: [UInt8] = []
        for index in 0..<6 {
            let start = normalized.index(normalized.startIndex, offsetBy: index * 2)
            let end = normalized.index(start, offsetBy: 2)
            guard let byte = UInt8(normalized[start..<end], radix: 16) else {
                throw SyncDeckError.friendly("O endereço da placa de rede salvo é inválido.")
            }
            mac.append(byte)
        }

        var packet = Data(repeating: 0xff, count: 6)
        for _ in 0..<16 { packet.append(contentsOf: mac) }
        var destinations = Self.currentBroadcastAddresses()
        destinations.insert(savedBroadcast)
        destinations.insert("255.255.255.255")
        let sent = Self.sendMagicPacket(packet, destinations: Array(destinations), port: port)
        guard sent > 0 else {
            throw SyncDeckError.friendly("O iPhone não conseguiu enviar o sinal pela rede Wi-Fi.")
        }
    }

    func actionIcon(for action: SyncAction) async throws -> UIImage {
        guard action.id.range(of: "^[a-z0-9][a-z0-9-]{1,63}$", options: .regularExpression) != nil else {
            throw SyncDeckError.friendly("Identificador de imagem inválido.")
        }
        let token = Self.safeCachePart(action.imageKey.isEmpty ? "legacy" : action.imageKey)
        let destination = iconCache.appendingPathComponent("\(Self.safeCachePart(action.id))-\(token).png")
        if let data = try? Data(contentsOf: destination),
           data.count <= maximumResponseBytes,
           let image = UIImage(data: data) {
            return image
        }

        let path = "/api/icons/\(action.id)"
        let response = try await requestRaw(method: "GET", path: path, object: nil, signed: true, accept: "image/png")
        guard response.status == 200,
              response.contentType.lowercased().hasPrefix("image/png"),
              let image = UIImage(data: response.body),
              image.size.width > 0,
              image.size.height > 0,
              image.size.width <= 1_024,
              image.size.height <= 1_024 else {
            throw SyncDeckError.friendly(Self.responseMessage(response.body, fallback: "O Windows não encontrou a imagem desse aplicativo."))
        }
        try? response.body.write(to: destination, options: .atomic)
        return image
    }

    func execute(action: SyncAction, operation: String, confirmed: Bool) async throws {
        _ = try await requestJSON(
            method: "POST",
            path: "/api/execute",
            object: [
                "ActionId": action.id,
                "Operation": operation,
                "Confirmed": confirmed
            ],
            signed: true
        )
    }

    func save(action: SyncAction) async throws {
        _ = try await requestJSON(
            method: "POST",
            path: "/api/actions/save",
            object: ["Action": action.savingDictionary()],
            signed: true
        )
    }

    func delete(action: SyncAction) async throws {
        _ = try await requestJSON(
            method: "POST",
            path: "/api/actions/delete",
            object: ["ActionId": action.id],
            signed: true
        )
    }

    private func storedPort() -> Int {
        defaults.object(forKey: Keys.port) == nil ? defaultPort : defaults.integer(forKey: Keys.port)
    }

    private func requestJSON(
        method: String,
        path: String,
        object: [String: Any]?,
        signed: Bool
    ) async throws -> [String: Any] {
        let response = try await requestRaw(
            method: method,
            path: path,
            object: object,
            signed: signed,
            accept: "application/json"
        )
        guard let envelope = try JSONSerialization.jsonObject(with: response.body) as? [String: Any] else {
            throw SyncDeckError.invalidResponse
        }
        guard envelope.bool("Ok", "ok") else {
            throw SyncDeckError.friendly(envelope.string("Message", "message", fallback: "O agente recusou a solicitação."))
        }
        return envelope
    }

    private func requestRaw(
        method: String,
        path: String,
        object: [String: Any]?,
        signed: Bool,
        accept: String
    ) async throws -> RawResponse {
        let value = endpoint()
        return try await requestRawAt(
            host: value.host,
            port: value.port,
            method: method,
            path: path,
            object: object,
            signed: signed,
            accept: accept,
            timeout: 7
        )
    }

    private func requestRawAt(
        host: String,
        port: Int,
        method: String,
        path: String,
        object: [String: Any]?,
        signed: Bool,
        accept: String,
        timeout: TimeInterval
    ) async throws -> RawResponse {
        let body = object == nil ? Data() : try JSONSerialization.data(withJSONObject: object!)
        guard let url = URL(string: "http://\(host):\(port)\(path)") else {
            throw SyncDeckError.friendly("Endereço do PC inválido.")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body.isEmpty ? nil : body
        request.timeoutInterval = timeout
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.setValue("close", forHTTPHeaderField: "Connection")
        if !body.isEmpty { request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type") }

        var requestSecret: Data?
        var requestNonce = ""
        if signed {
            guard let secret = SecureStore.load(),
                  let clientID = defaults.string(forKey: Keys.clientID),
                  !clientID.isEmpty else {
                throw SyncDeckError.friendly("iPhone não pareado.")
            }
            let timestamp = Int64(Date().timeIntervalSince1970) + clockOffsetSeconds
            let nonce = SyncDeckCrypto.base64URL(try SyncDeckCrypto.randomBytes(count: 16))
            let signature = SyncDeckCrypto.sign(
                secret: secret,
                method: method,
                path: path,
                timestamp: timestamp,
                nonce: nonce,
                body: body
            )
            request.setValue(clientID, forHTTPHeaderField: "X-SyncDeck-Client")
            request.setValue(String(timestamp), forHTTPHeaderField: "X-SyncDeck-Timestamp")
            request.setValue(nonce, forHTTPHeaderField: "X-SyncDeck-Nonce")
            request.setValue(signature, forHTTPHeaderField: "X-SyncDeck-Signature")
            requestSecret = secret
            requestNonce = nonce
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout + 1
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        let session = URLSession(configuration: configuration)
        do {
            let (data, response) = try await session.data(for: request)
            guard data.count <= maximumResponseBytes,
                  let http = response as? HTTPURLResponse else {
                throw SyncDeckError.invalidResponse
            }
            if signed, let secret = requestSecret {
                let supplied = http.value(forHTTPHeaderField: "X-SyncDeck-Response-Signature") ?? ""
                let expected = SyncDeckCrypto.signResponse(
                    secret: secret,
                    status: http.statusCode,
                    nonce: requestNonce,
                    body: data
                )
                guard SyncDeckCrypto.constantTimeEquals(expected, supplied) else {
                    throw SyncDeckError.friendly("A resposta do PC não pôde ser autenticada. Pareie novamente ou verifique a rede.")
                }
                confirmObservedServer()
            }
            return RawResponse(
                status: http.statusCode,
                contentType: http.value(forHTTPHeaderField: "Content-Type") ?? "",
                body: data
            )
        } catch let error as SyncDeckError {
            throw error
        } catch let error as URLError {
            switch error.code {
            case .notConnectedToInternet, .cannotConnectToHost, .timedOut, .networkConnectionLost, .cannotFindHost:
                throw SyncDeckError.friendly("PC indisponível. Confira o Wi-Fi, o endereço, o agente e a permissão Rede Local do iPhone.")
            default:
                throw SyncDeckError.friendly(error.localizedDescription)
            }
        } catch {
            throw SyncDeckError.friendly(error.localizedDescription)
        }
    }

    private func fetchStatus(at host: String, port: Int, timeout: TimeInterval) async throws -> ServerStatus {
        guard Self.isPrivateIPv4(host), (1_024...65_535).contains(port) else {
            throw SyncDeckError.friendly("Endereço privado do PC inválido.")
        }
        let response = try await requestRawAt(
            host: host,
            port: port,
            method: "GET",
            path: "/api/status",
            object: nil,
            signed: false,
            accept: "application/json",
            timeout: timeout
        )
        guard let envelope = try JSONSerialization.jsonObject(with: response.body) as? [String: Any] else {
            throw SyncDeckError.invalidResponse
        }
        guard envelope.bool("Ok", "ok") else {
            throw SyncDeckError.friendly(envelope.string("Message", "message", fallback: "O agente recusou a solicitação."))
        }
        guard let data = envelope["Data"] as? [String: Any] else {
            throw SyncDeckError.invalidResponse
        }

        var status = ServerStatus()
        status.name = data.string("name", "Name", fallback: "PC Windows")
        status.host = host
        status.serverTime = data.int64("serverTime", "ServerTime", fallback: Int64(Date().timeIntervalSince1970))
        status.pairedDevices = data.int("pairedDevices", "PairedDevices")
        if let pairing = data.value("pairing", "Pairing") as? [String: Any] {
            status.pairingAvailable = pairing.bool("Available", "available")
            status.modulus = pairing.string("Modulus", "modulus")
            status.exponent = pairing.string("Exponent", "exponent")
            status.expiresAt = pairing.int64("ExpiresAt", "expiresAt")
            if !status.modulus.isEmpty, !status.exponent.isEmpty {
                status.fingerprint = try SyncDeckCrypto.fingerprint(
                    modulus: status.modulus,
                    exponent: status.exponent
                )
                let advertised = pairing.string("Fingerprint", "fingerprint")
                if !advertised.isEmpty,
                   advertised.caseInsensitiveCompare(status.fingerprint) != .orderedSame {
                    throw SyncDeckError.friendly("A chave recebida não corresponde ao agente. Não faça o pareamento.")
                }
            }
        }
        return status
    }

    private func observe(_ status: ServerStatus) {
        clockOffsetSeconds = status.serverTime - Int64(Date().timeIntervalSince1970)
        if !status.fingerprint.isEmpty { pendingServerFingerprint = status.fingerprint }
    }

    private func confirmObservedServer() {
        guard !pendingServerFingerprint.isEmpty,
              !(defaults.string(forKey: Keys.clientID) ?? "").isEmpty else { return }
        defaults.set(pendingServerFingerprint, forKey: Keys.fingerprint)
        pendingServerFingerprint = ""
    }

    private func discoverPairedComputer() async -> ServerStatus? {
        let expected = defaults.string(forKey: Keys.fingerprint) ?? ""
        guard !expected.isEmpty,
              !(defaults.string(forKey: Keys.clientID) ?? "").isEmpty,
              SecureStore.load() != nil else { return nil }

        let current = endpoint()
        var prefixes = Set<String>()
        if let savedPrefix = Self.prefix(of: current.host) { prefixes.insert(savedPrefix) }
        for address in Self.localPrivateIPv4Addresses() {
            if let prefix = Self.prefix(of: address) { prefixes.insert(prefix) }
            if prefixes.count >= 3 { break }
        }
        guard !prefixes.isEmpty else { return nil }

        let ownAddresses = Set(Self.localPrivateIPv4Addresses())
        var candidates: [String] = []
        for prefix in prefixes.sorted() {
            for last in 1...254 {
                let candidate = "\(prefix)\(last)"
                if candidate != current.host, !ownAddresses.contains(candidate) { candidates.append(candidate) }
            }
        }

        let port = current.port
        let workers = min(discoveryWorkers, candidates.count)
        return await withTaskGroup(of: ServerStatus?.self) { group in
            var nextIndex = 0
            for _ in 0..<workers {
                let candidate = candidates[nextIndex]
                nextIndex += 1
                group.addTask { [weak self] in
                    guard let self else { return nil }
                    return try? await self.fetchStatus(at: candidate, port: port, timeout: 0.7)
                }
            }
            while let result = await group.next() {
                if var status = result,
                   status.fingerprint.caseInsensitiveCompare(expected) == .orderedSame {
                    status.endpointRecovered = true
                    defaults.set(status.host, forKey: Keys.host)
                    group.cancelAll()
                    return status
                }
                if nextIndex < candidates.count {
                    let candidate = candidates[nextIndex]
                    nextIndex += 1
                    group.addTask { [weak self] in
                        guard let self else { return nil }
                        return try? await self.fetchStatus(at: candidate, port: port, timeout: 0.7)
                    }
                }
            }
            return nil
        }
    }

    private static func localPrivateIPv4Addresses() -> [String] {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }
        var result: [String] = []
        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            defer { pointer = current.pointee.ifa_next }
            guard let address = current.pointee.ifa_addr,
                  address.pointee.sa_family == UInt8(AF_INET) else { continue }
            let flags = Int32(current.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }
            var socketAddress = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &socketAddress.sin_addr, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { continue }
            let value = String(cString: buffer)
            if isPrivateIPv4(value), !result.contains(value) { result.append(value) }
        }
        return result
    }

    private static func currentBroadcastAddresses() -> Set<String> {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }
        var result = Set<String>()
        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            defer { pointer = current.pointee.ifa_next }
            guard let address = current.pointee.ifa_addr,
                  let netmask = current.pointee.ifa_netmask,
                  address.pointee.sa_family == UInt8(AF_INET),
                  netmask.pointee.sa_family == UInt8(AF_INET) else { continue }
            let flags = Int32(current.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }
            let ip = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
            let mask = netmask.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
            var broadcast = in_addr(s_addr: ip.s_addr | ~mask.s_addr)
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &broadcast, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { continue }
            let value = String(cString: buffer)
            if validWakeAddress(value) { result.insert(value) }
        }
        return result
    }

    private static func sendMagicPacket(_ packet: Data, destinations: [String], port: Int) -> Int {
        let descriptor = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard descriptor >= 0 else { return 0 }
        defer { close(descriptor) }
        var enabled: Int32 = 1
        guard setsockopt(
            descriptor,
            SOL_SOCKET,
            SO_BROADCAST,
            &enabled,
            socklen_t(MemoryLayout<Int32>.size)
        ) == 0 else { return 0 }

        var sent = 0
        for repeatIndex in 0..<3 {
            for destination in destinations {
                var target = sockaddr_in()
                target.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
                target.sin_family = sa_family_t(AF_INET)
                target.sin_port = in_port_t(port).bigEndian
                guard inet_pton(AF_INET, destination, &target.sin_addr) == 1 else { continue }
                let result: Int = packet.withUnsafeBytes { bytes in
                    withUnsafePointer(to: &target) { pointer in
                        pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { address in
                            sendto(
                                descriptor,
                                bytes.baseAddress,
                                packet.count,
                                0,
                                address,
                                socklen_t(MemoryLayout<sockaddr_in>.size)
                            )
                        }
                    }
                }
                if result == packet.count { sent += 1 }
            }
            if repeatIndex < 2 { usleep(120_000) }
        }
        return sent
    }

    private static func normalizedMAC(_ value: String) -> String? {
        let cleaned = value.uppercased().filter { $0.isHexDigit }
        guard cleaned.count == 12,
              cleaned != "000000000000",
              cleaned != "FFFFFFFFFFFF",
              let first = UInt8(cleaned.prefix(2), radix: 16),
              first & 1 == 0 else { return nil }
        return cleaned
    }

    private static func validWakeAddress(_ value: String) -> Bool {
        value == "255.255.255.255" || isPrivateIPv4(value)
    }

    private static func prefix(of address: String) -> String? {
        guard isPrivateIPv4(address), let separator = address.lastIndex(of: ".") else { return nil }
        return String(address[...separator])
    }

    static func isPrivateIPv4(_ value: String) -> Bool {
        let parts = value.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return false }
        let numbers = parts.compactMap { Int($0) }
        guard numbers.count == 4, numbers.allSatisfy({ (0...255).contains($0) }) else { return false }
        return numbers[0] == 10 ||
            (numbers[0] == 172 && (16...31).contains(numbers[1])) ||
            (numbers[0] == 192 && numbers[1] == 168) ||
            (numbers[0] == 169 && numbers[1] == 254)
    }

    private static func safeCachePart(_ value: String) -> String {
        let cleaned = value.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "-" }
        return cleaned.isEmpty ? "item" : String(cleaned.prefix(64))
    }

    private static func responseMessage(_ data: Data, fallback: String) -> String {
        guard let envelope = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return fallback }
        return envelope.string("Message", "message", fallback: fallback)
    }
}
