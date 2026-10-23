package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.util.IfInfo
import org.localsend.miuix.util.NetworkFilter

class NetworkFilterTest {
    private fun ifInfo(
        name: String,
        vararg addresses: String,
    ) = IfInfo(name, addresses.toList())

    // 差异化数据： wlan0=192.168.1.5（WiFi），eth0=10.0.0.8 + fd00::8（多地址），
    // eth1=172.16.0.3（被排除的下游口）
    private val wlan0 = ifInfo("wlan0", "192.168.1.5")
    private val eth0 = ifInfo("eth0", "10.0.0.8", "fd00::8")
    private val eth1 = ifInfo("eth1", "172.16.0.3")
    private val all = listOf(wlan0, eth0, eth1)

    @Test
    fun emptyListsKeepAllInterfacesInOrder() {
        assertEquals(all, NetworkFilter.selectInterfaces(all, "", ""))
    }

    @Test
    fun blankEntriesBehaveLikeEmptyLists() {
        // 仅空白/逗号的名单等价于未配置，不应误伤任何接口
        assertEquals(all, NetworkFilter.selectInterfaces(all, "  , ", ",,"))
    }

    @Test
    fun whitelistKeepsOnlyInterfacesMatchingNameOrPrefix() {
        // 按网卡名
        assertEquals(listOf(wlan0), NetworkFilter.selectInterfaces(all, "wlan0", ""))
        // 按 IPv4 点分前缀
        assertEquals(listOf(wlan0), NetworkFilter.selectInterfaces(all, "192.168.1.", ""))
        // 网卡名 + 前缀混合，命中多个
        assertEquals(listOf(eth0, eth1), NetworkFilter.selectInterfaces(all, "eth0,172.16.", ""))
    }

    @Test
    fun blacklistRemovesMatchingInterfaces() {
        // 按网卡名剔除
        assertEquals(listOf(wlan0, eth0), NetworkFilter.selectInterfaces(all, "", "eth1"))
        // 按 IPv4 前缀剔除
        assertEquals(listOf(eth0, eth1), NetworkFilter.selectInterfaces(all, "", "192.168.1."))
    }

    @Test
    fun blacklistWinsOverWhitelist() {
        // eth0 同时命中白名单与黑名单时必须被剔除（黑名单优先）
        assertEquals(listOf(wlan0), NetworkFilter.selectInterfaces(all, "wlan0,eth0", "eth0"))
        // 前缀形式的黑名单同样优先
        assertEquals(listOf(wlan0), NetworkFilter.selectInterfaces(all, "eth0,wlan0", "10.0.0."))
    }

    @Test
    fun fullyFilteredResultFallsBackToUnfiltered() {
        // 黑名单剔除全部接口时回退为不过滤，防止用户把自己锁死
        assertEquals(all, NetworkFilter.selectInterfaces(all, "", "wlan0,eth0,eth1"))
        // 白名单一个都匹配不上时同样回退
        assertEquals(all, NetworkFilter.selectInterfaces(all, "nonexistent9", ""))
    }

    @Test
    fun whitelistFallbackStillAppliesBlacklist() {
        // 白名单无命中回退时不得丢弃黑名单剔除结果（“黑名单永远剔除”优先于防自锁回退）
        assertEquals(listOf(wlan0, eth0), NetworkFilter.selectInterfaces(all, "nonexistent9", "eth1"))
    }

    @Test
    fun entriesAndNamesTolerateCaseAndWhitespace() {
        // 条目大小写 + 首尾空白，接口名大写
        val upper = ifInfo("WLAN0", "192.168.1.5")
        assertEquals(listOf(upper), NetworkFilter.selectInterfaces(listOf(upper), "  WLAN0  ", ""))
        // 白名单条目无命中时回退为不过滤
        assertEquals(listOf(upper), NetworkFilter.selectInterfaces(listOf(upper), "eth1", ""))
        // 黑名单条目带空白与大小写
        assertEquals(listOf(wlan0, eth0), NetworkFilter.selectInterfaces(all, "", " ETH1 "))
    }

    @Test
    fun ipv6PrefixMatches() {
        // IPv6 前缀命中白名单
        assertEquals(listOf(eth0), NetworkFilter.selectInterfaces(all, "fd00:", ""))
        // IPv6 前缀命中黑名单：eth0 虽在白名单仍被剔除（黑优先），结果非空不触发回退
        assertEquals(listOf(wlan0), NetworkFilter.selectInterfaces(all, "eth0,wlan0", "FD00:"))
    }

    @Test
    fun multiAddressInterfaceKeptWhenAnyAddressMatches() {
        // 接口任一地址命中即保留整个接口（连同其它地址一起参与发现）
        assertEquals(listOf(eth0), NetworkFilter.selectInterfaces(all, "fd00::8", ""))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals(emptyList<IfInfo>(), NetworkFilter.selectInterfaces(emptyList(), "wlan0", ""))
    }
}
