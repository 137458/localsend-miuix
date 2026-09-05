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

    @Test
    fun testTransferSessionMetrics() {
        val file1 = FileItem(id = "1", name = "file1.txt", size = 1000, status = org.localsend.miuix.model.TransferStatus.Completed, bytesTransferred = 1000)
        val file2 = FileItem(id = "2", name = "file2.mp4", size = 4000, status = org.localsend.miuix.model.TransferStatus.InProgress, bytesTransferred = 1000)
        val device = Device(alias = "Test", fingerprint = "fp123", ip = "192.168.1.5")
        val session = org.localsend.miuix.model.TransferSession(
            sessionId = "sess-1",
            device = device,
            isIncoming = false,
            files = listOf(file1, file2),
            totalBytes = 5000,
            transferredBytes = 2000,
            speed = 1000,
            status = org.localsend.miuix.model.TransferStatus.InProgress
        )

        assertEquals(40, session.progressPercent)
        assertEquals(1, session.currentFileIndex)
        assertEquals("file2.mp4", session.currentFile?.name)
        assertEquals("剩余约 3秒", session.remainingTimeFormatted)
    }

    @Test
    fun testFingerprintNormalization() {
        val rawFp = "AA:BB:CC:DD:EE:FF:11:22"
        val normalized = org.localsend.miuix.network.FingerprintTrust.normalize(rawFp)
        assertEquals("aabbccddeeff1122", normalized)
    }

    @Test
    fun testShareSessionDownloadLink() {
        val share = org.localsend.miuix.model.ShareSession(files = emptyList())
        assertEquals("http://192.168.1.10:53317", share.downloadLink("http", "192.168.1.10", 53317))
        assertEquals("https://192.168.1.10:53317", share.downloadLink("https", "192.168.1.10", 53317))
    }

    @Test
    fun testDeviceMultiHomedAllIps() {
        val device = Device(
            alias = "Multi-homed Device",
            fingerprint = "fp-multi-1",
            ip = "192.168.1.100",
            alternateIps = listOf("192.168.43.15", "192.168.1.100")
        )
        assertEquals(listOf("192.168.1.100", "192.168.43.15"), device.allIps)
    }

    @Test
    fun testNetworkUtilsPrivateIpv4AndSubnet() {
        assertTrue(org.localsend.miuix.network.NetworkUtils.isPrivateIpv4("192.168.1.5"))
        assertTrue(org.localsend.miuix.network.NetworkUtils.isPrivateIpv4("10.0.0.1"))
        assertTrue(org.localsend.miuix.network.NetworkUtils.isPrivateIpv4("172.20.10.2"))
        org.junit.Assert.assertFalse(org.localsend.miuix.network.NetworkUtils.isPrivateIpv4("8.8.8.8"))
        org.junit.Assert.assertFalse(org.localsend.miuix.network.NetworkUtils.isPrivateIpv4("100.64.0.1"))

        assertTrue(org.localsend.miuix.network.NetworkUtils.isSameSubnet("192.168.1.5", "192.168.1.100"))
        org.junit.Assert.assertFalse(org.localsend.miuix.network.NetworkUtils.isSameSubnet("192.168.1.5", "192.168.43.15"))
    }
}
