package com.syncdeck.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class ApiClient {
    public interface Callback<T> {
        void onSuccess(T value, String message);
        void onError(String message);
    }

    public static final class Status {
        public String name;
        public String fingerprint;
        public String modulus;
        public String exponent;
        public boolean pairingAvailable;
        public long expiresAt;
        public long serverTime;
        public int pairedDevices;
    }

    public static final class ActionState {
        public String id;
        public boolean isOpen;
        public int windowCount;

        static ActionState fromJson(JSONObject object) {
            ActionState state = new ActionState();
            state.id = object.optString("Id", object.optString("id", ""));
            state.isOpen = object.has("IsOpen") ? object.optBoolean("IsOpen", false) : object.optBoolean("isOpen", false);
            state.windowCount = object.has("WindowCount") ? object.optInt("WindowCount", 0) : object.optInt("windowCount", 0);
            return state;
        }
    }

    private static final String PREFS = "syncdeck";
    private static final int MAX_RESPONSE = 262144;
    private final SharedPreferences preferences;
    private final SecureStore secureStore;
    private final File iconCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile long clockOffsetSeconds;

    public ApiClient(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secureStore = new SecureStore(context);
        iconCache = new File(context.getCacheDir(), "action-icons-v2");
        if (!iconCache.exists()) iconCache.mkdirs();
        pruneIconCache();
    }

    public String getHost() { return preferences.getString("host", ""); }
    public int getPort() { return preferences.getInt("port", 47321); }
    public String getClientId() { return preferences.getString("client_id", ""); }
    public boolean isConfigured() { return isPrivateIPv4(getHost()) && getPort() > 0; }
    public boolean isPaired() { return isConfigured() && !getClientId().isEmpty() && secureStore.getSecret() != null; }

    public void setEndpoint(String host, int port) {
        if (!isPrivateIPv4(host)) throw new IllegalArgumentException("Use um IP privado, como 192.168.0.10.");
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Porta inválida.");
        preferences.edit().putString("host", host.trim()).putInt("port", port).apply();
    }

    public void clearPairing() {
        secureStore.clear();
        preferences.edit().remove("client_id").apply();
    }

    public void getStatus(Callback<Status> callback) {
        run(callback, () -> {
            JSONObject envelope = request("GET", "/api/status", null, false);
            JSONObject data = envelope.getJSONObject("Data");
            Status status = new Status();
            status.name = data.optString("name", "PC Windows");
            status.serverTime = data.optLong("serverTime", System.currentTimeMillis() / 1000L);
            status.pairedDevices = data.optInt("pairedDevices", 0);
            JSONObject pairing = data.optJSONObject("pairing");
            if (pairing != null) {
                status.pairingAvailable = pairing.optBoolean("Available", false);
                status.modulus = pairing.optString("Modulus", "");
                status.exponent = pairing.optString("Exponent", "");
                status.fingerprint = keyFingerprint(status.modulus, status.exponent);
                String advertised = pairing.optString("Fingerprint", "");
                if (!advertised.isEmpty() && !advertised.equalsIgnoreCase(status.fingerprint))
                    throw new FriendlyException("A chave recebida não corresponde ao agente. Não faça o pareamento.");
                status.expiresAt = pairing.optLong("ExpiresAt", 0);
            }
            clockOffsetSeconds = status.serverTime - System.currentTimeMillis() / 1000L;
            return status;
        });
    }

    public void pair(Status status, String code, Callback<Boolean> callback) {
        run(callback, () -> {
            if (status == null || !status.pairingAvailable) throw new FriendlyException("Gere um código de pareamento no Windows.");
            if (code == null || !code.matches("^[0-9]{6}$")) throw new FriendlyException("Digite o código de 6 números.");

            String clientId = UUID.randomUUID().toString();
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            JSONObject payload = new JSONObject();
            payload.put("Code", code);
            payload.put("ClientId", clientId);
            payload.put("DeviceName", (Build.MANUFACTURER + " " + Build.MODEL).trim());
            payload.put("Secret", SignatureUtil.base64Url(secret));

            PublicKey key = publicKey(status.modulus, status.exponent);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, key, oaep);
            byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject body = new JSONObject();
            body.put("Payload", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            request("POST", "/api/pair", body, false);

            secureStore.putSecret(secret);
            preferences.edit().putString("client_id", clientId).commit();
            return Boolean.TRUE;
        });
    }

    public void getActions(boolean editable, Callback<List<SyncAction>> callback) {
        run(callback, () -> {
            JSONObject envelope = request("GET", editable ? "/api/actions/edit" : "/api/actions", null, true);
            JSONArray data = envelope.getJSONArray("Data");
            List<SyncAction> actions = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) actions.add(SyncAction.fromJson(data.getJSONObject(i)));
            return actions;
        });
    }

    public void getActionStates(Callback<List<ActionState>> callback) {
        run(callback, () -> {
            JSONObject envelope = request("GET", "/api/actions/state", null, true);
            JSONArray data = envelope.getJSONArray("Data");
            List<ActionState> states = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) states.add(ActionState.fromJson(data.getJSONObject(i)));
            return states;
        });
    }

    public void getActionIcon(SyncAction action, Callback<Bitmap> callback) {
        executor.execute(() -> {
            Bitmap cached = null;
            File destination = null;
            try {
                if (action == null || action.id == null || !action.id.matches("^[a-z0-9][a-z0-9-]{1,63}$"))
                    throw new FriendlyException("Identificador de imagem inválido.");
                String key = safeCachePart(action.imageKey == null || action.imageKey.isEmpty() ? "legacy" : action.imageKey);
                destination = new File(iconCache, safeCachePart(action.id) + "-" + key + ".png");
                cached = decodeIcon(readCachedIcon(destination));
                if (cached != null) {
                    Bitmap ready = cached;
                    main.post(() -> callback.onSuccess(ready, "Imagem em cache."));
                }

                String path = "/api/icons/" + action.id;
                RawResponse response = requestRaw("GET", path, null, true, "image/png");
                if (response.status != 200 || response.contentType == null || !response.contentType.toLowerCase(Locale.ROOT).startsWith("image/png"))
                    throw new FriendlyException(responseMessage(response.body, "O Windows não encontrou a imagem desse aplicativo."));
                Bitmap fresh = decodeIcon(response.body);
                if (fresh == null) throw new FriendlyException("A imagem recebida do Windows é inválida.");
                writeCachedIcon(destination, response.body);
                main.post(() -> callback.onSuccess(fresh, "Imagem atualizada."));
            } catch (Exception exception) {
                if (cached == null) {
                    String message = friendlyMessage(exception);
                    main.post(() -> callback.onError(message));
                }
            }
        });
    }

    public void execute(SyncAction action, String operation, boolean confirmed, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("ActionId", action.id);
            body.put("Operation", operation == null ? "open" : operation);
            body.put("Confirmed", confirmed);
            request("POST", "/api/execute", body, true);
            return Boolean.TRUE;
        });
    }

    public void saveAction(SyncAction action, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("Action", action.toJson());
            request("POST", "/api/actions/save", body, true);
            return Boolean.TRUE;
        });
    }

    public void deleteAction(SyncAction action, Callback<Boolean> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject();
            body.put("ActionId", action.id);
            request("POST", "/api/actions/delete", body, true);
            return Boolean.TRUE;
        });
    }

    public void shutdown() { executor.shutdownNow(); }

    private JSONObject request(String method, String path, JSONObject json, boolean signed) throws Exception {
        RawResponse raw = requestRaw(method, path, json, signed, "application/json");
        JSONObject envelope = new JSONObject(new String(raw.body, StandardCharsets.UTF_8));
        if (!envelope.optBoolean("Ok", false))
            throw new FriendlyException(envelope.optString("Message", "O agente recusou a solicitação."));
        return envelope;
    }

    private RawResponse requestRaw(String method, String path, JSONObject json, boolean signed, String accept) throws Exception {
        byte[] body = json == null ? new byte[0] : json.toString().getBytes(StandardCharsets.UTF_8);
        URL url = new URL("http://" + getHost() + ":" + getPort() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(7000);
            connection.setUseCaches(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("Connection", "close");

            byte[] requestSecret = null;
            String requestNonce = null;
            if (signed) {
                byte[] secret = secureStore.getSecret();
                String clientId = getClientId();
                if (secret == null || clientId.isEmpty()) throw new FriendlyException("Celular não pareado.");
                long timestamp = System.currentTimeMillis() / 1000L + clockOffsetSeconds;
                byte[] nonceBytes = new byte[16];
                new SecureRandom().nextBytes(nonceBytes);
                String nonce = SignatureUtil.base64Url(nonceBytes);
                String signature = SignatureUtil.sign(secret, method, path, timestamp, nonce, body);
                connection.setRequestProperty("X-SyncDeck-Client", clientId);
                connection.setRequestProperty("X-SyncDeck-Timestamp", Long.toString(timestamp));
                connection.setRequestProperty("X-SyncDeck-Nonce", nonce);
                connection.setRequestProperty("X-SyncDeck-Signature", signature);
                requestSecret = secret;
                requestNonce = nonce;
            }

            if (body.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }

            int status = connection.getResponseCode();
            BufferedInputStream input = new BufferedInputStream(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            byte[] response = readLimited(input);
            if (signed) {
                String supplied = connection.getHeaderField("X-SyncDeck-Response-Signature");
                String expected = SignatureUtil.signResponse(requestSecret, status, requestNonce, response);
                if (!SignatureUtil.constantTimeEquals(expected, supplied))
                    throw new FriendlyException("A resposta do PC não pôde ser autenticada. Pareie novamente ou verifique a rede.");
            }
            return new RawResponse(status, connection.getContentType(), response);
        } finally {
            connection.disconnect();
        }
    }

    private static String responseMessage(byte[] body, String fallback) {
        try { return new JSONObject(new String(body, StandardCharsets.UTF_8)).optString("Message", fallback); }
        catch (Exception ignored) { return fallback; }
    }

    private static Bitmap decodeIcon(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_RESPONSE) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 1024 || bounds.outHeight > 1024) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static byte[] readCachedIcon(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_RESPONSE) return null;
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[4096];
            int total = 0, read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE) return null;
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Exception ignored) { return null; }
    }

    private static void writeCachedIcon(File destination, byte[] bytes) {
        if (destination == null || bytes == null || bytes.length == 0) return;
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (Exception ignored) { temporary.delete(); return; }
        if (destination.exists() && !destination.delete()) { temporary.delete(); return; }
        if (!temporary.renameTo(destination)) temporary.delete();
    }

    private void pruneIconCache() {
        File[] files = iconCache.listFiles();
        if (files == null || files.length <= 64) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int i = 0; i < files.length - 48; i++) files[i].delete();
    }

    private static String safeCachePart(String value) {
        String cleaned = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return cleaned.isEmpty() ? "item" : cleaned.substring(0, Math.min(cleaned.length(), 64));
    }

    private static byte[] readLimited(BufferedInputStream input) throws Exception {
        if (input == null) throw new FriendlyException("O agente não respondeu corretamente.");
        try (BufferedInputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0, read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE) throw new FriendlyException("Resposta muito grande.");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static PublicKey publicKey(String modulus, String exponent) throws Exception {
        byte[] modulusBytes = Base64.decode(modulus, Base64.DEFAULT);
        byte[] exponentBytes = Base64.decode(exponent, Base64.DEFAULT);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, modulusBytes), new BigInteger(1, exponentBytes));
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String keyFingerprint(String modulus, String exponent) throws Exception {
        byte[] modulusBytes = Base64.decode(modulus, Base64.DEFAULT);
        byte[] exponentBytes = Base64.decode(exponent, Base64.DEFAULT);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(modulusBytes);
        byte[] hash = digest.digest(exponentBytes);
        StringBuilder value = new StringBuilder(12);
        for (int i = 0; i < 6; i++) value.append(String.format(Locale.ROOT, "%02X", hash[i] & 0xff));
        return value.substring(0, 4) + "-" + value.substring(4, 8) + "-" + value.substring(8, 12);
    }

    private <T> void run(Callback<T> callback, Work<T> work) {
        executor.execute(() -> {
            try {
                T value = work.run();
                main.post(() -> callback.onSuccess(value, "Concluído."));
            } catch (Exception exception) {
                String message = friendlyMessage(exception);
                main.post(() -> callback.onError(message));
            }
        });
    }

    private static String friendlyMessage(Exception exception) {
        if (exception instanceof FriendlyException) return exception.getMessage();
        String message = exception.getMessage();
        if (message == null) return "Não foi possível conectar ao PC.";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to connect") || lower.contains("timed out") || lower.contains("refused") || lower.contains("unreachable"))
            return "PC indisponível. Confira o Wi-Fi, o endereço e o agente do Windows.";
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static boolean isPrivateIPv4(String value) {
        if (value == null || !value.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")) return false;
        String[] parts = value.split("\\.");
        int[] numbers = new int[4];
        try { for (int i = 0; i < 4; i++) { numbers[i] = Integer.parseInt(parts[i]); if (numbers[i] > 255) return false; } }
        catch (NumberFormatException ignored) { return false; }
        return numbers[0] == 10 ||
                (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31) ||
                (numbers[0] == 192 && numbers[1] == 168) ||
                (numbers[0] == 169 && numbers[1] == 254);
    }

    private interface Work<T> { T run() throws Exception; }
    private static final class RawResponse {
        final int status;
        final String contentType;
        final byte[] body;
        RawResponse(int status, String contentType, byte[] body) {
            this.status = status; this.contentType = contentType; this.body = body;
        }
    }
    private static final class FriendlyException extends Exception { FriendlyException(String message) { super(message); } }
}
