package org.localsend.miuix

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.network.PeerRejectedException
import org.localsend.miuix.network.TargetPinRequiredException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class LocalSendClientErrorHandlingTest {

    private var server: HttpServer? = null
    private var serverPort: Int = 0

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server?.createContext(LocalSendRoutes.PREPARE_UPLOAD) { exchange ->
            // 模拟对端需要 PIN 码返回 401 Unauthorized
            val response = "{\"message\":\"PIN required\"}".toByteArray()
            exchange.sendResponseHeaders(401, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server?.start()
        serverPort = server?.address?.port ?: 0
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private class TestContext : ContextWrapper(null)

    @Test
    fun prepareUploadReturnsTargetPinRequiredExceptionOn401() = runBlocking {
        val testContext = TestContext()
        val localDevice = Device(
            alias = "TestSender",
            version = "2.1",
            deviceModel = "Test",
            deviceType = DeviceType.mobile,
            fingerprint = "fp123",
            port = 53317,
            protocol = "http",
            download = false,
            ip = "127.0.0.1"
        )
        val targetDevice = Device(
            alias = "TestReceiver",
            version = "2.1",
            deviceModel = "Receiver",
            deviceType = DeviceType.mobile,
            fingerprint = "",
            port = serverPort,
            protocol = "http",
            download = false,
            ip = "127.0.0.1"
        )

        val client = LocalSendClient(testContext) { localDevice }
        val files = listOf(
            FileItem(
                id = "1",
                name = "test.txt",
                size = 100,
                mimeType = "text/plain"
            )
        )

        val result = client.prepareUpload(targetDevice, files)
        assertTrue("Expected isFailure but was success", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected TargetPinRequiredException but was: ${error?.javaClass?.name}, message=${error?.message}",
            error is TargetPinRequiredException
        )
    }

    @Test
    fun receiverCancelIsReportedAsTerminalRejectionWithoutRetry() = runBlocking {
        val uploadAttempts = AtomicInteger(0)
        server?.createContext(LocalSendRoutes.UPLOAD) { exchange ->
            uploadAttempts.incrementAndGet()
            val response = "{\"message\":\"Transfer canceled by receiver\"}".toByteArray()
            exchange.sendResponseHeaders(403, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }

        val localDevice = Device(
            alias = "TestSender",
            version = "2.1",
            deviceModel = "Test",
            deviceType = DeviceType.mobile,
            fingerprint = "fp123",
            port = 53317,
            protocol = "http",
            download = false,
            ip = "127.0.0.1"
        )
        val targetDevice = Device(
            alias = "TestReceiver",
            version = "2.1",
            deviceModel = "Receiver",
            deviceType = DeviceType.mobile,
            fingerprint = "",
            port = serverPort,
            protocol = "http",
            download = false,
            ip = "127.0.0.1"
        )

        val client = LocalSendClient(TestContext()) { localDevice }
        val payload = "cancel-test".toByteArray()
        val file = FileItem(
            id = "1",
            name = "cancel.txt",
            size = payload.size.toLong(),
            textContent = "cancel-test",
            mimeType = "text/plain"
        )

        val result = client.uploadFile(
            targetDevice = targetDevice,
            sessionId = "session-1",
            fileItem = file,
            token = "token-1",
            onProgress = { _, _ -> }
        )

        assertTrue("Expected isFailure but was success", result.isFailure)
        assertTrue(
            "Expected PeerRejectedException but was: ${result.exceptionOrNull()?.javaClass?.name}",
            result.exceptionOrNull() is PeerRejectedException
        )
        assertEquals("Peer rejection must not be retried", 1, uploadAttempts.get())
    }
}
