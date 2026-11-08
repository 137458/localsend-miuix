package org.localsend.miuix.network

import android.content.Context
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.localsend.miuix.R
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.ChecksumHeaders
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.core.ProtocolMessages
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.FileDto
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.PrepareDownloadResponseDto
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import org.localsend.miuix.model.SaveTarget
import org.localsend.miuix.model.ShareSession
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.TransferOutcome
import org.localsend.miuix.transfer.isReceivedSizeConsistent
import org.localsend.miuix.util.AcceptPolicy
import org.localsend.miuix.util.Sha256
import org.localsend.miuix.webshare.WebShareCopy
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class IncomingDecision(
    val accepted: Boolean,
    val selectedFileIds: Set<String>? = null,
) {
    companion object {
        val Rejected = IncomingDecision(false)
        val AcceptAll = IncomingDecision(true)

        fun accept(selectedIds: Set<String>?) = IncomingDecision(true, selectedIds)
    }
}

class LocalSendServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getPort: () -> Int,
    private val getLocalDevice: () -> Device,
    private val isQuickSave: () -> Boolean,
    private val isQuickSaveFavoritesOnly: () -> Boolean,
    private val isFavoriteDevice: (Device) -> Boolean,
    private val isWebShareAutoAccept: () -> Boolean,
    private val getSaveTarget: () -> SaveTarget,
    private val getPin: () -> String?,
    private val getUseHttps: () -> Boolean,
    private val getShares: () -> List<ShareSession>,
    private val onDeviceDiscovered: (Device) -> Unit,
    private val onIncomingRequest: suspend (session: TransferSession) -> IncomingDecision,
    private val onSessionUpdated: (TransferSession) -> Unit,
    private val onCancelReceived: (sessionId: String, remoteIp: String) -> Unit = { _, _ -> },
    private val getSaveTextAsFile: () -> Boolean = { false },
    private val getAutoCategorizeMedia: () -> Boolean = { false },
) {
    private var engine: ApplicationEngine? = null
    private val json = AppJson.default
    private val activeSessions = ConcurrentHashMap<String, TransferSession>()
    private val sessionTokens = ConcurrentHashMap<String, MutableMap<String, String>>() // sessionId -> (fileId -> token)
    private val downloadSessionIps = ConcurrentHashMap<String, String>() // Web Share 下载会话 -> 绑定来源 IP
    private val saveTargetWriter = SaveTargetWriter(context)
    private val rateLimiter = RequestRateLimiter()
    private val pendingUploads = PendingUploads()
    private val pendingSweepScope = CoroutineScope(Dispatchers.IO)
    private var pendingSweepJob: Job? = null

    /**
     * 清理过期、中断、已完成或处于终端状态的残留接收会话，防止 activeSessions 死锁导致后续传输报 409。
     */
    private fun cleanStaleSessions(incomingIp: String? = null) {
        val now = System.currentTimeMillis()
        val iterator = activeSessions.entries.iterator()
        while (iterator.hasNext()) {
            val (id, session) = iterator.next()
            val isTerminal =
                session.status == TransferStatus.Completed ||
                    session.status == TransferStatus.Failed ||
                    session.status == TransferStatus.Canceled
            val isStale =
                (now - session.lastActiveTime > 30_000L) ||
                    (session.endTime?.let { now - it > 5_000L } ?: false)
            val isSameSenderReconnecting =
                incomingIp != null &&
                    session.device.ip == incomingIp &&
                    (session.status == TransferStatus.WaitingApproval || session.status == TransferStatus.Failed || (session.status != TransferStatus.InProgress && now - session.startTime > 10_000L))

            if (isTerminal || isStale || isSameSenderReconnecting) {
                iterator.remove()
                sessionTokens.remove(id)
                // 会话被清理/需要重新连接时不留下未确认的临时文件
                pendingUploads.discardSession(id)
                if (session.status == TransferStatus.WaitingApproval || session.status == TransferStatus.InProgress) {
                    session.status = TransferStatus.Canceled
                    session.endTime = now
                    onSessionUpdated(session)
                }
            }
        }
    }

    /** 检查会话内所有文件是否均已到达终态（成功/失败/取消），全部终结时才结算会话状态并释放令牌。 */
    private fun checkSessionFinished(
        session: TransferSession,
        sessionId: String,
    ) {
        val allTerminal =
            session.files.all {
                it.status == TransferStatus.Completed ||
                    it.status == TransferStatus.Failed ||
                    it.status == TransferStatus.Canceled
            }
        if (allTerminal) {
            // 复用与发送端一致的聚合规则：已取消的会话保持取消；部分失败仍判完成，但携带失败说明供界面展示
            val outcome =
                TransferOutcome.aggregate(
                    files = session.files,
                    currentStatus = session.status,
                    allFailedMessage = context.getString(R.string.msg_all_files_failed),
                    partialFailedMessage = { failed, total ->
                        context.getString(R.string.msg_partial_files_failed, failed, total)
                    },
                )
            session.status = outcome.status
            session.errorMessage = outcome.errorMessage
            session.endTime = System.currentTimeMillis()
            activeSessions.remove(sessionId)
            sessionTokens.remove(sessionId)
        }
    }

    /** 单文件到达终态：统一落状态、收敛进度与错误信息，再结算会话并通知界面。 */
    private fun finishUploadFile(
        session: TransferSession,
        sessionId: String,
        fileItem: FileItem,
        status: TransferStatus,
        error: String?,
    ) {
        fileItem.status = status
        fileItem.error = error
        if (status == TransferStatus.Completed) {
            fileItem.progress = 1f
            fileItem.bytesTransferred = fileItem.size
        } else {
            fileItem.progress = 0f
        }
        checkSessionFinished(session, sessionId)
        onSessionUpdated(session)
    }

    /** 服务端真实监听端口；0 表示尚未启动（端口可能被占用而顺延，消费方必须用真实值而非请求值）。 */
    @Volatile
    private var boundPort: Int = 0

    fun getBoundPort(): Int = boundPort

    /**
     * 接收方主动取消传输：把会话置为终态。正在写入的上传循环会在下一个数据块处中止，
     * 半截文件由上传路由的取消分支负责删除，发送方收到明确的取消提示而非令牌错误。
     */
    fun cancelIncomingSession(sessionId: String) {
        val session = activeSessions[sessionId] ?: return
        if (session.status == TransferStatus.Canceled) return
        session.status = TransferStatus.Canceled
        session.endTime = System.currentTimeMillis()
        session.files.forEach { file ->
            if (file.status != TransferStatus.Completed) file.status = TransferStatus.Canceled
        }
        onSessionUpdated(session)
    }

    private val startLock = Any()

    @Volatile
    private var isStarting = false

    fun isRunning(): Boolean = engine != null

    fun ensureStarted() {
        if (engine == null && !isStarting) {
            start()
        }
    }

    private fun isPortAvailable(port: Int): Boolean =
        try {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress("0.0.0.0", port))
                true
            }
        } catch (e: Exception) {
            false
        }

    private fun findAvailablePort(preferredPort: Int): Int {
        if (isPortAvailable(preferredPort)) return preferredPort
        for (candidate in (preferredPort + 1)..(preferredPort + 20)) {
            if (isPortAvailable(candidate)) return candidate
        }
        return preferredPort
    }

    fun start() {
        synchronized(startLock) {
            if (engine != null || isStarting) return
            isStarting = true
        }
        try {
            val requestedPort = getPort()
            val portToUse = findAvailablePort(requestedPort)
            boundPort = portToUse
            // 上次进程被杀可能遗留待确认临时文件：启动时按专有命名规则与范围上限清理。
            // 在监听端口前同步执行，避免与随后开始的传输并发而误删正在写入的临时文件。
            runCatching { saveTargetWriter.sweepOrphanedTempFiles(getSaveTarget()) }
            try {
                if (getUseHttps()) startHttps(portToUse) else startHttp(portToUse)
            } catch (e: Exception) {
                Log.w(TAG, "start server on port $portToUse failed", e)
                // Retry with a fallback port if needed
                val fallbackPort = findAvailablePort(portToUse + 1)
                boundPort = fallbackPort
                try {
                    if (getUseHttps()) startHttps(fallbackPort) else startHttp(fallbackPort)
                } catch (retryEx: Exception) {
                    Log.e(TAG, "start server on fallback port $fallbackPort failed", retryEx)
                }
            }
            startPendingSweep()
        } finally {
            synchronized(startLock) {
                isStarting = false
            }
        }
    }

    /** 纯 HTTP 模式：CIO 引擎直接监听指定端口。 */
    private fun startHttp(port: Int) {
        engine =
            embeddedServer(
                factory = CIO,
                port = port,
                host = "0.0.0.0",
                configure = {
                    reuseAddress = true
                },
            ) {
                installCommon()
                configureRouting()
            }.start(wait = false)
    }

    /**
     * HTTPS 模式：CIO 服务端引擎不支持 TLS，需切换到 Netty 引擎并挂载自签名证书（sslConnector）。
     */
    private fun startHttps(port: Int) {
        val keystore = TlsStore.loadKeyStore(context)
        val password = TlsStore.STORE_PASSWORD.toCharArray()
        val environment =
            applicationEngineEnvironment {
                sslConnector(
                    keyStore = keystore,
                    keyAlias = TlsStore.KEY_ALIAS,
                    keyStorePassword = { password },
                    privateKeyPassword = { password },
                ) {
                    this.port = port
                }
                module {
                    installCommon()
                    configureRouting()
                }
            }
        engine = embeddedServer(Netty, environment).start(wait = false)
    }

    private fun Application.installCommon() {
        install(ContentNegotiation) {
            json(this@LocalSendServer.json)
        }
        install(CORS) {
            anyHost()
            allowHeaders { true }
            allowNonSimpleContentTypes = true
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentLength)
            allowHeader(HttpHeaders.ContentDisposition)
            exposeHeader(HttpHeaders.ContentDisposition)
        }
    }

    fun stop() {
        synchronized(startLock) {
            try {
                engine?.stop(200, 500)
            } catch (ignored: Exception) {
            }
            engine = null
            isStarting = false
            boundPort = 0
            pendingSweepJob?.cancel()
            pendingSweepJob = null
            // 服务停止时不留下任何未确认的临时文件
            pendingUploads.discardAll()
            activeSessions.clear()
            sessionTokens.clear()
            rateLimiter.clear()
            saveTargetWriter.clearAllocatedNames()
        }
    }

    /** 定时清理超过保鲜期仍未确认的临时文件，防止发送方崩溃/断网后留下错误内容。 */
    private fun startPendingSweep() {
        pendingSweepJob?.cancel()
        pendingSweepJob =
            pendingSweepScope.launch {
                while (isActive) {
                    delay(15_000L)
                    expirePendingUploads()
                }
            }
    }

    private fun expirePendingUploads() {
        val expired = pendingUploads.discardExpired()
        for (entry in expired) {
            val session = activeSessions[entry.sessionId] ?: continue
            val fileItem = session.files.firstOrNull { it.id == entry.fileId } ?: continue
            Log.w(TAG, "Pending upload expired without confirmation: '${fileItem.name}' (session=${entry.sessionId})")
            finishUploadFile(
                session,
                entry.sessionId,
                fileItem,
                TransferStatus.Failed,
                context.getString(R.string.msg_checksum_confirm_timeout),
            )
        }
    }

    /** 打开 Web Share 共享文件的源输入流（URI / 路径 / 文本内容）。 */
    private fun openShareStream(fileItem: FileItem): InputStream? =
        try {
            val text = fileItem.textContent
            when {
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
        } catch (e: Exception) {
            null
        }

    /** 校验 PIN：未配置 PIN 视为放行；配置后要求查询参数 ?pin= 精确匹配，否则回 401。 */
    private fun pinOk(pinFromRequest: String?): Boolean {
        val required = getPin()
        return required.isNullOrEmpty() || (required == pinFromRequest)
    }

    private fun Application.configureRouting() {
        routing {
            // 协议 §5.1：Web Share 浏览器入口页，展示待共享文件并允许逐个下载与复制文本
            get("/") {
                val copy = WebShareCopy.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
                val shares = getShares()
                val session = shares.firstOrNull()
                val html = WebShareHtmlRenderer.buildWebShareHtml(getLocalDevice().alias, session, copy)
                call.respondText(html, ContentType.Text.Html)
            }

            // 协议 §5.2：接收方请求文件元数据（支持 ?sessionId= 避免刷新后丢失会话）
            post(LocalSendRoutes.PREPARE_DOWNLOAD) {
                if (!pinOk(call.request.queryParameters["pin"])) {
                    call.respond(HttpStatusCode.Unauthorized, "Request is unauthorized")
                    return@post
                }
                val remoteIp = call.request.origin.remoteHost
                if (rateLimiter.tooFrequent(remoteIp)) {
                    call.respond(HttpStatusCode.TooManyRequests, "Too many requests")
                    return@post
                }
                val shares = getShares()
                if (shares.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, "No active share session")
                    return@post
                }
                val requestedSessionId = call.request.queryParameters["sessionId"]
                val session =
                    if (requestedSessionId == null) {
                        shares.first()
                    } else {
                        // 与上游一致：下载会话按来源 IP 绑定，仅同一 IP 可复用已有 sessionId
                        val boundIp = downloadSessionIps[requestedSessionId]
                        val matched = shares.firstOrNull { it.sessionId == requestedSessionId }
                        if (matched == null || (boundIp != null && !WebShareHtmlRenderer.isSameIp(remoteIp, boundIp))) {
                            call.respond(HttpStatusCode.Forbidden, "Invalid sessionId.")
                            return@post
                        }
                        matched
                    }
                downloadSessionIps[session.sessionId] = remoteIp
                val filesMap = session.files.associate { it.id to it.toDto() }
                call.respond(
                    PrepareDownloadResponseDto(
                        info = getLocalDevice().toDto(),
                        sessionId = session.sessionId,
                        files = filesMap,
                    ),
                )
            }

            // 协议 §5.3：按 fileId 流式回传文件二进制
            get(LocalSendRoutes.DOWNLOAD) {
                val sessionId = call.request.queryParameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing sessionId.")
                    return@get
                }
                val fileId = call.request.queryParameters["fileId"]
                if (fileId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing fileId.")
                    return@get
                }
                val remoteIp = call.request.origin.remoteHost
                val boundIp = downloadSessionIps[sessionId]
                val session = getShares().firstOrNull { it.sessionId == sessionId }
                // 与上游一致：无效会话/非绑定 IP 一律 403，不泄露会话内容
                if (session == null || boundIp == null || !WebShareHtmlRenderer.isSameIp(remoteIp, boundIp)) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid sessionId.")
                    return@get
                }
                val file = session.files.firstOrNull { it.id == fileId }
                if (file == null) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid fileId.")
                    return@get
                }
                val input = openShareStream(file)
                if (input == null) {
                    call.respond(HttpStatusCode.NotFound, "File source unavailable")
                    return@get
                }
                // 协议 §5.3：attachment + octet-stream，文件名百分号编码，浏览器按下载而非内联预览处理
                call.response.header(HttpHeaders.ContentDisposition, attachmentContentDisposition(file.name))
                call.response.header(HttpHeaders.ContentLength, file.size.toString())
                call.respondOutputStream(contentType = ContentType.Application.OctetStream, status = HttpStatusCode.OK) {
                    input.use { src ->
                        val buffer = ByteArray(128 * 1024)
                        var bytesRead: Int
                        while (src.read(buffer).also { bytesRead = it } != -1) {
                            write(buffer, 0, bytesRead)
                        }
                        flush()
                    }
                }
            }

            // Web Share 增强：多文件一键打包流式下载为 ZIP；与单文件下载一致，按来源 IP 绑定会话。
            get(LocalSendRoutes.DOWNLOAD_ZIP) {
                val sessionId = call.request.queryParameters["sessionId"]
                val remoteIp = call.request.origin.remoteHost
                val boundIp = sessionId?.let { downloadSessionIps[it] }
                val session = sessionId?.let { id -> getShares().firstOrNull { it.sessionId == id } }
                if (session == null || session.files.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, "No files found in share session")
                    return@get
                }
                if (boundIp == null || !WebShareHtmlRenderer.isSameIp(remoteIp, boundIp)) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid sessionId.")
                    return@get
                }
                val zipFileName = "LocalSend_${session.files.size}_Files.zip"
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"$zipFileName\"",
                )
                call.respondOutputStream(contentType = ContentType("application", "zip"), status = HttpStatusCode.OK) {
                    java.util.zip.ZipOutputStream(this).use { zipOut ->
                        val buffer = ByteArray(128 * 1024)
                        val addedNames = mutableSetOf<String>()
                        for (file in session.files) {
                            val input = openShareStream(file) ?: continue
                            var entryName = file.name
                            var counter = 1
                            while (entryName in addedNames) {
                                val base = file.name.substringBeforeLast('.', "").ifEmpty { file.name }
                                val ext = if (file.name.contains('.')) ".${file.name.substringAfterLast('.')}" else ""
                                entryName = "$base ($counter)$ext"
                                counter++
                            }
                            addedNames.add(entryName)
                            val zipEntry = java.util.zip.ZipEntry(entryName)
                            zipOut.putNextEntry(zipEntry)
                            input.use { src ->
                                var bytesRead: Int
                                while (src.read(buffer).also { bytesRead = it } != -1) {
                                    zipOut.write(buffer, 0, bytesRead)
                                }
                            }
                            zipOut.closeEntry()
                        }
                        zipOut.finish()
                    }
                }
            }

            get(LocalSendRoutes.INFO_V2) {
                call.respond(getLocalDevice().toDto())
            }

            get(LocalSendRoutes.INFO_V1) {
                call.respond(getLocalDevice().toDto())
            }

            // 已知局限：本机服务端不要求客户端证书，也不校验 register 声明的指纹，
            // 安全语义弱于上游（官方在 Web 未启用时强制 mTLS，并比对证书指纹）。
            // Ktor Netty 的 sslConnector 没有读取客户端证书的入口，补齐需要大改。
            post(LocalSendRoutes.REGISTER_V2) {
                val remoteDto = call.receive<DeviceDto>()
                val remoteIp = call.request.origin.remoteHost
                val remoteDevice = Device.fromDto(remoteDto, remoteIp)
                onDeviceDiscovered(remoteDevice)
                call.respond(getLocalDevice().toDto())
            }

            post(LocalSendRoutes.REGISTER_V1) {
                val remoteDto = call.receive<DeviceDto>()
                val remoteIp = call.request.origin.remoteHost
                val remoteDevice = Device.fromDto(remoteDto, remoteIp)
                onDeviceDiscovered(remoteDevice)
                call.respond(getLocalDevice().toDto())
            }

            post(LocalSendRoutes.PREPARE_UPLOAD) {
                if (!pinOk(call.request.queryParameters["pin"])) {
                    call.respond(HttpStatusCode.Unauthorized, "Request is unauthorized")
                    return@post
                }
                val remoteIp = call.request.origin.remoteHost
                cleanStaleSessions(remoteIp)
                if (rateLimiter.tooFrequent(remoteIp)) {
                    call.respond(HttpStatusCode.TooManyRequests, "Too many requests")
                    return@post
                }
                // 409：同时只允许一个接收会话，被其他进行中会话占用时拒绝
                if (activeSessions.isNotEmpty()) {
                    call.respond(HttpStatusCode.Conflict, "Blocked by another session")
                    return@post
                }
                val request = call.receive<PrepareUploadRequestDto>()
                if (request.files.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "No files provided")
                    return@post
                }
                val senderDevice = Device.fromDto(request.info, remoteIp)
                onDeviceDiscovered(senderDevice)

                val sessionId = UUID.randomUUID().toString()
                val fileItems =
                    request.files.values.map { dto ->
                        val isTextMessage = isInlineTextMessage(dto)
                        FileItem(
                            id = dto.id,
                            name = dto.fileName,
                            size = dto.size,
                            mimeType = dto.fileType,
                            textContent = if (isTextMessage) dto.preview else null,
                            token = UUID.randomUUID().toString(),
                            expectedSha256 = dto.sha256,
                            status = TransferStatus.WaitingApproval,
                            isTextMessage = isTextMessage,
                        )
                    }

                val totalBytes = fileItems.sumOf { it.size }
                val session =
                    TransferSession(
                        sessionId = sessionId,
                        device = senderDevice,
                        isIncoming = true,
                        files = fileItems,
                        totalBytes = totalBytes,
                        status = TransferStatus.WaitingApproval,
                    )

                activeSessions[sessionId] = session
                onSessionUpdated(session)

                val approval =
                    if (AcceptPolicy.shouldAutoAcceptWebShare(isWebShareAutoAccept(), senderDevice.deviceType) ||
                        AcceptPolicy.shouldAutoAccept(
                            quickSave = isQuickSave(),
                            favoritesOnly = isQuickSaveFavoritesOnly(),
                            isFavorite = isFavoriteDevice(senderDevice),
                        )
                    ) {
                        IncomingDecision.AcceptAll
                    } else {
                        onIncomingRequest(session)
                    }

                if (approval.accepted) {
                    val decision =
                        resolvePrepareUploadDecision(
                            files = fileItems,
                            saveTextAsFile = getSaveTextAsFile(),
                            allowedFileIds = approval.selectedFileIds,
                        )
                    session.totalBytes = calculateEffectiveTotalBytes(fileItems, approval.selectedFileIds)
                    if (decision.shouldRespondNoContent) {
                        session.status = TransferStatus.Completed
                        session.transferredBytes = session.totalBytes
                        session.endTime = System.currentTimeMillis()
                        activeSessions.remove(sessionId)
                        sessionTokens.remove(sessionId)
                        onSessionUpdated(session)
                        call.respond(HttpStatusCode.NoContent)
                        return@post
                    }

                    session.status = TransferStatus.InProgress
                    session.transferredBytes = fileItems.filter { it.status == TransferStatus.Completed }.sumOf { it.bytesTransferred }
                    sessionTokens[sessionId] = decision.tokenMap.toMutableMap()
                    onSessionUpdated(session)

                    call.respond(
                        PrepareUploadResponseDto(
                            sessionId = sessionId,
                            files = decision.tokenMap,
                        ),
                    )
                } else {
                    // 接收方主动取消与用户显式拒绝共用 403，但文案必须可区分：
                    // 发送方据 CANCELED_BY_RECEIVER 提示“对方已取消”，否则会被当成误导性的令牌/来源错误
                    val canceledByReceiver = session.status == TransferStatus.Canceled
                    session.status = TransferStatus.Canceled
                    session.endTime = System.currentTimeMillis()
                    activeSessions.remove(sessionId)
                    sessionTokens.remove(sessionId)
                    onSessionUpdated(session)
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("message" to prepareUploadRejectionMessage(canceledByReceiver)),
                    )
                }
            }

            post(LocalSendRoutes.UPLOAD) {
                val sessionId = call.request.queryParameters["sessionId"]
                val fileId = call.request.queryParameters["fileId"]
                val token = call.request.queryParameters["token"]

                if (sessionId == null || fileId == null || token == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                    return@post
                }

                val session = activeSessions[sessionId]
                val expectedToken = sessionTokens[sessionId]?.get(fileId)
                val requestIp = call.request.origin.remoteHost
                val isIpMatch = session != null && WebShareHtmlRenderer.isSameIp(requestIp, session.device.ip)
                val fileItem = session?.files?.firstOrNull { it.id == fileId }
                // 与上游一致：未知会话/令牌不匹配/来源 IP 不符/文件不属于会话统一 403，不泄露会话是否存在
                if (session == null || expectedToken == null || token != expectedToken || !isIpMatch || fileItem == null) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid token or IP address")
                    return@post
                }
                // 已完成/已取消的文件拒绝重放；失败或中断中的文件保留重试能力
                if (!isUploadFileAcceptable(fileItem.status)) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid token or IP address")
                    return@post
                }

                val saveTarget = getSaveTarget()
                // 发送方通过自定义扩展头请求校验；官方发送端不会发送，行为与原来一致。
                // "1" = 旧版发送方，只要回报摘要并立即转正；"2" = 新版，暂存待确认后再转正
                val checksumRequest = call.request.headers[ChecksumHeaders.REQUEST]
                val requestChecksumDigest =
                    checksumRequest == ChecksumHeaders.REQUEST_REPORT_ONLY ||
                        checksumRequest == ChecksumHeaders.REQUEST_DEFERRED
                val deferredCommit = checksumRequest == ChecksumHeaders.REQUEST_DEFERRED
                // 同一文件的上传重试先清掉上一轮的待确认临时文件，避免残留（此时新文件尚未写入）
                if (deferredCommit) pendingUploads.discardForFile(sessionId, fileId)

                fileItem.status = TransferStatus.InProgress
                fileItem.bytesTransferred = 0L

                try {
                    val checksum =
                        withContext(Dispatchers.IO) {
                            var digest: MessageDigest? =
                                if (fileItem.expectedSha256 != null || requestChecksumDigest) {
                                    MessageDigest.getInstance("SHA-256")
                                } else {
                                    null
                                }
                            val textBuffer =
                                if (fileItem.isTextMessage && fileItem.size <= org.localsend.miuix.model.MAX_INLINE_TEXT_SIZE) {
                                    java.io.ByteArrayOutputStream()
                                } else {
                                    null
                                }

                            // 待确认路径先落在临时位置；官方发送端不请求校验，仍按原逻辑立即转正
                            saveTargetWriter.openSaveStream(fileItem, saveTarget, deferCommit = deferredCommit) { getAutoCategorizeMedia() }.buffered(128 * 1024).use { fos ->
                                val channel = call.receiveChannel()
                                val buffer = ByteArray(128 * 1024)
                                var lastTime = System.currentTimeMillis()
                                var bytesSinceLast = 0L
                                var smoothedSpeed = 0L

                                while (!channel.isClosedForRead) {
                                    val read = channel.readAvailable(buffer, 0, buffer.size)
                                    if (read <= 0) break
                                    fos.write(buffer, 0, read)
                                    textBuffer?.write(buffer, 0, read)
                                    digest?.update(buffer, 0, read)
                                    fileItem.bytesTransferred += read
                                    session.transferredBytes = session.files.sumOf { it.bytesTransferred }
                                    bytesSinceLast += read

                                    val now = System.currentTimeMillis()
                                    session.lastActiveTime = now
                                    if (session.status == TransferStatus.Canceled) {
                                        throw kotlinx.coroutines.CancellationException("Transfer session canceled by receiver")
                                    }
                                    val delta = now - lastTime
                                    if (delta >= 64) {
                                        val instantSpeed = (bytesSinceLast * 1000) / delta
                                        smoothedSpeed = if (smoothedSpeed == 0L) instantSpeed else (smoothedSpeed * 3 + instantSpeed) / 4
                                        fileItem.speed = smoothedSpeed
                                        session.speed = smoothedSpeed
                                        if (fileItem.size > 0) {
                                            fileItem.progress = (fileItem.bytesTransferred.toFloat() / fileItem.size).coerceIn(0f, 1f)
                                        }
                                        bytesSinceLast = 0
                                        lastTime = now
                                        onSessionUpdated(session)
                                    }
                                }
                                fos.flush()
                                if (bytesSinceLast > 0 || fileItem.bytesTransferred == fileItem.size) {
                                    if (fileItem.size > 0) {
                                        fileItem.progress = (fileItem.bytesTransferred.toFloat() / fileItem.size).coerceIn(0f, 1f)
                                    }
                                    fileItem.speed = smoothedSpeed
                                    session.speed = smoothedSpeed
                                    onSessionUpdated(session)
                                }
                            }
                            if (textBuffer != null && textBuffer.size() > 0) {
                                fileItem.textContent = textBuffer.toString(Charsets.UTF_8.name())
                            }
                            // MediaStore 路径：写入完成后清除 IS_PENDING，使文件立即可见；待确认路径保留 pending
                            if (!deferredCommit) saveTargetWriter.confirmMediaStoreWrite(fileItem)
                            digest?.digest()?.let { Sha256.hex(it) }
                        }

                    // 声明了大小却与实际收到字节不一致：删除残文件并按请求错误处理
                    if (!isReceivedSizeConsistent(fileItem.size, fileItem.bytesTransferred)) {
                        saveTargetWriter.deleteSavedFile(fileItem, saveTarget)
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Failed, "SIZE_MISMATCH")
                        call.respond(HttpStatusCode.BadRequest, "SIZE_MISMATCH")
                        return@post
                    }

                    // 发送方声明了 sha256 且校验失败：删除已写入文件并按规范回 422
                    if (fileItem.expectedSha256 != null && fileItem.expectedSha256 != checksum) {
                        saveTargetWriter.deleteSavedFile(fileItem, saveTarget)
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Failed, "CHECKSUM_MISMATCH")
                        call.respond(HttpStatusCode.UnprocessableEntity, "CHECKSUM_MISMATCH")
                        return@post
                    }

                    if (deferredCommit) {
                        // 发送方要求回报摘要：文件暂存未转正，等发送方确认一致后再 commit，否则 reject 或保鲜期清理
                        val reportedSha256 = checksum ?: error("checksum digest unavailable")
                        pendingUploads.register(
                            PendingUploads.Entry(
                                sessionId = sessionId,
                                fileId = fileId,
                                token = token,
                                deviceIp = session.device.ip,
                                createdAt = System.currentTimeMillis(),
                                commitAction = { saveTargetWriter.finishDeferredWrite(fileItem, saveTarget) },
                                discardAction = { saveTargetWriter.deleteSavedFile(fileItem, saveTarget) },
                            ),
                        )
                        uploadSuccessHeaders(
                            httpVersion = call.request.origin.version,
                            reportedSha256 = reportedSha256,
                            deferredCommit = true,
                        ).forEach { (name, value) ->
                            call.response.header(name, value)
                        }
                        call.respond(HttpStatusCode.OK, mapOf("message" to "File uploaded, awaiting checksum confirmation"))
                        return@post
                    }

                    finishUploadFile(session, sessionId, fileItem, TransferStatus.Completed, null)

                    uploadSuccessHeaders(
                        httpVersion = call.request.origin.version,
                        // 旧版发送方（"1"）只要求回报摘要，仍需在响应头里给出实际摘要
                        reportedSha256 = checksum.takeIf { requestChecksumDigest },
                    ).forEach { (name, value) ->
                        call.response.header(name, value)
                    }
                    // 协议 §4.3：成功上传返回 200 空 body
                    call.respond(HttpStatusCode.OK)
                } catch (e: Throwable) {
                    pendingUploads.discardForFile(sessionId, fileId)
                    saveTargetWriter.deleteSavedFile(fileItem, saveTarget)
                    if (session.status == TransferStatus.Canceled) {
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Canceled, null)
                        runCatching {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                mapOf("message" to ProtocolMessages.CANCELED_BY_RECEIVER),
                            )
                        }
                    } else {
                        Log.w(TAG, "upload failed for '${fileItem.name}'", e)
                        val failureMessage = e.message ?: "Upload failed"
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Failed, failureMessage)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("message" to failureMessage))
                    }
                }
            }

            post(LocalSendRoutes.CANCEL) {
                val sessionId = call.request.queryParameters["sessionId"]
                val remoteIp = call.request.origin.remoteHost

                // 等待审批的会话：上游允许不带 sessionId，仅按来源 IP 匹配，取消应立即结束挂起的审批
                val pending = findPendingCancelTarget(activeSessions.values, sessionId, remoteIp)
                if (pending != null) {
                    activeSessions.remove(pending.sessionId)
                    sessionTokens.remove(pending.sessionId)
                    pendingUploads.discardSession(pending.sessionId)
                    pending.status = TransferStatus.Canceled
                    pending.endTime = System.currentTimeMillis()
                    pending.files.forEach { file ->
                        if (file.status != TransferStatus.Completed) file.status = TransferStatus.Canceled
                    }
                    onCancelReceived(pending.sessionId, remoteIp)
                    onSessionUpdated(pending)
                    call.respond(HttpStatusCode.OK)
                    return@post
                }

                val active = sessionId?.let { activeSessions[it] }
                if (active != null && WebShareHtmlRenderer.isSameIp(remoteIp, active.device.ip)) {
                    activeSessions.remove(active.sessionId)
                    sessionTokens.remove(active.sessionId)
                    pendingUploads.discardSession(active.sessionId)
                    active.status = TransferStatus.Canceled
                    active.endTime = System.currentTimeMillis()
                    active.files.forEach { file ->
                        if (file.status != TransferStatus.Completed) file.status = TransferStatus.Canceled
                    }
                    onSessionUpdated(active)
                } else if (sessionId != null) {
                    // 会话不在本机（对端取消其接收会话）：上游发 CancelReceived 事件，让应用停止对应的外发传输
                    onCancelReceived(sessionId, remoteIp)
                }
                call.respond(HttpStatusCode.OK)
            }

            // 自定义扩展：发送方确认摘要一致后转正 / 不一致时丢弃临时文件
            post(LocalSendRoutes.CHECKSUM_COMMIT) {
                resolveChecksumDecision(call, commit = true)
            }
            post(LocalSendRoutes.CHECKSUM_REJECT) {
                resolveChecksumDecision(call, commit = false)
            }
            // 自定义扩展：查询本次文件的确认结果，供发送方在确认请求超时时区分“确实失败”与“状态未知”
            get(LocalSendRoutes.CHECKSUM_STATUS) {
                resolveChecksumStatus(call)
            }
        }
    }

    /** 已鉴权的确认类请求作用域：会话已结束时 session/fileItem 为空，仅保留幂等结果。 */
    private class ChecksumRequestScope(
        val sessionId: String,
        val fileId: String,
        val token: String,
        val session: TransferSession?,
        val fileItem: FileItem?,
    )

    /**
     * 确认/丢弃/查询接口的统一鉴权：只接受 sessionId + fileId + token，并校验来源 IP。
     * 绝不接受路径参数，待清理的文件由服务端登记的临时句柄决定，避免伪造请求删除任意文件。
     * 会话已结束后仍凭原 token + 原设备 IP 放行，使确认结果可重放查询。
     */
    private suspend fun authorizeChecksumRequest(call: ApplicationCall): ChecksumRequestScope? {
        val sessionId = call.request.queryParameters["sessionId"]
        val fileId = call.request.queryParameters["fileId"]
        val token = call.request.queryParameters["token"]
        if (sessionId == null || fileId == null || token == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing sessionId, fileId or token")
            return null
        }

        val session = activeSessions[sessionId]
        if (session != null) {
            val expectedToken = sessionTokens[sessionId]?.get(fileId)
            val isIpMatch = WebShareHtmlRenderer.isSameIp(call.request.origin.remoteHost, session.device.ip)
            if (expectedToken == null || expectedToken != token || !isIpMatch) {
                call.respond(HttpStatusCode.Forbidden, "Invalid token or IP address")
                return null
            }
            val fileItem = session.files.firstOrNull { it.id == fileId }
            if (fileItem == null) {
                call.respond(HttpStatusCode.NotFound, "File metadata not found in session")
                return null
            }
            return ChecksumRequestScope(sessionId, fileId, token, session, fileItem)
        }

        val ownerIp = withContext(Dispatchers.IO) { pendingUploads.ownerIp(sessionId, fileId, token) }
        if (ownerIp == null) {
            call.respond(HttpStatusCode.NotFound, "Pending upload not found")
            return null
        }
        if (!WebShareHtmlRenderer.isSameIp(call.request.origin.remoteHost, ownerIp)) {
            call.respond(HttpStatusCode.Forbidden, "Invalid token or IP address")
            return null
        }
        return ChecksumRequestScope(sessionId, fileId, token, null, null)
    }

    private suspend fun resolveChecksumDecision(
        call: ApplicationCall,
        commit: Boolean,
    ) {
        val scope = authorizeChecksumRequest(call) ?: return

        if (commit) {
            val result = withContext(Dispatchers.IO) { pendingUploads.commit(scope.sessionId, scope.fileId, scope.token) }
            when (result.state) {
                CommitState.COMMITTED -> {
                    finishChecksumFile(scope, TransferStatus.Completed, null)
                    call.respond(HttpStatusCode.OK, mapOf("state" to "committed"))
                }

                CommitState.DISCARDED -> {
                    finishChecksumFile(
                        scope,
                        TransferStatus.Failed,
                        context.getString(R.string.msg_checksum_commit_failed_receiver),
                    )
                    call.respond(HttpStatusCode.InternalServerError, mapOf("state" to "discarded"))
                }

                CommitState.PENDING, null -> call.respond(HttpStatusCode.NotFound, "Pending upload not found")
            }
        } else {
            when (withContext(Dispatchers.IO) { pendingUploads.reject(scope.sessionId, scope.fileId, scope.token) }) {
                CommitState.DISCARDED -> {
                    // 发送方报告摘要不一致：接收端已丢弃临时文件，不留下内容错误的文件
                    finishChecksumFile(
                        scope,
                        TransferStatus.Failed,
                        context.getString(R.string.msg_checksum_discarded_mismatch),
                    )
                    call.respond(HttpStatusCode.OK, mapOf("state" to "discarded"))
                }

                CommitState.COMMITTED -> call.respond(HttpStatusCode.Conflict, "Already committed")
                CommitState.PENDING, null -> call.respond(HttpStatusCode.NotFound, "Pending upload not found")
            }
        }
    }

    private suspend fun resolveChecksumStatus(call: ApplicationCall) {
        val scope = authorizeChecksumRequest(call) ?: return
        val state = withContext(Dispatchers.IO) { pendingUploads.state(scope.sessionId, scope.fileId, scope.token) }
        val name = state?.name?.lowercase() ?: "unknown"
        call.response.header(ChecksumHeaders.STATE, name)
        call.respond(HttpStatusCode.OK, mapOf("state" to name))
    }

    /** 会话仍存在时把本次文件的确认结果落到界面状态；已终态的文件不重复结算，保证幂等重放无副作用。 */
    private fun finishChecksumFile(
        scope: ChecksumRequestScope,
        status: TransferStatus,
        error: String?,
    ) {
        val session = scope.session ?: return
        val fileItem = scope.fileItem ?: return
        if (fileItem.status == TransferStatus.Completed ||
            fileItem.status == TransferStatus.Failed ||
            fileItem.status == TransferStatus.Canceled
        ) {
            return
        }
        finishUploadFile(session, scope.sessionId, fileItem, status, error)
    }

    companion object {
        private const val TAG = "LocalSendServer"

        /** prepare-upload 被拒时的 403 文案：主动取消与显式拒绝必须可区分，发送方据此选择提示语义。 */
        internal fun prepareUploadRejectionMessage(canceledByReceiver: Boolean): String = if (canceledByReceiver) ProtocolMessages.CANCELED_BY_RECEIVER else ProtocolMessages.DECLINED_BY_USER

        val FORBIDDEN_HTTP2_HEADERS =
            setOf(
                "connection",
                "keep-alive",
                "proxy-connection",
                "transfer-encoding",
                "upgrade",
            )

        fun getUploadResponseHeaders(httpVersion: String?): Map<String, String> {
            val isHttp2 = httpVersion?.contains("2") == true
            return if (isHttp2) {
                emptyMap()
            } else {
                mapOf(HttpHeaders.Connection to "keep-alive")
            }
        }

        /**
         * 上传成功响应头：在原有 Connection 处理之上，仅在发送方请求回报时附带实际落盘摘要；
         * [deferredCommit] 为 true 时额外标记“文件暂存未转正”，发送方须再确认。
         * 这是加载式扩展，不改公开协议字段；不认识该响应头的发送方会直接忽略。
         */
        internal fun uploadSuccessHeaders(
            httpVersion: String?,
            reportedSha256: String?,
            deferredCommit: Boolean = false,
        ): Map<String, String> =
            getUploadResponseHeaders(httpVersion) +
                (if (reportedSha256 != null) mapOf(ChecksumHeaders.RESPONSE to reportedSha256) else emptyMap()) +
                (if (deferredCommit) mapOf(ChecksumHeaders.PENDING to "1") else emptyMap())

        data class PrepareUploadDecision(
            val shouldRespondNoContent: Boolean,
            val tokenMap: Map<String, String>,
            val isSessionCompletedImmediately: Boolean,
        )

        /**
         * 判定 FileDto 是否为完全内联于 preview 的纯文本消息。
         * 只有当 preview 字节数完全等于文件 declared size，且符合文本类型与大小约束时才为 true。
         */
        fun isInlineTextMessage(dto: org.localsend.miuix.model.FileDto): Boolean {
            val preview = dto.preview ?: return false
            // 规范写法为 text/* MIME；裸 "text" 是旧版客户端的枚举名，保留兼容
            val isTextMime =
                dto.fileType.equals("text", ignoreCase = true) ||
                    dto.fileType.startsWith("text/", ignoreCase = true)
            if (!isTextMime) return false
            val previewBytes = preview.toByteArray(Charsets.UTF_8).size.toLong()
            return previewBytes == dto.size && dto.size <= org.localsend.miuix.model.MAX_INLINE_TEXT_SIZE
        }

        fun calculateEffectiveTotalBytes(
            files: List<FileItem>,
            allowedFileIds: Set<String>? = null,
        ): Long =
            if (allowedFileIds != null) {
                files.filter { allowedFileIds.contains(it.id) }.sumOf { it.size }
            } else {
                files.sumOf { it.size }
            }

        /**
         * 依据 LocalSend 协议 §4.1 与配置，决定 prepare-upload 响应及各文件项的 Upload Token 与生命周期。
         */
        fun resolvePrepareUploadDecision(
            files: List<FileItem>,
            saveTextAsFile: Boolean,
            allowedFileIds: Set<String>? = null,
        ): PrepareUploadDecision {
            val effectiveFiles =
                if (allowedFileIds != null) {
                    files.filter { allowedFileIds.contains(it.id) }
                } else {
                    files
                }

            if (allowedFileIds != null) {
                files.filterNot { allowedFileIds.contains(it.id) }.forEach {
                    it.status = TransferStatus.Canceled
                }
            }

            val hasBinaryFiles = effectiveFiles.any { !it.isTextMessage }

            if (!saveTextAsFile && !hasBinaryFiles && effectiveFiles.isNotEmpty()) {
                // 场景 1：全为纯文本消息，且未开启保存为文件。
                // 按照 LocalSend 协议规范 §4.1 直接响应 204 No Content，不分发 token，本地直接标记为已完成
                effectiveFiles.forEach { item ->
                    item.status = TransferStatus.Completed
                    item.bytesTransferred = item.size
                    item.progress = 1f
                }
                return PrepareUploadDecision(
                    shouldRespondNoContent = true,
                    tokenMap = emptyMap(),
                    isSessionCompletedImmediately = true,
                )
            }

            // 场景 2：混合传输或开启了 saveTextAsFile
            val tokenMap = mutableMapOf<String, String>()
            effectiveFiles.forEach { item ->
                if (item.isTextMessage && !saveTextAsFile) {
                    // 混合传输中未开启保存的纯文本项：直接标记完成，不分配 token
                    item.status = TransferStatus.Completed
                    item.bytesTransferred = item.size
                    item.progress = 1f
                } else {
                    // 普通二进制文件或开启了保存为文件的纯文本项：分配 token 进行正常上传
                    tokenMap[item.id] = item.token ?: item.id
                }
            }

            return PrepareUploadDecision(
                shouldRespondNoContent = false,
                tokenMap = tokenMap,
                isSessionCompletedImmediately = false,
            )
        }
    }
}

/** 上传是否仍可写入：仅 Completed/Canceled 拒绝重放，Failed/InProgress 保留断线重试能力。 */
internal fun isUploadFileAcceptable(status: TransferStatus): Boolean = status != TransferStatus.Completed && status != TransferStatus.Canceled

/** pending（等待审批）取消匹配：来源 IP 必须一致；sessionId 为空时匹配该 IP 的任一 pending 会话。 */
internal fun findPendingCancelTarget(
    sessions: Collection<TransferSession>,
    sessionId: String?,
    remoteIp: String,
): TransferSession? =
    sessions.firstOrNull { session ->
        session.status == TransferStatus.WaitingApproval &&
            WebShareHtmlRenderer.isSameIp(remoteIp, session.device.ip) &&
            (sessionId == null || session.sessionId == sessionId)
    }

/** 协议 §5.3：attachment + 百分号编码文件名（空格编码为 %20，去掉引号与路径分隔符）。 */
internal fun attachmentContentDisposition(fileName: String): String {
    val sanitized = fileName.replace('/', '-').replace("\"", "")
    val encoded = URLEncoder.encode(sanitized, "UTF-8").replace("+", "%20")
    return "attachment; filename=\"$encoded\""
}
