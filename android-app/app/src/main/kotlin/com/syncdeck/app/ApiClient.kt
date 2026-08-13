package com.syncdeck.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class ApiClient(context: Context) {
    interface Callback<T> {
        fun onSuccess(value: T, message: String)
        fun onError(message: String)
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secureStore = SecureStore(context)
    private val iconCache = File(context.cacheDir, "action-icons-v3").apply { mkdirs() }
    private val executor = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var clockOffsetSeconds = 0L
    @Volatile private var pendingServerFingerprint: String? = null

    init {
        pruneIconCache()
    }

    val host: String get() = preferences.getString("host", "").orEmpty()
    val port: Int get() = preferences.getInt("port", 47321)
    val clientId: String get() = preferences.getString("client_id", "").orEmpty()
    private val serverFingerprint: String get() = preferences.getString("server_fingerprint", "").orEmpty()
    val isConfigured: Boolean get() = isPrivateIpv4(host) && port in 1024..65535
    val isPaired: Boolean get() = isConfigured && clientId.isNotEmpty() && secureStore.getSecret() != null
    val hasWakeConfig: Boolean get() =
        normalizedMac(preferences.getString("wake_mac", "")) != null &&
            validWakeAddress(preferences.getString("wake_broadcast", ""))

    fun setEndpoint(host: String, port: Int) {
        require(isPrivateIpv4(host)) { "Use um IP privado, como 192.168.0.10." }
        require(port in 1024..65535) { "Porta inválida." }
        check(preferences.edit().putString("host", host.trim()).putInt("port", port).commit()) {
            "Não foi possível salvar o endereço do PC."
        }
    }

    fun clearPairing() {
        secureStore.clear()
        preferences.edit()
            .remove("client_id")
            .remove("server_fingerprint")
            .remove("wake_mac")
            .remove("wake_broadcast")
            .remove("wake_port")
            .remove("wake_interface")
            .commit()
        pendingServerFingerprint = null
    }

    fun getStatus(callback: Callback<AgentStatus>) = runTask(callback) {
        fetchStatusAt(host, port, 4_500, 7_000).also(::observeServer)
    }

    fun getStatusWithRecovery(callback: Callback<AgentStatus>) = runTask(callback) {
        val directResult = runCatching { fetchStatusAt(host, port, 4_500, 7_000) }
        val direct = directResult.getOrNull()
        val expected = serverFingerprint
        (direct?.takeIf { expected.isEmpty() || expected.equals(it.fingerprint, ignoreCase = true) }
            ?: discoverPairedComputer()?.copy(endpointRecovered = true)
            ?: directResult.exceptionOrNull()?.let { throw it }
            ?: throw FriendlyException("O endereço agora pertence a outro agente e o PC pareado não foi encontrado."))
            .also(::observeServer)
    }

    fun pair(status: AgentStatus, code: String, callback: Callback<Boolean>) = runTask(callback) {
        if (!status.pairingAvailable) throw FriendlyException("Gere um código de pareamento no Windows.")
        if (status.protocol < 2) throw FriendlyException("Atualize o agente do Windows para a versão 1.0 antes de parear.")
        if (!code.matches("^[0-9]{6}$".toRegex())) throw FriendlyException("Digite o código de 6 números.")

        val newClientId = UUID.randomUUID().toString()
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        val payload = JSONObject().apply {
            put("Code", code)
            put("ClientId", newClientId)
            put("DeviceName", safeDeviceName())
            put("Secret", ProtocolCrypto.base64Url(secret))
        }
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey(status.modulus, status.exponent),
            OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
        )
        val body = JSONObject().put(
            "Payload",
            Base64.encodeToString(cipher.doFinal(payload.toString().toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP),
        )
        request("POST", "/api/pair", body, signed = false)
        secureStore.putSecret(secret)
        check(
            preferences.edit()
                .putString("client_id", newClientId)
                .putString("server_fingerprint", status.fingerprint)
                .putInt("protocol_version", status.protocol)
                .commit(),
        ) { "O Android não conseguiu salvar o pareamento." }
        pendingServerFingerprint = null
        true
    }

    fun getActions(editable: Boolean, callback: Callback<List<SyncAction>>) = runTask(callback) {
        val envelope = request("GET", if (editable) "/api/actions/edit" else "/api/actions", signed = true)
        val data = envelope.getJSONArray("Data")
        buildList { repeat(data.length()) { add(SyncAction.fromJson(data.getJSONObject(it))) } }
    }

    fun getActionStates(callback: Callback<List<ActionState>>) = runTask(callback) {
        val data = request("GET", "/api/actions/state", signed = true).getJSONArray("Data")
        buildList { repeat(data.length()) { add(ActionState.fromJson(data.getJSONObject(it))) } }
    }

    fun getApplications(callback: Callback<List<CatalogApplication>>) = runTask(callback) {
        val data = request("GET", "/api/catalog/apps", signed = true, readTimeout = 18_000).getJSONArray("Data")
        buildList { repeat(data.length()) { add(CatalogApplication.fromJson(data.getJSONObject(it))) } }
    }

    fun pickPath(kind: String, callback: Callback<PickedPath>) = runTask(callback) {
        val body = JSONObject().put("Kind", kind)
        val data = request("POST", "/api/catalog/pick", body, signed = true, readTimeout = 180_000)
            .getJSONObject("Data")
        PickedPath.fromJson(data)
    }

    fun refreshWakeConfig(callback: Callback<WakeConfig>) = runTask(callback) {
        val data = request("GET", "/api/wake-config", signed = true).getJSONObject("Data")
        val config = WakeConfig(
            macAddress = data.text("MacAddress", "macAddress"),
            broadcastAddress = data.text("BroadcastAddress", "broadcastAddress"),
            port = data.int("Port", "port", 9),
            interfaceName = data.text("InterfaceName", "interfaceName").ifBlank { "Rede Ethernet" },
        )
        val mac = normalizedMac(config.macAddress)
            ?: throw FriendlyException("O PC enviou uma configuração Wake-on-LAN inválida.")
        if (!validWakeAddress(config.broadcastAddress) || config.port !in 1..65535)
            throw FriendlyException("O PC enviou uma configuração Wake-on-LAN inválida.")
        check(
            preferences.edit()
                .putString("wake_mac", mac)
                .putString("wake_broadcast", config.broadcastAddress)
                .putInt("wake_port", config.port)
                .putString("wake_interface", config.interfaceName)
                .commit(),
        ) { "O Android não conseguiu salvar o Wake-on-LAN." }
        config.copy(macAddress = mac)
    }

    fun wakeComputer(callback: Callback<Boolean>) = runTask(callback) {
        val macText = normalizedMac(preferences.getString("wake_mac", ""))
            ?: throw FriendlyException("Ligue o PC normalmente uma vez para o SyncDeck salvar a placa de rede.")
        val savedBroadcast = preferences.getString("wake_broadcast", "").orEmpty()
        val wakePort = preferences.getInt("wake_port", 9)
        if (!validWakeAddress(savedBroadcast) || wakePort !in 1..65535)
            throw FriendlyException("A configuração para ligar o PC ficou inválida.")

        val mac = ByteArray(6) { index -> macText.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        val magic = ByteArray(6 + 16 * mac.size) { if (it < 6) 0xff.toByte() else mac[(it - 6) % mac.size] }
        val destinations = currentBroadcastAddresses().apply {
            add(savedBroadcast)
            add("255.255.255.255")
        }
        var sent = 0
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(3) { attempt ->
                destinations.forEach { destination ->
                    runCatching {
                        socket.send(DatagramPacket(magic, magic.size, InetAddress.getByName(destination), wakePort))
                        sent++
                    }
                }
                if (attempt < 2) Thread.sleep(120)
            }
        }
        if (sent == 0) throw FriendlyException("O Android não conseguiu enviar o sinal pela rede Wi-Fi.")
        true
    }

    fun getActionIcon(action: SyncAction, callback: Callback<Bitmap>) {
        executor.execute {
            var cached: Bitmap? = null
            try {
                if (!action.id.matches("^[a-z0-9][a-z0-9-]{1,63}$".toRegex()))
                    throw FriendlyException("Identificador de imagem inválido.")
                val key = safeCachePart(action.imageKey.ifEmpty { "legacy" })
                val destination = File(iconCache, "${safeCachePart(action.id)}-$key.png")
                cached = decodeIcon(readCachedIcon(destination))
                cached?.let { ready -> main.post { callback.onSuccess(ready, "Imagem em cache.") } }

                val response = requestRaw("GET", "/api/icons/${action.id}", signed = true, accept = "image/png")
                if (response.status != 200 || !response.contentType.orEmpty().lowercase().startsWith("image/png"))
                    throw FriendlyException(responseMessage(response.body, "O Windows não encontrou a imagem desse aplicativo."))
                val fresh = decodeIcon(response.body)
                    ?: throw FriendlyException("A imagem recebida do Windows é inválida.")
                writeCachedIcon(destination, response.body)
                main.post { callback.onSuccess(fresh, "Imagem atualizada.") }
            } catch (exception: Exception) {
                if (cached == null) main.post { callback.onError(friendlyMessage(exception)) }
            }
        }
    }

    fun execute(action: SyncAction, operation: String, confirmed: Boolean, callback: Callback<Boolean>) = runTask(callback) {
        val body = JSONObject().apply {
            put("ActionId", action.id)
            put("Operation", operation)
            put("Confirmed", confirmed)
        }
        val timeout = if (action.type == "command" || action.type == "hotkey") 70_000 else 12_000
        request("POST", "/api/execute", body, signed = true, readTimeout = timeout)
        true
    }

    fun saveAction(action: SyncAction, callback: Callback<Boolean>) = runTask(callback) {
        val value = action.normalizedForSave()
        val body = JSONObject().apply {
            put("Action", value.toJson())
            put("SelectionToken", value.selectionToken)
        }
        request("POST", "/api/actions/save", body, signed = true, readTimeout = 70_000)
        true
    }

    fun deleteAction(action: SyncAction, callback: Callback<Boolean>) = runTask(callback) {
        request("POST", "/api/actions/delete", JSONObject().put("ActionId", action.id), signed = true)
        true
    }

    fun shutdown() = executor.shutdownNow()

    private fun request(
        method: String,
        path: String,
        json: JSONObject? = null,
        signed: Boolean,
        readTimeout: Int = 10_000,
    ): JSONObject {
        val raw = requestRaw(method, path, json, signed, "application/json", readTimeout)
        val envelope = runCatching { JSONObject(String(raw.body, StandardCharsets.UTF_8)) }
            .getOrElse { throw FriendlyException("O agente enviou uma resposta inválida.") }
        if (!envelope.optBoolean("Ok", false))
            throw FriendlyException(envelope.optString("Message", "O agente recusou a solicitação."))
        return envelope
    }

    private fun requestRaw(
        method: String,
        path: String,
        json: JSONObject? = null,
        signed: Boolean,
        accept: String,
        readTimeout: Int = 10_000,
    ) = requestRawAt(host, port, method, path, json, signed, accept, 4_500, readTimeout)

    private fun requestRawAt(
        host: String,
        port: Int,
        method: String,
        path: String,
        json: JSONObject?,
        signed: Boolean,
        accept: String,
        connectTimeout: Int,
        readTimeout: Int,
    ): RawResponse {
        val plainBody = json?.toString()?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Connection", "close")

            var requestSecret: ByteArray? = null
            var requestNonce: String? = null
            var wireBody = plainBody
            if (signed) {
                val secret = secureStore.getSecret() ?: throw FriendlyException("Celular não pareado.")
                if (clientId.isEmpty()) throw FriendlyException("Celular não pareado.")
                if (plainBody.isNotEmpty()) wireBody = ProtocolCrypto.encrypt(secret, plainBody)
                val timestamp = System.currentTimeMillis() / 1_000L + clockOffsetSeconds
                val nonce = ProtocolCrypto.base64Url(ByteArray(16).also(SecureRandom()::nextBytes))
                connection.setRequestProperty("X-SyncDeck-Client", clientId)
                connection.setRequestProperty("X-SyncDeck-Timestamp", timestamp.toString())
                connection.setRequestProperty("X-SyncDeck-Nonce", nonce)
                connection.setRequestProperty("X-SyncDeck-Signature", ProtocolCrypto.sign(secret, method, path, timestamp, nonce, wireBody))
                connection.setRequestProperty("X-SyncDeck-Encryption", ProtocolCrypto.ENCRYPTION_PROTOCOL)
                requestSecret = secret
                requestNonce = nonce
            }

            if (wireBody.isNotEmpty()) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(wireBody.size)
                connection.setRequestProperty(
                    "Content-Type",
                    if (signed) "application/octet-stream" else "application/json; charset=utf-8",
                )
                connection.outputStream.use { it.write(wireBody) }
            }

            val status = connection.responseCode
            val responseWire = readLimited(if (status >= 400) connection.errorStream else connection.inputStream)
            var response = responseWire
            if (signed) {
                val expected = ProtocolCrypto.signResponse(requestSecret!!, status, requestNonce!!, responseWire)
                val supplied = connection.getHeaderField("X-SyncDeck-Response-Signature")
                if (!ProtocolCrypto.constantTimeEquals(expected, supplied))
                    throw FriendlyException("A resposta do PC não pôde ser autenticada. Atualize o agente ou pareie novamente.")
                if (ProtocolCrypto.ENCRYPTION_PROTOCOL.equals(
                        connection.getHeaderField("X-SyncDeck-Encryption"),
                        ignoreCase = true,
                    )
                ) {
                    response = ProtocolCrypto.decrypt(requestSecret!!, responseWire, MAX_RESPONSE)
                } else {
                    throw FriendlyException("O agente do Windows precisa ser atualizado para a versão 1.0.")
                }
                confirmObservedServer()
            }
            return RawResponse(status, connection.contentType, response)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchStatusAt(host: String, port: Int, connectTimeout: Int, readTimeout: Int): AgentStatus {
        if (!isPrivateIpv4(host) || port !in 1024..65535) throw FriendlyException("Endereço privado do PC inválido.")
        val raw = requestRawAt(host, port, "GET", "/api/status", null, false, "application/json", connectTimeout, readTimeout)
        val envelope = JSONObject(String(raw.body, StandardCharsets.UTF_8))
        if (!envelope.optBoolean("Ok", false))
            throw FriendlyException(envelope.optString("Message", "O agente recusou a solicitação."))
        val data = envelope.getJSONObject("Data")
        val pairing = data.optJSONObject("pairing")
        val modulus = pairing?.optString("Modulus").orEmpty()
        val exponent = pairing?.optString("Exponent").orEmpty()
        val calculated = if (modulus.isNotEmpty() && exponent.isNotEmpty()) keyFingerprint(modulus, exponent) else ""
        val advertised = pairing?.optString("Fingerprint").orEmpty()
        if (advertised.isNotEmpty() && !advertised.equals(calculated, ignoreCase = true))
            throw FriendlyException("A chave recebida não corresponde ao agente. Não faça o pareamento.")
        return AgentStatus(
            name = data.optString("name", "PC Windows"),
            host = host,
            fingerprint = calculated,
            modulus = modulus,
            exponent = exponent,
            pairingAvailable = pairing?.optBoolean("Available", false) == true,
            expiresAt = pairing?.optLong("ExpiresAt", 0) ?: 0,
            serverTime = data.optLong("serverTime", System.currentTimeMillis() / 1_000L),
            pairedDevices = data.optInt("pairedDevices", 0),
            protocol = data.optJSONObject("security")?.optInt("protocol", 1) ?: 1,
        )
    }

    private fun observeServer(status: AgentStatus) {
        clockOffsetSeconds = status.serverTime - System.currentTimeMillis() / 1_000L
        if (status.fingerprint.isNotEmpty()) pendingServerFingerprint = status.fingerprint
    }

    private fun confirmObservedServer() {
        val fingerprint = pendingServerFingerprint.orEmpty()
        if (fingerprint.isEmpty() || clientId.isEmpty()) return
        if (preferences.edit().putString("server_fingerprint", fingerprint).putInt("protocol_version", 2).commit())
            pendingServerFingerprint = null
    }

    private fun discoverPairedComputer(): AgentStatus? {
        val expected = serverFingerprint
        if (expected.isEmpty() || clientId.isEmpty() || secureStore.getSecret() == null) return null
        val prefixes = privatePrefixes()
        if (prefixes.isEmpty()) return null
        val ownAddresses = localPrivateAddresses()
        val candidates = prefixes.flatMap { prefix ->
            (1..254).map { "$prefix$it" }.filter { it != host && it !in ownAddresses }
        }
        val pool = Executors.newFixedThreadPool(DISCOVERY_WORKERS)
        val completion = ExecutorCompletionService<Pair<String, AgentStatus>?>(pool)
        val pending = candidates.map { candidate ->
            completion.submit {
                runCatching { fetchStatusAt(candidate, port, 320, 550) }
                    .getOrNull()
                    ?.takeIf { expected.equals(it.fingerprint, ignoreCase = true) }
                    ?.let { candidate to it }
            }
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DISCOVERY_LIMIT_MILLIS)
        try {
            repeat(pending.size) {
                val wait = deadline - System.nanoTime()
                if (wait <= 0) return null
                val found = completion.poll(wait, TimeUnit.NANOSECONDS)?.get() ?: return@repeat
                if (!preferences.edit().putString("host", found.first).commit()) return null
                return found.second
            }
        } catch (_: Exception) {
            return null
        } finally {
            pending.forEach { it.cancel(true) }
            pool.shutdownNow()
        }
        return null
    }

    private fun privatePrefixes(): LinkedHashSet<String> = linkedSetOf<String>().apply {
        prefixOf(host)?.let(::add)
        localPrivateAddresses().forEach { address ->
            prefixOf(address)?.let(::add)
            if (size >= 3) return@forEach
        }
    }

    private fun <T> runTask(callback: Callback<T>, work: () -> T) {
        executor.execute {
            try {
                val value = work()
                main.post { callback.onSuccess(value, "Concluído.") }
            } catch (exception: Exception) {
                main.post { callback.onError(friendlyMessage(exception)) }
            }
        }
    }

    private fun pruneIconCache() {
        val files = iconCache.listFiles() ?: return
        if (files.size <= 64) return
        files.sortedBy(File::lastModified).take(files.size - 48).forEach(File::delete)
    }

    private data class RawResponse(val status: Int, val contentType: String?, val body: ByteArray)

    private class FriendlyException(message: String) : Exception(message)

    companion object {
        private const val PREFS = "syncdeck"
        private const val MAX_RESPONSE = 524_288
        private const val DISCOVERY_WORKERS = 24
        private const val DISCOVERY_LIMIT_MILLIS = 6_500L

        private fun safeDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
            .filter { it.code in 32..126 }
            .trim()
            .take(48)
            .ifBlank { "Android" }

        private fun publicKey(modulus: String, exponent: String): PublicKey {
            val spec = RSAPublicKeySpec(
                BigInteger(1, Base64.decode(modulus, Base64.DEFAULT)),
                BigInteger(1, Base64.decode(exponent, Base64.DEFAULT)),
            )
            return KeyFactory.getInstance("RSA").generatePublic(spec)
        }

        private fun keyFingerprint(modulus: String, exponent: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(Base64.decode(modulus, Base64.DEFAULT))
            val hash = digest.digest(Base64.decode(exponent, Base64.DEFAULT))
            val value = hash.take(6).joinToString("") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }
            return "${value.substring(0, 4)}-${value.substring(4, 8)}-${value.substring(8, 12)}"
        }

        private fun localPrivateAddresses(): Set<String> = buildSet {
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (!network.isUp || network.isLoopback) continue
                    val addresses = network.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && isPrivateIpv4(address.hostAddress)) add(address.hostAddress.orEmpty())
                    }
                }
            }
        }

        private fun currentBroadcastAddresses(): LinkedHashSet<String> = linkedSetOf<String>().apply {
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (!network.isUp || network.isLoopback) continue
                    network.interfaceAddresses.forEach { item ->
                        val address = item.address
                        if (address is Inet4Address && isPrivateIpv4(address.hostAddress))
                            item.broadcast?.hostAddress?.let(::add)
                    }
                }
            }
        }

        private fun normalizedMac(value: String?): String? {
            val cleaned = value.orEmpty().replace("[^0-9A-Fa-f]".toRegex(), "").uppercase(Locale.ROOT)
            if (!cleaned.matches("^[0-9A-F]{12}$".toRegex()) || cleaned == "000000000000" || cleaned == "FFFFFFFFFFFF")
                return null
            return cleaned.takeIf { (it.substring(0, 2).toInt(16) and 1) == 0 }
        }

        private fun validWakeAddress(value: String?): Boolean = isPrivateIpv4(value) || value == "255.255.255.255"

        private fun prefixOf(address: String?): String? {
            if (!isPrivateIpv4(address)) return null
            val separator = address!!.lastIndexOf('.')
            return address.substring(0, separator + 1)
        }

        private fun responseMessage(body: ByteArray, fallback: String): String = runCatching {
            JSONObject(String(body, StandardCharsets.UTF_8)).optString("Message", fallback)
        }.getOrDefault(fallback)

        private fun decodeIcon(bytes: ByteArray?): Bitmap? {
            if (bytes == null || bytes.isEmpty() || bytes.size > MAX_RESPONSE) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }

        private fun readCachedIcon(file: File): ByteArray? {
            if (!file.isFile || file.length() !in 1L..MAX_RESPONSE.toLong()) return null
            return runCatching {
                FileInputStream(file).use { input ->
                    ByteArrayOutputStream(file.length().toInt()).use { output ->
                        val buffer = ByteArray(4_096)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_RESPONSE) return null
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                }
            }.getOrNull()
        }

        private fun writeCachedIcon(destination: File, bytes: ByteArray) {
            if (bytes.isEmpty()) return
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            runCatching {
                FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
                if (destination.exists() && !destination.delete()) error("cache")
                if (!temporary.renameTo(destination)) error("cache")
            }.onFailure { temporary.delete() }
        }

        private fun safeCachePart(value: String): String = value.lowercase(Locale.ROOT)
            .replace("[^a-z0-9-]".toRegex(), "")
            .ifEmpty { "item" }
            .take(64)

        private fun readLimited(input: java.io.InputStream?): ByteArray {
            if (input == null) throw FriendlyException("O agente não respondeu corretamente.")
            BufferedInputStream(input).use { source ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(4_096)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RESPONSE) throw FriendlyException("Resposta muito grande.")
                        output.write(buffer, 0, read)
                    }
                    return output.toByteArray()
                }
            }
        }

        private fun friendlyMessage(exception: Exception): String {
            if (exception is FriendlyException) return exception.message.orEmpty()
            val message = exception.message ?: return "Não foi possível conectar ao PC."
            val lower = message.lowercase(Locale.ROOT)
            if (listOf("failed to connect", "timed out", "refused", "unreachable", "timeout").any { lower.contains(it) })
                return "PC indisponível. Confira o Wi-Fi, o endereço e o agente do Windows."
            return message.take(180)
        }

        fun isPrivateIpv4(value: String?): Boolean {
            if (value == null || !value.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$".toRegex())) return false
            val values = runCatching { value.split('.').map { it.toInt() } }.getOrNull() ?: return false
            if (values.any { it !in 0..255 }) return false
            return values[0] == 10 ||
                (values[0] == 172 && values[1] in 16..31) ||
                (values[0] == 192 && values[1] == 168) ||
                (values[0] == 169 && values[1] == 254)
        }
    }
}
