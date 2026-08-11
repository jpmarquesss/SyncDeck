package com.syncdeck.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignatureUtil {
    private SignatureUtil() {}

    public static String bodyHash(byte[] body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(body == null ? new byte[0] : body);
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    public static String sign(byte[] secret, String method, String path, long timestamp, String nonce, byte[] body) throws Exception {
        String canonical = method.toUpperCase(java.util.Locale.ROOT) + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash(body);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return base64Url(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public static String signResponse(byte[] secret, int statusCode, String requestNonce, byte[] body) throws Exception {
        String canonical = "RESPONSE\n" + statusCode + "\n" + requestNonce + "\n" + bodyHash(body);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return base64Url(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                (expected == null ? "" : expected).getBytes(StandardCharsets.US_ASCII),
                (supplied == null ? "" : supplied).getBytes(StandardCharsets.US_ASCII));
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
