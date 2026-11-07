package org.localsend.miuix.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.localsend.miuix.R
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.ChecksumHeaders
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.IntegrityStatus
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import org.localsend.miuix.util.Sha256
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** 单份文件的完整性校验路径，由 [LocalSendClient.checksumPlan] 统一判定。 */
enum class ChecksumPlan {
    /** 门槛内：握手前预置 sha256，并随 prepare-upload 声明。 */
    Declared,

    /** 超出门槛：不预置哈希，上传时流式计算摘要并由接收端回报比对。 */
    Streamed,
}

class TargetPinRequiredException(
    message: String = "PIN required",
) : IllegalStateException(message)

/** 对端明确拒绝本次上传（403/404/422 等业务错误），重试无意义，直接以该原因结束该项文件。 */
class PeerRejectedException(
    message: String,
) : IllegalStateException(message)

/** 发送端流式摘要与接收端回报不一致：数据已在链路或落盘环节损坏，显式失败且不重试。 */
class ChecksumMismatchException(
    message: String,
) : IllegalStateException(message)

/** 摘要一致但接收端未能确认转正：临时文件会被接收端按保鲜期丢弃，按失败上报以免误判成功。 */
class ChecksumCommitException(
    message: String,
) : IllegalStateException(message)

class LocalSendClient(
    private val context: Context,
    private val getLocalDevice: () -> Device,
) {
    companion object {
        private const val TAG = "LocalSendTransfer"

        /** 上传阶段对端的业务级拒绝状态码：命中即判定为不可重试。 */
        private val TERMINAL_REJECTION_CODES =
            setOf(
                HttpURLConnection.HTTP_FORBIDDEN,
                HttpURLConnection.HTTP_NOT_FOUND,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                422,
            )

        /** 声明 sha256 的单份文件大小门槛：超过即不声明，避免握手前的整文件预读。 */
        const val SHA256_PREFILL_MAX_BYTES = 2L * 1024 * 1024

        /** 声明 sha256 的条目数量门槛：文件更多时逐份预读会显著拖慢握手。 */
        const val SHA256_PREFILL_MAX_FILES = 3

        /**
         * 单份文件的完整性校验路径：这里是唯一的判定处，避免“握手前预置”与“上传时流式”两套逻辑并存。
         *
         * 内联文本内容恒定，始终走声明路径；普通文件仅当“待发文件数 ≤ 3 且单份 ≤ 2 MB”时预置 sha256，
         * 其余一律改为上传时流式计算摘要，由接收端落盘后回报比对。
         */
        internal fun checksumPlan(
            files: List<FileItem>,
            fileItem: FileItem,
        ): ChecksumPlan =
            if (fileItem.textContent != null ||
                (files.size <= SHA256_PREFILL_MAX_FILES && fileItem.size in 1..SHA256_PREFILL_MAX_BYTES)
            ) {
                ChecksumPlan.Declared
            } else {
                ChecksumPlan.Streamed
            }

        /** 源文件指纹中记录的字节数；解析失败按 0 处理（视为未超出门槛）。 */
        private fun stampLength(stamp: String): Long = stamp.substringBefore(':').toLongOrNull() ?: 0L

        /**
         * 为走声明路径的条目补算 sha256；已有摘要但源文件指纹已变化时重算，保证声明值与上传内容同源。
         * 流式路径一律撤销历史声明：否则握手会带上早先批次预热出的快照摘要，与上传阶段的流式校验形成两套判定。
         * [shouldCancel] 在每份文件前求值，便于取消传输或文件被移出待发列表时尽快退出。
         */
        internal fun fillMissingSha256(
            files: List<FileItem>,
            shouldCancel: () -> Boolean = { false },
            sourceStamp: (FileItem) -> String? = { null },
            compute: (FileItem) -> String?,
        ) {
            for (fileItem in files) {
                if (shouldCancel()) return
                if (checksumPlan(files, fileItem) != ChecksumPlan.Declared) {
                    fileItem.expectedSha256 = null
                    fileItem.sha256SourceStamp = null
                    fileItem.integrity = IntegrityStatus.None
                    continue
                }
                val stamp = sourceStamp(fileItem)
                // 文本/URI 无指纹来源视为内容恒定；已有摘要且指纹未变时复用，避免重复读盘
                if (fileItem.expectedSha256 != null && (stamp == null || stamp == fileItem.sha256SourceStamp)) {
                    fileItem.integrity = IntegrityStatus.Declared
                    continue
                }
                if (stamp != null && stampLength(stamp) > SHA256_PREFILL_MAX_BYTES) {
                    // 添加后被追加写入、已超出声明门槛：撤销过期声明，避免陈旧摘要触发接收端 422
                    fileItem.expectedSha256 = null
                    fileItem.sha256SourceStamp = null
                    fileItem.integrity = IntegrityStatus.None
                    continue
                }
                fileItem.expectedSha256 = compute(fileItem)
                fileItem.sha256SourceStamp = stamp
                fileItem.integrity = if (fileItem.expectedSha256 != null) IntegrityStatus.Declared else IntegrityStatus.None
            }
        }

        fun buildPrepareUploadUrl(
            baseUrl: String,
            targetPin: String?,
        ): String {
            val pin = targetPin?.trim()?.takeIf { it.isNotEmpty() } ?: return "$baseUrl${LocalSendRoutes.PREPARE_UPLOAD}"
            return "$baseUrl${LocalSendRoutes.PREPARE_UPLOAD}?pin=${URLEncoder.encode(pin, "UTF-8")}"
        }
    }

    private val json = AppJson.default

    data class HandshakeResult(
        val response: PrepareUploadResponseDto,
        val activeDevice: Device,
        val completedImmediately: Boolean = false,
    )

    suspend fun prepareUpload(
        targetDevice: Device,
        files: List<FileItem>,
        targetPin: String? = null,
        shouldCancel: () -> Boolean = { false },
    ): Result<HandshakeResult> =
        withContext(Dispatchers.IO) {
            try {
                // 发送方按协议为每个待传文件计算 sha256（字段可选，缺失即令接收端放弃完整性校验）；
                // 仅对 ≤3 份、单份 ≤2 MB 的条目声明，且指纹变化时重算，杜绝握手前的整文件预读与陈旧摘要
                fillMissingSha256(files, shouldCancel)
                val localDevice = getLocalDevice()
                val filesMap = files.associate { it.id to it.toDto() }
                val requestDto =
                    PrepareUploadRequestDto(
                        info = localDevice.toDto(),
                        files = filesMap,
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
                            connection =
                                (URL(url).openConnection() as HttpURLConnection).apply {
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
                                        completedImmediately = true,
                                    ),
                                )
                            } else {
                                val errorBody =
                                    try {
                                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                    } catch (ignored: Exception) {
                                        null
                                    }
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
                            } catch (ignored: Exception) {
                            }
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

    private fun safeGetString(
        resId: Int,
        fallback: String,
        vararg formatArgs: Any,
    ): String =
        try {
            if (formatArgs.isEmpty()) context.getString(resId) else context.getString(resId, *formatArgs)
        } catch (_: Throwable) {
            fallback
        }

    private fun prepareErrorText(
        code: Int,
        body: String,
    ): String =
        when (code) {
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
        requestChecksum: Boolean = false,
        maxRetries: Int = 2,
        isCanceled: () -> Boolean = { false },
        onProgress: (bytesWritten: Long, speed: Long) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (attempt in 0..maxRetries) {
                if (attempt > 0) {
                    Log.w(TAG, "Retrying upload for '${fileItem.name}' (attempt $attempt/$maxRetries) after error: ${lastError?.message}")
                }
                if (isCanceled()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.session_canceled)))
                }
                val result =
                    uploadFileOnce(
                        targetDevice = targetDevice,
                        sessionId = sessionId,
                        fileItem = fileItem,
                        token = token,
                        requestChecksum = requestChecksum,
                        isCanceled = isCanceled,
                        onProgress = onProgress,
                    )
                if (result.isSuccess) {
                    return@withContext result
                }
                lastError = result.exceptionOrNull()
                val msg = lastError?.message?.lowercase() ?: ""
                val isTransientConnectionError =
                    lastError is java.io.IOException ||
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
                // 摘要不一致说明本次数据已损坏，重传同源数据通常无收益，直接以该项失败结束
                if (lastError is ChecksumMismatchException) {
                    Log.w(TAG, "Checksum mismatch for '${fileItem.name}', aborting retry")
                    break
                }
                // 转正未确认同样属于该项的终态失败，重传只会再次产生待确认临时文件
                if (lastError is ChecksumCommitException) {
                    Log.w(TAG, "Checksum commit unconfirmed for '${fileItem.name}', aborting retry")
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
        requestChecksum: Boolean,
        isCanceled: () -> Boolean,
        onProgress: (bytesWritten: Long, speed: Long) -> Unit,
    ): Result<Unit> {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        var isSucceeded = false
        val startTime = System.currentTimeMillis()
        val digest = if (requestChecksum) MessageDigest.getInstance("SHA-256") else null
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
            connection =
                (uploadUrl.openConnection() as HttpURLConnection).apply {
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
                    if (requestChecksum) {
                        // 自定义扩展头：要求接收端落盘后回报摘要并暂存，待本端比对后再确认转正；
                        // 官方/第三方接收端会忽略；旧版接收端按 "1" 语义立即转正，不会误判成功
                        setRequestProperty(ChecksumHeaders.REQUEST, ChecksumHeaders.REQUEST_DEFERRED)
                    }
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
                // 摘要与写入套接字的字节严格同源：校验值覆盖的就是本次实际发送的内容
                digest?.update(buffer, 0, read)
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
                } catch (ignored: Exception) {
                }
                if (requestChecksum) {
                    val peerChecksum = connection.getHeaderField(ChecksumHeaders.RESPONSE)?.trim()?.lowercase()
                    val localChecksum = digest?.digest()?.let { Sha256.hex(it) }
                    // 接收端把文件暂存在临时位置，只有在收到本端的确认后才会转正
                    val deferredCommit = connection.getHeaderField(ChecksumHeaders.PENDING) == "1"
                    when {
                        peerChecksum.isNullOrEmpty() -> {
                            // 对端是官方/第三方实现：不识别回报扩展，正常传输照旧算成功，仅降级为无校验
                            Log.w(TAG, "uploadFileOnce: peer did not report sha256 for '${fileItem.name}' (unsupported receiver), transfer succeeded without verification")
                            fileItem.integrity = IntegrityStatus.Unverified
                        }
                        peerChecksum == localChecksum -> {
                            if (deferredCommit) {
                                val decision = sendChecksumDecision(targetDevice, sessionId, fileItem, token, commit = true, timeoutMs = 10_000)
                                if (decision.isFailure) {
                                    Log.e(TAG, "uploadFileOnce: failed to confirm checksum for '${fileItem.name}': ${decision.exceptionOrNull()?.message}")
                                    fileItem.integrity = IntegrityStatus.None
                                    return Result.failure(
                                        ChecksumCommitException(
                                            safeGetString(
                                                R.string.msg_checksum_commit_failed,
                                                "Receiver did not confirm the file: ${fileItem.name}",
                                                fileItem.name,
                                            ),
                                        ),
                                    )
                                }
                            }
                            Log.i(TAG, "uploadFileOnce: checksum verified for '${fileItem.name}' (deferredCommit=$deferredCommit)")
                            fileItem.integrity = IntegrityStatus.Verified
                        }
                        else -> {
                            Log.e(TAG, "uploadFileOnce: checksum mismatch for '${fileItem.name}': local=$localChecksum, peer=$peerChecksum")
                            if (deferredCommit) {
                                // 明确通知接收端丢弃暂存文件；即便请求丢失，接收端也会在保鲜期后自动清理
                                sendChecksumDecision(targetDevice, sessionId, fileItem, token, commit = false, timeoutMs = 5_000)
                                    .onFailure { Log.w(TAG, "uploadFileOnce: failed to notify receiver to discard '${fileItem.name}': ${it.message}") }
                            }
                            fileItem.integrity = IntegrityStatus.Mismatch
                            return Result.failure(
                                ChecksumMismatchException(
                                    safeGetString(
                                        R.string.msg_checksum_mismatch_file,
                                        "Checksum mismatch for ${fileItem.name}",
                                        fileItem.name,
                                    ),
                                ),
                            )
                        }
                    }
                }
                onProgress(bytesWritten, 0L)
                isSucceeded = true
                Result.success(Unit)
            } else {
                val errorBody =
                    try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (ignored: Exception) {
                        null
                    }
                Log.e(TAG, "uploadFileOnce: upload rejected by peer for '${fileItem.name}', HTTP $responseCode: $errorBody")
                val message =
                    when (responseCode) {
                        HttpURLConnection.HTTP_FORBIDDEN -> forbiddenErrorText(errorBody)
                        422 -> context.getString(R.string.msg_sha256_mismatch)
                        404 -> context.getString(R.string.msg_session_not_found)
                        else -> context.getString(R.string.msg_upload_http, responseCode, errorBody?.take(100) ?: "").trim()
                    }
                Result.failure(if (responseCode in TERMINAL_REJECTION_CODES) PeerRejectedException(message) else Exception(message))
            }
        } catch (e: Exception) {
            val responseCode =
                try {
                    connection?.responseCode
                } catch (_: Exception) {
                    -1
                }
            val errorBody =
                try {
                    connection?.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            Log.e(TAG, "uploadFileOnce: exception during upload of '${fileItem.name}' (HTTP $responseCode, errorBody='$errorBody'): ${e.message}", e)

            val mappedException =
                if (responseCode != -1 && responseCode != 200 && responseCode != 204) {
                    val message =
                        when (responseCode) {
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
            } catch (ignored: Exception) {
            }
            val connHeader =
                try {
                    connection?.getHeaderField("Connection")
                } catch (_: Exception) {
                    null
                }
            // 若传输未成功，或对端明确要求 Connection: close，关闭并释放连接，防止污染 OkHttp 内部连接池
            if (!isSucceeded || connHeader?.equals("close", ignoreCase = true) == true) {
                try {
                    connection?.disconnect()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * 通知接收端转正或丢弃暂存文件。只携带 sessionId / fileId / token，不传任何路径：
     * 接收端按自己登记的临时句柄操作，伪造请求无法删除任意文件。
     * 该请求无法与上传响应合并（接收端要在发送方比对后才能知道结果），因此成功路径多一次往返。
     */
    private fun sendChecksumDecision(
        targetDevice: Device,
        sessionId: String,
        fileItem: FileItem,
        token: String,
        commit: Boolean,
        timeoutMs: Int,
    ): Result<Unit> {
        var connection: HttpURLConnection? = null
        val route = if (commit) LocalSendRoutes.CHECKSUM_COMMIT else LocalSendRoutes.CHECKSUM_REJECT
        return try {
            val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
            val encodedFileId = URLEncoder.encode(fileItem.id, "UTF-8")
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val url = "${targetDevice.url}$route?sessionId=$encodedSessionId&fileId=$encodedFileId&token=$encodedToken"
            FingerprintTrust.pin(targetDevice.fingerprint)
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        sslSocketFactory = FingerprintTrust.pinnedSslSocketFactory
                        hostnameVerifier = SslHelper.trustAllHostnameVerifier
                    }
                    requestMethod = "POST"
                    useCaches = false
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                }
            val code = connection.responseCode
            try {
                connection.inputStream?.use { stream ->
                    val drainBuf = ByteArray(4096)
                    while (stream.read(drainBuf) != -1) {}
                }
            } catch (ignored: Exception) {
            }
            if (code in 200..299) {
                Log.i(TAG, "sendChecksumDecision: peer ${if (commit) "committed" else "discarded"} '${fileItem.name}' (HTTP $code)")
                Result.success(Unit)
            } else {
                Log.w(TAG, "sendChecksumDecision: peer returned HTTP $code for '${fileItem.name}'")
                Result.failure(java.io.IOException("Checksum decision HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FingerprintTrust.unpin(targetDevice.fingerprint)
            runCatching { connection?.disconnect() }
        }
    }

    suspend fun cancelUpload(
        targetDevice: Device,
        sessionId: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
                val url = "${targetDevice.url}${LocalSendRoutes.CANCEL}?sessionId=$encodedSessionId"
                Log.i(TAG, "cancelUpload: sending cancel for session $sessionId to $url")
                FingerprintTrust.pin(targetDevice.fingerprint)
                try {
                    connection =
                        (URL(url).openConnection() as HttpURLConnection).apply {
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
                    } catch (ignored: Exception) {
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "cancelUpload failed: ${e.message}")
                Result.failure(e)
            }
        }

    /** 添加待发文件时后台预热 sha256；仅覆盖可声明门槛内的条目，实际声明值仍以发送前指纹校验为准。 */
    suspend fun precomputeSha256(
        files: List<FileItem>,
        shouldCancel: () -> Boolean = { false },
    ) = fillMissingSha256(files, shouldCancel)

    /** 补算缺失哈希的实例入口：统一握手与预热两条路径，compute 固定为本地实现。 */
    private suspend fun fillMissingSha256(
        files: List<FileItem>,
        shouldCancel: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        LocalSendClient.fillMissingSha256(
            files = files,
            compute = { computeSha256(it) },
            shouldCancel = shouldCancel,
            sourceStamp = { sourceStamp(it) },
        )
    }

    /** 源文件 (长度, 修改时间) 指纹；仅本地路径可读，URI 与文本返回 null（视为内容恒定）。 */
    private fun sourceStamp(fileItem: FileItem): String? {
        val path = fileItem.path ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return "${file.length()}:${file.lastModified()}"
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

    /** 计算文件/URI/文本内容的 sha256；数据源不可读时返回 null（协议中该字段可选）。 */
    private fun computeSha256(fileItem: FileItem): String? =
        try {
            openSourceStream(fileItem)?.let { Sha256.hexDigest(it) }
        } catch (e: Exception) {
            Log.w(TAG, "computeSha256: failed for '${fileItem.name}': ${e.message}")
            null
        }
}
