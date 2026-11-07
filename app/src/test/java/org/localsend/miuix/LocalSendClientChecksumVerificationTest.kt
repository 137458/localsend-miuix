package org.localsend.miuix

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.localsend.miuix.core.ChecksumHeaders
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.IntegrityStatus
import org.localsend.miuix.network.ChecksumCommitException
import org.localsend.miuix.network.ChecksumMismatchException
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.util.Sha256
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 覆盖“超门槛文件上传时流式摘要 + 接收端回报”的成功、降级与失败路径。 */
class LocalSendClientChecksumVerificationTest {
    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val uploadAttempts = AtomicInteger(0)
    private val commitRequests = AtomicInteger(0)
    private val rejectRequests = AtomicInteger(0)
    private val commitStatus = AtomicInteger(200)
    private val requestChecksumHeader = AtomicReference<String?>(null)
    private val reportedChecksum = AtomicReference<String?>(null)
    private val pendingDeferred = AtomicBoolean(false)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(LocalSendRoutes.UPLOAD) { exchange ->
            uploadAttempts.incrementAndGet()
            requestChecksumHeader.set(exchange.requestHeaders.getFirst(ChecksumHeaders.REQUEST))
            // 必须读完请求体再响应，否则服务端会提前关闭连接
            exchange.requestBody.readAllBytes()
            reportedChecksum.get()?.let { exchange.responseHeaders.add(ChecksumHeaders.RESPONSE, it) }
            if (pendingDeferred.get()) exchange.responseHeaders.add(ChecksumHeaders.PENDING, "1")
            respond(exchange, 200)
        }
        server.createContext(LocalSendRoutes.CHECKSUM_COMMIT) { exchange ->
            commitRequests.incrementAndGet()
            exchange.requestBody.readAllBytes()
            respond(exchange, commitStatus.get())
        }
        server.createContext(LocalSendRoutes.CHECKSUM_REJECT) { exchange ->
            rejectRequests.incrementAndGet()
            exchange.requestBody.readAllBytes()
            respond(exchange, 200)
        }
        server.start()
        serverPort = server.address.port
    }

    private fun respond(
        exchange: com.sun.net.httpserver.HttpExchange,
        code: Int,
    ) {
        val body = "{}".toByteArray()
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private class TestContext : ContextWrapper(null)

    private fun client(): LocalSendClient {
        val localDevice =
            Device(
                alias = "TestSender",
                version = "2.1",
                deviceModel = "Test",
                deviceType = DeviceType.mobile,
                fingerprint = "fp123",
                port = 53317,
                protocol = "http",
                download = false,
                ip = "127.0.0.1",
            )
        return LocalSendClient(TestContext()) { localDevice }
    }

    private fun targetDevice(): Device =
        Device(
            alias = "TestReceiver",
            version = "2.1",
            deviceModel = "Receiver",
            deviceType = DeviceType.mobile,
            fingerprint = "",
            port = serverPort,
            protocol = "http",
            download = false,
            ip = "127.0.0.1",
        )

    private fun binaryFile(
        name: String,
        payload: ByteArray,
    ): FileItem {
        val temp = File.createTempFile("checksum-", "-$name")
        temp.writeBytes(payload)
        temp.deleteOnExit()
        return FileItem(
            id = name,
            name = name,
            size = payload.size.toLong(),
            path = temp.absolutePath,
            mimeType = "application/octet-stream",
        )
    }

    @Test
    fun streamedUploadIsVerifiedWhenPeerReportsMatchingDigest() =
        runBlocking {
            val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
            val file = binaryFile("verified.bin", payload)
            reportedChecksum.set(Sha256.hexDigest(payload))

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-verified",
                    fileItem = file,
                    token = "token-verified",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isSuccess)
            assertEquals("发送方必须声明请求回报并暂存待确认", "2", requestChecksumHeader.get())
            assertEquals(IntegrityStatus.Verified, file.integrity)
            assertEquals(1, uploadAttempts.get())
            assertEquals("未标记暂存时无需额外往返", 0, commitRequests.get())
            assertEquals(0, rejectRequests.get())
        }

    @Test
    fun pendingUploadIsCommittedAfterMatchingDigest() =
        runBlocking {
            // 新版接收端先落临时文件，摘要一致后必须显式确认转正
            pendingDeferred.set(true)
            val payload = ByteArray(32 * 1024) { (it % 127).toByte() }
            val file = binaryFile("pending-commit.bin", payload)
            reportedChecksum.set(Sha256.hexDigest(payload))

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-commit",
                    fileItem = file,
                    token = "token-commit",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isSuccess)
            assertEquals(IntegrityStatus.Verified, file.integrity)
            assertEquals(1, uploadAttempts.get())
            assertEquals(1, commitRequests.get())
            assertEquals("摘要一致时不得触发丢弃", 0, rejectRequests.get())
        }

    @Test
    fun pendingUploadIsRejectedOnDigestMismatch() =
        runBlocking {
            pendingDeferred.set(true)
            reportedChecksum.set("f".repeat(64))
            val payload = ByteArray(8 * 1024) { 5 }
            val file = binaryFile("pending-reject.bin", payload)

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-reject",
                    fileItem = file,
                    token = "token-reject",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
            assertEquals(IntegrityStatus.Mismatch, file.integrity)
            assertEquals("摘要不一致必须通知接收端丢弃暂存文件", 1, rejectRequests.get())
            assertEquals(0, commitRequests.get())
            assertEquals(1, uploadAttempts.get())
        }

    @Test
    fun commitFailureIsReportedAsFailureWithoutRetry() =
        runBlocking {
            pendingDeferred.set(true)
            commitStatus.set(500)
            val payload = ByteArray(16 * 1024) { 2 }
            val file = binaryFile("commit-fail.bin", payload)
            reportedChecksum.set(Sha256.hexDigest(payload))

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-commit-fail",
                    fileItem = file,
                    token = "token-commit-fail",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isFailure)
            assertTrue(
                "Expected ChecksumCommitException but was: ${result.exceptionOrNull()?.javaClass?.name}",
                result.exceptionOrNull() is ChecksumCommitException,
            )
            assertTrue(result.exceptionOrNull()?.message?.contains("commit-fail.bin") == true)
            assertEquals("转正失败不应重传", 1, uploadAttempts.get())
            assertEquals(1, commitRequests.get())
        }

    @Test
    fun streamedUploadDegradesWhenPeerDoesNotReportDigest() =
        runBlocking {
            // 官方 / 第三方接收端不识别扩展头，只会返回 200 空响应
            reportedChecksum.set(null)
            val payload = ByteArray(16 * 1024) { 7 }
            val file = binaryFile("unsupported.bin", payload)

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-unsupported",
                    fileItem = file,
                    token = "token-unsupported",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue("对端不支持回报时传输仍应成功", result.isSuccess)
            assertEquals(IntegrityStatus.Unverified, file.integrity)
            assertEquals(1, uploadAttempts.get())
            assertEquals("对端不支持时不得发起清理/确认请求", 0, commitRequests.get())
            assertEquals(0, rejectRequests.get())
        }

    @Test
    fun streamedUploadFailsOnDigestMismatchWithoutRetry() =
        runBlocking {
            reportedChecksum.set("0".repeat(64))
            val payload = ByteArray(8 * 1024) { 1 }
            val file = binaryFile("mismatch.bin", payload)

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-mismatch",
                    fileItem = file,
                    token = "token-mismatch",
                    requestChecksum = true,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(
                "Expected ChecksumMismatchException but was: ${error?.javaClass?.name}",
                error is ChecksumMismatchException,
            )
            assertTrue("错误信息必须能定位到具体文件", error?.message?.contains("mismatch.bin") == true)
            assertEquals(IntegrityStatus.Mismatch, file.integrity)
            assertEquals("摘要不一致不应重试", 1, uploadAttempts.get())
        }

    @Test
    fun declaredUploadDoesNotRequestReportedChecksum() =
        runBlocking {
            reportedChecksum.set(Sha256.hexDigest("ignored".toByteArray()))
            val payload = ByteArray(4 * 1024) { 3 }
            val file = binaryFile("declared.bin", payload)
            file.expectedSha256 = Sha256.hexDigest(payload)

            val result =
                client().uploadFile(
                    targetDevice = targetDevice(),
                    sessionId = "session-declared",
                    fileItem = file,
                    token = "token-declared",
                    requestChecksum = false,
                    onProgress = { _, _ -> },
                )

            assertTrue(result.isSuccess)
            assertNull("声明路径由握手时的 sha256 由接收端校验，不应再请求回报", requestChecksumHeader.get())
            assertEquals(IntegrityStatus.None, file.integrity)
        }
}
