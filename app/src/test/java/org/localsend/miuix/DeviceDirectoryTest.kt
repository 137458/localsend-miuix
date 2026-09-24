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
    fun deviceMatchesMethod() {
        val d1 = device("A", "fp-1", "10.0.0.2", 53317)
        val d2 = device("A2", "FP-1", "10.0.0.3", 53318)
        val d3 = device("B", "", "10.0.0.2", 53317)
        val d4 = device("B2", "", "10.0.0.2", 53317)
        val d5 = device("B3", "", "10.0.0.2", 53318)
        val d6 = device("C", "fp-2", "10.0.0.2", 53317)

        assertTrue(d1.matches(d2)) // case-insensitive fingerprint match
        assertTrue(d3.matches(d4)) // blank fingerprint matches same ip:port
        assertTrue(d1.matches(d3)) // blank fingerprint fallback to same ip:port
        assertFalse(d3.matches(d5)) // different port
        assertFalse(d1.matches(d6)) // different fingerprints
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

    @Test
    fun pruneDropsExpiredDevicesWithoutNewUpsert() {
        var now = 0L
        val directory = DeviceDirectory(
            ttlMs = 1_000L,
            clock = { now },
            primaryIp = { null }
        )
        directory.upsert(device("Stay", "fp-stay", "10.0.0.2", lastSeen = 0L))

        // TTL 内保留，避免把仍在线的设备剔除
        now = 500L
        assertEquals(listOf("fp-stay"), directory.prune().map { it.fingerprint })

        // 超过 TTL 且局域网无任何新广播时，离线设备同样要被清理
        now = 2_000L
        assertTrue(directory.prune().isEmpty())

        // 清理后设备重新上线不会产生重复条目
        val rejoined = directory.upsert(device("Stay", "fp-stay", "10.0.0.2", lastSeen = 2_000L))
        assertEquals(listOf("fp-stay"), rejoined.map { it.fingerprint })
    }
}
