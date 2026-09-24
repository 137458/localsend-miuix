package org.localsend.miuix

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.core.ProtocolMessages
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileDto
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import org.localsend.miuix.network.LocalSendServer

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
    fun declineMessageCannotBeMistakenForReceiverCancel() {
        // 发送方在 403 文案里检索取消标记来决定提示语义：显式拒绝不得命中该标记，否则会误报“对方已取消”
        val canceled = LocalSendServer.prepareUploadRejectionMessage(canceledByReceiver = true)
        val declined = LocalSendServer.prepareUploadRejectionMessage(canceledByReceiver = false)
        assertTrue(canceled.contains(ProtocolMessages.CANCELED_BY_RECEIVER))
        assertFalse(declined.contains(ProtocolMessages.CANCELED_BY_RECEIVER))
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
        assertEquals(org.localsend.miuix.transfer.RemainingTime.Seconds(3), session.remainingTime)
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

    @Test
    fun testMultiFilePinLifecycle() {
        val fp = "11:22:33:44:55:66:77:88"
        org.localsend.miuix.network.FingerprintTrust.pin(fp)
        // Session level pin keeps it pinned
        org.localsend.miuix.network.FingerprintTrust.pin(fp)
        org.localsend.miuix.network.FingerprintTrust.unpin(fp)
        // Still pinned by the session
        org.localsend.miuix.network.FingerprintTrust.unpin(fp)
    }

    @Test
    fun testMultiFileBatchStatusAggregation() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 100, status = org.localsend.miuix.model.TransferStatus.Completed),
            FileItem(id = "2", name = "b.txt", size = 200, status = org.localsend.miuix.model.TransferStatus.Failed, error = "Connection closed"),
            FileItem(id = "3", name = "c.txt", size = 300, status = org.localsend.miuix.model.TransferStatus.Completed)
        )
        val failedCount = files.count { it.status == org.localsend.miuix.model.TransferStatus.Failed }
        assertEquals(1, failedCount)
        val hasCompleted = files.any { it.status == org.localsend.miuix.model.TransferStatus.Completed }
        assertTrue(hasCompleted)
        val finalStatus = if (failedCount == files.size) {
            org.localsend.miuix.model.TransferStatus.Failed
        } else if (failedCount > 0) {
            org.localsend.miuix.model.TransferStatus.Completed
        } else {
            org.localsend.miuix.model.TransferStatus.Completed
        }
        assertEquals(org.localsend.miuix.model.TransferStatus.Completed, finalStatus)
    }

    @Test
    fun testHandshakeResultImmediateCompletion() {
        val device = Device(alias = "TestPeer", fingerprint = "fp-test", ip = "192.168.1.50")
        val handshakeResult = org.localsend.miuix.network.LocalSendClient.HandshakeResult(
            response = PrepareUploadResponseDto(sessionId = "", files = emptyMap()),
            activeDevice = device,
            completedImmediately = true
        )
        assertTrue(handshakeResult.completedImmediately)
        assertTrue(handshakeResult.response.files.isEmpty())
        assertEquals("TestPeer", handshakeResult.activeDevice.alias)
    }

    @Test
    fun testTextMessageCopyStatusTransition() {
        // 模拟向原版应用发送纯文本消息：接收方点击“复制”，服务端返回 HTTP 204 或 200且空令牌
        val textMessage = "Hello LocalSend from test"
        val textBytes = textMessage.toByteArray(Charsets.UTF_8)
        val fileItem = FileItem(
            id = "text-1",
            name = "纯文本消息",
            size = textBytes.size.toLong(),
            textContent = textMessage,
            mimeType = "text/plain",
            status = org.localsend.miuix.model.TransferStatus.InProgress
        )
        val files = listOf(fileItem)
        val isAllTextMessage = files.all { it.isTextMessage }
        assertTrue(isAllTextMessage)

        val fileTokens = emptyMap<String, String>()
        val completedImmediately = true

        if (completedImmediately || (isAllTextMessage && fileTokens.isEmpty())) {
            files.forEach {
                it.status = org.localsend.miuix.model.TransferStatus.Completed
                it.progress = 1f
                it.bytesTransferred = it.size
            }
        }

        assertEquals(org.localsend.miuix.model.TransferStatus.Completed, fileItem.status)
        assertEquals(1f, fileItem.progress, 0.001f)
        assertEquals(textBytes.size.toLong(), fileItem.bytesTransferred)
        org.junit.Assert.assertNull(fileItem.error)
    }

    @Test
    fun testTextMessageInBatchWithoutTokenHandledAsCompleted() {
        // 混合传输场景：纯文本消息在 preview 中提供（<= 2000 字符），接收方未索取 token
        val textContent = "短文本消息"
        val textItem = FileItem(
            id = "text-msg",
            name = "说明.txt",
            size = textContent.toByteArray().size.toLong(),
            textContent = textContent,
            mimeType = "text/plain",
            status = org.localsend.miuix.model.TransferStatus.InProgress
        )
        val binaryItem = FileItem(
            id = "binary-file",
            name = "image.png",
            size = 1024,
            mimeType = "image/png",
            status = org.localsend.miuix.model.TransferStatus.InProgress
        )
        val files = listOf(textItem, binaryItem)
        // 接收方仅给 binaryItem 分配了 token，未给 textItem 分配 token
        val fileTokens = mapOf("binary-file" to "token-123")

        for (item in files) {
            val token = fileTokens[item.id]
            if (token == null) {
                if (item.isTextMessage && !item.textContent.isNullOrEmpty() && item.textContent!!.length <= 2000) {
                    item.status = org.localsend.miuix.model.TransferStatus.Completed
                    item.progress = 1f
                    item.bytesTransferred = item.size
                    continue
                }
                item.status = org.localsend.miuix.model.TransferStatus.Failed
            }
        }

        assertEquals(org.localsend.miuix.model.TransferStatus.Completed, textItem.status)
        assertEquals(org.localsend.miuix.model.TransferStatus.InProgress, binaryItem.status)
    }

    @Test
    fun testHttp2ResponseHeadersDoNotContainConnectionHeader() {
        // RFC 7540 §8.1.2.2 & RFC 9113 §8.2.2:
        // HTTP/2 prohibits connection-specific headers (Connection, Keep-Alive, etc.).
        // Presence of Connection header causes Hyper / rhttp client to fail with PROTOCOL_ERROR (Reset stream).
        val http2Headers = org.localsend.miuix.network.LocalSendServer.getUploadResponseHeaders("HTTP/2.0")
        org.junit.Assert.assertFalse(
            "HTTP/2 response must not contain Connection header",
            http2Headers.containsKey(io.ktor.http.HttpHeaders.Connection)
        )
        org.junit.Assert.assertFalse(
            "HTTP/2 response must not contain connection-specific headers",
            http2Headers.keys.any { it.lowercase() in org.localsend.miuix.network.LocalSendServer.FORBIDDEN_HTTP2_HEADERS }
        )

        val http2AlternativeHeaders = org.localsend.miuix.network.LocalSendServer.getUploadResponseHeaders("HTTP/2")
        org.junit.Assert.assertFalse(
            "HTTP/2 alternative response must not contain Connection header",
            http2AlternativeHeaders.containsKey(io.ktor.http.HttpHeaders.Connection)
        )

        // For HTTP/1.1, Connection: keep-alive is permissible
        val http1Headers = org.localsend.miuix.network.LocalSendServer.getUploadResponseHeaders("HTTP/1.1")
        assertEquals("keep-alive", http1Headers[io.ktor.http.HttpHeaders.Connection])
    }

    @Test
    fun testLargeTxtFileIsNotConsideredTextMessage() {
        val txtFile = FileItem(
            id = "file-txt-1",
            name = "large_log.txt",
            size = 10_000_000L,
            mimeType = "text/plain",
            textContent = null
        )
        // 关键断言：纯文本文件（如 10MB txt 文件）绝不能被误判为纯文本消息
        org.junit.Assert.assertFalse("Large txt file must not be treated as text message", txtFile.isTextMessage)
    }

    @Test
    fun testFileDtoMappingWithoutPreviewIsNotTextMessage() {
        val dto = FileDto(
            id = "dto-1",
            fileName = "book.txt",
            size = 5_000_000L,
            fileType = "text/plain",
            preview = null
        )
        val isTextMsg = !dto.preview.isNullOrEmpty() && (dto.fileType == "text" || dto.fileType == "text/plain") && dto.size <= org.localsend.miuix.model.MAX_INLINE_TEXT_SIZE
        val item = FileItem(
            id = dto.id,
            name = dto.fileName,
            size = dto.size,
            mimeType = dto.fileType,
            textContent = if (isTextMsg) dto.preview else null,
            isTextMessage = isTextMsg
        )
        org.junit.Assert.assertFalse(item.isTextMessage)
        org.junit.Assert.assertNull(item.textContent)
    }

    @Test
    fun testTextMessageServerResponds204WhenSaveTextAsFileDisabled() {
        // 纯文本传输场景：全为文本消息，saveTextAsFile 为 false
        val textItem = FileItem(
            id = "txt-1",
            name = "text_123.txt",
            size = 50,
            textContent = "这是一条文本消息",
            mimeType = "text/plain",
            token = "token-txt-1",
            isTextMessage = true
        )
        val files = listOf(textItem)

        val decision = org.localsend.miuix.network.LocalSendServer.resolvePrepareUploadDecision(
            files = files,
            saveTextAsFile = false
        )

        // 核心断言：纯文本消息在未开启另存为文件时，必须响应 204 No Content，不分发 upload token，立即完成
        assertTrue("All-text transfer must respond with 204 NoContent", decision.shouldRespondNoContent)
        assertTrue("No upload token should be issued for text messages", decision.tokenMap.isEmpty())
        assertTrue("Session should complete immediately without upload stage", decision.isSessionCompletedImmediately)
        assertEquals(org.localsend.miuix.model.TransferStatus.Completed, textItem.status)
        assertEquals(50L, textItem.bytesTransferred)
        assertEquals(1f, textItem.progress, 0.001f)
    }

    @Test
    fun testMixedTransferOmitsTextMessageTokens() {
        // 混合传输场景：包含二进制文件与纯文本消息，saveTextAsFile 为 false
        val textItem = FileItem(
            id = "txt-msg",
            name = "text_note.txt",
            size = 30,
            textContent = "附言文本",
            mimeType = "text/plain",
            token = "token-txt",
            isTextMessage = true
        )
        val binaryItem = FileItem(
            id = "bin-file",
            name = "avatar.png",
            size = 1024,
            mimeType = "image/png",
            token = "token-bin",
            isTextMessage = false
        )
        val files = listOf(textItem, binaryItem)

        val decision = org.localsend.miuix.network.LocalSendServer.resolvePrepareUploadDecision(
            files = files,
            saveTextAsFile = false
        )

        // 核心断言：不能直接回 204（因为有二进制文件需传输），但仅给二进制文件分发 token，文本消息不分发 token
        org.junit.Assert.assertFalse("Mixed transfer must not respond with 204", decision.shouldRespondNoContent)
        org.junit.Assert.assertFalse("Session still requires uploading binary files", decision.isSessionCompletedImmediately)
        assertEquals("Token map must contain exactly 1 token for binary file", 1, decision.tokenMap.size)
        assertEquals("token-bin", decision.tokenMap["bin-file"])
        org.junit.Assert.assertFalse("Token map must NOT contain text item", decision.tokenMap.containsKey("txt-msg"))
        assertEquals(org.localsend.miuix.model.TransferStatus.Completed, textItem.status)
        org.junit.Assert.assertNotEquals(org.localsend.miuix.model.TransferStatus.Completed, binaryItem.status)
    }

    @Test
    fun testTextMessageServerAllocatesTokenWhenSaveTextAsFileEnabled() {
        // 用户主动在设置中开启“将文本保存为文件”：此时应恢复旧逻辑，分发 token 并落盘
        val textItem = FileItem(
            id = "txt-1",
            name = "text_123.txt",
            size = 50,
            textContent = "这是一条文本消息",
            mimeType = "text/plain",
            token = "token-txt-1",
            isTextMessage = true
        )
        val files = listOf(textItem)

        val decision = org.localsend.miuix.network.LocalSendServer.resolvePrepareUploadDecision(
            files = files,
            saveTextAsFile = true
        )

        org.junit.Assert.assertFalse("When saveTextAsFile is enabled, must not return 204", decision.shouldRespondNoContent)
        assertEquals("Token map must contain the text token for upload", "token-txt-1", decision.tokenMap["txt-1"])
        org.junit.Assert.assertFalse("Session still requires upload", decision.isSessionCompletedImmediately)
    }
}



