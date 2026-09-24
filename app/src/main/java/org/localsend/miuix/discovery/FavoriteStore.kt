package org.localsend.miuix.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import java.io.File

@Serializable
data class FavoriteDevice(
    val alias: String,
    val fingerprint: String,
    val ip: String,
    val port: Int = 53317,
    val protocol: String = "http",
    val deviceModel: String? = null,
    val deviceType: DeviceType = DeviceType.mobile,
    val download: Boolean = false,
    val customAlias: String? = null,
) {
    fun matches(device: Device): Boolean {
        if (fingerprint.isNotBlank() && device.fingerprint.isNotBlank()) {
            return fingerprint.equals(device.fingerprint, ignoreCase = true)
        }
        return ip == device.ip && port == device.port
    }

    fun toDevice(): Device =
        Device(
            alias = customAlias?.takeIf { it.isNotBlank() } ?: alias,
            fingerprint = fingerprint,
            port = port,
            protocol = protocol,
            ip = ip,
            deviceModel = deviceModel,
            deviceType = deviceType,
            download = download,
        )

    companion object {
        fun fromDevice(device: Device): FavoriteDevice =
            FavoriteDevice(
                alias = device.alias,
                fingerprint = device.fingerprint,
                ip = device.ip,
                port = device.port,
                protocol = device.protocol,
                deviceModel = device.deviceModel,
                deviceType = device.deviceType,
                download = device.download,
            )
    }
}

class FavoriteStore(
    private val file: File,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        },
) {
    private val writeLock = Any()

    fun load(): List<FavoriteDevice> {
        return try {
            if (!file.exists()) return emptyList()
            val content = file.readText()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun persist(items: List<FavoriteDevice>) {
        synchronized(writeLock) {
            val capped = items.take(maxItems)
            val parent = file.parentFile ?: return@synchronized
            if (!parent.exists() && !parent.mkdirs()) return@synchronized
            val tempFile = File(parent, "${file.name}.tmp")
            tempFile.writeText(json.encodeToString(capped))
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }

    fun isFavorite(device: Device): Boolean {
        val current = load()
        return current.any { it.matches(device) }
    }

    fun toggle(device: Device): List<FavoriteDevice> {
        val current = load()
        val index = current.indexOfFirst { it.matches(device) }
        val updated =
            if (index >= 0) {
                current.filterNot { it.matches(device) }
            } else {
                (listOf(FavoriteDevice.fromDevice(device)) + current).take(maxItems)
            }
        persist(updated)
        return updated
    }

    fun remove(device: Device): List<FavoriteDevice> {
        val current = load()
        val updated = current.filterNot { it.matches(device) }
        persist(updated)
        return updated
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 20
        const val FILENAME = "favorite_devices.json"
    }
}
