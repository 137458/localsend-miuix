package org.localsend.miuix.network

import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getLocalIpAddresses(): List<String> {
        val wifiAddresses = mutableListOf<String>()
        val otherAddresses = mutableListOf<String>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                val isWifi = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap") || name.startsWith("en")

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.")) continue

                        if (isWifi) {
                            wifiAddresses.add(host)
                        } else {
                            otherAddresses.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val all = wifiAddresses + otherAddresses
        return all.ifEmpty { listOf("127.0.0.1") }
    }

    fun getBroadcastAddresses(): List<String> {
        val broadcasts = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (ia in intf.interfaceAddresses) {
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

    fun getSubnetBaseIp(): String? {
        val ip = getLocalIpAddresses().firstOrNull() ?: return null
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
    }
}
