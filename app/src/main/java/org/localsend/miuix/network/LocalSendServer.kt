package org.localsend.miuix.network

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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
import kotlinx.coroutines.withContext
import org.localsend.miuix.R
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.core.ProtocolMessages
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType
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
import org.localsend.miuix.webshare.WebShareCopy
import java.io.File
import java.io.InputStream
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
    private val getSaveTarget: () -> SaveTarget,
    private val getPin: () -> String?,
    private val getUseHttps: () -> Boolean,
    private val getShares: () -> List<ShareSession>,
    private val onDeviceDiscovered: (Device) -> Unit,
    private val onIncomingRequest: suspend (session: TransferSession) -> IncomingDecision,
    private val onSessionUpdated: (TransferSession) -> Unit,
    private val getSaveTextAsFile: () -> Boolean = { false },
    private val getAutoCategorizeMedia: () -> Boolean = { false },
) {
    private var engine: ApplicationEngine? = null
    private val json = AppJson.default
    private val activeSessions = ConcurrentHashMap<String, TransferSession>()
    private val sessionTokens = ConcurrentHashMap<String, MutableMap<String, String>>() // sessionId -> (fileId -> token)
    private val saveTargetWriter = SaveTargetWriter(context)
    private val rateLimiter = RequestRateLimiter()

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
            try {
                if (getUseHttps()) startHttps(portToUse) else startHttp(portToUse)
            } catch (e: Exception) {
                e.printStackTrace()
                // Retry with a fallback port if needed
                val fallbackPort = findAvailablePort(portToUse + 1)
                boundPort = fallbackPort
                try {
                    if (getUseHttps()) startHttps(fallbackPort) else startHttp(fallbackPort)
                } catch (retryEx: Exception) {
                    retryEx.printStackTrace()
                }
            }
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
            activeSessions.clear()
            sessionTokens.clear()
            rateLimiter.clear()
            saveTargetWriter.clearAllocatedNames()
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
                val rawIp = call.request.origin.remoteHost
                val remoteIp = rawIp.removePrefix("::ffff:").removePrefix("/").trim()
                val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: ""
                val copy = WebShareCopy.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
                val model = WebShareCopy.browserModel(userAgent, copy)
                val webDevice =
                    Device(
                        alias = copy.webAliasFor(remoteIp),
                        version = "2.1",
                        deviceModel = model,
                        deviceType = DeviceType.web,
                        fingerprint = "web-$remoteIp",
                        port = 0,
                        protocol = if (getUseHttps()) "https" else "http",
                        download = false,
                        ip = remoteIp,
                    )
                onDeviceDiscovered(webDevice)

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
                    shares.firstOrNull {
                        requestedSessionId == null || it.sessionId == requestedSessionId
                    } ?: run {
                        call.respond(HttpStatusCode.Forbidden, "Session not found")
                        return@post
                    }
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
                val fileId = call.request.queryParameters["fileId"]
                if (sessionId == null || fileId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing sessionId or fileId")
                    return@get
                }
                val session = getShares().firstOrNull { it.sessionId == sessionId }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@get
                }
                val file = session.files.firstOrNull { it.id == fileId }
                if (file == null) {
                    call.respond(HttpStatusCode.NotFound, "File not found in session")
                    return@get
                }
                val input = openShareStream(file)
                if (input == null) {
                    call.respond(HttpStatusCode.NotFound, "File source unavailable")
                    return@get
                }
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "inline; filename=\"${file.name.replace("\"", "")}\"",
                )
                val contentType = ContentType.parse(file.mimeType.ifEmpty { "application/octet-stream" })
                call.respondOutputStream(contentType = contentType, status = HttpStatusCode.OK) {
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

            // Web Share 增强：多文件一键打包流式下载为 ZIP
            get(LocalSendRoutes.DOWNLOAD_ZIP) {
                val sessionId = call.request.queryParameters["sessionId"]
                val session =
                    if (sessionId != null) {
                        getShares().firstOrNull { it.sessionId == sessionId }
                    } else {
                        getShares().firstOrNull()
                    }
                if (session == null || session.files.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, "No files found in share session")
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
                    call.respond(HttpStatusCode.BadRequest, "Invalid body")
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
                    if (isQuickSave()) {
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

                if (sessionId == null || fileId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing sessionId or fileId")
                    return@post
                }

                val session = activeSessions[sessionId]
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                val expectedToken = sessionTokens[sessionId]?.get(fileId)
                val requestIp = call.request.origin.remoteHost
                val isIpMatch = WebShareHtmlRenderer.isSameIp(requestIp, session.device.ip)
                // 403：令牌缺失/错误，或来源 IP 与会话所属设备不一致均拒绝
                if (token == null || expectedToken == null || token != expectedToken || !isIpMatch) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid token or IP address")
                    return@post
                }

                val fileItem = session.files.firstOrNull { it.id == fileId }
                if (fileItem == null) {
                    call.respond(HttpStatusCode.NotFound, "File metadata not found in session")
                    return@post
                }

                val saveTarget = getSaveTarget()

                fileItem.status = TransferStatus.InProgress
                fileItem.bytesTransferred = 0L

                try {
                    val checksum =
                        withContext(Dispatchers.IO) {
                            var digest: MessageDigest? =
                                if (fileItem.expectedSha256 != null) {
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

                            saveTargetWriter.openSaveStream(fileItem, saveTarget) { getAutoCategorizeMedia() }.buffered(128 * 1024).use { fos ->
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
                            // MediaStore 路径：写入完成后清除 IS_PENDING，使文件立即可见
                            saveTargetWriter.confirmMediaStoreWrite(fileItem)
                            digest?.digest()?.joinToString("") { "%02x".format(it) }
                        }

                    // 发送方声明了 sha256 且校验失败：删除已写入文件并按规范回 422
                    if (fileItem.expectedSha256 != null && fileItem.expectedSha256 != checksum) {
                        saveTargetWriter.deleteSavedFile(fileItem, saveTarget)
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Failed, "CHECKSUM_MISMATCH")
                        call.respond(HttpStatusCode.UnprocessableEntity, "CHECKSUM_MISMATCH")
                        return@post
                    }

                    finishUploadFile(session, sessionId, fileItem, TransferStatus.Completed, null)

                    getUploadResponseHeaders(call.request.origin.version).forEach { (name, value) ->
                        call.response.header(name, value)
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "File uploaded successfully"))
                } catch (e: Throwable) {
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
                        e.printStackTrace()
                        val failureMessage = e.message ?: "Upload failed"
                        finishUploadFile(session, sessionId, fileItem, TransferStatus.Failed, failureMessage)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("message" to failureMessage))
                    }
                }
            }

            post(LocalSendRoutes.CANCEL) {
                val sessionId = call.request.queryParameters["sessionId"]
                if (sessionId != null) {
                    val session = activeSessions.remove(sessionId)
                    if (session != null) {
                        session.status = TransferStatus.Canceled
                        session.endTime = System.currentTimeMillis()
                        onSessionUpdated(session)
                    }
                    sessionTokens.remove(sessionId)
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Session cancelled"))
            }
        }
    }

    companion object {
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
            val isTextMime =
                dto.fileType.equals("text", ignoreCase = true) ||
                    dto.fileType.equals("text/plain", ignoreCase = true)
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
