using System;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

namespace SyncDeck.Agent
{
    public sealed class PairingStatus
    {
        public bool Available { get; set; }
        public string Fingerprint { get; set; }
        public string Modulus { get; set; }
        public string Exponent { get; set; }
        public long ExpiresAt { get; set; }
    }

    public sealed class PairingManager : IDisposable
    {
        private readonly object _gate = new object();
        private readonly RSACryptoServiceProvider _rsa;
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private string _code;
        private DateTime _expiresUtc;
        private int _remainingAttempts;

        public PairingManager()
        {
            CspParameters parameters = new CspParameters
            {
                KeyContainerName = "SyncDeck.PairingKey.v1",
                Flags = CspProviderFlags.NoPrompt
            };
            _rsa = new RSACryptoServiceProvider(2048, parameters) { PersistKeyInCsp = true };
        }

        public string CurrentCode
        {
            get { lock (_gate) { return IsAvailableUnsafe() ? _code : "------"; } }
        }

        public DateTime ExpiresUtc
        {
            get { lock (_gate) { return _expiresUtc; } }
        }

        public string Fingerprint
        {
            get
            {
                RSAParameters key = _rsa.ExportParameters(false);
                byte[] combined = key.Modulus.Concat(key.Exponent).ToArray();
                using (SHA256 sha = SHA256.Create())
                {
                    string hex = BitConverter.ToString(sha.ComputeHash(combined)).Replace("-", string.Empty);
                    return hex.Substring(0, 4) + "-" + hex.Substring(4, 4) + "-" + hex.Substring(8, 4);
                }
            }
        }

        public void Begin()
        {
            lock (_gate)
            {
                byte[] bytes = new byte[4];
                using (RandomNumberGenerator rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
                uint value = BitConverter.ToUInt32(bytes, 0);
                _code = (100000 + (value % 900000)).ToString();
                _expiresUtc = DateTime.UtcNow.AddMinutes(5);
                _remainingAttempts = 5;
            }
        }

        public void End()
        {
            lock (_gate)
            {
                _code = null;
                _remainingAttempts = 0;
                _expiresUtc = DateTime.MinValue;
            }
        }

        public PairingStatus GetStatus()
        {
            RSAParameters key = _rsa.ExportParameters(false);
            lock (_gate)
            {
                return new PairingStatus
                {
                    Available = IsAvailableUnsafe(),
                    Fingerprint = Fingerprint,
                    Modulus = Convert.ToBase64String(key.Modulus),
                    Exponent = Convert.ToBase64String(key.Exponent),
                    ExpiresAt = new DateTimeOffset(_expiresUtc).ToUnixTimeSeconds()
                };
            }
        }

        public bool TryComplete(string encryptedPayload, out PairPayload payload, out string error)
        {
            payload = null;
            error = null;
            lock (_gate)
            {
                if (!IsAvailableUnsafe())
                {
                    error = "Abra 'Parear celular' no agente do Windows e tente novamente.";
                    return false;
                }

                _remainingAttempts--;
                try
                {
                    byte[] encrypted = Convert.FromBase64String(encryptedPayload ?? string.Empty);
                    byte[] decrypted = _rsa.Decrypt(encrypted, true);
                    if (decrypted.Length > 1024) throw new CryptographicException();
                    payload = _json.Deserialize<PairPayload>(Encoding.UTF8.GetString(decrypted));
                }
                catch
                {
                    error = "Dados de pareamento inválidos.";
                    return false;
                }

                Guid parsed;
                byte[] secret;
                if (payload == null || !SecureEquals(payload.Code, _code) ||
                    !Guid.TryParse(payload.ClientId, out parsed) ||
                    string.IsNullOrWhiteSpace(payload.DeviceName) || payload.DeviceName.Length > 80 ||
                    payload.DeviceName.Any(char.IsControl) ||
                    !TryBase64UrlDecode(payload.Secret, out secret) || secret.Length != 32)
                {
                    error = _remainingAttempts > 0
                        ? "Código ou dados inválidos. Tentativas restantes: " + _remainingAttempts + "."
                        : "Pareamento bloqueado. Gere um novo código no Windows.";
                    if (_remainingAttempts <= 0) End();
                    return false;
                }

                End();
                return true;
            }
        }

        private bool IsAvailableUnsafe()
        {
            return !string.IsNullOrWhiteSpace(_code) && _remainingAttempts > 0 && DateTime.UtcNow < _expiresUtc;
        }

        private static bool SecureEquals(string left, string right)
        {
            byte[] a = Encoding.UTF8.GetBytes(left ?? string.Empty);
            byte[] b = Encoding.UTF8.GetBytes(right ?? string.Empty);
            int difference = a.Length ^ b.Length;
            int length = Math.Max(a.Length, b.Length);
            for (int i = 0; i < length; i++)
            {
                byte av = i < a.Length ? a[i] : (byte)0;
                byte bv = i < b.Length ? b[i] : (byte)0;
                difference |= av ^ bv;
            }
            return difference == 0;
        }

        public static string Base64UrlEncode(byte[] bytes)
        {
            return Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        }

        public static bool TryBase64UrlDecode(string value, out byte[] bytes)
        {
            bytes = null;
            try
            {
                string text = (value ?? string.Empty).Replace('-', '+').Replace('_', '/');
                switch (text.Length % 4)
                {
                    case 2: text += "=="; break;
                    case 3: text += "="; break;
                }
                bytes = Convert.FromBase64String(text);
                return true;
            }
            catch { return false; }
        }

        public void Dispose()
        {
            _rsa.Dispose();
        }
    }

    internal static class RequestSigner
    {
        public static string BodyHash(byte[] body)
        {
            using (SHA256 sha = SHA256.Create())
            {
                byte[] hash = sha.ComputeHash(body ?? new byte[0]);
                return BitConverter.ToString(hash).Replace("-", string.Empty).ToLowerInvariant();
            }
        }

        public static string Sign(byte[] secret, string method, string path, string timestamp, string nonce, byte[] body)
        {
            string canonical = (method ?? string.Empty).ToUpperInvariant() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + BodyHash(body);
            using (HMACSHA256 hmac = new HMACSHA256(secret))
            {
                return PairingManager.Base64UrlEncode(hmac.ComputeHash(Encoding.UTF8.GetBytes(canonical)));
            }
        }

        public static string SignResponse(byte[] secret, int statusCode, string requestNonce, byte[] body)
        {
            string canonical = "RESPONSE\n" + statusCode + "\n" + requestNonce + "\n" + BodyHash(body);
            using (HMACSHA256 hmac = new HMACSHA256(secret))
            {
                return PairingManager.Base64UrlEncode(hmac.ComputeHash(Encoding.UTF8.GetBytes(canonical)));
            }
        }

        public static bool FixedTimeEquals(string expected, string supplied)
        {
            byte[] a = Encoding.ASCII.GetBytes(expected ?? string.Empty);
            byte[] b = Encoding.ASCII.GetBytes(supplied ?? string.Empty);
            int difference = a.Length ^ b.Length;
            int length = Math.Max(a.Length, b.Length);
            for (int i = 0; i < length; i++)
            {
                byte av = i < a.Length ? a[i] : (byte)0;
                byte bv = i < b.Length ? b[i] : (byte)0;
                difference |= av ^ bv;
            }
            return difference == 0;
        }
    }

    internal static class PayloadCipher
    {
        private static readonly byte[] Context = Encoding.UTF8.GetBytes("SyncDeck.Encryption.v1");

        private static byte[] DeriveKey(byte[] secret)
        {
            using (HMACSHA256 hmac = new HMACSHA256(secret))
                return hmac.ComputeHash(Context);
        }

        public static byte[] Encrypt(byte[] secret, byte[] plaintext)
        {
            if (secret == null || secret.Length != 32) throw new CryptographicException("Chave inválida.");
            byte[] value = plaintext ?? new byte[0];
            using (Aes aes = Aes.Create())
            {
                aes.KeySize = 256;
                aes.BlockSize = 128;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = DeriveKey(secret);
                aes.GenerateIV();
                using (ICryptoTransform encryptor = aes.CreateEncryptor())
                {
                    byte[] ciphertext = encryptor.TransformFinalBlock(value, 0, value.Length);
                    byte[] result = new byte[aes.IV.Length + ciphertext.Length];
                    Buffer.BlockCopy(aes.IV, 0, result, 0, aes.IV.Length);
                    Buffer.BlockCopy(ciphertext, 0, result, aes.IV.Length, ciphertext.Length);
                    return result;
                }
            }
        }

        public static byte[] Decrypt(byte[] secret, byte[] payload, int maximumPlaintextBytes)
        {
            if (secret == null || secret.Length != 32 || payload == null || payload.Length < 32 || payload.Length % 16 != 0)
                throw new CryptographicException("Conteúdo criptografado inválido.");
            using (Aes aes = Aes.Create())
            {
                aes.KeySize = 256;
                aes.BlockSize = 128;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = DeriveKey(secret);
                byte[] iv = new byte[16];
                Buffer.BlockCopy(payload, 0, iv, 0, iv.Length);
                aes.IV = iv;
                using (ICryptoTransform decryptor = aes.CreateDecryptor())
                {
                    byte[] plaintext = decryptor.TransformFinalBlock(payload, iv.Length, payload.Length - iv.Length);
                    if (plaintext.Length > maximumPlaintextBytes)
                        throw new CryptographicException("Conteúdo descriptografado muito grande.");
                    return plaintext;
                }
            }
        }
    }
}
