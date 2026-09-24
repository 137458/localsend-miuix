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
    private val sameSubnet: (String, String) -> Boolean = NetworkUtils::isSameSubnet,
) {
    @Volatile
    private var devices: List<Device> = emptyList()

    fun snapshot(): List<Device> = devices

    /** TTL 内仍存活的设备；upsert 与 prune 共用同一判定，避免两处漂移。 */
    private fun aliveDevices(now: Long): List<Device> = devices.filter { now - it.lastSeen < ttlMs }

    @Synchronized
    fun upsert(device: Device): List<Device> {
        val now = clock()
        val alive = aliveDevices(now)
        val index = alive.indexOfFirst { isSameDevice(it, device) }
        devices =
            if (index < 0) {
                alive + device
            } else {
                val known = alive[index]
                val allConfirmedIps = (known.allIps + device.allIps).distinct()
                val primaryLocalIp = primaryIp()
                val bestIp =
                    if (primaryLocalIp != null && allConfirmedIps.any { sameSubnet(it, primaryLocalIp) }) {
                        allConfirmedIps.first { sameSubnet(it, primaryLocalIp) }
                    } else {
                        device.ip
                    }
                val merged =
                    device.copy(
                        ip = bestIp,
                        alternateIps = allConfirmedIps.filter { it != bestIp },
                        deviceModel = device.deviceModel ?: known.deviceModel,
                        lastSeen = maxOf(known.lastSeen, device.lastSeen),
                    )
                alive.toMutableList().apply { set(index, merged) }
            }
        return devices
    }

    /**
     * 周期性剔除已超过 TTL 未再出现的设备。
     * [upsert] 只在收到新数据包时顺带清理，若局域网长期无广播，离线设备会一直留在列表中，故需要外部定时调用。
     */
    @Synchronized
    fun prune(): List<Device> {
        val now = clock()
        val alive = aliveDevices(now)
        if (alive.size != devices.size) {
            devices = alive
        }
        return devices
    }

    companion object {
        const val DEFAULT_TTL_MS = 90_000L

        fun isSameDevice(
            a: Device,
            b: Device,
        ): Boolean = a.matches(b)
    }
}
