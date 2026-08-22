package org.localsend.miuix.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
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
import org.localsend.miuix.model.PrepareUploadRequestDto
import org.localsend.miuix.model.PrepareUploadResponseDto
import org.localsend.miuix.model.SaveTarget
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSendServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getPort: () -> Int,
    private val getLocalDevice: () -> Device,
    private val isQuickSave: () -> Boolean,
    private val getSaveTarget: () -> SaveTarget,
    private val onDeviceDiscovered: (Device) -> Unit,
    private val onIncomingRequest: suspend (session: TransferSession) -> Boolean,
    private val onSessionUpdated: (TransferSession) -> Unit
) {
    private var engine: ApplicationEngine? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val activeSessions = ConcurrentHashMap<String, TransferSession>()
    private val sessionTokens = ConcurrentHashMap<String, MutableMap<String, String>>() // sessionId -> (fileId -> token)

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

    fun start() {
        if (engine != null) return
        val port = getPort()
        try {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(ContentNegotiation) {
                    json(this@LocalSendServer.json)
                }
                install(CORS) {
                    anyHost()
                    allowHeader("*")
                    allowMethod(io.ktor.http.HttpMethod.Get)
                    allowMethod(io.ktor.http.HttpMethod.Post)
                    allowMethod(io.ktor.http.HttpMethod.Options)
                }
                configureRouting()
            }.start(wait = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            engine?.stop(500, 1000)
        } catch (ignored: Exception) {}
        engine = null
        activeSessions.clear()
        sessionTokens.clear()
    }

    private fun Application.configureRouting() {
        routing {
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
                val request = call.receive<PrepareUploadRequestDto>()
                val remoteIp = call.request.origin.remoteHost
                val senderDevice = Device.fromDto(request.info, remoteIp)
                onDeviceDiscovered(senderDevice)

                val sessionId = UUID.randomUUID().toString()
                val fileItems = request.files.values.map { dto ->
                    FileItem(
                        id = dto.id,
                        name = dto.fileName,
                        size = dto.size,
                        mimeType = dto.fileType,
                        token = UUID.randomUUID().toString(),
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
                if (token != null && expectedToken != null && token != expectedToken) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid file token")
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

                withContext(Dispatchers.IO) {
                    openSaveStream(fileItem, saveTarget).use { fos ->
                        val channel = call.receiveChannel()
                        val buffer = ByteArray(64 * 1024)
                        var lastTime = System.currentTimeMillis()
                        var bytesSinceLast = 0L

                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) break
                            fos.write(buffer, 0, read)
                            fileItem.bytesTransferred += read
                            session.transferredBytes += read
                            bytesSinceLast += read

                            val now = System.currentTimeMillis()
                            val delta = now - lastTime
                            if (delta >= 500) {
                                val currentSpeed = (bytesSinceLast * 1000) / delta
                                fileItem.speed = currentSpeed
                                session.speed = currentSpeed
                                bytesSinceLast = 0
                                lastTime = now
                                onSessionUpdated(session)
                            }
                        }
                    }
                    // MediaStore 路径：写入完成后清除 IS_PENDING，使文件立即可见
                    confirmMediaStoreWrite(fileItem)
                }

                fileItem.status = TransferStatus.Completed
                fileItem.progress = 1f

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
