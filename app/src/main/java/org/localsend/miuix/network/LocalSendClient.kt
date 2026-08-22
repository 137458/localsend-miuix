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

    suspend fun prepareUpload(
        targetDevice: Device,
        files: List<FileItem>
    ): Result<PrepareUploadResponseDto> = withContext(Dispatchers.IO) {
        try {
            // 发送方按规范计算每个文件的 sha256（可选字段），供接收方校验
            for (fileItem in files) {
                if (fileItem.expectedSha256 == null) {
                    fileItem.expectedSha256 = computeSha256(fileItem)
                }
            }
            val localDevice = getLocalDevice()
            val filesMap = files.associate { it.id to it.toDto() }
            val requestDto = PrepareUploadRequestDto(
                info = localDevice.toDto(),
                files = filesMap
            )

            val urlBuilder = StringBuilder("${targetDevice.url}/api/localsend/v2/prepare-upload")
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
                Result.success(responseDto)
            } else {
                val text = response.bodyAsText()
                Result.failure(Exception(prepareErrorText(response.status.value, text)))
            }
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
        onProgress: (bytesWritten: Long, speed: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        try {
            // Open input stream from content URI or File path or textContent
            inputStream = when {
                fileItem.uri != null -> context.contentResolver.openInputStream(fileItem.uri)
                fileItem.path != null -> File(fileItem.path).inputStream()
                fileItem.textContent != null -> fileItem.textContent.byteInputStream(Charsets.UTF_8)
                else -> throw IllegalArgumentException("No source available for ${fileItem.name}")
            } ?: throw IllegalStateException("Cannot open input stream for ${fileItem.name}")

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
                setChunkedStreamingMode(64 * 1024)
                connectTimeout = 15000
                readTimeout = 120000
                setRequestProperty("Content-Type", "application/octet-stream")
            }

            // 注意：openConnection() 是惰性的，真正的 TLS 握手发生在获取 outputStream/读取响应时，
            // 因此 fingerprint 的 pin 必须保持到整个上传结束，直到 finally 才 unpin。
            val outputStream: OutputStream = connection.outputStream
            val buffer = ByteArray(64 * 1024)
            var bytesWritten = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesWritten += read
                bytesSinceLast += read

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 500) {
                    val currentSpeed = (bytesSinceLast * 1000) / delta
                    onProgress(bytesWritten, currentSpeed)
                    bytesSinceLast = 0
                    lastTime = now
                }
            }
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                onProgress(bytesWritten, 0L)
                Result.success(Unit)
            } else {
                val message = when (responseCode) {
                    HttpURLConnection.HTTP_FORBIDDEN -> "上传被拒绝：令牌或来源地址无效（403）"
                    422 -> "文件校验失败：SHA-256 不匹配（422）"
                    else -> "上传失败: HTTP $responseCode"
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
            try {
                connection?.disconnect()
            } catch (ignored: Exception) {}
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
    private fun openSourceStream(fileItem: FileItem): InputStream? = when {
        fileItem.uri != null -> context.contentResolver.openInputStream(fileItem.uri)
        fileItem.path != null -> File(fileItem.path).inputStream()
        fileItem.textContent != null -> fileItem.textContent.byteInputStream(Charsets.UTF_8)
        else -> null
    }

    private fun computeSha256(fileItem: FileItem): String? {
        return try {
            val input = openSourceStream(fileItem) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
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
