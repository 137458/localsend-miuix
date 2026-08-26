package org.localsend.miuix.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.AppSettings
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.HistoryFileEntry
import org.localsend.miuix.model.SaveTarget
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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.X509TrustManager

data class AppInfoItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val apkSize: Long,
    val isSystemApp: Boolean
)

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
            vibrateOnComplete = prefs.getBoolean(KEY_VIBRATE, true)
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<Device>>(emptyList())
    val nearbyDevices: StateFlow<List<Device>> = _nearbyDevices.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    private val historyFile = File(context.filesDir, "transfer_history.json")
    private val historyJson = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun loadPersistedHistory(): List<TransferHistoryItem> {
        return try {
            if (historyFile.exists()) {
                val content = historyFile.readText()
                if (content.isNotBlank()) {
                    historyJson.decodeFromString<List<TransferHistoryItem>>(content)
                } else emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistHistory(items: List<TransferHistoryItem>) {
        scope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(context.filesDir, "transfer_history.json.tmp")
                tempFile.writeText(historyJson.encodeToString(items))
                if (!tempFile.renameTo(historyFile)) {
                    tempFile.copyTo(historyFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                // Ignore file write error
            }
        }
    }

    private val _pendingIncomingSession = MutableStateFlow<TransferSession?>(null)
    val pendingIncomingSession: StateFlow<TransferSession?> = _pendingIncomingSession.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val activeSessions: StateFlow<List<TransferSession>> = _activeSessions.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferHistoryItem>>(loadPersistedHistory())
    val transferHistory: StateFlow<List<TransferHistoryItem>> = _transferHistory.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 传输过程中的一次性提示（如"对方拒绝接收"、"已自动复制文本"），UI 读取后清空
    private val _sessionMessage = MutableStateFlow<String?>(null)
    val sessionMessage: StateFlow<String?> = _sessionMessage.asStateFlow()

    // Web Share：当前正在共享给它人浏览器下载的文件会话；空即未共享
    private val _shares = MutableStateFlow<List<ShareSession>>(emptyList())
    val shares: StateFlow<List<ShareSession>> = _shares.asStateFlow()

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
            handleSessionUpdate(session)
        }
    )

    fun start() {
        server.start()
        discoveryService.start()
    }

    fun onResume() {
        server.ensureStarted()
        discoveryService.ensureStarted()
        discoveryService.sendAnnouncement()
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
            deviceType = _settings.value.deviceType,
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

    private fun sameIdentity(a: Device, b: Device): Boolean =
        a.fingerprint == b.fingerprint && a.port == b.port && a.protocol == b.protocol

    private fun upsertDevice(device: Device) {
        if (device.protocol.equals("https", ignoreCase = true) && device.fingerprint.isNotBlank()) {
            FingerprintTrust.trust(device.fingerprint)
        }
        scope.launch {
            _nearbyDevices.update { current ->
                val now = System.currentTimeMillis()
                val alive = current.filter { now - it.lastSeen < DEVICE_TTL_MS }
                val index = alive.indexOfFirst {
                    it.ip == device.ip && it.port == device.port
                }
                if (index < 0) {
                    alive + device
                } else if (sameIdentity(alive[index], device)) {
                    if (alive.size == current.size && alive[index].lastSeen == device.lastSeen) {
                        current
                    } else {
                        alive.toMutableList().apply { set(index, device) }
                    }
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
                    if (fileItem.size > 0) {
                        fileItem.progress = (bytesWritten.toFloat() / fileItem.size).coerceIn(0f, 1f)
                    }
                    session.transferredBytes = filesToSend.sumOf { it.bytesTransferred }
                    session.speed = speed
                    updateSessionState(session)
                }

                if (uploadResult.isSuccess) {
                    fileItem.status = TransferStatus.Completed
                    fileItem.progress = 1f
                    fileItem.bytesTransferred = fileItem.size
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

    /** 手动输入 IP 发起传输：先通过 /info 探查目标设备元数据，再发起上传。 */
    fun sendToIp(ip: String, port: Int = 53317, filesToSend: List<FileItem> = _selectedFiles.value) {
        if (filesToSend.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            var targetDevice: Device? = null
            val rawHttpClient = HttpClient(CIO) {
                engine {
                    https {
                        trustManager = SslHelper.trustAllCerts[0] as X509TrustManager
                    }
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
            try {
                for (proto in listOf("https", "http")) {
                    try {
                        val response = rawHttpClient.get("$proto://$ip:$port/api/localsend/v2/info")
                        val dto = response.body<DeviceDto>()
                        targetDevice = Device.fromDto(dto, ip)
                        if (targetDevice.protocol.equals("https", ignoreCase = true) && targetDevice.fingerprint.isNotBlank()) {
                            FingerprintTrust.trust(targetDevice.fingerprint)
                        }
                        break
                    } catch (ignored: Exception) {}
                }
            } finally {
                rawHttpClient.close()
            }

            val finalDevice = targetDevice ?: Device(
                alias = "设备 ($ip)",
                fingerprint = "",
                port = port,
                protocol = "http",
                ip = ip
            )
            withContext(Dispatchers.Main) {
                sendFilesTo(finalDevice, filesToSend)
            }
        }
    }

    private fun handleSessionUpdate(session: TransferSession) {
        scope.launch {
            updateTransferNotification(session)

            if (isTerminal(session.status)) {
                _activeSessions.update { list -> list.filterNot { it.sessionId == session.sessionId } }
                
                // 处理文本接收与反馈
                if (session.isIncoming && session.status == TransferStatus.Completed) {
                    if (session.isTextMessage && !session.singleTextMessageContent.isNullOrEmpty()) {
                        val text = session.singleTextMessageContent!!
                        if (_settings.value.autoCopyText) {
                            copyTextToClipboard(text)
                            _sessionMessage.value = "已接收并自动复制文本：${text.take(20)}"
                        } else {
                            _sessionMessage.value = "已收到来自 ${session.device.alias} 的文本消息"
                        }
                    }
                    vibrateIfEnabled()
                } else if (!session.isIncoming && session.status == TransferStatus.Completed) {
                    _sessionMessage.value = "内容已成功发送至 ${session.device.alias}"
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

    private fun updateSessionState(session: TransferSession) {
        handleSessionUpdate(session)
    }

    private fun isTerminal(status: TransferStatus): Boolean =
        status == TransferStatus.Completed ||
            status == TransferStatus.Failed ||
            status == TransferStatus.Canceled

    private fun updateTransferNotification(session: TransferSession) {
        if (isTerminal(session.status)) {
            TransferNotifier.notifyResult(context, session)
        } else if (session.isIncoming && notifiedIncoming.add(session.sessionId)) {
            TransferNotifier.notifyIncoming(context, session)
        } else {
            TransferNotifier.updateProgress(context, session)
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
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v?.vibrate(50)
                }
            }
        } catch (ignored: Exception) {}
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

    /** 提取本机已安装的应用 (APK) 列表。 */
    suspend fun getInstalledApps(): List<AppInfoItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
        apps.mapNotNull { appInfo ->
            try {
                val label = pm.getApplicationLabel(appInfo).toString()
                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                val file = File(sourceDir)
                if (!file.exists()) return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val pkgInfo = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        pm.getPackageInfo(appInfo.packageName, 0)
                    }
                } catch (e: Exception) {
                    null
                }
                AppInfoItem(
                    label = label,
                    packageName = appInfo.packageName,
                    versionName = pkgInfo?.versionName ?: "1.0",
                    sourceDir = sourceDir,
                    apkSize = file.length(),
                    isSystemApp = isSystem
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.isSystemApp }, { it.label.lowercase() }))
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
                _sessionMessage.value = "已成功添加文件夹中的 ${items.size} 个文件"
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
                _sessionMessage.value = "安全证书已重新生成，指纹已更新"
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
            .apply()
    }

    companion object {
        private const val DEVICE_TTL_MS = 90_000L
        private const val KEY_ALIAS = "alias"
        private const val KEY_PORT = "port"
        private const val KEY_QUICK_SAVE = "quick_save"
        private const val KEY_AUTO_COPY_TEXT = "auto_copy_text"
        private const val KEY_SAVE_TO_HISTORY = "save_to_history"
        private const val KEY_USE_HTTPS = "use_https"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_DOWNLOAD = "download"
        private const val KEY_PIN = "pin"
        private const val KEY_THEME = "theme_mode_index"
        private const val KEY_TREE_URI = "download_tree_uri"
        private const val KEY_DOWNLOAD_DISPLAY = "download_display"
        private const val KEY_VIBRATE = "vibrate_on_complete"
    }
}
