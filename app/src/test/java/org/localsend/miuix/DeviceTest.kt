package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType

class DeviceTest {

    @Test
    fun deviceTypeFromStringIsCaseInsensitive() {
        assertEquals(DeviceType.mobile, DeviceType.fromString("MOBILE"))
        assertEquals(DeviceType.tablet, DeviceType.fromString("Tablet"))
        assertEquals(DeviceType.server, DeviceType.fromString("server"))
        assertEquals(DeviceType.headless, DeviceType.fromString("HEADLESS"))
    }

    @Test
    fun deviceTypeFromStringFallsBackToMobile() {
        assertEquals(DeviceType.mobile, DeviceType.fromString(null))
        assertEquals(DeviceType.mobile, DeviceType.fromString("unknown-device"))
        assertEquals(DeviceType.mobile, DeviceType.fromString(""))
    }

    @Test
    fun matchesPrefersFingerprintIgnoringCase() {
        val a = Device(alias = "A", fingerprint = "AA:BB:CC", ip = "10.0.0.1")
        val b = Device(alias = "B", fingerprint = "aa:bb:cc", ip = "10.0.0.99")
        assertTrue(a.matches(b))

        val different = Device(alias = "C", fingerprint = "aa:bb:cd", ip = "10.0.0.1")
        assertFalse(a.matches(different))
    }

    @Test
    fun matchesFallsBackToIpAndPortWhenFingerprintMissing() {
        val noFp = Device(alias = "A", fingerprint = "", ip = "10.0.0.7", port = 53317)
        assertTrue(noFp.matches(Device(alias = "B", fingerprint = "real", ip = "10.0.0.7", port = 53317)))
        assertFalse(noFp.matches(Device(alias = "B", fingerprint = "real", ip = "10.0.0.7", port = 53318)))
        assertFalse(noFp.matches(Device(alias = "B", fingerprint = "real", ip = "10.0.0.8", port = 53317)))
    }

    @Test
    fun allIpsIncludesAlternatesWithoutDuplicates() {
        val device = Device(
            alias = "Multi",
            fingerprint = "fp",
            ip = "192.168.43.1",
            alternateIps = listOf("192.168.1.10", "192.168.43.1")
        )
        assertEquals(listOf("192.168.43.1", "192.168.1.10"), device.allIps)

        assertEquals(listOf("10.0.0.1"), Device(alias = "Single", fingerprint = "fp", ip = "10.0.0.1").allIps)
    }

    @Test
    fun toDtoCarriesDeviceTypeValueAndAnnounceFlag() {
        val device = Device(alias = "Tablet", fingerprint = "fp-t", ip = "10.0.0.1", deviceType = DeviceType.tablet)
        val dto = device.toDto(announce = true)
        assertEquals("Tablet", dto.alias)
        assertEquals("tablet", dto.deviceType)
        assertTrue(dto.announce == true)

        val announcedNull = Device(alias = "Web", fingerprint = "fp-w", ip = "10.0.0.2", deviceType = DeviceType.web).toDto()
        assertEquals("web", announcedNull.deviceType)
        assertNull(announcedNull.announce)
    }

    @Test
    fun fromDtoMapsIpAndDeviceType() {
        val dto = DeviceDto(alias = "Desktop", fingerprint = "fp-d", deviceType = "desktop", port = 53318, protocol = "https")
        val device = Device.fromDto(dto, ip = "192.168.1.55")
        assertEquals("192.168.1.55", device.ip)
        assertEquals(DeviceType.desktop, device.deviceType)
        assertEquals(53318, device.port)
        assertEquals("https", device.protocol)
        assertTrue(device.lastSeen > 0L)
    }

    @Test
    fun urlUsesProtocolIpAndPort() {
        assertEquals("http://192.168.1.5:53317", Device(alias = "a", fingerprint = "f", ip = "192.168.1.5").url)
        assertEquals(
            "https://10.0.0.9:8443",
            Device(alias = "a", fingerprint = "f", ip = "10.0.0.9", port = 8443, protocol = "https").url
        )
    }
}