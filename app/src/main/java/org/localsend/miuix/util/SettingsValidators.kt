package org.localsend.miuix.util

/** 设置页高级网络选项的纯函数校验，供 UI 弹窗与单元测试复用。 */
object SettingsValidators {
    // IPv4 组播：首字节 224-239（224.0.0.0/4），其余字节 0-255
    private val IPV4_MULTICAST =
        Regex(
            "^(22[4-9]|23\\d)\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\." +
                "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$",
        )

    /** 校验 IPv4/IPv6 组播地址（简单格式校验：224.0.0.0/4 或 ff00::/8，允许 :: 压缩）。 */
    fun isValidMulticastGroup(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty()) return false
        return IPV4_MULTICAST.matches(candidate) || isIpv6Multicast(candidate)
    }

    private fun isIpv6Multicast(value: String): Boolean {
        if (!value.startsWith("ff", ignoreCase = true)) return false
        if (!value.contains(':')) return false
        return value.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    }

    /** 发现超时：整数且在 1-30 秒之间，非法输入返回 null。 */
    fun parseDiscoveryTimeout(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in 1..30 }
}
