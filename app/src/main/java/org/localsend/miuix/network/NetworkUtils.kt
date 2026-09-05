package org.localsend.miuix.network

import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /** 检查是否属于局域网私有 IPv4 地址（RFC 1918） */
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts
        return when (a) {
            10 -> true
            172 -> b in 16..31
            192 -> b == 168
            else -> false
        }
    }

    /** 检查两个 IP 是否在同一个 /24 子网网段内 */
    fun isSameSubnet(ip1: String, ip2: String): Boolean {
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        return p1.size == 4 && p2.size == 4 &&
                p1[0] == p2[0] && p1[1] == p2[1] && p1[2] == p2[2]
    }

    fun getLocalIpAddresses(): List<String> {
        val wifiAddresses = mutableListOf<String>()
        val hotspotAddresses = mutableListOf<String>()
        val otherLanAddresses = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()

                // 排除蜂窝移动数据网卡与虚拟 VPN 隧道接口，避免无效扫描与超时噪音
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") ||
                    name.startsWith("wwan") || name.startsWith("tun") || name.startsWith("ppp") ||
                    name.startsWith("dummy") || name.startsWith("wg") || name.startsWith("clat")
                ) {
                    continue
                }

                val isWifi = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")
                val isHotspot = name.startsWith("ap") || name.startsWith("softap")
                val isTethering = name.startsWith("rndis") || name.startsWith("usb")

                val addrs = intf.inetAddresses?.toList().orEmpty()
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.") || !isPrivateIpv4(host)) continue

                        when {
                            isWifi -> wifiAddresses.add(host)
                            isHotspot -> hotspotAddresses.add(host)
                            isTethering -> otherLanAddresses.add(host)
                            else -> otherLanAddresses.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val all = wifiAddresses + hotspotAddresses + otherLanAddresses
        return all.ifEmpty { listOf("127.0.0.1") }
    }

    fun getPrimaryIp(): String? = getLocalIpAddresses().firstOrNull { it != "127.0.0.1" }

    fun getBroadcastAddresses(): List<String> {
        val broadcasts = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun")) continue

                for (ia in intf.interfaceAddresses.orEmpty()) {
                    val broadcast = ia.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        val host = broadcast.hostAddress
                        if (!host.isNullOrEmpty() && !broadcasts.contains(host)) {
                            broadcasts.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (!broadcasts.contains("255.255.255.255")) {
            broadcasts.add("255.255.255.255")
        }
        return broadcasts
    }

    fun getSubnetBaseIps(): List<String> {
        val ips = getLocalIpAddresses()
        val bases = mutableListOf<String>()
        for (ip in ips) {
            if (ip == "127.0.0.1" || !isPrivateIpv4(ip)) continue
            val parts = ip.split(".")
            if (parts.size == 4) {
                val base = "${parts[0]}.${parts[1]}.${parts[2]}"
                if (!bases.contains(base)) {
                    bases.add(base)
                }
            }
        }
        return bases
    }

    fun getSubnetBaseIp(): String? = getSubnetBaseIps().firstOrNull()
}
