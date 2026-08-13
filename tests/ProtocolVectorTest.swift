import CryptoKit
import Foundation

func hexData(_ text: String) -> Data? {
    guard text.count.isMultiple(of: 2) else { return nil }
    var data = Data()
    var index = text.startIndex
    while index < text.endIndex {
        let next = text.index(index, offsetBy: 2)
        guard let byte = UInt8(text[index..<next], radix: 16) else { return nil }
        data.append(byte)
        index = next
    }
    return data
}

func sha256Hex(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

func base64URL(_ data: Data) -> String {
    data.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

func sign(secret: Data, canonical: String) -> String {
    let key = SymmetricKey(data: secret)
    let code = HMAC<SHA256>.authenticationCode(for: Data(canonical.utf8), using: key)
    return base64URL(Data(code))
}

let path = CommandLine.arguments.dropFirst().first ?? "tests/protocol-vector.json"
let vectorData = try Data(contentsOf: URL(fileURLWithPath: path))
guard let vector = try JSONSerialization.jsonObject(with: vectorData) as? [String: Any],
      let secretText = vector["secretHex"] as? String,
      let secret = hexData(secretText),
      let method = vector["method"] as? String,
      let route = vector["path"] as? String,
      let timestamp = vector["timestamp"] as? NSNumber,
      let nonce = vector["nonce"] as? String,
      let bodyText = vector["body"] as? String,
      let expectedBodyHash = vector["bodySha256"] as? String,
      let expectedRequest = vector["signatureBase64Url"] as? String,
      let responseStatus = vector["responseStatus"] as? NSNumber,
      let responseText = vector["responseBody"] as? String,
      let expectedResponseHash = vector["responseBodySha256"] as? String,
      let expectedResponse = vector["responseSignatureBase64Url"] as? String else {
    fatalError("Vetor criptográfico incompleto.")
}

let body = Data(bodyText.utf8)
let bodyHash = sha256Hex(body)
let requestCanonical = "\(method)\n\(route)\n\(timestamp.int64Value)\n\(nonce)\n\(bodyHash)"
let requestSignature = sign(secret: secret, canonical: requestCanonical)

let responseBody = Data(responseText.utf8)
let responseHash = sha256Hex(responseBody)
let responseCanonical = "RESPONSE\n\(responseStatus.intValue)\n\(nonce)\n\(responseHash)"
let responseSignature = sign(secret: secret, canonical: responseCanonical)

guard bodyHash == expectedBodyHash,
      requestSignature == expectedRequest,
      responseHash == expectedResponseHash,
      responseSignature == expectedResponse else {
    fatalError("Implementação Swift diverge do vetor oficial do protocolo.")
}

print("SyncDeck Swift protocol vector: OK")
