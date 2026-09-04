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

    /**
     * 清理过期、中断、已完成或处于终端状态的残留接收会话，防止 activeSessions 死锁导致后续传输报 409。
     */
    private fun cleanStaleSessions(incomingIp: String? = null) {
        val now = System.currentTimeMillis()
        val iterator = activeSessions.entries.iterator()
        while (iterator.hasNext()) {
            val (id, session) = iterator.next()
            val isTerminal = session.status == TransferStatus.Completed ||
                session.status == TransferStatus.Failed ||
                session.status == TransferStatus.Canceled
            val isStale = (now - session.startTime > 120_000L && session.transferredBytes == 0L) ||
                (session.endTime?.let { now - it > 5_000L } ?: false)
            val isSameSenderReconnecting = incomingIp != null && session.device.ip == incomingIp &&
                (session.status == TransferStatus.WaitingApproval || session.status == TransferStatus.Failed || now - session.startTime > 10_000L)

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

    @Volatile
    private var boundPort: Int = 53317

    fun getBoundPort(): Int = boundPort

    private val startLock = Any()
    @Volatile
    private var isStarting = false

    fun isRunning(): Boolean = engine != null

    fun ensureStarted() {
        if (engine == null && !isStarting) {
            start()
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress("0.0.0.0", port))
                true
            }
        } catch (e: Exception) {
            false
        }
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
        engine = embeddedServer(
            factory = CIO,
            port = port,
            host = "0.0.0.0",
            configure = {
                reuseAddress = true
            }
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
            } catch (ignored: Exception) {}
            engine = null
            isStarting = false
            activeSessions.clear()
            sessionTokens.clear()
            requestHits.clear()
        }
    }

    /** 打开 Web Share 共享文件的源输入流（URI / 路径 / 文本内容）。 */
    private fun openShareStream(fileItem: FileItem): InputStream? = try {
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

    /** 简单的 HTML 转义，用于根页展示文件名，避免注入。 */
    private fun escapeHtml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun getWebFileSvgIcon(mimeType: String, fileName: String): String {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime == "application/vnd.android.package-archive" || lowerName.endsWith(".apk") || lowerName.endsWith(".xapk") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="8" width="16" height="12" rx="2"></rect><path d="M9 4v4"></path><path d="M15 4v4"></path><circle cx="9" cy="13" r="1" fill="currentColor"></circle><circle cx="15" cy="13" r="1" fill="currentColor"></circle></svg>"""

            lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") || lowerName.endsWith(".svg") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>"""

            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") || lowerName.endsWith(".avi") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"></rect><line x1="7" y1="2" x2="7" y2="22"></line><line x1="17" y1="2" x2="17" y2="22"></line><line x1="2" y1="12" x2="22" y2="12"></line><line x1="2" y1="7" x2="7" y2="7"></line><line x1="2" y1="17" x2="7" y2="17"></line><line x1="17" y1="17" x2="22" y2="17"></line><line x1="17" y1="7" x2="22" y2="7"></line></svg>"""

            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>"""

            lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("compressed") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path><line x1="12" y1="11" x2="12" y2="17"></line><line x1="9" y1="14" x2="15" y2="14"></line></svg>"""

            lowerMime.startsWith("text/") || lowerMime == "application/json" || lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") || lowerName.endsWith(".kt") || lowerName.endsWith(".java") || lowerName.endsWith(".py") || lowerName.endsWith(".js") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>"""

            else ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>"""
        }
    }

    private fun isSameIp(ip1: String, ip2: String): Boolean {
        if (ip1 == ip2) return true
        val clean1 = ip1.removePrefix("::ffff:").removePrefix("/").trim()
        val clean2 = ip2.removePrefix("::ffff:").removePrefix("/").trim()
        if (clean1 == clean2) return true
        val loopbacks = setOf("127.0.0.1", "::1", "localhost", "0:0:0:0:0:0:0:1")
        if (clean1 in loopbacks && clean2 in loopbacks) return true
        return false
    }

    private fun buildWebShareHtml(alias: String, session: ShareSession?): String {
        val hasSessionFiles = session != null && session.files.isNotEmpty()
        val textItems = session?.files?.filter { it.isTextMessage && !it.textContent.isNullOrEmpty() } ?: emptyList()
        val binaryFiles = session?.files?.filterNot { it.isTextMessage && !it.textContent.isNullOrEmpty() } ?: emptyList()

        val textSectionHtml = if (textItems.isNotEmpty()) {
            val textCards = textItems.joinToString("") { textItem ->
                val escapedText = escapeHtml(textItem.textContent ?: "")
                """
                <div class="text-card">
                    <pre class="text-content" id="text-${textItem.id}">$escapedText</pre>
                    <button class="btn btn-sec" onclick="copyText('text-${textItem.id}')">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                        复制文本
                    </button>
                </div>
                """.trimIndent()
            }
            """
            <div class="section-card">
                <div class="section-header">
                    <span class="section-title">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                        共享文本
                    </span>
                    <span class="section-tag">${textItems.size} 条</span>
                </div>
                $textCards
            </div>
            """.trimIndent()
        } else ""

        val fileListHtml = if (binaryFiles.isNotEmpty()) {
            val rows = binaryFiles.joinToString("") { file ->
                val downloadUrl = "/api/localsend/v2/download?sessionId=${session?.sessionId}&fileId=${file.id}"
                val iconSvg = getWebFileSvgIcon(file.mimeType, file.name)
                """
                <div class="file-item">
                    <div style="display:flex; align-items:center; gap:12px; max-width:70%;">
                        <div style="flex-shrink:0; display:flex; align-items:center;">
                            $iconSvg
                        </div>
                        <div class="file-details">
                            <span class="file-name">${escapeHtml(file.name)}</span>
                            <span class="file-meta">${file.formattedSize}</span>
                        </div>
                    </div>
                    <a class="btn btn-primary" href="$downloadUrl" download="${escapeHtml(file.name)}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                        下载
                    </a>
                </div>
                """.trimIndent()
            }
            """
            <div class="section-card">
                <div class="section-header">
                    <span class="section-title">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
                        共享文件
                    </span>
                    <span class="section-tag">${binaryFiles.size} 个</span>
                </div>
                <div class="file-list">$rows</div>
            </div>
            """.trimIndent()
        } else ""

        val noShareHint = if (!hasSessionFiles) {
            """
            <div class="empty-hint">
                <p>当前发送端未添加共享内容，但您可以直接向手机上传文件</p>
            </div>
            """.trimIndent()
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>${escapeHtml(alias)} - LocalSend 局域网快传</title>
                <style>
                    :root {
                        --bg: #f4f5f8;
                        --card-bg: #ffffff;
                        --text-main: #111827;
                        --text-sub: #6b7280;
                        --primary: #007aff;
                        --primary-hover: #0062cc;
                        --primary-light: rgba(0, 122, 255, 0.08);
                        --border: #e5e7eb;
                        --card-border: rgba(0, 0, 0, 0.06);
                        --success: #34c759;
                        --danger: #ff3b30;
                        --radius-lg: 20px;
                        --radius-md: 12px;
                        --radius-sm: 8px;
                        --shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg: #0e0f12;
                            --card-bg: #18191e;
                            --text-main: #f3f4f6;
                            --text-sub: #9ca3af;
                            --primary: #0a84ff;
                            --primary-hover: #0071e3;
                            --primary-light: rgba(10, 132, 255, 0.15);
                            --border: #262833;
                            --card-border: rgba(255, 255, 255, 0.08);
                            --shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
                        }
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
                        background: var(--bg);
                        color: var(--text-main);
                        min-height: 100vh;
                        padding: 24px 16px 48px 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container { max-width: 580px; width: 100%; display: flex; flex-direction: column; gap: 16px; }
                    .header-card {
                        background: var(--card-bg);
                        border-radius: var(--radius-lg);
                        padding: 24px 20px;
                        text-align: center;
                        border: 1px solid var(--card-border);
                        box-shadow: var(--shadow);
                    }
                    .device-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        padding: 4px 12px;
                        background: var(--primary-light);
                        color: var(--primary);
                        font-size: 13px;
                        font-weight: 600;
                        border-radius: 20px;
                        margin-bottom: 8px;
                    }
                    .header-title { font-size: 20px; font-weight: 700; color: var(--text-main); margin-bottom: 4px; }
                    .header-sub { font-size: 13px; color: var(--text-sub); }
                    .empty-hint { text-align: center; padding: 12px; font-size: 13px; color: var(--text-sub); }
                    .section-card {
                        background: var(--card-bg);
                        border-radius: var(--radius-lg);
                        padding: 18px;
                        border: 1px solid var(--card-border);
                        box-shadow: var(--shadow);
                    }
                    .section-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 12px;
                        padding: 0 4px;
                    }
                    .section-title {
                        font-size: 15px;
                        font-weight: 600;
                        color: var(--text-main);
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .section-tag {
                        font-size: 12px;
                        background: var(--primary-light);
                        color: var(--primary);
                        padding: 2px 8px;
                        border-radius: 10px;
                        font-weight: 500;
                    }
                    .text-card { margin-bottom: 10px; }
                    .text-card:last-child { margin-bottom: 0; }
                    .text-content {
                        background: var(--bg);
                        border: 1px solid var(--border);
                        border-radius: var(--radius-md);
                        padding: 12px;
                        font-family: inherit;
                        font-size: 14px;
                        line-height: 1.5;
                        white-space: pre-wrap;
                        word-break: break-word;
                        max-height: 180px;
                        overflow-y: auto;
                        margin-bottom: 8px;
                    }
                    .file-list { display: flex; flex-direction: column; gap: 8px; }
                    .file-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 12px 14px;
                        background: var(--bg);
                        border-radius: var(--radius-md);
                        border: 1px solid var(--border);
                    }
                    .file-details { display: flex; flex-direction: column; max-width: 70%; }
                    .file-name { font-size: 14px; font-weight: 500; word-break: break-all; color: var(--text-main); }
                    .file-meta { font-size: 12px; color: var(--text-sub); margin-top: 2px; }
                    .btn {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        padding: 8px 16px;
                        border-radius: var(--radius-sm);
                        font-size: 13px;
                        font-weight: 600;
                        text-decoration: none;
                        border: none;
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }
                    .btn-primary { background: var(--primary); color: #fff; }
                    .btn-primary:hover { background: var(--primary-hover); transform: translateY(-1px); }
                    .btn-sec { background: var(--bg); color: var(--text-main); border: 1px solid var(--border); width: 100%; padding: 8px; }
                    .btn-sec:hover { background: var(--border); }
                    .upload-dropzone {
                        border: 2px dashed var(--primary);
                        background: var(--primary-light);
                        border-radius: var(--radius-md);
                        padding: 26px 16px;
                        text-align: center;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        user-select: none;
                    }
                    .upload-dropzone:hover {
                        border-color: var(--primary-hover);
                        background: rgba(0, 122, 255, 0.14);
                    }
                    .dropzone-icon { display: flex; justify-content: center; margin-bottom: 8px; }
                    .dropzone-text { font-size: 14px; font-weight: 600; color: var(--primary); margin-bottom: 4px; }
                    .dropzone-hint { font-size: 12px; color: var(--text-sub); }
                    .upload-status-box {
                        display: none;
                        margin-top: 12px;
                        padding: 12px;
                        background: var(--bg);
                        border-radius: var(--radius-md);
                        border: 1px solid var(--border);
                    }
                    .progress-bar-wrap {
                        width: 100%;
                        height: 6px;
                        background: var(--border);
                        border-radius: 3px;
                        overflow: hidden;
                        margin: 8px 0;
                    }
                    .progress-bar {
                        height: 100%;
                        width: 0%;
                        background: var(--primary);
                        border-radius: 3px;
                        transition: width 0.2s ease;
                    }
                    .status-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 12px;
                        color: var(--text-sub);
                    }
                    .drag-overlay {
                        position: fixed;
                        top: 0; left: 0; right: 0; bottom: 0;
                        background: rgba(0, 122, 255, 0.92);
                        backdrop-filter: blur(12px);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        color: #fff;
                        z-index: 9999;
                        opacity: 0;
                        pointer-events: none;
                        transition: opacity 0.2s ease;
                    }
                    .drag-overlay.active { opacity: 1; pointer-events: all; }
                    .drag-overlay-icon { display: flex; justify-content: center; margin-bottom: 16px; }
                    .drag-overlay-title { font-size: 20px; font-weight: 700; }
                    .toast {
                        position: fixed;
                        bottom: 28px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: rgba(20, 20, 24, 0.92);
                        backdrop-filter: blur(12px);
                        color: #fff;
                        padding: 10px 20px;
                        border-radius: 30px;
                        font-size: 13px;
                        font-weight: 500;
                        box-shadow: 0 6px 20px rgba(0,0,0,0.25);
                        opacity: 0;
                        transition: opacity 0.25s ease;
                        pointer-events: none;
                        z-index: 10000;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header-card">
                        <div class="device-badge">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.55a11 11 0 0 1 14.08 0"></path><path d="M1.42 9a16 16 0 0 1 21.16 0"></path><path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path><line x1="12" y1="20" x2="12.01" y2="20"></line></svg>
                            局域网在线
                        </div>
                        <div class="header-title">${escapeHtml(alias)}</div>
                        <div class="header-sub">通过局域网高速安全传输，无需外网连接</div>
                    </div>
                    $noShareHint
                    $textSectionHtml
                    $fileListHtml
                    <div class="section-card">
                        <div class="section-header">
                            <span class="section-title">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                                上传文件到手机
                            </span>
                            <span class="section-tag">双向快传</span>
                        </div>
                        <div class="upload-dropzone" id="uploadDropzone">
                            <div class="dropzone-icon">
                                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                            </div>
                            <div class="dropzone-text">点击选择文件 或 拖拽文件到此处</div>
                            <div class="dropzone-hint">支持任意格式文件与多文件同时上传</div>
                        </div>
                        <input type="file" id="fileInput" multiple style="display:none">
                        <div class="upload-status-box" id="uploadStatusBox">
                            <div class="status-row">
                                <span id="uploadStatusTitle" style="font-weight:600; color:var(--text-main)">准备上传...</span>
                                <span id="uploadStatusPercent">0%</span>
                            </div>
                            <div class="progress-bar-wrap">
                                <div class="progress-bar" id="uploadProgressBar"></div>
                            </div>
                            <div class="status-row">
                                <span id="uploadDetail">等待中...</span>
                                <span id="uploadCount">0/0</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="drag-overlay" id="dragOverlay">
                    <div class="drag-overlay-icon">
                        <svg width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                    </div>
                    <div class="drag-overlay-title">松开鼠标即可上传至手机</div>
                </div>

                <div id="toast" class="toast">已复制到剪贴板</div>

                <script>
                    function showToast(msg) {
                        var t = document.getElementById('toast');
                        t.innerText = msg;
                        t.style.opacity = '1';
                        setTimeout(function() { t.style.opacity = '0'; }, 2000);
                    }

                    function copyText(id) {
                        var el = document.getElementById(id);
                        if (!el) return;
                        navigator.clipboard.writeText(el.innerText).then(function() {
                            showToast('已复制到剪贴板');
                        }).catch(function() {
                            showToast('复制失败，请手动选择复制');
                        });
                    }

                    function formatSize(bytes) {
                        if (bytes < 1024) return bytes + ' B';
                        var i = Math.floor(Math.log(bytes) / Math.log(1024));
                        var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
                        return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
                    }

                    var isUploading = false;
                    var dragDepth = 0;
                    var currentPin = null;

                    function getFingerprint() {
                        var fp = sessionStorage.getItem('localsend_web_fp');
                        if (!fp) {
                            fp = 'web-' + Math.random().toString(36).substring(2) + Date.now().toString(36);
                            sessionStorage.setItem('localsend_web_fp', fp);
                        }
                        return fp;
                    }

                    var dropzone = document.getElementById('uploadDropzone');
                    var fileInput = document.getElementById('fileInput');
                    var statusBox = document.getElementById('uploadStatusBox');
                    var statusTitle = document.getElementById('uploadStatusTitle');
                    var statusPercent = document.getElementById('uploadStatusPercent');
                    var progressBar = document.getElementById('uploadProgressBar');
                    var detailText = document.getElementById('uploadDetail');
                    var countText = document.getElementById('uploadCount');
                    var overlay = document.getElementById('dragOverlay');

                    dropzone.onclick = function() {
                        if (!isUploading) fileInput.click();
                    };

                    fileInput.onchange = function() {
                        if (fileInput.files && fileInput.files.length > 0) {
                            startUpload(fileInput.files);
                            fileInput.value = '';
                        }
                    };

                    window.addEventListener('dragenter', function(e) {
                        if (!e.dataTransfer || !e.dataTransfer.types || e.dataTransfer.types.indexOf('Files') === -1) return;
                        e.preventDefault();
                        dragDepth++;
                        if (!isUploading) overlay.classList.add('active');
                    });

                    window.addEventListener('dragover', function(e) {
                        if (!e.dataTransfer || !e.dataTransfer.types || e.dataTransfer.types.indexOf('Files') === -1) return;
                        e.preventDefault();
                        if (isUploading) e.dataTransfer.dropEffect = 'none';
                    });

                    window.addEventListener('dragleave', function(e) {
                        dragDepth--;
                        if (dragDepth <= 0) {
                            dragDepth = 0;
                            overlay.classList.remove('active');
                        }
                    });

                    window.addEventListener('drop', function(e) {
                        e.preventDefault();
                        dragDepth = 0;
                        overlay.classList.remove('active');
                        if (isUploading) return;
                        var dt = e.dataTransfer;
                        var files = [];
                        if (dt && dt.items) {
                            for (var i = 0; i < dt.items.length; i++) {
                                var item = dt.items[i];
                                if (item.webkitGetAsEntry && item.webkitGetAsEntry().isDirectory) continue;
                                var f = item.getAsFile();
                                if (f) files.push(f);
                            }
                        } else if (dt && dt.files) {
                            for (var j = 0; j < dt.files.length; j++) files.push(dt.files[j]);
                        }
                        if (files.length > 0) startUpload(files);
                    });

                    function startUpload(fileList) {
                        isUploading = true;
                        statusBox.style.display = 'block';
                        statusTitle.innerText = '正在等待手机端确认...';
                        statusTitle.style.color = 'var(--text-main)';
                        statusPercent.innerText = '0%';
                        progressBar.style.width = '0%';
                        detailText.innerText = '请在手机上点击同意接收';
                        countText.innerText = '0/' + fileList.length;

                        var filesMap = {};
                        var fileBlobs = {};
                        for (var i = 0; i < fileList.length; i++) {
                            var f = fileList[i];
                            var id = 'web-f-' + i + '-' + Date.now();
                            filesMap[id] = {
                                id: id,
                                fileName: f.name,
                                size: f.size,
                                fileType: f.type || 'application/octet-stream'
                            };
                            fileBlobs[id] = f;
                        }

                        var requestBody = {
                            info: {
                                alias: '浏览器 Web 端',
                                version: '2.1',
                                deviceModel: navigator.userAgent.indexOf('Mac') !== -1 ? 'Mac Browser' : (navigator.userAgent.indexOf('Windows') !== -1 ? 'PC Browser' : 'Web Client'),
                                deviceType: 'web',
                                fingerprint: getFingerprint(),
                                port: 0,
                                protocol: location.protocol.replace(':', ''),
                                download: false
                            },
                            files: filesMap
                        };

                        executePrepare(requestBody, fileBlobs, fileList.length, true);
                    }

                    function executePrepare(reqBody, fileBlobs, totalCount, isFirst) {
                        var url = '/api/localsend/v2/prepare-upload';
                        if (currentPin) url += '?pin=' + encodeURIComponent(currentPin);

                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', url, true);
                        xhr.setRequestHeader('Content-Type', 'application/json');
                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                try {
                                    var res = JSON.parse(xhr.responseText);
                                    uploadAllFiles(res.sessionId, res.files, fileBlobs, totalCount);
                                } catch(e) {
                                    finishError('解析响应失败');
                                }
                            } else if (xhr.status === 401) {
                                var pinPrompt = prompt(isFirst ? '该设备启用了 PIN 码保护，请输入 PIN 码：' : 'PIN 码错误，请重新输入：');
                                if (!pinPrompt) {
                                    finishError('未提供 PIN 码，上传已终止');
                                    return;
                                }
                                currentPin = pinPrompt;
                                executePrepare(reqBody, fileBlobs, totalCount, false);
                            } else if (xhr.status === 403) {
                                finishError('手机端拒绝了此次接收请求');
                            } else if (xhr.status === 409) {
                                finishError('手机端正在处理其他传输，请稍后再试');
                            } else if (xhr.status === 429) {
                                finishError('请求过于频繁，请稍后再试');
                            } else {
                                finishError('上传握手失败 (HTTP ' + xhr.status + ')');
                            }
                        };
                        xhr.onerror = function() {
                            finishError('网络连接失败，请检查局域网连接');
                        };
                        xhr.send(JSON.stringify(reqBody));
                    }

                    function uploadAllFiles(sessionId, tokens, fileBlobs, totalCount) {
                        var fileIds = Object.keys(tokens);
                        var completed = 0;

                        function uploadNext(index) {
                            if (index >= fileIds.length) {
                                statusTitle.innerText = '上传完成';
                                statusTitle.style.color = 'var(--success)';
                                statusPercent.innerText = '100%';
                                progressBar.style.width = '100%';
                                detailText.innerText = '所有文件已成功保存到手机';
                                countText.innerText = totalCount + '/' + totalCount;
                                showToast('所有文件已成功传输至手机');
                                setTimeout(function() {
                                    isUploading = false;
                                    statusBox.style.display = 'none';
                                }, 3500);
                                return;
                            }

                            var fId = fileIds[index];
                            var blob = fileBlobs[fId];
                            var token = tokens[fId];
                            statusTitle.innerText = '正在上传: ' + blob.name;
                            countText.innerText = (index + 1) + '/' + totalCount;

                            var uploadUrl = '/api/localsend/v2/upload?sessionId=' + encodeURIComponent(sessionId) +
                                '&fileId=' + encodeURIComponent(fId) +
                                '&token=' + encodeURIComponent(token);

                            var xhr = new XMLHttpRequest();
                            xhr.open('POST', uploadUrl, true);
                            xhr.setRequestHeader('Content-Type', 'application/octet-stream');

                            xhr.upload.onprogress = function(e) {
                                if (e.lengthComputable) {
                                    var percent = Math.round((e.loaded / e.total) * 100);
                                    statusPercent.innerText = percent + '%';
                                    progressBar.style.width = percent + '%';
                                    detailText.innerText = formatSize(e.loaded) + ' / ' + formatSize(e.total);
                                }
                            };

                            xhr.onload = function() {
                                if (xhr.status === 200 || xhr.status === 204) {
                                    completed++;
                                    uploadNext(index + 1);
                                } else {
                                    finishError('上传文件 ' + blob.name + ' 失败 (HTTP ' + xhr.status + ')');
                                }
                            };

                            xhr.onerror = function() {
                                finishError('上传文件 ' + blob.name + ' 时网络中断');
                            };

                            xhr.send(blob);
                        }

                        uploadNext(0);
                    }

                    function finishError(msg) {
                        isUploading = false;
                        statusTitle.innerText = '上传失败';
                        statusTitle.style.color = 'var(--danger)';
                        detailText.innerText = msg;
                        showToast(msg);
                    }

                    function registerBrowserDevice() {
                        var fp = getFingerprint();
                        var model = 'Web 浏览器';
                        var ua = navigator.userAgent;
                        if (ua.indexOf('Mac') !== -1) model = 'Mac 浏览器';
                        else if (ua.indexOf('Windows') !== -1) model = 'PC 浏览器';
                        else if (ua.indexOf('iPhone') !== -1) model = 'iPhone 浏览器';
                        else if (ua.indexOf('iPad') !== -1) model = 'iPad 浏览器';
                        else if (ua.indexOf('Android') !== -1) model = 'Android 浏览器';

                        var reqBody = {
                            alias: '浏览器 Web 端',
                            version: '2.1',
                            deviceModel: model,
                            deviceType: 'web',
                            fingerprint: fp,
                            port: 0,
                            protocol: location.protocol.replace(':', ''),
                            download: false
                        };
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/localsend/v2/register', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');
                        xhr.send(JSON.stringify(reqBody));
                    }
                    try { registerBrowserDevice(); } catch(e) {}
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
                val rawIp = call.request.origin.remoteHost
                val remoteIp = rawIp.removePrefix("::ffff:").removePrefix("/").trim()
                val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: ""
                val model = when {
                    userAgent.contains("Macintosh") || userAgent.contains("Mac OS") -> "Mac 浏览器"
                    userAgent.contains("Windows") -> "PC 浏览器"
                    userAgent.contains("iPhone") -> "iPhone 浏览器"
                    userAgent.contains("iPad") -> "iPad 浏览器"
                    userAgent.contains("Android") -> "Android 浏览器"
                    userAgent.contains("Linux") -> "Linux 浏览器"
                    else -> "Web 浏览器"
                }
                val webDevice = Device(
                    alias = "浏览器 Web 端 ($remoteIp)",
                    version = "2.1",
                    deviceModel = model,
                    deviceType = DeviceType.web,
                    fingerprint = "web-$remoteIp",
                    port = 0,
                    protocol = if (getUseHttps()) "https" else "http",
                    download = false,
                    ip = remoteIp
                )
                onDeviceDiscovered(webDevice)

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
                cleanStaleSessions(remoteIp)
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

                val isTextSession = fileItems.all { it.isTextMessage || it.mimeType == "text/plain" || it.mimeType == "text" }
                val accepted = if (isQuickSave() && !isTextSession) {
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
                    session.endTime = System.currentTimeMillis()
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
                val requestIp = call.request.origin.remoteHost
                val isIpMatch = isSameIp(requestIp, session.device.ip)
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
                        session.status = TransferStatus.Failed
                        session.endTime = System.currentTimeMillis()
                        activeSessions.remove(sessionId)
                        sessionTokens.remove(sessionId)
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
                } catch (e: Throwable) {
                    e.printStackTrace()
                    deleteSavedFile(fileItem, saveTarget)
                    fileItem.status = TransferStatus.Failed
                    fileItem.progress = 0f
                    session.status = TransferStatus.Failed
                    session.endTime = System.currentTimeMillis()
                    activeSessions.remove(sessionId)
                    sessionTokens.remove(sessionId)
                    onSessionUpdated(session)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("message" to (e.message ?: "Upload failed")))
                }
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
