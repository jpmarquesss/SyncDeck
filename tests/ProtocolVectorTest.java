import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ProtocolVectorTest {
    private static final byte[] SECRET = new byte[32];
    private static final String NONCE = "AQIDBAUGBwgJCgsMDR4PEA";

    static {
        for (int i = 0; i < SECRET.length; i++) SECRET[i] = (byte) i;
    }

    public static void main(String[] args) throws Exception {
        byte[] body = "{\"ActionId\":\"chrome\",\"Operation\":\"open\",\"Confirmed\":false}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] response = "{\"Ok\":true,\"Data\":null,\"Message\":\"Chrome foi aberto.\",\"Code\":null}"
                .getBytes(StandardCharsets.UTF_8);

        require("39219856d3a55223aa8f4a26e79a3895ae3bab9d364ebf54bbb8dcd1655cda9d",
                bodyHash(body), "hash v1");
        require("CY6iAOR9ulMYDSjlr7khVYokrvCm4rubfNxfPUQwieQ",
                sign("POST", "/api/execute", 1786440000L, NONCE, body), "assinatura v1");
        require("gM24LoNKqbdNYBhy27Mp3usGGWlXoRV-rkZZp0Q5lDo",
                signResponse(200, NONCE, response), "assinatura de resposta v1");

        byte[] requestWire = encrypt(body, hex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"));
        require("oKGio6SlpqeoqaqrrK2ur599euqBI0udwljvdK-HC_leKQhWOBvFb-OlJcfZ0U25_3lIXa7OB7yw4vO11tvl97BkuPcnHYmlptXZgbFfVKs",
                base64Url(requestWire), "cifra de requisição v2");
        require("emD5FHnEfHEClzJWaSB0OEwYl4ZmSFkoLCBmvHDFows",
                sign("POST", "/api/execute", 1786440000L, NONCE, requestWire), "assinatura cifrada v2");
        requireBytes(body, decrypt(requestWire), "decifra de requisição v2");

        byte[] responseWire = encrypt(response, hex("b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"));
        require("sLGys7S1tre4ubq7vL2-vyGdzVT10AwYGJA5Za7RoF9nqLl0aOVevnmnPTr-pfO2PPDoi_Rd5qinwa2tsPIaTrrMX_YTtrqLGZn6y3MHWBRcSfJ3UH9ZF81iTsYTt_oU",
                base64Url(responseWire), "cifra de resposta v2");
        require("LlNkpKfnQNgTMkbVnsbkqZsOM6SMuCFt0i2byD8Nm0o",
                signResponse(200, NONCE, responseWire), "assinatura de resposta cifrada v2");
        requireBytes(response, decrypt(responseWire), "decifra de resposta v2");

        System.out.println("SyncDeck protocol vectors v1/v2: OK");
    }

    private static byte[] encryptionKey() throws Exception {
        return hmac("SyncDeck.Encryption.v1".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] encrypt(byte[] plaintext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] value = Arrays.copyOf(iv, iv.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, value, iv.length, ciphertext.length);
        return value;
    }

    private static byte[] decrypt(byte[] value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"),
                new IvParameterSpec(Arrays.copyOfRange(value, 0, 16)));
        return cipher.doFinal(value, 16, value.length - 16);
    }

    private static String sign(String method, String path, long timestamp, String nonce, byte[] body) throws Exception {
        String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash(body);
        return base64Url(hmac(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static String signResponse(int status, String nonce, byte[] body) throws Exception {
        String canonical = "RESPONSE\n" + status + "\n" + nonce + "\n" + bodyHash(body);
        return base64Url(hmac(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private static String bodyHash(byte[] body) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(body)) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private static void require(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + " divergente: " + actual);
    }

    private static void requireBytes(byte[] expected, byte[] actual, String label) {
        if (!MessageDigest.isEqual(expected, actual)) throw new AssertionError(label + " divergente");
    }
}
