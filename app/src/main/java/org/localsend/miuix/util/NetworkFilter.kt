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
 * - 黑名单永远剔除命中项（黑名单优先于白名单），回退时也不得把黑名单剔除的接口重新放回。
 * - 白名单单独把接口滤空时回退为“仅应用黑名单”的结果：白名单多为选填偏好，
 *   配错不应把用户彻底锁死；黑名单则表示明确的排除意图，必须严格生效。
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
            // 白名单未配置：黑名单结果即最终结果；即使被滤空也不回退，黑名单必须严格生效
            return filtered
        }
        val whitelisted = filtered.filter { matchesAny(it, whitelistEntries) }
        // 白名单把接口全部滤空时回退为“仅应用黑名单”的结果：防自锁回退不得连黑名单剔除一起丢弃
        return whitelisted.ifEmpty { filtered }
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
