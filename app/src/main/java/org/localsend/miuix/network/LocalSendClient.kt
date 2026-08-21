package org.localsend.miuix.network

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileDto
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalSendClient(
    private val context: Context,
    private val getLocalDevice: () -> Device
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@LocalSendClient.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 30000
            }
        }
    }

    suspend fun prepareUpload(
        targetDevice: Device,
        files: List<FileItem>
    ): Result<PrepareUploadResponseDto> = withContext(Dispatchers.IO) {
        try {
            val localDevice = getLocalDevice()
            val filesMap = files.associate { it.id to it.toDto() }
            val requestDto = PrepareUploadRequestDto(
                info = localDevice.toDto(),
                files = filesMap
            )

            val url = "${targetDevice.url}/api/localsend/v2/prepare-upload"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestDto)
            }

            if (response.status == HttpStatusCode.OK) {
                val responseDto = response.body<PrepareUploadResponseDto>()
                Result.success(responseDto)
            } else {
                val text = response.bodyAsText()
                Result.failure(Exception("Target rejected transfer: ${response.status} ($text)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                else -> throw IllegalArgumentException("No file source available for ${fileItem.name}")
            } ?: throw IllegalStateException("Cannot open stream for ${fileItem.name}")

            val uploadUrl = URL("${targetDevice.url}/api/localsend/v2/upload?sessionId=$sessionId&fileId=${fileItem.id}&token=$token")
            connection = (uploadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setChunkedStreamingMode(64 * 1024)
                connectTimeout = 10000
                readTimeout = 60000
                setRequestProperty("Content-Type", fileItem.mimeType)
            }

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
            if (responseCode == HttpURLConnection.HTTP_OK) {
                onProgress(bytesWritten, 0L)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Upload failed with HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
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
            httpClient.post(url)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
