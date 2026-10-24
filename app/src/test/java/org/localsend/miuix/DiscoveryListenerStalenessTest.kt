package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.ListenerConfig
import org.localsend.miuix.network.isListenerStale

class DiscoveryListenerStalenessTest {
    private val defaultGroup = ListenerConfig.parse("224.0.0.167", 5)!!

    @Test
    fun unchangedGroupAndTimeoutAreFresh() {
        assertFalse(isListenerStale(defaultGroup, ListenerConfig.parse("224.0.0.167", 5)))
    }

    @Test
    fun changedGroupIsStale() {
        // IPv4 换 IPv4
        assertTrue(isListenerStale(defaultGroup, ListenerConfig.parse("239.255.255.250", 5)))
        // IPv4 换 IPv6
        assertTrue(isListenerStale(defaultGroup, ListenerConfig.parse("ff02::1", 5)))
    }

    @Test
    fun changedTimeoutIsStale() {
        assertTrue(isListenerStale(defaultGroup, ListenerConfig.parse("224.0.0.167", 10)))
    }

    @Test
    fun invalidCurrentGroupKeepsListenerAlive() {
        // 非法组地址不触发重建风暴，等待用户改回合法值再切换
        assertFalse(isListenerStale(defaultGroup, ListenerConfig.parse("not-an-address", 5)))
    }

    @Test
    fun parseReturnsNullForInvalidGroup() {
        assertNull(ListenerConfig.parse("not-an-address", 5))
        assertNull(ListenerConfig.parse("ff:", 5))
    }
}
