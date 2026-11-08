package org.localsend.miuix.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.JsonFileStore
import org.localsend.miuix.core.NetworkConstants
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.isSameDevice
import java.io.File

@Serializable
data class FavoriteDevice(
    val alias: String,
    val fingerprint: String,
    val ip: String,
    val port: Int = NetworkConstants.DEFAULT_PORT,
    val protocol: String = "http",
    val deviceModel: String? = null,
    val deviceType: DeviceType = DeviceType.mobile,
    val download: Boolean = false,
    val customAlias: String? = null,
) {
    fun matches(device: Device): Boolean =
        isSameDevice(
            leftFingerprint = fingerprint,
            leftIp = ip,
            leftPort = port,
            rightFingerprint = device.fingerprint,
            rightIp = device.ip,
            rightPort = device.port,
        )

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
    file: File,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
    json: Json = AppJson.default,
) {
    private val store =
        JsonFileStore(
            file = file,
            serializer = FavoriteDevice.serializer(),
            maxItems = maxItems,
            json = json,
        )

    fun load(): List<FavoriteDevice> = store.load()

    fun persist(items: List<FavoriteDevice>) = store.persist(items)

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
