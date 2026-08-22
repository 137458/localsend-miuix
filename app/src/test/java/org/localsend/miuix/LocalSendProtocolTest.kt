package org.localsend.miuix

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileDto
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto

class LocalSendProtocolTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test
    fun testDeviceDtoSerialization() {
        val dto = DeviceDto(
            alias = "Cool Xiaomi",
            version = "2.1",
            deviceModel = "Xiaomi 14",
            deviceType = "mobile",
            fingerprint = "test-fingerprint-123",
            port = 53317,
            protocol = "http",
            download = true,
            announce = true
        )

        val encoded = json.encodeToString(DeviceDto.serializer(), dto)
        assertTrue(encoded.contains("\"alias\":\"Cool Xiaomi\""))
        assertTrue(encoded.contains("\"port\":53317"))

        val decoded = json.decodeFromString<DeviceDto>(encoded)
        assertEquals("Cool Xiaomi", decoded.alias)
        assertEquals("test-fingerprint-123", decoded.fingerprint)
        assertEquals(53317, decoded.port)
    }

    @Test
    fun testPrepareUploadDtos() {
        val infoDto = DeviceDto(
            alias = "Fast Apple",
            fingerprint = "apple-fp-456"
        )
        val fileDto = FileDto(
            id = "file-uuid-1",
            fileName = "photo.jpg",
            size = 2048576,
            fileType = "image/jpeg"
        )
        val req = PrepareUploadRequestDto(
            info = infoDto,
            files = mapOf("file-uuid-1" to fileDto)
        )

        val reqEncoded = json.encodeToString(PrepareUploadRequestDto.serializer(), req)
        val reqDecoded = json.decodeFromString<PrepareUploadRequestDto>(reqEncoded)
        assertEquals(1, reqDecoded.files.size)
        assertEquals("photo.jpg", reqDecoded.files["file-uuid-1"]?.fileName)

        val res = PrepareUploadResponseDto(
            sessionId = "session-123",
            files = mapOf("file-uuid-1" to "token-abc")
        )
        val resEncoded = json.encodeToString(PrepareUploadResponseDto.serializer(), res)
        val resDecoded = json.decodeFromString<PrepareUploadResponseDto>(resEncoded)
        assertEquals("session-123", resDecoded.sessionId)
        assertEquals("token-abc", resDecoded.files["file-uuid-1"])
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", FileItem.formatFileSize(500))
        assertEquals("1.0 KB", FileItem.formatFileSize(1024))
        assertEquals("1.5 MB", FileItem.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", FileItem.formatFileSize((2L * 1024 * 1024 * 1024)))
    }
}
