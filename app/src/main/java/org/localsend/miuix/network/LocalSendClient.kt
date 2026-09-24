package org.localsend.miuix.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.localsend.miuix.R
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class TargetPinRequiredException(message: String = "PIN required") : IllegalStateException(message)

/** 对端明确拒绝本次上传（403/404/422 等业务错误），重试无意义，直接以该原因结束该项文件。 */
class PeerRejectedException(message: String) : IllegalStateException(message)

class LocalSendClient(
    private val context: Context,
    private val getLocalDevice: () -> Device
) {
    companion object {
        private const val TAG = "LocalSendTransfer"

        /** 上传阶段对端的业务级拒绝状态码：命中即判定为不可重试。 */
        private val TERMINAL_REJECTION_CODES = setOf(
            HttpURLConnection.HTTP_FORBIDDEN,
            HttpURLConnection.HTTP_NOT_FOUND,
            HttpURLConnection.HTTP_UNAUTHORIZED,
            422
        )

        fun buildPrepareUploadUrl(baseUrl: String, targetPin: String?): String {
            val pin = targetPin?.trim()?.takeIf { it.isNotEmpty() } ?: return "$baseUrl${LocalSendRoutes.PREPARE_UPLOAD}"
            return "$baseUrl${LocalSendRoutes.PREPARE_UPLOAD}?pin=${URLEncoder.encode(pin, "UTF-8")}"
        }
    }
    private val json = AppJson.default

    data class HandshakeResult(
        val response: PrepareUploadResponseDto,
        val activeDevice: Device,
        val completedImmediately: Boolean = false
    )

    suspend fun prepareUpload(
        targetDevice: Device,
        files: List<FileItem>,
        targetPin: String? = null
    ): Result<HandshakeResult> = withContext(Dispatchers.IO) {
        try {
            // 发送方按规范计算小文本文件的 sha256（协议中为可选字段）；
            // 多文件或大文件跳过全盘预读，消除握手前数十秒卡顿并避免闪存双重读盘与发热
            val maxSha256PrecomputeBytes = 2 * 1024 * 1024L
            for (fileItem in files) {
                if (fileItem.expectedSha256 == null && ((files.size <= 3 && fileItem.size in 1..maxSha256PrecomputeBytes) || fileItem.textContent != null)) {
                    fileItem.expectedSha256 = computeSha256(fileItem)
                }
            }
            val localDevice = getLocalDevice()
            val filesMap = files.associate { it.id to it.toDto() }
            val requestDto = PrepareUploadRequestDto(
                info = localDevice.toDto(),
                files = filesMap
            )
            val jsonPayload = json.encodeToString(requestDto)

            val candidateHosts = targetDevice.allIps.ifEmpty { listOf(targetDevice.ip) }
            Log.i(TAG, "prepareUpload: target='${targetDevice.alias}' (${targetDevice.url}), candidates=$candidateHosts, filesCount=${files.size}, totalBytes=${files.sumOf { it.size }}")
            var lastException: Exception? = null

            for (host in candidateHosts) {
                var connection: HttpURLConnection? = null
                try {
                    val candidateDevice = if (host == targetDevice.ip) targetDevice else targetDevice.copy(ip = host)
                    val url = buildPrepareUploadUrl(candidateDevice.url, targetPin)
                    Log.d(TAG, "prepareUpload: sending handshake via native TLS to $url (candidate host: $host)")

                    FingerprintTrust.pin(targetDevice.fingerprint)
                    try {
                        connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            if (this is HttpsURLConnection) {
                                sslSocketFactory = FingerprintTrust.pinnedSslSocketFactory
                                hostnameVerifier = SslHelper.trustAllHostnameVerifier
                            }
                            requestMethod = "POST"
                            doOutput = true
                            useCaches = false
                            instanceFollowRedirects = false
                            connectTimeout = 10000
                            readTimeout = 60000
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
                        }

                        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(jsonPayload)
                            writer.flush()
                        }

                        val responseCode = connection.responseCode
                        val responseMsg = connection.responseMessage
                        Log.d(TAG, "prepareUpload: host $host returned HTTP $responseCode $responseMsg")

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            val responseDto = json.decodeFromString<PrepareUploadResponseDto>(responseBody)
                            Log.i(TAG, "prepareUpload: handshake successful with $host, sessionId=${responseDto.sessionId}, granted tokens count=${responseDto.files.size}/${files.size}")
                            return@withContext Result.success(HandshakeResult(responseDto, candidateDevice, completedImmediately = false))
                        } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                            // 协议 §4.1 / RFC 7231：204 No Content 表示对端完全接受请求且无需传输文件体（如纯文本消息在 preview 中已被接收方直接复制或消费）
                            Log.i(TAG, "prepareUpload: handshake completed immediately with $host (HTTP 204 No Content, no file upload needed)")
                            return@withContext Result.success(
                                HandshakeResult(
                                    response = PrepareUploadResponseDto(sessionId = "", files = emptyMap()),
                                    activeDevice = candidateDevice,
                                    completedImmediately = true
                                )
                            )
                        } else {
                            val errorBody = try {
                                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            } catch (ignored: Exception) { null }
                            Log.e(TAG, "prepareUpload: rejected by $host, HTTP $responseCode: $errorBody")
                            val errMsg = prepareErrorText(responseCode, errorBody ?: "")
                            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                                return@withContext Result.failure(TargetPinRequiredException(errMsg))
                            }
                            return@withContext Result.failure(Exception(errMsg))
                        }
                    } finally {
                        FingerprintTrust.unpin(targetDevice.fingerprint)
                        try {
                            connection?.disconnect()
                        } catch (ignored: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "prepareUpload: connection attempt to host $host failed: ${e.message}", e)
                    lastException = e
                }
            }
            Result.failure(lastException ?: Exception(context.getString(R.string.msg_cannot_connect)))
        } catch (e: Exception) {
            Log.e(TAG, "prepareUpload failed with exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun safeGetString(resId: Int, fallback: String, vararg formatArgs: Any): String = try {
        if (formatArgs.isEmpty()) context.getString(resId) else context.getString(resId, *formatArgs)
    } catch (_: Throwable) {
        fallback
    }

    private fun prepareErrorText(code: Int, body: String): String = when (code) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> safeGetString(R.string.msg_pin_required, "PIN required")
        HttpURLConnection.HTTP_CONFLICT -> safeGetString(R.string.msg_peer_busy, "Peer is busy")
        429 -> safeGetString(R.string.msg_too_many_requests, "Too many requests")
        else -> safeGetString(R.string.msg_peer_rejected_http, "HTTP $code: $body", code, body).trim()
    }

    /** 403 通用文案会误导用户以为是令牌问题，接收方主动取消时改用明确的取消提示。 */
    private fun forbiddenErrorText(errorBody: String?): String =
        if (errorBody?.contains(org.localsend.miuix.core.ProtocolMessages.CANCELED_BY_RECEIVER) == true) {
            safeGetString(R.string.msg_receiver_canceled, "Receiver canceled the transfer")
        } else {
            context.getString(R.string.msg_upload_forbidden)
        }

    suspend fun uploadFile(
        targetDevice: Device,
        sessionId: String,
        fileItem: FileItem,
        token: String,
        maxRetries: Int = 2,
        isCanceled: () -> Boolean = { false },
        onProgress: (bytesWritten: Long, speed: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                Log.w(TAG, "Retrying upload for '${fileItem.name}' (attempt $attempt/$maxRetries) after error: ${lastError?.message}")
            }
            if (isCanceled()) {
                return@withContext Result.failure(Exception(context.getString(R.string.session_canceled)))
            }
            val result = uploadFileOnce(
                targetDevice = targetDevice,
                sessionId = sessionId,
                fileItem = fileItem,
                token = token,
                isCanceled = isCanceled,
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

            // 对端的业务级拒绝（403/404/422/401）统一由 uploadFileOnce 包装为该异常，命中即不再重试
            if (lastError is PeerRejectedException) {
                Log.w(TAG, "Upload rejected with terminal HTTP status, aborting retry: $msg")
                break
            }

            if (attempt < maxRetries && isTransientConnectionError) {
                onProgress(0L, 0L)
                delay(200L * (attempt + 1))
                continue
            }
            break
        }
        Result.failure(lastError ?: Exception(context.getString(R.string.msg_upload_failed)))
    }

    private fun uploadFileOnce(
        targetDevice: Device,
        sessionId: String,
        fileItem: FileItem,
        token: String,
        isCanceled: () -> Boolean,
        onProgress: (bytesWritten: Long, speed: Long) -> Unit
    ): Result<Unit> {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        var isSucceeded = false
        val startTime = System.currentTimeMillis()
        return try {
            inputStream = openSourceStream(fileItem)?.buffered(128 * 1024)
                ?: throw IllegalStateException("Cannot open input stream for ${fileItem.name}")

            val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
            val encodedFileId = URLEncoder.encode(fileItem.id, "UTF-8")
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val uploadUrlStr = "${targetDevice.url}${LocalSendRoutes.UPLOAD}?sessionId=$encodedSessionId&fileId=$encodedFileId&token=$encodedToken"
            val uploadUrl = URL(uploadUrlStr)

            Log.d(TAG, "uploadFileOnce: connecting to ${targetDevice.url}${LocalSendRoutes.UPLOAD} for '${fileItem.name}' (id=${fileItem.id}, size=${fileItem.size})")

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
                if (fileItem.size > 0L) {
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
                if (isCanceled()) {
                    throw java.io.InterruptedIOException(context.getString(R.string.session_canceled))
                }
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
            val responseMsg = connection.responseMessage
            val connHeader = connection.getHeaderField("Connection")
            Log.d(TAG, "uploadFileOnce: response for '${fileItem.name}': HTTP $responseCode $responseMsg, Connection: $connHeader, bytesWritten=$bytesWritten in ${System.currentTimeMillis() - startTime}ms")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                // 完整排空响应输入流，防止 TCP 缓冲区残留数据导致连接关闭时向对端发送 TCP RST
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
                Log.e(TAG, "uploadFileOnce: upload rejected by peer for '${fileItem.name}', HTTP $responseCode: $errorBody")
                val message = when (responseCode) {
                    HttpURLConnection.HTTP_FORBIDDEN -> forbiddenErrorText(errorBody)
                    422 -> context.getString(R.string.msg_sha256_mismatch)
                    404 -> context.getString(R.string.msg_session_not_found)
                    else -> context.getString(R.string.msg_upload_http, responseCode, errorBody?.take(100) ?: "").trim()
                }
                Result.failure(if (responseCode in TERMINAL_REJECTION_CODES) PeerRejectedException(message) else Exception(message))
            }
        } catch (e: Exception) {
            val responseCode = try { connection?.responseCode } catch (_: Exception) { -1 }
            val errorBody = try {
                connection?.errorStream?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) { null }
            Log.e(TAG, "uploadFileOnce: exception during upload of '${fileItem.name}' (HTTP $responseCode, errorBody='$errorBody'): ${e.message}", e)

            val mappedException = if (responseCode != -1 && responseCode != 200 && responseCode != 204) {
                val message = when (responseCode) {
                    HttpURLConnection.HTTP_FORBIDDEN -> forbiddenErrorText(errorBody)
                    422 -> context.getString(R.string.msg_sha256_mismatch)
                    404 -> context.getString(R.string.msg_session_not_found)
                    500 -> context.getString(R.string.msg_peer_internal_error, errorBody ?: "").trim()
                    else -> context.getString(R.string.msg_peer_http, responseCode, errorBody ?: "").trim()
                }
                if (responseCode in TERMINAL_REJECTION_CODES) PeerRejectedException(message) else Exception(message, e)
            } else {
                e
            }
            Result.failure(mappedException)
        } finally {
            FingerprintTrust.unpin(targetDevice.fingerprint)
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
            val connHeader = try { connection?.getHeaderField("Connection") } catch (_: Exception) { null }
            // 若传输未成功，或对端明确要求 Connection: close，关闭并释放连接，防止污染 OkHttp 内部连接池
            if (!isSucceeded || connHeader?.equals("close", ignoreCase = true) == true) {
                try {
                    connection?.disconnect()
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun cancelUpload(targetDevice: Device, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
            val url = "${targetDevice.url}${LocalSendRoutes.CANCEL}?sessionId=$encodedSessionId"
            Log.i(TAG, "cancelUpload: sending cancel for session $sessionId to $url")
            FingerprintTrust.pin(targetDevice.fingerprint)
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        sslSocketFactory = FingerprintTrust.pinnedSslSocketFactory
                        hostnameVerifier = SslHelper.trustAllHostnameVerifier
                    }
                    requestMethod = "POST"
                    useCaches = false
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val code = connection.responseCode
                Log.d(TAG, "cancelUpload: returned HTTP $code")
            } finally {
                FingerprintTrust.unpin(targetDevice.fingerprint)
                try {
                    connection?.disconnect()
                } catch (ignored: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "cancelUpload failed: ${e.message}")
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
