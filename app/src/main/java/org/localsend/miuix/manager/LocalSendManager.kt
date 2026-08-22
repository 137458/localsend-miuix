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
import org.localsend.miuix.model.ShareSession
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.DiscoveryService
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.network.LocalSendServer
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.network.TlsStore
import org.localsend.miuix.notification.TransferNotifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSendManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fingerprint = UUID.randomUUID().toString()

    // 默认保存位置说明：公共 Download/LocalSend（通过 MediaStore 写入，Android 10+ 免存储权限）
    private val defaultDownloadPath: String by lazy {
        Environment.DIRECTORY_DOWNLOADS + "/LocalSend"
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
            download = prefs.getBoolean(KEY_DOWNLOAD, false),
            pin = prefs.getString(KEY_PIN, null),
            themeModeIndex = prefs.getInt(KEY_THEME, 0),
            downloadTreeUri = prefs.getString(KEY_TREE_URI, null),
            downloadDisplay = prefs.getString(KEY_DOWNLOAD_DISPLAY, null),
            downloadPath = defaultDownloadPath
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

    // 传输过程中的一次性提示（如"对方拒绝接收"），UI 读取后清空
    private val _sessionMessage = MutableStateFlow<String?>(null)
    val sessionMessage: StateFlow<String?> = _sessionMessage.asStateFlow()

    // Web Share：当前正在共享给它人浏览器下载的文件会话；空即未共享
    private val _shares = MutableStateFlow<List<ShareSession>>(emptyList())
    val shares: StateFlow<List<ShareSession>> = _shares.asStateFlow()

    private val incomingApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    // 记录哪些 incoming 会话已弹出过首帧"收到文件"通知，避免进度更新时重复弹
    private val notifiedIncoming = ConcurrentHashMap.newKeySet<String>()

    private val discoveryService = DiscoveryService(
        context = context,
        scope = scope,
        getLocalDevice = { getLocalDevice() },
        onDeviceDiscovered = { device ->
            upsertDevice(device)
        }
    )

    private val client = LocalSendClient(
        context = context,
        getLocalDevice = { getLocalDevice() },
        getPin = { _settings.value.pin }
    )

    private val server = LocalSendServer(
        context = context,
        scope = scope,
        getPort = { _settings.value.port },
        getLocalDevice = { getLocalDevice() },
        isQuickSave = { _settings.value.quickSave },
        getSaveTarget = { getSaveTarget() },
        getPin = { _settings.value.pin },
        getUseHttps = { _settings.value.useHttps },
        getShares = { _shares.value },
        onDeviceDiscovered = { device ->
            upsertDevice(device)
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
                // 接收端通知：进度与结果反馈（仅对 incoming 会话发通知，避免发送端刷屏）
                updateTransferNotification(session)

                // 进行中的会话写入正在传输区；已结束的会话移入历史并从活动列表移除
                if (isTerminal(session.status)) {
                    _activeSessions.update { list -> list.filterNot { it.sessionId == session.sessionId } }
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
                } else {
                    _activeSessions.update { list ->
                        val index = list.indexOfFirst { it.sessionId == session.sessionId }
                        if (index >= 0) {
                            list.toMutableList().apply { set(index, session) }
                        } else {
                            list + session
                        }
                    }
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
        val useHttps = _settings.value.useHttps
        return Device(
            alias = _settings.value.alias,
            version = "2.1",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceType = DeviceType.mobile,
            // HTTPS 模式下指纹 = 自签名证书的 SHA-256 哈希（协议 §2），否则用稳定的随机 UUID
            fingerprint = if (useHttps) TlsStore.fingerprint(context) else fingerprint,
            port = _settings.value.port,
            protocol = if (useHttps) "https" else "http",
            // Web Share 启用时按协议 §2 announce download=true；否则沿用设置
            download = _shares.value.isNotEmpty() || _settings.value.download,
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

    /** 发现到的新设备与列表内已有条目是否"数据一致"（仅指纹/协议/端口改变才值得刷新，降低扫描期重组开销）。 */
    private fun sameIdentity(a: Device, b: Device): Boolean =
        a.fingerprint == b.fingerprint && a.port == b.port && a.protocol == b.protocol

    /**
     * 集中处理设备发现：多播 / 广播 / 子网扫描 / HTTP register 都走这里合并进 nearbyDevices。
     * 去重键为 fingerprint，其次 (ip+port)；同时清理长时间未刷新的过期设备，
     * 避免同一设备因更换 IP / HTTPS↔HTTP 指纹变化而在列表中残留成多个同名条目。
     */
    private fun upsertDevice(device: Device) {
        scope.launch {
            _nearbyDevices.update { current ->
                val now = System.currentTimeMillis()
                val alive = current.filter { now - it.lastSeen < DEVICE_TTL_MS }
                val index = alive.indexOfFirst {
                    it.fingerprint == device.fingerprint ||
                        (it.ip == device.ip && it.port == device.port)
                }
                if (index < 0) {
                    // 全新设备：追加
                    alive + device
                } else if (sameIdentity(alive[index], device) && alive[index].ip == device.ip) {
                    // 内容未变，仅刷新 lastSeen：返回原列表，避免无意义重组导致 UI 卡顿
                    current
                } else {
                    alive.toMutableList().apply { set(index, device) }
                }
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
                _sessionMessage.value = "对方拒绝接收：${session.errorMessage}"
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
            if (isTerminal(session.status)) {
                // 已结束的会话：从活动列表移除并写入历史
                _activeSessions.update { list -> list.filterNot { it.sessionId == session.sessionId } }
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
            } else {
                _activeSessions.update { list ->
                    val index = list.indexOfFirst { it.sessionId == session.sessionId }
                    if (index >= 0) {
                        list.toMutableList().apply { set(index, session) }
                    } else {
                        list + session
                    }
                }
            }
        }
    }

    private fun isTerminal(status: TransferStatus): Boolean =
        status == TransferStatus.Completed ||
            status == TransferStatus.Failed ||
            status == TransferStatus.Canceled

    /**
     * 接收端的系统通知：仅对 incoming 会话弹出。
     * 非终止状态：首帧弹"收到文件"，随后仅更新进度条；终止状态：弹最终结果。
     */
    private fun updateTransferNotification(session: TransferSession) {
        if (!session.isIncoming) return
        if (isTerminal(session.status)) {
            TransferNotifier.notifyResult(context, session)
        } else if (notifiedIncoming.add(session.sessionId)) {
            TransferNotifier.notifyIncoming(context, session)
        } else {
            TransferNotifier.updateProgress(context, session)
        }
    }

    fun consumeSessionMessage() {
        _sessionMessage.value = null
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

    /** 计算当前接收保存目标：优先使用用户通过 SAF 选择的自定义目录，否则写入公共 Download（MediaStore）。 */
    fun getSaveTarget(): SaveTarget {
        val tree = _settings.value.downloadTreeUri
        if (!tree.isNullOrEmpty()) {
            try {
                return SaveTarget.UriTarget(Uri.parse(tree))
            } catch (ignored: Exception) {}
        }
        return SaveTarget.MediaStoreTarget
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
        scope.launch(Dispatchers.IO) {
            server.stop()
            server.start()
        }
    }

    /** 切换 HTTPS/HTTP 后重启服务（引擎与指纹会随之变化），并重新广播设备信息。 */
    fun applyUseHttpsChange(useHttps: Boolean) {
        if (useHttps == _settings.value.useHttps) return
        updateSettings { it.copy(useHttps = useHttps) }
        // HTTPS 引擎（Netty + 自签名证书解析）初始化较重，切到 IO 线程执行，避免冻结主线程（UI）
        scope.launch(Dispatchers.IO) {
            server.stop()
            server.start()
            scope.launch { discoveryService.sendAnnouncement() }
        }
    }

    /** 开始 Web Share：把指定文件共享给局域网浏览器，并重新广播（download=true）。 */
    fun startShare(files: List<FileItem>) {
        if (files.isEmpty() || _shares.value.isNotEmpty()) return
        val session = ShareSession(files = files)
        _shares.value = listOf(session)
        discoveryService.sendAnnouncement()
    }

    /** 结束 Web Share 并重新广播（download=false）。 */
    fun stopShare() {
        if (_shares.value.isEmpty()) return
        _shares.value = emptyList()
        discoveryService.sendAnnouncement()
    }

    private fun persistSettings(s: AppSettings) {
        prefs.edit()
            .putString(KEY_ALIAS, s.alias)
            .putInt(KEY_PORT, s.port)
            .putString(KEY_TREE_URI, s.downloadTreeUri)
            .putString(KEY_DOWNLOAD_DISPLAY, s.downloadDisplay)
            .putBoolean(KEY_QUICK_SAVE, s.quickSave)
            .putBoolean(KEY_USE_HTTPS, s.useHttps)
            .putBoolean(KEY_DOWNLOAD, s.download)
            .putString(KEY_PIN, s.pin)
            .putInt(KEY_THEME, s.themeModeIndex)
            .apply()
    }

    companion object {
        // 设备发现的过期清理阈值：超过该时长未刷新的设备从附近设备列表移除
        private const val DEVICE_TTL_MS = 90_000L
        private const val KEY_ALIAS = "alias"
        private const val KEY_PORT = "port"
        private const val KEY_QUICK_SAVE = "quick_save"
        private const val KEY_USE_HTTPS = "use_https"
        private const val KEY_DOWNLOAD = "download"
        private const val KEY_PIN = "pin"
        private const val KEY_THEME = "theme_mode_index"
        private const val KEY_TREE_URI = "download_tree_uri"
        private const val KEY_DOWNLOAD_DISPLAY = "download_display"
    }
}
