package org.localsend.miuix.network

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
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSendServer(
    private val scope: CoroutineScope,
    private val getPort: () -> Int,
    private val getLocalDevice: () -> Device,
    private val isQuickSave: () -> Boolean,
    private val getDownloadDir: () -> File,
    private val onDeviceDiscovered: (Device) -> Unit,
    private val onIncomingRequest: suspend (session: TransferSession) -> Boolean,
    private val onSessionUpdated: (TransferSession) -> Unit
) {
    private var engine: ApplicationEngine? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val activeSessions = ConcurrentHashMap<String, TransferSession>()
    private val sessionTokens = ConcurrentHashMap<String, MutableMap<String, String>>() // sessionId -> (fileId -> token)

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

                val targetDir = getDownloadDir()
                if (!targetDir.exists()) targetDir.mkdirs()

                // Create destination file (avoid filename collision)
                var destFile = File(targetDir, fileItem.name)
                var counter = 1
                val baseName = destFile.nameWithoutExtension
                val ext = destFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                while (destFile.exists()) {
                    destFile = File(targetDir, "$baseName ($counter)$ext")
                    counter++
                }

                fileItem.status = TransferStatus.InProgress
                fileItem.bytesTransferred = 0L

                withContext(Dispatchers.IO) {
                    val channel = call.receiveChannel()
                    val buffer = ByteArray(64 * 1024)
                    var lastTime = System.currentTimeMillis()
                    var bytesSinceLast = 0L

                    FileOutputStream(destFile).use { fos ->
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
