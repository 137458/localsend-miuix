package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.NetworkUtils

class NetworkUtilsTest {

    @Test
    fun isPrivateIpv4CoversRfc1918Boundaries() {
        // 172.16.0.0 - 172.31.255.255 才是私有段
        assertTrue(NetworkUtils.isPrivateIpv4("172.16.0.0"))
        assertTrue(NetworkUtils.isPrivateIpv4("172.31.255.255"))
        assertFalse(NetworkUtils.isPrivateIpv4("172.15.255.255"))
        assertFalse(NetworkUtils.isPrivateIpv4("172.32.0.1"))

        assertTrue(NetworkUtils.isPrivateIpv4("10.255.255.255"))
        assertTrue(NetworkUtils.isPrivateIpv4("192.168.0.0"))
        assertFalse(NetworkUtils.isPrivateIpv4("192.169.1.1"))
        assertFalse(NetworkUtils.isPrivateIpv4("11.0.0.1"))
    }

    @Test
    fun isPrivateIpv4RejectsMalformedInput() {
        assertFalse(NetworkUtils.isPrivateIpv4("192.168.1"))
        assertFalse(NetworkUtils.isPrivateIpv4("192.168.1.1.1"))
        assertFalse(NetworkUtils.isPrivateIpv4("a.b.c.d"))
        assertFalse(NetworkUtils.isPrivateIpv4(""))
        assertFalse(NetworkUtils.isPrivateIpv4("192.168.1.a"))
    }

    @Test
    fun isSameSubnetComparesFirstThreeOctets() {
        assertTrue(NetworkUtils.isSameSubnet("10.1.2.3", "10.1.2.200"))
        assertTrue(NetworkUtils.isSameSubnet("10.1.2.3", "10.1.2.3"))
        assertFalse(NetworkUtils.isSameSubnet("10.1.2.3", "10.1.3.3"))
        assertFalse(NetworkUtils.isSameSubnet("172.16.5.1", "172.16.6.1"))
    }

    @Test
    fun isSameSubnetRejectsSegmentsOfDifferentLength() {
        assertFalse(NetworkUtils.isSameSubnet("10.1.2", "10.1.2.3"))
        assertFalse(NetworkUtils.isSameSubnet("10.1.2.3", "10.1.2.3.4"))
    }
}