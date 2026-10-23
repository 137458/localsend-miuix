package org.localsend.miuix.util

/**
 * 网络接口的纯数据快照：网卡名 + 该接口上的 IP 地址列表。
 * 供 [NetworkFilter] 做纯逻辑过滤，不持有任何系统资源。
 */
data class IfInfo(
    val name: String,
    val addresses: List<String>,
)

/**
 * 高级网络选项的网卡过滤纯逻辑（白/黑名单生效于本机参与发现与被发现的接口选择）。
 *
 * 语义：
 * - 白名单为空 = 全部允许；非空 = 仅命中的接口参与。
 * - 黑名单永远剔除命中项（黑名单优先于白名单）。
 * - 过滤后结果为空时回退为不过滤：防止用户把所有网卡配错后把自己彻底锁死，
 *   此时“不过滤”比“断网式静默失败”更安全。
 *
 * 匹配规则：
 * - 名单条目按逗号/空白切分，忽略条目与接口名的大小写及首尾空白。
 * - 条目命中“网卡显示名”（忽略大小写的全等）或“IP 前缀”（点分前缀/IPv6 前缀的
 *   startsWith，如 192.168.1. 或 fd00:）即视为匹配；接口任一地址命中即保留整个接口。
 */
object NetworkFilter {
    fun selectInterfaces(
        interfaces: List<IfInfo>,
        whitelist: String,
        blacklist: String,
    ): List<IfInfo> {
        val blacklistEntries = parseEntries(blacklist)
        val filtered = interfaces.filter { !matchesAny(it, blacklistEntries) }
        val whitelistEntries = parseEntries(whitelist)
        if (whitelistEntries.isEmpty()) {
            // 白名单未配置：黑名单过滤结果（可能已回退）即为最终结果
            return filtered.ifEmpty { interfaces }
        }
        val whitelisted = filtered.filter { matchesAny(it, whitelistEntries) }
        // 白名单把接口全部滤空时同样回退为不过滤
        return whitelisted.ifEmpty { interfaces }
    }

    /** 条目按逗号/空白切分，忽略大小写与首尾空白。 */
    private fun parseEntries(raw: String): List<String> =
        raw
            .split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    /** 接口名全等命中，或任一地址以条目为前缀命中。 */
    private fun matchesAny(
        iface: IfInfo,
        entries: List<String>,
    ): Boolean {
        val name = iface.name.lowercase()
        return entries.any { entry ->
            name == entry || iface.addresses.any { it.startsWith(entry) }
        }
    }
}
