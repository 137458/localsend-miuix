package org.localsend.miuix.network

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class LocalSendClient(
    private val context: Context,
    private val getLocalDevice: () -> Device,
    private val getPin: () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val httpClient by lazy {
        HttpClient(CIO) {
            followRedirects = false
            engine {
                https {
                    // HTTPS 模式：仅信任已通过 FingerprintTrust.pin() 登记了证书指纹的对端
                    trustManager = FingerprintTrust.trustManager
                }
            }
            install(ContentNegotiation) {
                json(this@LocalSendClient.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 60000
            }
        }
    }

    data class HandshakeResult(
        val response: PrepareUploadResponseDto,
        val activeDevice: Device
    )

    suspend fun prepareUpload(
        targetDevice: Device,
        files: List<FileItem>
    ): Result<HandshakeResult> = withContext(Dispatchers.IO) {
        try {
            // 发送方按规范计算小文件（<= 20MB）或文本的 sha256（协议中为可选字段）
            // 大文件跳过全盘预读，消除握手前数十秒卡顿并避免闪存双重读盘与发热
            val maxSha256PrecomputeBytes = 20 * 1024 * 1024L
            for (fileItem in files) {
                if (fileItem.expectedSha256 == null && (fileItem.size in 1..maxSha256PrecomputeBytes || fileItem.textContent != null)) {
                    fileItem.expectedSha256 = computeSha256(fileItem)
                }
            }
            val localDevice = getLocalDevice()
            val filesMap = files.associate { it.id to it.toDto() }
            val requestDto = PrepareUploadRequestDto(
                info = localDevice.toDto(),
                files = filesMap
            )

            val candidateHosts = targetDevice.allIps.ifEmpty { listOf(targetDevice.ip) }
            var lastException: Exception? = null

            for (host in candidateHosts) {
                try {
                    val candidateDevice = if (host == targetDevice.ip) targetDevice else targetDevice.copy(ip = host)
                    val urlBuilder = StringBuilder("${candidateDevice.url}/api/localsend/v2/prepare-upload")
                    getPin()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.append("?pin=").append(it) }
                    val url = urlBuilder.toString()
                    FingerprintTrust.pin(targetDevice.fingerprint)
                    val response = try {
                        httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(requestDto)
                        }
                    } finally {
                        FingerprintTrust.unpin(targetDevice.fingerprint)
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val responseDto = response.body<PrepareUploadResponseDto>()
                        return@withContext Result.success(HandshakeResult(responseDto, candidateDevice))
                    } else {
                        val text = response.bodyAsText()
                        return@withContext Result.failure(Exception(prepareErrorText(response.status.value, text)))
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            Result.failure(lastException ?: Exception("无法连接到目标设备"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 将 prepare-upload 的错误码映射为用户可读的中文提示。 */
    private fun prepareErrorText(code: Int, body: String): String = when (code) {
        HttpStatusCode.Unauthorized.value -> "接收方要求输入正确的 PIN 码（401）"
        HttpStatusCode.Conflict.value -> "对方正在处理其他传输会话，请稍后再试（409）"
        HttpStatusCode.TooManyRequests.value -> "请求过于频繁，请稍后再试（429）"
        else -> "对方拒绝了接收请求: $code ($body)"
    }

    suspend fun uploadFile(
        targetDevice: Device,
        sessionId: String,
        fileItem: FileItem,
        token: String,
        maxRetries: Int = 2,
        onProgress: (bytesWritten: Long, speed: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (attempt in 0..maxRetries) {
            val result = uploadFileOnce(
                targetDevice = targetDevice,
                sessionId = sessionId,
                fileItem = fileItem,
                token = token,
                onProgress = onProgress
            )
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()
            val msg = lastError?.message?.lowercase() ?: ""
            val isTransientConnectionError = lastError is java.io.IOException ||
                msg.contains("closed") ||
                msg.contains("reset") ||
                msg.contains("abort") ||
                msg.contains("eof") ||
                msg.contains("broken pipe")

            if (attempt < maxRetries && isTransientConnectionError) {
                onProgress(0L, 0L)
                delay(150L * (attempt + 1))
                continue
            }
            break
        }
        Result.failure(lastError ?: Exception("上传失败"))
    }

    private fun uploadFileOnce(
        targetDevice: Device,
        sessionId: String,
        fileItem: FileItem,
        token: String,
        onProgress: (bytesWritten: Long, speed: Long) -> Unit
    ): Result<Unit> {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        var isSucceeded = false
        return try {
            inputStream = openSourceStream(fileItem)?.buffered(128 * 1024)
                ?: throw IllegalStateException("Cannot open input stream for ${fileItem.name}")

            val uploadUrlStr = "${targetDevice.url}/api/localsend/v2/upload?sessionId=$sessionId&fileId=${fileItem.id}&token=$token"
            val uploadUrl = URL(uploadUrlStr)

            FingerprintTrust.pin(targetDevice.fingerprint)
            connection = (uploadUrl.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    sslSocketFactory = FingerprintTrust.pinnedSslSocketFactory
                    hostnameVerifier = SslHelper.trustAllHostnameVerifier
                }
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                if (fileItem.size >= 0) {
                    setFixedLengthStreamingMode(fileItem.size)
                } else {
                    setChunkedStreamingMode(128 * 1024)
                }
                connectTimeout = 15000
                readTimeout = 120000
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Keep-Alive", "timeout=60")
            }

            val outputStream: OutputStream = connection.outputStream.buffered(128 * 1024)
            val buffer = ByteArray(128 * 1024)
            var bytesWritten = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L
            var smoothedSpeed = 0L

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesWritten += read
                bytesSinceLast += read

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 64) {
                    val instantSpeed = (bytesSinceLast * 1000) / delta
                    smoothedSpeed = if (smoothedSpeed == 0L) instantSpeed else (smoothedSpeed * 3 + instantSpeed) / 4
                    onProgress(bytesWritten, smoothedSpeed)
                    bytesSinceLast = 0
                    lastTime = now
                }
            }
            outputStream.flush()
            outputStream.close()
            if (bytesSinceLast > 0 || bytesWritten == fileItem.size) {
                onProgress(bytesWritten, smoothedSpeed)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                // 关键优化：完整排空响应输入流，防止 TCP 缓冲区残留数据导致连接关闭时向对端发送 TCP RST
                try {
                    connection.inputStream?.use { stream ->
                        val drainBuf = ByteArray(8192)
                        while (stream.read(drainBuf) != -1) {}
                    }
                } catch (ignored: Exception) {}
                onProgress(bytesWritten, 0L)
                isSucceeded = true
                Result.success(Unit)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (ignored: Exception) { null }
                val message = when (responseCode) {
                    HttpURLConnection.HTTP_FORBIDDEN -> "上传被拒绝：令牌或来源地址无效（403）"
                    422 -> "文件校验失败：SHA-256 不匹配（422）"
                    else -> "上传失败: HTTP $responseCode ${errorBody?.take(100) ?: ""}".trim()
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FingerprintTrust.unpin(targetDevice.fingerprint)
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
            // 关键优化：若传输成功且响应流已排空，保持底层 TCP/TLS 长连接供后续文件直接复用，
            // 避免频繁四次挥手/拆建链与并发冲突；仅在异常或失败时 disconnect 踢出坏连接。
            if (!isSucceeded) {
                try {
                    connection?.disconnect()
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun cancelUpload(targetDevice: Device, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${targetDevice.url}/api/localsend/v2/cancel?sessionId=$sessionId"
            FingerprintTrust.pin(targetDevice.fingerprint)
            try {
                httpClient.post(url)
            } finally {
                FingerprintTrust.unpin(targetDevice.fingerprint)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 打开文件/URI/文本对应的输入流，供上传与哈希计算共用；不持有则返回 null。 */
    private fun openSourceStream(fileItem: FileItem): InputStream? {
        val text = fileItem.textContent
        return when {
            fileItem.uri != null -> {
                if (fileItem.uri.scheme == "file") {
                    fileItem.uri.path?.let { File(it).inputStream() } ?: context.contentResolver.openInputStream(fileItem.uri)
                } else {
                    context.contentResolver.openInputStream(fileItem.uri)
                }
            }
            fileItem.path != null -> File(fileItem.path).inputStream()
            text != null -> text.byteInputStream(Charsets.UTF_8)
            else -> null
        }
    }

    private fun computeSha256(fileItem: FileItem): String? {
        return try {
            val input = openSourceStream(fileItem)?.buffered(128 * 1024) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            input.use {
                var read: Int
                while (it.read(buffer).also { r -> read = r } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
