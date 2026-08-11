package com.syncdeck.app;

import java.nio.charset.StandardCharsets;

public final class ProtocolVectorTest {
    public static void main(String[] args) throws Exception {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        byte[] body = "{\"ActionId\":\"chrome\",\"Operation\":\"open\",\"Confirmed\":false}"
                .getBytes(StandardCharsets.UTF_8);
        String hash = SignatureUtil.bodyHash(body);
        String signature = SignatureUtil.sign(secret, "POST", "/api/execute", 1786440000L,
                "AQIDBAUGBwgJCgsMDR4PEA", body);
        byte[] response = "{\"Ok\":true,\"Data\":null,\"Message\":\"Chrome foi aberto.\",\"Code\":null}"
                .getBytes(StandardCharsets.UTF_8);
        String responseSignature = SignatureUtil.signResponse(secret, 200,
                "AQIDBAUGBwgJCgsMDR4PEA", response);
        if (!"39219856d3a55223aa8f4a26e79a3895ae3bab9d364ebf54bbb8dcd1655cda9d".equals(hash))
            throw new AssertionError("Hash divergente: " + hash);
        if (!"CY6iAOR9ulMYDSjlr7khVYokrvCm4rubfNxfPUQwieQ".equals(signature))
            throw new AssertionError("Assinatura divergente: " + signature);
        if (!"gM24LoNKqbdNYBhy27Mp3usGGWlXoRV-rkZZp0Q5lDo".equals(responseSignature))
            throw new AssertionError("Assinatura de resposta divergente: " + responseSignature);
        System.out.println("ProtocolVectorTest: OK");
    }
}
