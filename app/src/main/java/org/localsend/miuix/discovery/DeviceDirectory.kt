package org.localsend.miuix.discovery

import org.localsend.miuix.model.Device
import org.localsend.miuix.network.NetworkUtils

/**
 * Nearby-device directory: identity, TTL eviction, and multi-homed IP merge.
 *
 * Identity matches official LocalSend: fingerprint when both sides declare one,
 * otherwise ip+port. Does not send or parse protocol packets.
 */
class DeviceDirectory(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val primaryIp: () -> String? = { NetworkUtils.getPrimaryIp() },
    private val sameSubnet: (String, String) -> Boolean = NetworkUtils::isSameSubnet
) {
    @Volatile
    private var devices: List<Device> = emptyList()

    fun snapshot(): List<Device> = devices

    @Synchronized
    fun upsert(device: Device): List<Device> {
        val now = clock()
        val alive = devices.filter { now - it.lastSeen < ttlMs }
        val index = alive.indexOfFirst { isSameDevice(it, device) }
        devices = if (index < 0) {
            alive + device
        } else {
            val known = alive[index]
            val allConfirmedIps = (known.allIps + device.allIps).distinct()
            val primaryLocalIp = primaryIp()
            val bestIp = if (primaryLocalIp != null && allConfirmedIps.any { sameSubnet(it, primaryLocalIp) }) {
                allConfirmedIps.first { sameSubnet(it, primaryLocalIp) }
            } else {
                device.ip
            }
            val merged = device.copy(
                ip = bestIp,
                alternateIps = allConfirmedIps.filter { it != bestIp },
                deviceModel = device.deviceModel ?: known.deviceModel,
                lastSeen = maxOf(known.lastSeen, device.lastSeen)
            )
            alive.toMutableList().apply { set(index, merged) }
        }
        return devices
    }

    companion object {
        const val DEFAULT_TTL_MS = 90_000L

        fun isSameDevice(a: Device, b: Device): Boolean {
            if (a.fingerprint.isNotBlank() && b.fingerprint.isNotBlank()) {
                return a.fingerprint == b.fingerprint
            }
            return a.ip == b.ip && a.port == b.port
        }
    }
}
