package org.localsend.miuix.manager

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.localsend.miuix.model.AppSettings
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.DiscoveryService
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.network.LocalSendServer
import org.localsend.miuix.network.NetworkUtils
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSendManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fingerprint = UUID.randomUUID().toString()

    private val defaultDownloadDir: File by lazy {
        val pubDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (pubDownload != null && (pubDownload.exists() || pubDownload.mkdirs())) {
            pubDownload
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
    }

    private val _settings = MutableStateFlow(
        AppSettings(
            downloadPath = defaultDownloadDir.absolutePath
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<Device>>(emptyList())
    val nearbyDevices: StateFlow<List<Device>> = _nearbyDevices.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    private val _pendingIncomingSession = MutableStateFlow<TransferSession?>(null)
    val pendingIncomingSession: StateFlow<TransferSession?> = _pendingIncomingSession.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val activeSessions: StateFlow<List<TransferSession>> = _activeSessions.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val transferHistory: StateFlow<List<TransferHistoryItem>> = _transferHistory.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val incomingApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val discoveryService = DiscoveryService(
        context = context,
        scope = scope,
        getLocalDevice = { getLocalDevice() },
        onDeviceDiscovered = { device ->
            scope.launch {
                _nearbyDevices.update { list ->
                    val existingIndex = list.indexOfFirst { it.fingerprint == device.fingerprint || (it.ip == device.ip && it.port == device.port) }
                    if (existingIndex >= 0) {
                        list.toMutableList().apply { set(existingIndex, device) }
                    } else {
                        list + device
                    }
                }
            }
        }
    )

    private val client = LocalSendClient(
        context = context,
        getLocalDevice = { getLocalDevice() }
    )

    private val server = LocalSendServer(
        scope = scope,
        getPort = { _settings.value.port },
        getLocalDevice = { getLocalDevice() },
        isQuickSave = { _settings.value.quickSave },
        getDownloadDir = {
            val path = _settings.value.downloadPath
            if (path.isNotEmpty()) {
                val f = File(path)
                if (!f.exists()) f.mkdirs()
                f
            } else defaultDownloadDir
        },
        onDeviceDiscovered = { device ->
            scope.launch {
                _nearbyDevices.update { list ->
                    val existingIndex = list.indexOfFirst { it.fingerprint == device.fingerprint || (it.ip == device.ip && it.port == device.port) }
                    if (existingIndex >= 0) {
                        list.toMutableList().apply { set(existingIndex, device) }
                    } else {
                        list + device
                    }
                }
            }
        },
        onIncomingRequest = { session ->
            val deferred = CompletableDeferred<Boolean>()
            incomingApprovalDeferreds[session.sessionId] = deferred
            _pendingIncomingSession.value = session
            val result = deferred.await()
            _pendingIncomingSession.value = null
            incomingApprovalDeferreds.remove(session.sessionId)
            result
        },
        onSessionUpdated = { session ->
            scope.launch {
                _activeSessions.update { list ->
                    val index = list.indexOfFirst { it.sessionId == session.sessionId }
                    if (index >= 0) {
                        list.toMutableList().apply { set(index, session) }
                    } else {
                        list + session
                    }
                }

                if (session.status == TransferStatus.Completed || session.status == TransferStatus.Failed || session.status == TransferStatus.Canceled) {
                    addHistory(
                        TransferHistoryItem(
                            deviceAlias = session.device.alias,
                            deviceIp = session.device.ip,
                            isIncoming = session.isIncoming,
                            fileCount = session.files.size,
                            totalSize = session.totalBytes,
                            status = session.status,
                            fileNames = session.files.map { it.name }
                        )
                    )
                }
            }
        }
    )

    fun start() {
        server.start()
        discoveryService.start()
    }

    fun stop() {
        discoveryService.stop()
        server.stop()
    }

    fun getLocalDevice(): Device {
        val primaryIp = NetworkUtils.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
        return Device(
            alias = _settings.value.alias,
            version = "2.1",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceType = DeviceType.mobile,
            fingerprint = fingerprint,
            port = _settings.value.port,
            protocol = if (_settings.value.useHttps) "https" else "http",
            download = true,
            ip = primaryIp
        )
    }

    fun addFiles(files: List<FileItem>) {
        _selectedFiles.update { it + files }
    }

    fun removeFile(id: String) {
        _selectedFiles.update { it.filterNot { item -> item.id == id } }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun refreshDevices() {
        discoveryService.sendAnnouncement()
        scanSubnet()
    }

    fun scanSubnet() {
        _isScanning.value = true
        discoveryService.scanSubnet { current, total ->
            if (current >= total) {
                _isScanning.value = false
            }
        }
    }

    fun acceptIncomingTransfer(sessionId: String) {
        incomingApprovalDeferreds[sessionId]?.complete(true)
    }

    fun declineIncomingTransfer(sessionId: String) {
        incomingApprovalDeferreds[sessionId]?.complete(false)
    }

    fun sendFilesTo(targetDevice: Device, filesToSend: List<FileItem> = _selectedFiles.value) {
        if (filesToSend.isEmpty()) return

        val sessionId = UUID.randomUUID().toString()
        val totalBytes = filesToSend.sumOf { it.size }
        val session = TransferSession(
            sessionId = sessionId,
            device = targetDevice,
            isIncoming = false,
            files = filesToSend,
            totalBytes = totalBytes,
            status = TransferStatus.InProgress
        )

        _activeSessions.update { it + session }

        scope.launch(Dispatchers.IO) {
            val prepResult = client.prepareUpload(targetDevice, filesToSend)
            if (prepResult.isFailure) {
                session.status = TransferStatus.Failed
                session.errorMessage = prepResult.exceptionOrNull()?.message ?: "Handshake failed"
                updateSessionState(session)
                return@launch
            }

            val responseDto = prepResult.getOrNull()!!
            val remoteSessionId = responseDto.sessionId
            val fileTokens = responseDto.files

            for (fileItem in filesToSend) {
                val token = fileTokens[fileItem.id] ?: fileItem.id
                fileItem.status = TransferStatus.InProgress

                val uploadResult = client.uploadFile(
                    targetDevice = targetDevice,
                    sessionId = remoteSessionId,
                    fileItem = fileItem,
                    token = token
                ) { bytesWritten, speed ->
                    fileItem.bytesTransferred = bytesWritten
                    fileItem.speed = speed
                    session.transferredBytes = filesToSend.sumOf { it.bytesTransferred }
                    session.speed = speed
                    updateSessionState(session)
                }

                if (uploadResult.isSuccess) {
                    fileItem.status = TransferStatus.Completed
                    fileItem.progress = 1f
                } else {
                    fileItem.status = TransferStatus.Failed
                    fileItem.error = uploadResult.exceptionOrNull()?.message
                    session.status = TransferStatus.Failed
                    session.errorMessage = fileItem.error
                    updateSessionState(session)
                    return@launch
                }
                updateSessionState(session)
            }

            session.status = TransferStatus.Completed
            session.endTime = System.currentTimeMillis()
            updateSessionState(session)
        }
    }

    private fun updateSessionState(session: TransferSession) {
        scope.launch {
            _activeSessions.update { list ->
                val index = list.indexOfFirst { it.sessionId == session.sessionId }
                if (index >= 0) {
                    list.toMutableList().apply { set(index, session) }
                } else {
                    list + session
                }
            }

            if (session.status == TransferStatus.Completed || session.status == TransferStatus.Failed || session.status == TransferStatus.Canceled) {
                addHistory(
                    TransferHistoryItem(
                        deviceAlias = session.device.alias,
                        deviceIp = session.device.ip,
                        isIncoming = session.isIncoming,
                        fileCount = session.files.size,
                        totalSize = session.totalBytes,
                        status = session.status,
                        fileNames = session.files.map { it.name }
                    )
                )
            }
        }
    }

    fun cancelTransfer(sessionId: String) {
        val session = _activeSessions.value.firstOrNull { it.sessionId == sessionId } ?: return
        session.status = TransferStatus.Canceled
        session.endTime = System.currentTimeMillis()
        updateSessionState(session)

        scope.launch(Dispatchers.IO) {
            if (!session.isIncoming) {
                client.cancelUpload(session.device, sessionId)
            }
        }
    }

    private fun addHistory(item: TransferHistoryItem) {
        _transferHistory.update { listOf(item) + it }
    }

    fun clearHistory() {
        _transferHistory.value = emptyList()
    }

    fun deleteHistoryItem(id: String) {
        _transferHistory.update { it.filterNot { item -> item.id == id } }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
    }
}
