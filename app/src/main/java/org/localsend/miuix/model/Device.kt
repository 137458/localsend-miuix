package org.localsend.miuix.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType(val value: String) {
    mobile("mobile"),
    tablet("tablet"),
    desktop("desktop"),
    web("web"),
    headless("headless"),
    server("server");

    companion object {
        fun fromString(str: String?): DeviceType {
            return entries.firstOrNull { it.value.equals(str, ignoreCase = true) } ?: mobile
        }
    }
}

@Serializable
data class DeviceDto(
    val alias: String,
    val version: String = "2.1",
    val deviceModel: String? = null,
    val deviceType: String? = "mobile",
    val fingerprint: String,
    val port: Int = 53317,
    val protocol: String = "http",
    val download: Boolean = false,
    val announce: Boolean? = null
)

data class Device(
    val alias: String,
    val version: String = "2.1",
    val deviceModel: String? = null,
    val deviceType: DeviceType = DeviceType.mobile,
    val fingerprint: String,
    val port: Int = 53317,
    val protocol: String = "http",
    val download: Boolean = false,
    val ip: String,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toDto(announce: Boolean? = null): DeviceDto {
        return DeviceDto(
            alias = alias,
            version = version,
            deviceModel = deviceModel,
            deviceType = deviceType.value,
            fingerprint = fingerprint,
            port = port,
            protocol = protocol,
            download = download,
            announce = announce
        )
    }

    val url: String
        get() = "$protocol://$ip:$port"

    companion object {
        fun fromDto(dto: DeviceDto, ip: String): Device {
            return Device(
                alias = dto.alias,
                version = dto.version,
                deviceModel = dto.deviceModel,
                deviceType = DeviceType.fromString(dto.deviceType),
                fingerprint = dto.fingerprint,
                port = dto.port,
                protocol = dto.protocol,
                download = dto.download,
                ip = ip,
                lastSeen = System.currentTimeMillis()
            )
        }
    }
}
