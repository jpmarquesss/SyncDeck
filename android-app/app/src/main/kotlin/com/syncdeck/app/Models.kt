package com.syncdeck.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class SyncAction(
    val id: String = "",
    val label: String = "",
    val type: String = "app",
    val target: String = "",
    val arguments: String = "",
    val workingDirectory: String = "",
    val processNames: List<String> = emptyList(),
    val appNames: List<String> = emptyList(),
    val fallbackUrl: String = "",
    val icon: String = "app",
    val imageKey: String = "",
    val color: String = "#697386",
    val confirm: Boolean = false,
    val closable: Boolean = true,
    val enabled: Boolean = true,
    val isOpen: Boolean = false,
    val windowCount: Int = 0,
    val selectionToken: String = "",
) {
    fun normalizedForSave(): SyncAction {
        val safeId = normalizeId(if (id.isBlank()) label else id)
        return copy(
            id = safeId,
            label = label.trim(),
            type = type.trim().lowercase(Locale.ROOT),
            target = target.trim(),
            arguments = arguments.trim(),
            workingDirectory = workingDirectory.trim(),
            processNames = processNames.map { it.trim() }.filter(String::isNotEmpty).distinct().take(12),
            appNames = appNames.map { it.trim() }.filter(String::isNotEmpty).distinct().take(12),
            fallbackUrl = fallbackUrl.trim(),
            icon = icon.ifBlank { "app" }.trim().lowercase(Locale.ROOT),
            color = color.trim().uppercase(Locale.ROOT),
            confirm = confirm || type == "command" || type == "hotkey",
        )
    }

    fun toJson(): JSONObject {
        val value = normalizedForSave()
        return JSONObject().apply {
            put("Id", value.id)
            put("Label", value.label)
            put("Type", value.type)
            put("Target", value.target)
            put("Arguments", value.arguments)
            put("WorkingDirectory", value.workingDirectory)
            put("ProcessNames", JSONArray(value.processNames))
            put("AppNames", JSONArray(value.appNames))
            put("FallbackUrl", value.fallbackUrl)
            put("Icon", value.icon)
            put("Color", value.color)
            put("Confirm", value.confirm)
            put("Closable", value.closable)
            put("Enabled", value.enabled)
        }
    }

    companion object {
        fun fromJson(value: JSONObject) = SyncAction(
            id = value.text("Id", "id"),
            label = value.text("Label", "label"),
            type = value.text("Type", "type").ifBlank { "app" },
            target = value.text("Target", "target"),
            arguments = value.text("Arguments", "arguments"),
            workingDirectory = value.text("WorkingDirectory", "workingDirectory"),
            processNames = value.stringList("ProcessNames", "processNames"),
            appNames = value.stringList("AppNames", "appNames"),
            fallbackUrl = value.text("FallbackUrl", "fallbackUrl"),
            icon = value.text("Icon", "icon").ifBlank { "app" },
            imageKey = value.text("ImageKey", "imageKey"),
            color = value.text("Color", "color").ifBlank { "#697386" },
            confirm = value.bool("Confirm", "confirm"),
            closable = value.bool("Closable", "closable"),
            enabled = value.bool("Enabled", "enabled", true),
            isOpen = value.bool("IsOpen", "isOpen"),
            windowCount = value.int("WindowCount", "windowCount"),
        )

        fun normalizeId(value: String): String {
            val noMarks = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
            var slug = noMarks.replace("[^a-z0-9]+".toRegex(), "-").trim('-')
            if (slug.length < 2) slug = "acao-${UUID.randomUUID().toString().take(8)}"
            return slug.take(64)
        }

        fun split(value: String): List<String> = value.split(',').map { it.trim() }.filter(String::isNotEmpty)
    }
}

data class AgentStatus(
    val name: String,
    val host: String,
    val fingerprint: String,
    val modulus: String,
    val exponent: String,
    val pairingAvailable: Boolean,
    val expiresAt: Long,
    val serverTime: Long,
    val pairedDevices: Int,
    val protocol: Int,
    val endpointRecovered: Boolean = false,
)

data class ActionState(val id: String, val isOpen: Boolean, val windowCount: Int) {
    companion object {
        fun fromJson(value: JSONObject) = ActionState(
            value.text("Id", "id"),
            value.bool("IsOpen", "isOpen"),
            value.int("WindowCount", "windowCount"),
        )
    }
}

data class WakeConfig(
    val macAddress: String,
    val broadcastAddress: String,
    val port: Int,
    val interfaceName: String,
)

data class CatalogApplication(
    val name: String,
    val target: String,
    val processNames: List<String>,
    val appNames: List<String>,
    val icon: String,
    val color: String,
    val selectionToken: String,
) {
    fun toAction() = SyncAction(
        label = name,
        type = "app",
        target = target,
        processNames = processNames,
        appNames = appNames,
        icon = icon.ifBlank { "app" },
        color = color.ifBlank { "#64748B" },
        closable = true,
        selectionToken = selectionToken,
    )

    companion object {
        fun fromJson(value: JSONObject) = CatalogApplication(
            name = value.text("Name", "name"),
            target = value.text("Target", "target"),
            processNames = value.stringList("ProcessNames", "processNames"),
            appNames = value.stringList("AppNames", "appNames"),
            icon = value.text("Icon", "icon"),
            color = value.text("Color", "color"),
            selectionToken = value.text("SelectionToken", "selectionToken"),
        )
    }
}

data class PickedPath(
    val label: String,
    val target: String,
    val processNames: List<String>,
    val appNames: List<String>,
    val icon: String,
    val color: String,
    val selectionToken: String,
) {
    fun toAction() = SyncAction(
        label = label,
        type = "path",
        target = target,
        processNames = processNames,
        appNames = appNames,
        icon = icon.ifBlank { "folder" },
        color = color.ifBlank { "#F5B82E" },
        closable = false,
        selectionToken = selectionToken,
    )

    companion object {
        fun fromJson(value: JSONObject) = PickedPath(
            label = value.text("Label", "label"),
            target = value.text("Target", "target"),
            processNames = value.stringList("ProcessNames", "processNames"),
            appNames = value.stringList("AppNames", "appNames"),
            icon = value.text("Icon", "icon"),
            color = value.text("Color", "color"),
            selectionToken = value.text("SelectionToken", "selectionToken"),
        )
    }
}

internal fun JSONObject.text(primary: String, alternate: String): String =
    optString(primary, optString(alternate, ""))

internal fun JSONObject.bool(primary: String, alternate: String, fallback: Boolean = false): Boolean =
    if (has(primary)) optBoolean(primary, fallback) else optBoolean(alternate, fallback)

internal fun JSONObject.int(primary: String, alternate: String, fallback: Int = 0): Int =
    if (has(primary)) optInt(primary, fallback) else optInt(alternate, fallback)

internal fun JSONObject.stringList(primary: String, alternate: String): List<String> {
    val array = optJSONArray(primary) ?: optJSONArray(alternate) ?: return emptyList()
    return buildList {
        repeat(array.length()) { index -> array.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
    }
}
