package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.util.SettingsValidators

class SettingsValidatorsTest {
    @Test
    fun acceptsIpv4AndIpv6MulticastAddresses() {
        assertTrue(SettingsValidators.isValidMulticastGroup("224.0.0.167"))
        assertTrue(SettingsValidators.isValidMulticastGroup("239.255.255.250"))
        assertTrue(SettingsValidators.isValidMulticastGroup("ff02::1"))
        assertTrue(SettingsValidators.isValidMulticastGroup("FF02::1"))
        assertTrue(SettingsValidators.isValidMulticastGroup(" 224.0.0.167 "))
    }

    @Test
    fun rejectsNonMulticastOrMalformedAddresses() {
        assertFalse(SettingsValidators.isValidMulticastGroup(""))
        assertFalse(SettingsValidators.isValidMulticastGroup("192.168.1.1"))
        assertFalse(SettingsValidators.isValidMulticastGroup("223.0.0.1"))
        assertFalse(SettingsValidators.isValidMulticastGroup("240.0.0.1"))
        assertFalse(SettingsValidators.isValidMulticastGroup("256.0.0.1"))
        assertFalse(SettingsValidators.isValidMulticastGroup("fe80::1"))
        assertFalse(SettingsValidators.isValidMulticastGroup("ffxy::1"))
        // 旧正则校验会放行 “ff:”，存入后 InetAddress.getByName 抛异常导致监听线程反复失败
        assertFalse(SettingsValidators.isValidMulticastGroup("ff:"))
        assertFalse(SettingsValidators.isValidMulticastGroup("ff02::zz"))
    }

    @Test
    fun parsesDiscoveryTimeoutWithinRange() {
        assertEquals(1, SettingsValidators.parseDiscoveryTimeout("1"))
        assertEquals(5, SettingsValidators.parseDiscoveryTimeout("5"))
        assertEquals(30, SettingsValidators.parseDiscoveryTimeout("30"))
        assertEquals(10, SettingsValidators.parseDiscoveryTimeout(" 10 "))
    }

    @Test
    fun rejectsDiscoveryTimeoutOutsideRangeOrNonNumeric() {
        assertNull(SettingsValidators.parseDiscoveryTimeout("0"))
        assertNull(SettingsValidators.parseDiscoveryTimeout("31"))
        assertNull(SettingsValidators.parseDiscoveryTimeout("-1"))
        assertNull(SettingsValidators.parseDiscoveryTimeout("abc"))
        assertNull(SettingsValidators.parseDiscoveryTimeout(""))
    }
}
