package org.localsend.miuix

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.FileDto

class AppJsonTest {
    @Test
    fun ignoreUnknownKeysToleratesFutureProtocolFields() {
        val first =
            AppJson.default.decodeFromString<DeviceDto>(
                """{"alias":"Alpha","fingerprint":"fp-a","futureFlag":42}""",
            )
        assertEquals("Alpha", first.alias)

        val second =
            AppJson.default.decodeFromString<DeviceDto>(
                """{"alias":"Beta","fingerprint":"fp-b","nested":{"a":1},"download":true}""",
            )
        assertEquals("Beta", second.alias)
        assertTrue(second.download)
    }

    @Test
    fun lenientModeAcceptsQuotedBooleanLiterals() {
        // 宽松模式允许被引号包裹的布尔字面量：download 字段用 "true"/"false" 表达
        val enabled =
            AppJson.default.decodeFromString<DeviceDto>(
                """{"alias":"Lenient","fingerprint":"fp-on","download":"true"}""",
            )
        assertEquals("Lenient", enabled.alias)
        assertTrue(enabled.download)

        val disabled =
            AppJson.default.decodeFromString<DeviceDto>(
                """{"alias":"Lenient","fingerprint":"fp-off","download":"false"}""",
            )
        assertEquals("fp-off", disabled.fingerprint)
        assertFalse(disabled.download)
    }

    @Test
    fun encodeDefaultsWritesDefaultProtocolFields() {
        val encoded = AppJson.default.encodeToString(DeviceDto.serializer(), DeviceDto(alias = "A", fingerprint = "F"))
        assertTrue(encoded.contains("\"version\":\"2.1\""))
        assertTrue(encoded.contains("\"port\":53317"))
        assertTrue(encoded.contains("\"protocol\":\"http\""))
        assertTrue(encoded.contains("\"deviceType\":\"mobile\""))
    }

    @Test
    fun encodeDefaultsWritesDefaultFileType() {
        val encoded = AppJson.default.encodeToString(FileDto.serializer(), FileDto(id = "f1", fileName = "a.bin", size = 10L))
        assertTrue(encoded.contains("\"fileType\":\"application/octet-stream\""))
        assertEquals(
            10L,
            AppJson.default.decodeFromString<FileDto>(encoded).size,
        )
    }
}
