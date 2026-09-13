package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.discovery.DeviceDirectory
import org.localsend.miuix.model.Device

class DeviceDirectoryTest {

    private fun device(
        alias: String,
        fingerprint: String,
        ip: String,
        port: Int = 53317,
        lastSeen: Long = 1_000L,
        alternateIps: List<String> = emptyList(),
        deviceModel: String? = null
    ) = Device(
        alias = alias,
        fingerprint = fingerprint,
        port = port,
        ip = ip,
        alternateIps = alternateIps,
        lastSeen = lastSeen,
        deviceModel = deviceModel
    )

    @Test
    fun fingerprintIdentityMergesMultiHomedAddressesAndPrefersSameSubnet() {
        val directory = DeviceDirectory(
            ttlMs = 90_000L,
            clock = { 10_000L },
            primaryIp = { "192.168.1.5" },
            sameSubnet = { a, b ->
                a.substringBeforeLast('.') == b.substringBeforeLast('.')
            }
        )

        directory.upsert(
            device(
                alias = "Official",
                fingerprint = "fp-official",
                ip = "192.168.43.15",
                lastSeen = 1_000L,
                deviceModel = "Pixel 8"
            )
        )
        val merged = directory.upsert(
            device(
                alias = "Official",
                fingerprint = "fp-official",
                ip = "192.168.1.100",
                lastSeen = 2_000L
            )
        )

        assertEquals(1, merged.size)
        assertEquals("192.168.1.100", merged[0].ip)
        assertEquals(listOf("192.168.43.15"), merged[0].alternateIps)
        assertEquals("Pixel 8", merged[0].deviceModel)
        assertEquals(2_000L, merged[0].lastSeen)
    }

    @Test
    fun blankFingerprintFallsBackToIpAndPort() {
        assertTrue(
            DeviceDirectory.isSameDevice(
                device("A", "", "10.0.0.2", 53317),
                device("B", "", "10.0.0.2", 53317)
            )
        )
        assertFalse(
            DeviceDirectory.isSameDevice(
                device("A", "", "10.0.0.2", 53317),
                device("B", "", "10.0.0.3", 53317)
            )
        )
        assertFalse(
            DeviceDirectory.isSameDevice(
                device("A", "fp-a", "10.0.0.2"),
                device("B", "fp-b", "10.0.0.2")
            )
        )
    }

    @Test
    fun expiredDevicesAreDroppedOnUpsert() {
        var now = 0L
        val directory = DeviceDirectory(
            ttlMs = 1_000L,
            clock = { now },
            primaryIp = { null }
        )
        directory.upsert(device("Old", "fp-old", "10.0.0.2", lastSeen = 0L))
        now = 2_000L
        val remaining = directory.upsert(device("New", "fp-new", "10.0.0.3", lastSeen = 2_000L))
        assertEquals(listOf("fp-new"), remaining.map { it.fingerprint })
    }
}
