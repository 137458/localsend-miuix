package org.localsend.miuix.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent
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
import kotlinx.serialization.json.Json
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
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val onIncomingRequest: suspend (session: TransferSession) -> Boolean,
    private val onSessionUpdated: (TransferSession) -> Unit
) {
    private var engine: ApplicationEngine? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val activeSessions = ConcurrentHashMap<String, TransferSession>()
    private val sessionTokens = ConcurrentHashMap<String, MutableMap<String, String>>() // sessionId -> (fileId -> token)

    // 429 限流：按来源 IP 统计 prepare-upload 请求频率
    private val requestHits = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * 打开文件写入流。默认走公共 Download（MediaStore），用户自定义时走 SAF 目录树。
     * 返回目标流；MediaStore 路径写入完成后需调用 [confirmMediaStoreWrite] 清除 IS_PENDING 标记。
     */
    private fun openSaveStream(fileItem: FileItem, target: SaveTarget): OutputStream = when (target) {
        is SaveTarget.MediaStoreTarget -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IllegalStateException("MediaStore 保存需要 Android 10 (API 29) 及以上")
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val displayName = uniqueMediaName(fileItem.name)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, fileItem.mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/LocalSend"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("无法在公共下载目录中创建文件")
            // 记录实际写入的 Uri（文件名可能被系统自动加后缀），完成后用于清除 PENDING
            fileItem.mediaStoreUri = uri
            fileItem.name = displayName
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法打开公共下载目录中的文件")
        }
        is SaveTarget.UriTarget -> {
            val parent = DocumentFile.fromTreeUri(context, target.treeUri)
                ?: throw IllegalStateException("无法访问自定义保存目录")
            val existing = parent.listFiles().mapNotNull { it.name }.toHashSet()
            val uniqueName = uniqueTreeName(fileItem.name, existing)
            val created = parent.createFile(fileItem.mimeType, uniqueName)
                ?: throw IllegalStateException("无法在自定义保存目录中创建文件")
            fileItem.name = uniqueName
            context.contentResolver.openOutputStream(created.uri)
                ?: throw IllegalStateException("无法在自定义保存目录中创建文件")
        }
    }

    /** MediaStore 写入完成后清除 IS_PENDING，使文件在文件管理器中立即可见。 */
    private fun confirmMediaStoreWrite(fileItem: FileItem) {
        val uri = fileItem.mediaStoreUri ?: return
        try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        } catch (ignored: Exception) {}
    }

    /** 基于 MediaStore 公共下载目录的子目录列表生成不重复的文件名。 */
    private fun uniqueMediaName(name: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return name
        val existing = HashSet<String>()
        try {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/LocalSend/")
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (index != -1 && !cursor.isNull(index)) {
                            existing.add(cursor.getString(index))
                        }
                    }
                }
        } catch (ignored: Exception) {}
        if (name !in existing) return name
        val base = name.substringBeforeLast('.', "").ifEmpty { name }
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while ("$base ($counter)$ext" in existing) counter++
        return "$base ($counter)$ext"
    }

    private fun uniqueTreeName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val base = name.substringBeforeLast('.', "").ifEmpty { name }
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while ("$base ($counter)$ext" in existing) counter++
        return "$base ($counter)$ext"
    }

    /** 删除已写入的文件（用于 sha256 校验失败后的清理）。 */
    private fun deleteSavedFile(fileItem: FileItem, target: SaveTarget) {
        try {
            when (target) {
                is SaveTarget.MediaStoreTarget -> {
                    val uri = fileItem.mediaStoreUri ?: return
                    context.contentResolver.delete(uri, null, null)
                }
                is SaveTarget.UriTarget -> {
                    val parent = DocumentFile.fromTreeUri(context, target.treeUri)
                    parent?.findFile(fileItem.name)?.delete()
                }
            }
        } catch (ignored: Exception) {}
    }

    fun start() {
        if (engine != null) return
        val port = getPort()
        try {
            if (getUseHttps()) startHttps(port) else startHttp(port)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 纯 HTTP 模式：CIO 引擎直接监听指定端口。 */
    private fun startHttp(port: Int) {
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
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
        val environment = applicationEngineEnvironment {
            sslConnector(
                keyStore = keystore,
                keyAlias = TlsStore.KEY_ALIAS,
                keyStorePassword = { password },
                privateKeyPassword = { password }
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
            allowHeader("*")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
        }
    }

    fun stop() {
        try {
            engine?.stop(200, 500)
        } catch (ignored: Exception) {}
        engine = null
        activeSessions.clear()
        sessionTokens.clear()
        requestHits.clear()
    }

    /** 打开 Web Share 共享文件的源输入流（URI / 路径 / 文本内容）。 */
    private fun openShareStream(fileItem: FileItem): InputStream? = try {
        val text = fileItem.textContent
        when {
            fileItem.uri != null -> context.contentResolver.openInputStream(fileItem.uri)
            fileItem.path != null -> File(fileItem.path).inputStream()
            text != null -> text.byteInputStream(Charsets.UTF_8)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /** 简单的 HTML 转义，用于根页展示文件名，避免注入。 */
    private fun escapeHtml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun buildWebShareHtml(alias: String, session: ShareSession?): String {
        if (session == null || session.files.isEmpty()) {
            return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>LocalSend - Web Share</title>
                    <style>
                        :root { --bg: #f5f5f7; --card: #ffffff; --text: #1d1d1f; --text-sec: #86868b; --primary: #0071e3; }
                        @media (prefers-color-scheme: dark) { :root { --bg: #000000; --card: #1c1c1e; --text: #f5f5f7; --text-sec: #86868b; --primary: #2997ff; } }
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px 16px; display: flex; justify-content: center; }
                        .container { max-width: 600px; width: 100%; }
                        .card { background: var(--card); border-radius: 18px; padding: 32px 24px; text-align: center; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
                    </style>
                </head>
                <body>
                    <div class="container"><div class="card"><h2>当前没有正在共享的文件</h2><p style="color:var(--text-sec)">发送端已停止共享或未添加内容</p></div></div>
                </body>
                </html>
            """.trimIndent()
        }

        val textItems = session.files.filter { it.isTextMessage && !it.textContent.isNullOrEmpty() }
        val binaryFiles = session.files.filterNot { it.isTextMessage && !it.textContent.isNullOrEmpty() }

        val textSectionHtml = if (textItems.isNotEmpty()) {
            val textCards = textItems.joinToString("") { textItem ->
                val escapedText = escapeHtml(textItem.textContent ?: "")
                """
                <div class="text-card">
                    <pre class="text-content" id="text-${textItem.id}">$escapedText</pre>
                    <button class="btn btn-secondary" onclick="copyText('text-${textItem.id}')">📋 复制文本</button>
                </div>
                """.trimIndent()
            }
            """<h3 class="section-title">💬 共享文本</h3>$textCards"""
        } else ""

        val fileListHtml = if (binaryFiles.isNotEmpty()) {
            val rows = binaryFiles.joinToString("") { file ->
                val downloadUrl = "/api/localsend/v2/download?sessionId=${session.sessionId}&fileId=${file.id}"
                """
                <div class="file-row">
                    <div class="file-info">
                        <span class="file-name">${escapeHtml(file.name)}</span>
                        <span class="file-size">${file.formattedSize}</span>
                    </div>
                    <a class="btn btn-primary" href="$downloadUrl" download="${escapeHtml(file.name)}">⬇️ 下载</a>
                </div>
                """.trimIndent()
            }
            """<h3 class="section-title">📦 共享文件 (${binaryFiles.size})</h3><div class="file-list">$rows</div>"""
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${escapeHtml(alias)} 分享的内容 - LocalSend</title>
                <style>
                    :root { --bg: #f4f4f6; --card: #ffffff; --text: #1a1a1c; --text-sec: #808084; --primary: #007aff; --primary-hover: #0062cc; --btn-sec: #e5e5ea; --btn-sec-text: #1a1a1c; --border: #e5e5ea; }
                    @media (prefers-color-scheme: dark) { :root { --bg: #121214; --card: #1c1c1e; --text: #f2f2f7; --text-sec: #8e8e93; --primary: #0a84ff; --primary-hover: #0071e3; --btn-sec: #2c2c2e; --btn-sec-text: #f2f2f7; --border: #38383a; } }
                    * { box-sizing: border-box; }
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px 16px; display: flex; justify-content: center; }
                    .container { max-width: 580px; width: 100%; }
                    .header { text-align: center; margin-bottom: 24px; }
                    .header h1 { font-size: 22px; font-weight: 700; margin: 0 0 6px 0; }
                    .header p { font-size: 14px; color: var(--text-sec); margin: 0; }
                    .section-title { font-size: 15px; font-weight: 600; color: var(--text-sec); margin: 20px 8px 8px 8px; }
                    .text-card { background: var(--card); border-radius: 16px; padding: 16px; margin-bottom: 12px; border: 1px solid var(--border); }
                    .text-content { font-family: inherit; font-size: 15px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; margin: 0 0 12px 0; max-height: 240px; overflow-y: auto; }
                    .file-list { background: var(--card); border-radius: 16px; overflow: hidden; border: 1px solid var(--border); }
                    .file-row { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); }
                    .file-row:last-child { border-bottom: none; }
                    .file-info { display: flex; flex-direction: column; max-width: 70%; }
                    .file-name { font-size: 15px; font-weight: 500; word-break: break-all; }
                    .file-size { font-size: 12px; color: var(--text-sec); margin-top: 2px; }
                    .btn { display: inline-flex; align-items: center; justify-content: center; padding: 8px 16px; border-radius: 10px; font-size: 14px; font-weight: 600; text-decoration: none; border: none; cursor: pointer; transition: background 0.2s; }
                    .btn-primary { background: var(--primary); color: #fff; }
                    .btn-primary:hover { background: var(--primary-hover); }
                    .btn-secondary { background: var(--btn-sec); color: var(--btn-sec-text); width: 100%; }
                    .toast { position: fixed; bottom: 32px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.8); color: #fff; padding: 10px 20px; border-radius: 24px; font-size: 14px; opacity: 0; transition: opacity 0.3s; pointer-events: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📱 ${escapeHtml(alias)}</h1>
                        <p>通过局域网直接向您分享内容</p>
                    </div>
                    $textSectionHtml
                    $fileListHtml
                </div>
                <div id="toast" class="toast">已复制到剪贴板</div>
                <script>
                    function copyText(id) {
                        const el = document.getElementById(id);
                        if (!el) return;
                        navigator.clipboard.writeText(el.innerText).then(() => {
                            const toast = document.getElementById('toast');
                            toast.style.opacity = '1';
                            setTimeout(() => { toast.style.opacity = '0'; }, 2000);
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /** 校验 PIN：未配置 PIN 视为放行；配置后要求查询参数 ?pin= 精确匹配，否则回 401。 */
    private fun pinOk(pinFromRequest: String?): Boolean {
        val required = getPin()
        return required.isNullOrEmpty() || (required == pinFromRequest)
    }

    /** 429 限流：单 IP 在滑动窗口时间内 prepare-upload 请求过多时返回 true。 */
    private fun tooFrequent(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = 1_000L
        val maxHits = 10
        val list = requestHits.computeIfAbsent(ip) { mutableListOf() }
        synchronized(list) {
            list.removeAll { now - it > windowMs }
            if (list.size >= maxHits) return true
            list.add(now)
            return false
        }
    }

    private fun Application.configureRouting() {
        routing {

            // 协议 §5.1：Web Share 浏览器入口页，展示待共享文件并允许逐个下载与复制文本
            get("/") {
                val shares = getShares()
                val session = shares.firstOrNull()
                val html = buildWebShareHtml(getLocalDevice().alias, session)
                call.respondText(html, ContentType.Text.Html)
            }

            // 协议 §5.2：接收方请求文件元数据（支持 ?sessionId= 避免刷新后丢失会话）
            post("/api/localsend/v2/prepare-download") {
                if (!pinOk(call.request.queryParameters["pin"])) {
                    call.respond(HttpStatusCode.Unauthorized, "Request is unauthorized")
                    return@post
                }
                val remoteIp = call.request.origin.remoteHost
                if (tooFrequent(remoteIp)) {
                    call.respond(HttpStatusCode.TooManyRequests, "Too many requests")
                    return@post
                }
                val shares = getShares()
                if (shares.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, "No active share session")
                    return@post
                }
                val requestedSessionId = call.request.queryParameters["sessionId"]
                val session = shares.firstOrNull {
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
                        files = filesMap
                    )
                )
            }

            // 协议 §5.3：按 fileId 流式回传文件二进制
            get("/api/localsend/v2/download") {
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
                    "inline; filename=\"${file.name.replace("\"", "")}\""
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

            get("/api/localsend/v2/info") {
                call.respond(getLocalDevice().toDto())
            }

            get("/api/localsend/v1/info") {
                call.respond(getLocalDevice().toDto())
            }

            post("/api/localsend/v2/register") {
                val remoteDto = call.receive<DeviceDto>()
                val remoteIp = call.request.origin.remoteHost
                val remoteDevice = Device.fromDto(remoteDto, remoteIp)
                onDeviceDiscovered(remoteDevice)
                call.respond(getLocalDevice().toDto())
            }

            post("/api/localsend/v1/register") {
                val remoteDto = call.receive<DeviceDto>()
                val remoteIp = call.request.origin.remoteHost
                val remoteDevice = Device.fromDto(remoteDto, remoteIp)
                onDeviceDiscovered(remoteDevice)
                call.respond(getLocalDevice().toDto())
            }

            post("/api/localsend/v2/prepare-upload") {
                if (!pinOk(call.request.queryParameters["pin"])) {
                    call.respond(HttpStatusCode.Unauthorized, "Request is unauthorized")
                    return@post
                }
                val remoteIp = call.request.origin.remoteHost
                if (tooFrequent(remoteIp)) {
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
                val fileItems = request.files.values.map { dto ->
                    FileItem(
                        id = dto.id,
                        name = dto.fileName,
                        size = dto.size,
                        mimeType = dto.fileType,
                        textContent = dto.preview,
                        token = UUID.randomUUID().toString(),
                        expectedSha256 = dto.sha256,
                        status = TransferStatus.WaitingApproval
                    )
                }

                val totalBytes = fileItems.sumOf { it.size }
                val session = TransferSession(
                    sessionId = sessionId,
                    device = senderDevice,
                    isIncoming = true,
                    files = fileItems,
                    totalBytes = totalBytes,
                    status = TransferStatus.WaitingApproval
                )

                activeSessions[sessionId] = session
                onSessionUpdated(session)

                val accepted = if (isQuickSave()) {
                    true
                } else {
                    onIncomingRequest(session)
                }

                if (accepted) {
                    session.status = TransferStatus.InProgress
                    val tokenMap = mutableMapOf<String, String>()
                    fileItems.forEach { item ->
                        tokenMap[item.id] = item.token ?: item.id
                    }
                    sessionTokens[sessionId] = tokenMap
                    onSessionUpdated(session)

                    call.respond(
                        PrepareUploadResponseDto(
                            sessionId = sessionId,
                            files = tokenMap
                        )
                    )
                } else {
                    session.status = TransferStatus.Canceled
                    activeSessions.remove(sessionId)
                    sessionTokens.remove(sessionId)
                    onSessionUpdated(session)
                    call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Transfer declined by user"))
                }
            }

            post("/api/localsend/v2/upload") {
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
                // 403：令牌缺失/错误，或来源 IP 与会话所属设备不一致均拒绝
                if (token == null || expectedToken == null || token != expectedToken ||
                    call.request.origin.remoteHost != session.device.ip
                ) {
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

                val checksum = withContext(Dispatchers.IO) {
                    var digest: MessageDigest? = if (fileItem.expectedSha256 != null) {
                        MessageDigest.getInstance("SHA-256")
                    } else {
                        null
                    }
                    val textBuffer = if (fileItem.isTextMessage || fileItem.mimeType.startsWith("text/")) {
                        java.io.ByteArrayOutputStream()
                    } else null

                    openSaveStream(fileItem, saveTarget).buffered(128 * 1024).use { fos ->
                        val channel = call.receiveChannel()
                        val buffer = ByteArray(128 * 1024)
                        var lastTime = System.currentTimeMillis()
                        var bytesSinceLast = 0L

                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) break
                            fos.write(buffer, 0, read)
                            textBuffer?.write(buffer, 0, read)
                            digest?.update(buffer, 0, read)
                            fileItem.bytesTransferred += read
                            session.transferredBytes += read
                            bytesSinceLast += read

                            val now = System.currentTimeMillis()
                            val delta = now - lastTime
                            if (delta >= 500) {
                                val currentSpeed = (bytesSinceLast * 1000) / delta
                                fileItem.speed = currentSpeed
                                session.speed = currentSpeed
                                if (fileItem.size > 0) {
                                    fileItem.progress = (fileItem.bytesTransferred.toFloat() / fileItem.size).coerceIn(0f, 1f)
                                }
                                bytesSinceLast = 0
                                lastTime = now
                                onSessionUpdated(session)
                            }
                        }
                        fos.flush()
                    }
                    if (textBuffer != null && textBuffer.size() > 0) {
                        fileItem.textContent = textBuffer.toString(Charsets.UTF_8.name())
                    }
                    // MediaStore 路径：写入完成后清除 IS_PENDING，使文件立即可见
                    confirmMediaStoreWrite(fileItem)
                    digest?.digest()?.joinToString("") { "%02x".format(it) }
                }

                // 发送方声明了 sha256 且校验失败：删除已写入文件并按规范回 422
                if (fileItem.expectedSha256 != null && fileItem.expectedSha256 != checksum) {
                    deleteSavedFile(fileItem, saveTarget)
                    fileItem.status = TransferStatus.Failed
                    fileItem.progress = 0f
                    onSessionUpdated(session)
                    call.respond(HttpStatusCode.UnprocessableEntity, "CHECKSUM_MISMATCH")
                    return@post
                }

                fileItem.status = TransferStatus.Completed
                fileItem.progress = 1f
                fileItem.bytesTransferred = fileItem.size

                // Check if all files in session completed
                if (session.files.all { it.status == TransferStatus.Completed }) {
                    session.status = TransferStatus.Completed
                    session.endTime = System.currentTimeMillis()
                    activeSessions.remove(sessionId)
                    sessionTokens.remove(sessionId)
                }
                onSessionUpdated(session)

                call.respond(HttpStatusCode.OK, mapOf("message" to "File uploaded successfully"))
            }

            post("/api/localsend/v2/cancel") {
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
}
