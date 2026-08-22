package org.localsend.miuix.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import org.localsend.miuix.model.SaveTarget
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

    // 应用私有外部 Downloads 目录：免存储权限、所有 API 级别均可自由读写，
    // 规避了作用域存储下直接写公共 Download 被拒导致"无法接收文件"的问题。
    private val defaultDownloadDir: File by lazy {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val prefs = context.getSharedPreferences("localsend_settings", Context.MODE_PRIVATE)

    // 从持久化恢复设置；别名首次生成后即固化，避免冷启动每次都随机更换。
    private val initialAlias = prefs.getString(KEY_ALIAS, null) ?: run {
        val generated = AppSettings.generateDefaultAlias()
        prefs.edit().putString(KEY_ALIAS, generated).apply()
        generated
    }

    private val _settings = MutableStateFlow(
        AppSettings(
            alias = initialAlias,
            port = prefs.getInt(KEY_PORT, 53317),
            quickSave = prefs.getBoolean(KEY_QUICK_SAVE, false),
            useHttps = prefs.getBoolean(KEY_USE_HTTPS, false),
            themeModeIndex = prefs.getInt(KEY_THEME, 0),
            downloadTreeUri = prefs.getString(KEY_TREE_URI, null),
            downloadDisplay = prefs.getString(KEY_DOWNLOAD_DISPLAY, null),
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
        context = context,
        scope = scope,
        getPort = { _settings.value.port },
        getLocalDevice = { getLocalDevice() },
        isQuickSave = { _settings.value.quickSave },
        getSaveTarget = { getSaveTarget() },
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
        val updated = transform(_settings.value)
        _settings.value = updated
        persistSettings(updated)
    }

    /** 计算当前接收保存目标：优先使用用户通过 SAF 选择的自定义目录，否则使用默认目录。 */
    fun getSaveTarget(): SaveTarget {
        val tree = _settings.value.downloadTreeUri
        if (!tree.isNullOrEmpty()) {
            try {
                return SaveTarget.UriTarget(Uri.parse(tree))
            } catch (ignored: Exception) {}
        }
        return SaveTarget.FileTarget(defaultDownloadDir)
    }

    /** 用户通过 SAF 选择自定义保存目录后调用，持久化其访问权限与显示信息。 */
    fun setDownloadTree(uri: Uri, display: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (ignored: Exception) {}
        updateSettings { it.copy(downloadTreeUri = uri.toString(), downloadDisplay = display, downloadPath = display) }
    }

    /** 修改服务端口并重启服务使新端口立即生效。 */
    fun applyPortChange(newPort: Int) {
        if (newPort == _settings.value.port) return
        updateSettings { it.copy(port = newPort) }
        scope.launch {
            server.stop()
            server.start()
        }
    }

    private fun persistSettings(s: AppSettings) {
        prefs.edit()
            .putString(KEY_ALIAS, s.alias)
            .putInt(KEY_PORT, s.port)
            .putString(KEY_TREE_URI, s.downloadTreeUri)
            .putString(KEY_DOWNLOAD_DISPLAY, s.downloadDisplay)
            .putBoolean(KEY_QUICK_SAVE, s.quickSave)
            .putBoolean(KEY_USE_HTTPS, s.useHttps)
            .putInt(KEY_THEME, s.themeModeIndex)
            .apply()
    }

    companion object {
        private const val KEY_ALIAS = "alias"
        private const val KEY_PORT = "port"
        private const val KEY_QUICK_SAVE = "quick_save"
        private const val KEY_USE_HTTPS = "use_https"
        private const val KEY_THEME = "theme_mode_index"
        private const val KEY_TREE_URI = "download_tree_uri"
        private const val KEY_DOWNLOAD_DISPLAY = "download_display"
    }
}
