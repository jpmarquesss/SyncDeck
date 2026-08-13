import CryptoKit
import Foundation
import Security

enum SecureStore {
    private static let service = "com.eudollyn.syncdeck"
    private static let account = "client-secret-v1"

    static func save(secret: Data) throws {
        clear()
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: secret
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SyncDeckError.friendly("O iPhone não conseguiu proteger a chave de pareamento.")
        }
    }

    static func load() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              data.count == 32 else { return nil }
        return data
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

enum SyncDeckCrypto {
    static func sha256(_ data: Data) -> Data {
        Data(SHA256.hash(data: data))
    }

    static func bodyHash(_ body: Data) -> String {
        sha256(body).map { String(format: "%02x", $0) }.joined()
    }

    static func sign(
        secret: Data,
        method: String,
        path: String,
        timestamp: Int64,
        nonce: String,
        body: Data
    ) -> String {
        let canonical = "\(method.uppercased())\n\(path)\n\(timestamp)\n\(nonce)\n\(bodyHash(body))"
        let key = SymmetricKey(data: secret)
        let signature = HMAC<SHA256>.authenticationCode(for: Data(canonical.utf8), using: key)
        return base64URL(Data(signature))
    }

    static func signResponse(secret: Data, status: Int, nonce: String, body: Data) -> String {
        let canonical = "RESPONSE\n\(status)\n\(nonce)\n\(bodyHash(body))"
        let key = SymmetricKey(data: secret)
        let signature = HMAC<SHA256>.authenticationCode(for: Data(canonical.utf8), using: key)
        return base64URL(Data(signature))
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decodeBase64(_ text: String) -> Data? {
        var value = text.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = value.count % 4
        if remainder > 0 { value += String(repeating: "=", count: 4 - remainder) }
        return Data(base64Encoded: value)
    }

    static func randomBytes(count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else {
            throw SyncDeckError.friendly("Não foi possível gerar dados criptográficos seguros.")
        }
        return Data(bytes)
    }

    static func fingerprint(modulus: String, exponent: String) throws -> String {
        guard let modulusData = decodeBase64(modulus),
              let exponentData = decodeBase64(exponent),
              !modulusData.isEmpty,
              !exponentData.isEmpty else {
            throw SyncDeckError.friendly("A chave pública recebida do PC é inválida.")
        }
        var combined = Data()
        combined.append(modulusData)
        combined.append(exponentData)
        let first = sha256(combined).prefix(6).map { String(format: "%02X", $0) }.joined()
        return "\(first.prefix(4))-\(first.dropFirst(4).prefix(4))-\(first.dropFirst(8).prefix(4))"
    }

    static func encryptPairingPayload(_ payload: Data, modulus: String, exponent: String) throws -> Data {
        guard let modulusData = decodeBase64(modulus),
              let exponentData = decodeBase64(exponent) else {
            throw SyncDeckError.friendly("A chave pública do agente é inválida.")
        }

        let publicKeyData = rsaPublicKeyDER(modulus: modulusData, exponent: exponentData)
        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String: modulusData.count * 8
        ]
        var keyError: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(publicKeyData as CFData, attributes as CFDictionary, &keyError) else {
            throw SyncDeckError.friendly("O iPhone não conseguiu interpretar a chave do PC.")
        }
        guard SecKeyIsAlgorithmSupported(key, .encrypt, .rsaEncryptionOAEPSHA1) else {
            throw SyncDeckError.friendly("Este iPhone não oferece a criptografia exigida pelo pareamento.")
        }
        var encryptionError: Unmanaged<CFError>?
        guard let encryptedValue = SecKeyCreateEncryptedData(
            key,
            .rsaEncryptionOAEPSHA1,
            payload as CFData,
            &encryptionError
        ) else {
            throw SyncDeckError.friendly("Não foi possível proteger o código de pareamento.")
        }
        return encryptedValue as Data
    }

    static func constantTimeEquals(_ left: String, _ right: String) -> Bool {
        let a = Array(left.utf8)
        let b = Array(right.utf8)
        var difference = a.count ^ b.count
        let count = max(a.count, b.count)
        for index in 0..<count {
            let av = index < a.count ? a[index] : 0
            let bv = index < b.count ? b[index] : 0
            difference |= Int(av ^ bv)
        }
        return difference == 0
    }

    private static func rsaPublicKeyDER(modulus: Data, exponent: Data) -> Data {
        let body = derInteger(modulus) + derInteger(exponent)
        return Data([0x30]) + derLength(body.count) + body
    }

    private static func derInteger(_ value: Data) -> Data {
        var bytes = Array(value.drop(while: { $0 == 0 }))
        if bytes.isEmpty { bytes = [0] }
        if bytes[0] & 0x80 != 0 { bytes.insert(0, at: 0) }
        return Data([0x02]) + derLength(bytes.count) + Data(bytes)
    }

    private static func derLength(_ length: Int) -> Data {
        if length < 128 { return Data([UInt8(length)]) }
        var value = length
        var bytes: [UInt8] = []
        while value > 0 {
            bytes.insert(UInt8(value & 0xff), at: 0)
            value >>= 8
        }
        return Data([0x80 | UInt8(bytes.count)]) + Data(bytes)
    }
}
