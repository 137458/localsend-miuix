package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.isListenerStale
import java.net.InetAddress

class DiscoveryListenerStalenessTest {
    private val defaultGroup = InetAddress.getByName("224.0.0.167")

    @Test
    fun unchangedGroupAndTimeoutAreFresh() {
        assertFalse(isListenerStale(defaultGroup, 5, "224.0.0.167", 5))
    }

    @Test
    fun changedGroupIsStale() {
        // IPv4 换 IPv4
        assertTrue(isListenerStale(defaultGroup, 5, "239.255.255.250", 5))
        // IPv4 换 IPv6
        assertTrue(isListenerStale(defaultGroup, 5, "ff02::1", 5))
    }

    @Test
    fun changedTimeoutIsStale() {
        assertTrue(isListenerStale(defaultGroup, 5, "224.0.0.167", 10))
    }

    @Test
    fun invalidCurrentGroupKeepsListenerAlive() {
        // 非法组地址不触发重建风暴，等待用户改回合法值再切换
        assertFalse(isListenerStale(defaultGroup, 5, "not-an-address", 5))
    }
}
