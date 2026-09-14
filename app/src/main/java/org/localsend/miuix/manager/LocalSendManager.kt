package org.localsend.miuix.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import org.localsend.miuix.R
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.discovery.DeviceDirectory
import org.localsend.miuix.history.HistoryStore
import org.localsend.miuix.model.AppSettings
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.HistoryFileEntry
import org.localsend.miuix.model.SaveTarget
import org.localsend.miuix.network.TargetPinRequiredException
import org.localsend.miuix.model.ShareSession
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.DiscoveryService
import org.localsend.miuix.network.FingerprintTrust
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.network.LocalSendServer
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.network.SslHelper
import org.localsend.miuix.network.TlsStore
import org.localsend.miuix.notification.TransferNotifier
import org.localsend.miuix.transfer.TransferOutcome
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

data class AppInfoItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val apkSize: Long,
    val isSystemApp: Boolean
)

data class PinPromptRequest(
    val sessionId: String,
    val device: Device,
    val onPinEntered: (String) -> Unit,
    val onDismiss: () -> Unit
)

class LocalSendManager(private val context: Context) {

    init {
        instance = this
        TlsStore.init(context)
    }

    private val json = AppJson.default
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val started = AtomicBoolean(false)
    private val deviceDirectory = DeviceDirectory()
    private val canceledSessionIds = ConcurrentHashMap.newKeySet<String>()
    private val remoteSessionIds = ConcurrentHashMap<String, String>()

    // 默认保存位置说明：公共 Download/LocalSend（通过 MediaStore 写入，Android 10+ 免存储权限）
    private val defaultDownloadPath: String by lazy {
        Environment.DIRECTORY_DOWNLOADS + "/LocalSend"
    }

    private val prefs = context.getSharedPreferences(org.localsend.miuix.core.PreferenceKeys.PREF_NAME, Context.MODE_PRIVATE)

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
            autoCopyText = prefs.getBoolean(KEY_AUTO_COPY_TEXT, false),
            saveToHistory = prefs.getBoolean(KEY_SAVE_TO_HISTORY, true),
            useHttps = prefs.getBoolean(KEY_USE_HTTPS, false),
            deviceType = DeviceType.fromString(prefs.getString(KEY_DEVICE_TYPE, DeviceType.mobile.value)),
            download = prefs.getBoolean(KEY_DOWNLOAD, false),
            pin = prefs.getString(KEY_PIN, null),
            themeModeIndex = prefs.getInt(KEY_THEME, 0),
            downloadTreeUri = prefs.getString(KEY_TREE_URI, null),
            downloadDisplay = prefs.getString(KEY_DOWNLOAD_DISPLAY, null),
            downloadPath = defaultDownloadPath,
            vibrateOnComplete = prefs.getBoolean(KEY_VIBRATE, true),
            lastSelectedTabIndex = prefs.getInt(KEY_LAST_TAB, 0)
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<Device>>(emptyList())
    val nearbyDevices: StateFlow<List<Device>> = _nearbyDevices.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    private val historyStore = HistoryStore(File(context.filesDir, HistoryStore.FILENAME))

    private fun persistHistory(items: List<TransferHistoryItem>) {
        scope.launch(Dispatchers.IO) {
            try {
                historyStore.persist(items)
            } catch (_: Exception) {
            }
        }
    }

    private val _pendingIncomingSession = MutableStateFlow<TransferSession?>(null)
    val pendingIncomingSession: StateFlow<TransferSession?> = _pendingIncomingSession.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val activeSessions: StateFlow<List<TransferSession>> = _activeSessions.asStateFlow()

    private val _transferHistory = MutableStateFlow(historyStore.load())
    val transferHistory: StateFlow<List<TransferHistoryItem>> = _transferHistory.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 传输过程中的一次性提示（如"对方拒绝接收"、"已自动复制文本"），UI 读取后清空
    private val _sessionMessage = MutableStateFlow<String?>(null)
    val sessionMessage: StateFlow<String?> = _sessionMessage.asStateFlow()

    // Web Share：当前正在共享给它人浏览器下载的文件会话；空即未共享
    private val _shares = MutableStateFlow<List<ShareSession>>(emptyList())
    val shares: StateFlow<List<ShareSession>> = _shares.asStateFlow()

    // 外部 Intent（如分享或打开文件）驱动的主界面 Tab 切换请求（0: 接收, 1: 发送, 2: 设置），消费后置空
    private val _requestedTabIndex = MutableStateFlow<Int?>(null)
    val requestedTabIndex: StateFlow<Int?> = _requestedTabIndex.asStateFlow()

    // 手动输入 IP 历史记录（最多保留最近 5 个不同 IP）
    private val _recentManualIps = MutableStateFlow<List<String>>(loadRecentManualIps())
    val recentManualIps: StateFlow<List<String>> = _recentManualIps.asStateFlow()

    private fun loadRecentManualIps(): List<String> {
        val raw = prefs.getString(KEY_RECENT_MANUAL_IPS, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addRecentIp(ip: String) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return
        _recentManualIps.update { current ->
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(5)
            prefs.edit().putString(KEY_RECENT_MANUAL_IPS, updated.joinToString(",")).apply()
            updated
        }
    }

    fun requestNavigateToTab(index: Int) {
        _requestedTabIndex.value = index
    }

    fun consumeRequestedTab() {
        _requestedTabIndex.value = null
    }

    private val incomingApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val notifiedIncoming = ConcurrentHashMap.newKeySet<String>()

    private val discoveryService = DiscoveryService(
        context = context,
        scope = scope,
        getLocalDevice = { getLocalDevice() },
        onDeviceDiscovered = { device ->
            upsertDevice(device)
        }
    )

    private val targetDevicePins = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _pendingTargetPinPrompt = MutableStateFlow<PinPromptRequest?>(null)
    val pendingTargetPinPrompt: StateFlow<PinPromptRequest?> = _pendingTargetPinPrompt.asStateFlow()

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
            handleSessionUpdate(session)
        }
    )

    fun start() {
        if (!started.compareAndSet(false, true)) {
            onResume()
            return
        }
        scope.launch(Dispatchers.IO) {
            server.start()
            discoveryService.start()
            preloadInstalledApps()
        }
    }

    fun onResume() {
        scope.launch(Dispatchers.IO) {
            server.ensureStarted()
            discoveryService.ensureStarted()
            discoveryService.sendAnnouncement()
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        discoveryService.stop()
        server.stop()
        org.localsend.miuix.service.TransferService.stop(context)
    }

    private fun syncForegroundServiceState(activeCount: Int) {
        if (activeCount > 0) {
            org.localsend.miuix.service.TransferService.start(context, activeCount)
        } else {
            org.localsend.miuix.service.TransferService.stop(context)
        }
    }

    fun getLocalDevice(): Device {
        val primaryIp = NetworkUtils.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
        val useHttps = _settings.value.useHttps
        return Device(
            alias = _settings.value.alias,
            version = "2.1",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceType = _settings.value.deviceType,
            // 始终与本机自签名证书 SHA-256 指纹保持一致（协议 §2），确保作为客户端连接 HTTPS 对端时证书与 DTO 指纹完全匹配
            fingerprint = TlsStore.fingerprint(context),
            port = server.getBoundPort(),
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

    private fun upsertDevice(device: Device) {
        if (device.protocol.equals("https", ignoreCase = true) && device.fingerprint.isNotBlank()) {
            FingerprintTrust.trust(device.fingerprint)
        }
        synchronized(deviceDirectory) {
            _nearbyDevices.value = deviceDirectory.upsert(device)
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

        if (targetDevice.deviceType == DeviceType.web || targetDevice.port == 0) {
            startShare(filesToSend)
            _sessionMessage.value = context.getString(R.string.msg_web_share_published, filesToSend.size)
            return
        }

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

        _activeSessions.update { it + session.createSnapshot() }
        syncForegroundServiceState(_activeSessions.value.size)

        scope.launch(Dispatchers.IO) {
            FingerprintTrust.pin(targetDevice.fingerprint)
            try {
                if (canceledSessionIds.contains(sessionId)) {
                    session.status = TransferStatus.Canceled
                    session.endTime = System.currentTimeMillis()
                    updateSessionState(session)
                    return@launch
                }
                val deviceKey = targetDevice.fingerprint.ifEmpty { targetDevice.ip }
                var currentPin = targetDevicePins[deviceKey]
                var prepResult = client.prepareUpload(targetDevice, filesToSend, targetPin = currentPin)
                if (prepResult.isFailure && prepResult.exceptionOrNull() is TargetPinRequiredException) {
                    val deferred = CompletableDeferred<String?>()
                    _pendingTargetPinPrompt.value = PinPromptRequest(
                        sessionId = sessionId,
                        device = targetDevice,
                        onPinEntered = { pin ->
                            deferred.complete(pin)
                            _pendingTargetPinPrompt.value = null
                        },
                        onDismiss = {
                            deferred.complete(null)
                            _pendingTargetPinPrompt.value = null
                        }
                    )
                    val enteredPin = deferred.await()
                    if (!enteredPin.isNullOrBlank()) {
                        targetDevicePins[deviceKey] = enteredPin
                        prepResult = client.prepareUpload(targetDevice, filesToSend, targetPin = enteredPin)
                    }
                }
                if (prepResult.isFailure) {
                    session.status = TransferStatus.Failed
                    session.errorMessage = prepResult.exceptionOrNull()?.message
                        ?: context.getString(R.string.msg_handshake_failed)
                    _sessionMessage.value = context.getString(R.string.msg_peer_declined_detail, session.errorMessage)
                    updateSessionState(session)
                    return@launch
                }

                val handshake = prepResult.getOrNull()!!
                val responseDto = handshake.response
                val activeDevice = handshake.activeDevice
                val remoteSessionId = responseDto.sessionId
                val fileTokens = responseDto.files
                if (remoteSessionId.isNotBlank()) {
                    remoteSessionIds[sessionId] = remoteSessionId
                }

                if (activeDevice.ip != targetDevice.ip) {
                    upsertDevice(activeDevice)
                }

                val isAllTextMessage = filesToSend.all { it.isTextMessage }
                // 1. 协议 §4.1 / 原版应用纯文本传输特性：
                // 当对端响应 HTTP 204 No Content（完成，无需传输文件），说明接收方已在握手弹窗中直接复制/消费纯文本，无需上传二进制实体；
                // 或当发送纯文本消息时，对端返回 HTTP 200 但未分配任何上传令牌（fileTokens 为空），同样表示纯文本已被接收方消费且免后续上传
                if (handshake.completedImmediately || (isAllTextMessage && fileTokens.isEmpty())) {
                    Log.i(TAG, "Transfer completed immediately without upload (completedImmediately=${handshake.completedImmediately}, isAllTextMessage=$isAllTextMessage)")
                    filesToSend.forEach { fileItem ->
                        fileItem.status = TransferStatus.Completed
                        fileItem.progress = 1f
                        fileItem.bytesTransferred = fileItem.size
                    }
                    session.transferredBytes = session.totalBytes
                    session.speed = 0L
                    session.status = TransferStatus.Completed
                    session.errorMessage = null
                    session.endTime = System.currentTimeMillis()
                    updateSessionState(session)
                    return@launch
                }

                Log.i(TAG, "Starting transfer session $remoteSessionId to '${activeDevice.alias}' (${activeDevice.url}), ${filesToSend.size} files, ${fileTokens.size} tokens granted")

                for ((index, fileItem) in filesToSend.withIndex()) {
                    if (canceledSessionIds.contains(sessionId)) {
                        session.status = TransferStatus.Canceled
                        session.endTime = System.currentTimeMillis()
                        updateSessionState(session)
                        return@launch
                    }
                    val token = fileTokens[fileItem.id]
                    if (token == null) {
                        // 若该项为纯文本消息且其内容已在 prepare-upload 握手阶段通过 preview 完整提供，
                        // 且对端未授予 upload token，表明接收方已在对话框中复制/消费该文本且无需上传二进制流
                        if (fileItem.isTextMessage && !fileItem.textContent.isNullOrEmpty() && fileItem.textContent!!.length <= 2000) {
                            Log.i(TAG, "Text message '${fileItem.name}' (id=${fileItem.id}) accepted via preview without upload token.")
                            fileItem.status = TransferStatus.Completed
                            fileItem.progress = 1f
                            fileItem.bytesTransferred = fileItem.size
                            session.transferredBytes = filesToSend.sumOf { it.bytesTransferred }
                            updateSessionState(session)
                            continue
                        }

                        Log.w(TAG, "File '${fileItem.name}' (id=${fileItem.id}) has no token from peer! Skipping upload.")
                        fileItem.status = TransferStatus.Failed
                        fileItem.error = context.getString(R.string.msg_file_not_accepted)
                        updateSessionState(session)
                        continue
                    }

                    fileItem.status = TransferStatus.InProgress
                    Log.i(TAG, "Uploading [${index + 1}/${filesToSend.size}]: '${fileItem.name}' (${fileItem.size} bytes, id=${fileItem.id})")

                    val uploadResult = client.uploadFile(
                        targetDevice = activeDevice,
                        sessionId = remoteSessionId,
                        fileItem = fileItem,
                        token = token,
                        isCanceled = { canceledSessionIds.contains(sessionId) }
                    ) { bytesWritten, speed ->
                        fileItem.bytesTransferred = bytesWritten
                        fileItem.speed = speed
                        if (fileItem.size > 0) {
                            fileItem.progress = (bytesWritten.toFloat() / fileItem.size).coerceIn(0f, 1f)
                        }
                        session.transferredBytes = filesToSend.sumOf { it.bytesTransferred }
                        session.speed = speed
                        updateSessionState(session)
                    }

                    if (canceledSessionIds.contains(sessionId)) {
                        session.status = TransferStatus.Canceled
                        session.endTime = System.currentTimeMillis()
                        updateSessionState(session)
                        return@launch
                    }
                    if (uploadResult.isSuccess) {
                        Log.i(TAG, "File [${index + 1}/${filesToSend.size}] completed: '${fileItem.name}'")
                        fileItem.status = TransferStatus.Completed
                        fileItem.progress = 1f
                        fileItem.bytesTransferred = fileItem.size
                    } else {
                        val errMsg = uploadResult.exceptionOrNull()?.message
                            ?: context.getString(R.string.msg_upload_failed)
                        Log.e(TAG, "File [${index + 1}/${filesToSend.size}] failed: '${fileItem.name}', error: $errMsg")
                        fileItem.status = TransferStatus.Failed
                        fileItem.error = errMsg
                    }
                    updateSessionState(session)

                    if (index < filesToSend.size - 1) {
                        delay(30)
                    }
                }

                val outcome = TransferOutcome.aggregate(
                    files = filesToSend,
                    currentStatus = if (canceledSessionIds.contains(sessionId)) TransferStatus.Canceled else session.status,
                    allFailedMessage = context.getString(R.string.msg_all_files_failed),
                    partialFailedMessage = { failed, total ->
                        context.getString(R.string.msg_partial_files_failed, failed, total)
                    }
                )
                session.status = outcome.status
                session.errorMessage = outcome.errorMessage
                session.endTime = System.currentTimeMillis()
                updateSessionState(session)
            } finally {
                FingerprintTrust.unpin(targetDevice.fingerprint)
            }
        }
    }

    /** 手动输入 IP 发起传输：先通过 /info 探查目标设备元数据，再发起上传。 */
    fun sendToIp(ip: String, port: Int = 53317, filesToSend: List<FileItem> = _selectedFiles.value) {
        if (filesToSend.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            var targetDevice: Device? = null
            for (proto in listOf("https", "http")) {
                var conn: HttpURLConnection? = null
                try {
                    val url = "$proto://$ip:$port${LocalSendRoutes.INFO_V2}"
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        if (this is HttpsURLConnection) {
                            sslSocketFactory = SslHelper.sslSocketFactory
                            hostnameVerifier = SslHelper.trustAllHostnameVerifier
                        }
                        requestMethod = "GET"
                        useCaches = false
                        connectTimeout = 3000
                        readTimeout = 3000
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val dto = json.decodeFromString<DeviceDto>(body)
                        val observedFp = (conn as? HttpsURLConnection)?.let { SslHelper.certSha256(it) }.orEmpty()
                        if (proto == "https" &&
                            !org.localsend.miuix.network.CertificateBinding.dtoMatchesCert(dto.fingerprint, observedFp)
                        ) {
                            continue
                        }
                        targetDevice = Device.fromDto(dto, ip)
                        if (targetDevice.protocol.equals("https", ignoreCase = true) && targetDevice.fingerprint.isNotBlank()) {
                            FingerprintTrust.trust(targetDevice.fingerprint)
                        }
                        break
                    }
                } catch (ignored: Exception) {
                } finally {
                    try { conn?.disconnect() } catch (ignored: Exception) {}
                }
            }

            val finalDevice = targetDevice ?: Device(
                alias = context.getString(R.string.msg_device_alias_ip, ip),
                fingerprint = "",
                port = port,
                protocol = "http",
                ip = ip
            )
            addRecentIp(ip)
            withContext(Dispatchers.Main) {
                sendFilesTo(finalDevice, filesToSend)
            }
        }
    }

    private fun handleSessionUpdate(session: TransferSession) {
        scope.launch {
            updateTransferNotification(session)

            if (isTerminal(session.status)) {
                // 先更新快照，让界面展示最终完成/失败/取消状态
                val snapshot = session.createSnapshot()
                _activeSessions.update { list ->
                    val index = list.indexOfFirst { it.sessionId == session.sessionId }
                    if (index >= 0) {
                        list.toMutableList().apply { set(index, snapshot) }
                    } else {
                        list + snapshot
                    }
                }

                // 处理文本接收与反馈
                if (session.isIncoming && session.status == TransferStatus.Completed) {
                    if (session.isTextMessage && !session.singleTextMessageContent.isNullOrEmpty()) {
                        val text = session.singleTextMessageContent!!
                        if (_settings.value.autoCopyText) {
                            copyTextToClipboard(text)
                            _sessionMessage.value = context.getString(R.string.msg_text_auto_copied, text.take(20))
                        } else {
                            _sessionMessage.value = context.getString(R.string.msg_text_received_from, session.device.alias)
                        }
                    }
                    vibrateIfEnabled()
                } else if (!session.isIncoming && session.status == TransferStatus.Completed) {
                    _sessionMessage.value = context.getString(R.string.msg_sent_to, session.device.alias)
                    vibrateIfEnabled()
                }

                if (_settings.value.saveToHistory) {
                    val historyItem = TransferHistoryItem(
                        deviceAlias = session.device.alias,
                        deviceIp = session.device.ip,
                        isIncoming = session.isIncoming,
                        fileCount = session.files.size,
                        totalSize = session.totalBytes,
                        status = session.status,
                        fileNames = session.files.map { it.name },
                        textContent = if (session.isTextMessage) session.singleTextMessageContent else null,
                        isTextMessage = session.isTextMessage,
                        fileEntries = session.files.map {
                            HistoryFileEntry(
                                name = it.name,
                                size = it.size,
                                uri = it.uri ?: it.mediaStoreUri,
                                path = it.path,
                                mimeType = it.mimeType
                            )
                        }
                    )
                    addHistory(historyItem)
                }

                // 终结状态保留 2 秒，以便用户在设备 Card 内看清完成反馈动效，随后自动清理
                delay(2000L)
                _activeSessions.update { list -> list.filterNot { it.sessionId == session.sessionId } }
                syncForegroundServiceState(_activeSessions.value.size)
            } else {
                val snapshot = session.createSnapshot()
                _activeSessions.update { list ->
                    val index = list.indexOfFirst { it.sessionId == session.sessionId }
                    if (index >= 0) {
                        list.toMutableList().apply { set(index, snapshot) }
                    } else {
                        list + snapshot
                    }
                }
                syncForegroundServiceState(_activeSessions.value.size)
            }
        }
    }

    private fun updateSessionState(session: TransferSession) {
        handleSessionUpdate(session)
    }

    private fun isTerminal(status: TransferStatus): Boolean =
        status == TransferStatus.Completed ||
            status == TransferStatus.Failed ||
            status == TransferStatus.Canceled

    private var lastNotifTime = 0L

    private fun updateTransferNotification(session: TransferSession) {
        if (isTerminal(session.status)) {
            TransferNotifier.notifyResult(context, session)
        } else if (session.isIncoming && notifiedIncoming.add(session.sessionId)) {
            TransferNotifier.notifyIncoming(context, session)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastNotifTime >= 500) {
                lastNotifTime = now
                TransferNotifier.updateProgress(context, session)
            }
        }
    }

    fun copyTextToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend", text))
        } catch (ignored: Exception) {}
    }

    private fun vibrateIfEnabled() {
        if (!_settings.value.vibrateOnComplete) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(50)
                }
            }
        } catch (ignored: Exception) {}
    }

    fun consumeSessionMessage() {
        _sessionMessage.value = null
    }

    fun cancelTransfer(sessionId: String) {
        canceledSessionIds.add(sessionId)
        val session = _activeSessions.value.firstOrNull { it.sessionId == sessionId } ?: return
        session.status = TransferStatus.Canceled
        session.endTime = System.currentTimeMillis()
        updateSessionState(session)

        scope.launch(Dispatchers.IO) {
            if (!session.isIncoming) {
                val remoteId = remoteSessionIds[sessionId] ?: sessionId
                client.cancelUpload(session.device, remoteId)
            }
        }
    }

    private fun addHistory(item: TransferHistoryItem) {
        _transferHistory.update { current ->
            val updated = (listOf(item) + current).take(200)
            persistHistory(updated)
            updated
        }
    }

    fun clearHistory() {
        _transferHistory.value = emptyList()
        persistHistory(emptyList())
    }

    fun deleteHistoryItem(id: String) {
        _transferHistory.update { current ->
            val updated = current.filterNot { item -> item.id == id }
            persistHistory(updated)
            updated
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persistSettings(updated)
    }

    private var cachedInstalledApps: List<AppInfoItem>? = null
    private val installedAppsMutex = Mutex()

    /** 异步在后台预热本机应用列表缓存，避免首次打开弹窗时等待。 */
    fun preloadInstalledApps() {
        scope.launch(Dispatchers.IO) {
            try {
                getInstalledApps(forceRefresh = false)
            } catch (_: Exception) {}
        }
    }

    /**
     * 提取本机已安装的应用 (APK) 列表。
     * - 支持二级内存缓存（forceRefresh = false 时 0ms 秒开）
     * - 单次 IPC 批量获取 PackageInfo，消除数百次跨进程 Binder 往返
     * - 结合 CPU 核心数进行协程分块并发解析 (loadLabel 与 apkSize)
     */
    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<AppInfoItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedInstalledApps?.let { return@withContext it }
        }
        installedAppsMutex.withLock {
            if (!forceRefresh) {
                cachedInstalledApps?.let { return@withLock it }
            }
            val loaded = loadInstalledAppsInternal()
            cachedInstalledApps = loaded
            loaded
        }
    }

    private suspend fun loadInstalledAppsInternal(): List<AppInfoItem> = coroutineScope {
        val pm = context.packageManager
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getInstalledPackages(0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (packages.isEmpty()) return@coroutineScope emptyList()

        val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (packages.size / availableCores).coerceAtLeast(16)

        packages.chunked(chunkSize).map { chunk ->
            async(Dispatchers.IO) {
                chunk.mapNotNull { pkgInfo ->
                    try {
                        val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                        val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                        val file = File(sourceDir)
                        val size = file.length()
                        if (size <= 0L) return@mapNotNull null

                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val label = try {
                            appInfo.loadLabel(pm).toString()
                        } catch (_: Exception) {
                            pkgInfo.packageName
                        }.ifBlank { pkgInfo.packageName }

                        AppInfoItem(
                            label = label,
                            packageName = pkgInfo.packageName,
                            versionName = pkgInfo.versionName ?: "1.0",
                            sourceDir = sourceDir,
                            apkSize = size,
                            isSystemApp = isSystem
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().flatten().sortedWith(compareBy({ it.isSystemApp }, { it.label.lowercase() }))
    }

    /** 将选中的已安装应用作为 APK 文件添加到待发送列表。 */
    fun addAppsAsFiles(apps: List<AppInfoItem>) {
        val items = apps.map { app ->
            val cleanName = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            FileItem(
                name = "$cleanName.apk",
                size = app.apkSize,
                path = app.sourceDir,
                mimeType = "application/vnd.android.package-archive"
            )
        }
        addFiles(items)
    }

    /** 递归解析用户通过 SAF 选择的文件夹，将其下所有文件添加进发送队列。 */
    suspend fun addFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
        val items = mutableListOf<FileItem>()

        fun traverse(doc: DocumentFile, relativePrefix: String) {
            val children = doc.listFiles()
            for (child in children) {
                val childName = child.name ?: "unnamed"
                val relativePath = if (relativePrefix.isEmpty()) childName else "$relativePrefix/$childName"
                if (child.isDirectory) {
                    traverse(child, relativePath)
                } else if (child.isFile) {
                    val mime = child.type ?: "application/octet-stream"
                    items.add(
                        FileItem(
                            name = relativePath,
                            size = child.length(),
                            uri = child.uri,
                            mimeType = mime
                        )
                    )
                }
            }
        }

        traverse(rootDoc, "")
        if (items.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                addFiles(items)
                _sessionMessage.value = context.getString(R.string.msg_folder_files_added, items.size)
            }
        }
    }

    /** 重新生成自签名安全证书（HTTPS 模式）。 */
    fun regenerateCertificate() {
        scope.launch(Dispatchers.IO) {
            TlsStore.regenerateKeyStore(context)
            if (_settings.value.useHttps) {
                server.stop()
                server.start()
                discoveryService.sendAnnouncement()
            }
            withContext(Dispatchers.Main) {
                _sessionMessage.value = context.getString(R.string.msg_certificate_regenerated)
            }
        }
    }

    fun getSaveTarget(): SaveTarget {
        val tree = _settings.value.downloadTreeUri
        if (!tree.isNullOrEmpty()) {
            try {
                return SaveTarget.UriTarget(Uri.parse(tree))
            } catch (ignored: Exception) {}
        }
        return SaveTarget.MediaStoreTarget
    }

    fun setDownloadTree(uri: Uri, display: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (ignored: Exception) {}
        updateSettings { it.copy(downloadTreeUri = uri.toString(), downloadDisplay = display, downloadPath = display) }
    }

    fun applyPortChange(newPort: Int) {
        if (newPort == _settings.value.port) return
        updateSettings { it.copy(port = newPort) }
        scope.launch(Dispatchers.IO) {
            server.stop()
            server.start()
        }
    }

    fun applyUseHttpsChange(useHttps: Boolean) {
        if (useHttps == _settings.value.useHttps) return
        updateSettings { it.copy(useHttps = useHttps) }
        scope.launch(Dispatchers.IO) {
            server.stop()
            server.start()
            scope.launch { discoveryService.sendAnnouncement() }
        }
    }

    fun startShare(files: List<FileItem>) {
        if (files.isEmpty() || _shares.value.isNotEmpty()) return
        val session = ShareSession(files = files)
        _shares.value = listOf(session)
        discoveryService.sendAnnouncement()
    }

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
            .putBoolean(KEY_AUTO_COPY_TEXT, s.autoCopyText)
            .putBoolean(KEY_SAVE_TO_HISTORY, s.saveToHistory)
            .putBoolean(KEY_USE_HTTPS, s.useHttps)
            .putString(KEY_DEVICE_TYPE, s.deviceType.value)
            .putBoolean(KEY_DOWNLOAD, s.download)
            .putString(KEY_PIN, s.pin)
            .putInt(KEY_THEME, s.themeModeIndex)
            .putBoolean(KEY_VIBRATE, s.vibrateOnComplete)
            .putInt(KEY_LAST_TAB, s.lastSelectedTabIndex)
            .apply()
    }

    companion object {
        private const val TAG = "LocalSendTransfer"

        @Volatile
        private var instance: LocalSendManager? = null

        fun getInstance(): LocalSendManager? = instance

        fun getOrCreate(context: Context): LocalSendManager {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return LocalSendManager(context.applicationContext).also { instance = it }
            }
        }
        private const val KEY_ALIAS = org.localsend.miuix.core.PreferenceKeys.KEY_ALIAS
        private const val KEY_PORT = org.localsend.miuix.core.PreferenceKeys.KEY_PORT
        private const val KEY_QUICK_SAVE = org.localsend.miuix.core.PreferenceKeys.KEY_QUICK_SAVE
        private const val KEY_AUTO_COPY_TEXT = org.localsend.miuix.core.PreferenceKeys.KEY_AUTO_COPY_TEXT
        private const val KEY_SAVE_TO_HISTORY = org.localsend.miuix.core.PreferenceKeys.KEY_SAVE_TO_HISTORY
        private const val KEY_USE_HTTPS = org.localsend.miuix.core.PreferenceKeys.KEY_USE_HTTPS
        private const val KEY_DEVICE_TYPE = org.localsend.miuix.core.PreferenceKeys.KEY_DEVICE_TYPE
        private const val KEY_DOWNLOAD = org.localsend.miuix.core.PreferenceKeys.KEY_DOWNLOAD
        private const val KEY_PIN = org.localsend.miuix.core.PreferenceKeys.KEY_PIN
        private const val KEY_THEME = org.localsend.miuix.core.PreferenceKeys.KEY_THEME
        private const val KEY_TREE_URI = org.localsend.miuix.core.PreferenceKeys.KEY_TREE_URI
        private const val KEY_DOWNLOAD_DISPLAY = org.localsend.miuix.core.PreferenceKeys.KEY_DOWNLOAD_DISPLAY
        private const val KEY_VIBRATE = org.localsend.miuix.core.PreferenceKeys.KEY_VIBRATE
        private const val KEY_LAST_TAB = org.localsend.miuix.core.PreferenceKeys.KEY_LAST_TAB
        private const val KEY_RECENT_MANUAL_IPS = org.localsend.miuix.core.PreferenceKeys.KEY_RECENT_MANUAL_IPS
    }
}
