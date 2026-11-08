package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.deviceIdentityKey
import org.localsend.miuix.model.isSameDevice

class DeviceIdentityTest {
    @Test
    fun fingerprintWinsOverAddressWhenBothSidesDeclareIt() {
        assertTrue(
            isSameDevice(
                leftFingerprint = "AB-CD",
                leftIp = "10.0.0.2",
                leftPort = 53317,
                rightFingerprint = "ab-cd",
                rightIp = "10.0.0.9",
                rightPort = 53318,
            ),
        )
        assertFalse(
            isSameDevice(
                leftFingerprint = "fp-one",
                leftIp = "10.0.0.2",
                leftPort = 53317,
                rightFingerprint = "fp-two",
                rightIp = "10.0.0.2",
                rightPort = 53317,
            ),
        )
    }

    @Test
    fun missingFingerprintFallsBackToIpAndPort() {
        assertTrue(
            isSameDevice(
                leftFingerprint = "",
                leftIp = "10.0.0.2",
                leftPort = 53317,
                rightFingerprint = "fp-peer",
                rightIp = "10.0.0.2",
                rightPort = 53317,
            ),
        )
        assertFalse(
            isSameDevice(
                leftFingerprint = "fp-self",
                leftIp = "10.0.0.2",
                leftPort = 53317,
                rightFingerprint = "   ",
                rightIp = "10.0.0.2",
                rightPort = 53318,
            ),
        )
    }

    @Test
    fun identityKeyPrefersFingerprintAndFallsBackToAddress() {
        assertEquals("fp-peer", deviceIdentityKey("fp-peer", "10.0.0.2", 53317))
        assertEquals("fp-peer", deviceIdentityKey("  fp-peer  ", "10.0.0.2", 53317))
        assertEquals("10.0.0.2:53317", deviceIdentityKey("", "10.0.0.2", 53317))
        assertEquals("10.0.0.2:53317", deviceIdentityKey(null, "10.0.0.2", 53317))
    }

    @Test
    fun deviceIdentityKeyMatchesDeviceMatches() {
        val left = Device(alias = "A", fingerprint = "fp-1", ip = "10.0.0.2", port = 53317, deviceType = DeviceType.mobile)
        val renamedByAddress = Device(alias = "A2", fingerprint = "   ", ip = "10.0.0.2", port = 53317, deviceType = DeviceType.desktop)

        assertTrue(left.matches(renamedByAddress))
        assertEquals("fp-1", left.identityKey)
        assertEquals("10.0.0.2:53317", renamedByAddress.identityKey)
    }
}
