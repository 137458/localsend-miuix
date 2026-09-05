package org.localsend.miuix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.localsend.miuix.R
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.MainActivity

/**
 * 传输通知管理器。用于在后台或快速保存自动接收时，向用户提示传输进度与结果，
 * 并接入 ColorOS 流体云（Aqua Dynamics）与原生 Android 16+ Live Updates 胶囊显示。
 */
object TransferNotifier {

    const val CHANNEL_RECEIVE = "localsend_receive"
    const val CHANNEL_SEND = "localsend_send"
    const val CHANNEL_SERVICE = "localsend_service"
    const val CHANNEL_LIVE = "localsend_live_channel"

    const val NOTIF_ID_FOREGROUND_SERVICE = 1001
    private const val NOTIF_ID_RECEIVE_BASE = 2000
    private const val NOTIF_ID_SEND_BASE = 3000

    const val TEST_SESSION_ID = "test-live-notification-session"
    private var testJob: Job? = null
    val isTestRunning = MutableStateFlow(false)

    @Volatile
    private var allowed = false

    /** 创建通知渠道，并确认通知权限。 */
    fun ensure(context: Context) = synchronized(this) {
        createChannel(context)
        allowed = isNotificationsEnabled(context)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val liveChannel = NotificationChannel(
            CHANNEL_LIVE,
            "传输实时进度",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "显示流体云胶囊与实时传输进度"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val receiveChannel = NotificationChannel(
            CHANNEL_RECEIVE,
            "接收通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "接收文件与文本时的结果提示"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val sendChannel = NotificationChannel(
            CHANNEL_SEND,
            "发送通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "发送文件与文本时的结果提示"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "后台传输服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持后台传输连接与防杀保活"
            setShowBadge(false)
        }
        nm.createNotificationChannel(liveChannel)
        nm.createNotificationChannel(receiveChannel)
        nm.createNotificationChannel(sendChannel)
        nm.createNotificationChannel(serviceChannel)
    }

    fun buildForegroundNotification(context: Context, sessionCount: Int = 1): android.app.Notification {
        val title = "LocalSend 正在后台传输"
        val text = if (sessionCount > 1) "正在进行 $sessionCount 个传输任务，保持局域网连接..." else "正在进行文件传输，保持局域网连接..."
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(appPendingIntent(context))
            .build()
    }

    /**
     * 检查系统通知是否对本应用全局可用：
     * 1. 系统通知开关是否开启（NotificationManagerCompat.areNotificationsEnabled）；
     * 2. 若为 Android 13+ (API 33+)，是否已授予 POST_NOTIFICATIONS 运行时权限。
     */
    fun isNotificationsEnabled(context: Context): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasRuntimePerm = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!hasRuntimePerm) return false
        }
        return true
    }

    /** 是否已获取通知权限（用于决定是否发布通知）。 */
    fun isAllowed(context: Context): Boolean {
        val currentAllowed = isNotificationsEnabled(context)
        allowed = currentAllowed
        return currentAllowed
    }

    /**
     * 跳转至系统当前应用的通知设置页面，以便用户手动授权或开启各渠道通知与流体云开关。
     */
    fun openNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val promotionIntent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
                    putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (promotionIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(promotionIntent)
                    return
                }
            } catch (_: Exception) {}
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }

    private fun sessionNotifId(session: TransferSession): Int {
        val base = if (session.isIncoming) NOTIF_ID_RECEIVE_BASE else NOTIF_ID_SEND_BASE
        return base + (session.sessionId.hashCode() and 0x7FFF) % 100
    }

    private fun appPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    private fun cancelPendingIntent(context: Context, sessionId: String): PendingIntent {
        val intent = Intent(context, TransferActionReceiver::class.java).apply {
            action = TransferActionReceiver.ACTION_CANCEL_TRANSFER
            putExtra(TransferActionReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, sessionId.hashCode(), intent, flags)
    }

    fun notifyIncoming(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (session.isTextMessage) {
            "收到来自 ${session.device.alias} 的文本"
        } else {
            "收到来自 ${session.device.alias} 的文件"
        }
        val text = if (session.isTextMessage) {
            session.singleTextMessageContent?.take(80) ?: "纯文本消息"
        } else if (session.files.size == 1) {
            "${session.files.first().name} (${session.formattedTotalSize})"
        } else {
            "共 ${session.files.size} 个文件 (${session.formattedTotalSize})"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RECEIVE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(appPendingIntent(context))
            .setAutoCancel(true)
        nm.notify(sessionNotifId(session), builder.build())
    }

    fun updateProgress(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val actionText = if (session.isIncoming) "正在接收" else "正在发送"

        val notification = LiveUpdatesCompat.buildLiveNotification(
            context = context,
            channelId = CHANNEL_LIVE,
            session = session,
            actionText = actionText,
            contentIntent = appPendingIntent(context),
            cancelIntent = cancelPendingIntent(context, session.sessionId)
        )

        nm.notify(sessionNotifId(session), notification)
    }

    fun notifyResult(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = sessionNotifId(session)
        // 传输已终结，先清除进行中的流体云胶囊/进度通知
        nm.cancel(notifId)

        val channel = if (session.isIncoming) CHANNEL_RECEIVE else CHANNEL_SEND
        val (title, content) = when (session.status) {
            TransferStatus.Completed -> {
                if (session.isIncoming) {
                    if (session.isTextMessage) {
                        "已收到来自 ${session.device.alias} 的文本" to (session.singleTextMessageContent?.take(100) ?: "纯文本消息")
                    } else {
                        "文件接收完成" to "已成功接收来自 ${session.device.alias} 的 ${session.files.size} 个文件"
                    }
                } else {
                    if (session.isTextMessage) {
                        "文本消息已送达" to "已发送给 ${session.device.alias}"
                    } else {
                        "文件发送完成" to "已成功发送 ${session.files.size} 个文件给 ${session.device.alias}"
                    }
                }
            }
            TransferStatus.Canceled -> {
                (if (session.isIncoming) "接收已取消" else "发送已取消") to "对端：${session.device.alias}"
            }
            TransferStatus.Failed -> {
                (if (session.isIncoming) "接收失败" else "发送失败") to "${session.errorMessage ?: "传输异常"}"
            }
            else -> return
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(appPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
        nm.notify(notifId, builder.build())
    }

    /**
     * 启动实时通知 / 流体云胶囊模拟传输测试。
     * 在单机状态下模拟 5 秒持续传输，验证状态栏胶囊、锁屏卡片与通知栏 Live Updates 进度。
     */
    fun startTestSimulation(context: Context) {
        if (isTestRunning.value) {
            stopTestSimulation(context)
            return
        }
        ensure(context)
        val mockDevice = Device(
            alias = "测试设备 (Live Updates)",
            fingerprint = "test-fingerprint",
            port = 53317,
            protocol = "http",
            ip = "192.168.1.88",
            deviceModel = "Pixel / ColorOS"
        )
        val mockFiles = listOf(
            FileItem(name = "nature_landscape_4k.jpg", size = 18 * 1024 * 1024),
            FileItem(name = "presentation_demo.mp4", size = 72 * 1024 * 1024),
            FileItem(name = "firmware_update.apk", size = 30 * 1024 * 1024)
        )
        val totalBytes = mockFiles.sumOf { it.size }
        val testSession = TransferSession(
            sessionId = TEST_SESSION_ID,
            device = mockDevice,
            isIncoming = false,
            files = mockFiles,
            totalBytes = totalBytes,
            transferredBytes = 0L,
            speed = 0L,
            status = TransferStatus.InProgress
        )

        isTestRunning.value = true
        testJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val totalSteps = 50
                for (step in 1..totalSteps) {
                    if (!isActive || !isTestRunning.value) break
                    val currentProgress = step.toFloat() / totalSteps
                    testSession.transferredBytes = (totalBytes * currentProgress).toLong()
                    testSession.speed = (26L * 1024 * 1024) + ((step % 6) * 1024 * 1024)

                    val file1Size = mockFiles[0].size
                    val file2Size = mockFiles[1].size
                    when {
                        testSession.transferredBytes < file1Size -> {
                            mockFiles[0].status = TransferStatus.InProgress
                            mockFiles[0].bytesTransferred = testSession.transferredBytes
                        }
                        testSession.transferredBytes < file1Size + file2Size -> {
                            mockFiles[0].status = TransferStatus.Completed
                            mockFiles[0].bytesTransferred = file1Size
                            mockFiles[1].status = TransferStatus.InProgress
                            mockFiles[1].bytesTransferred = testSession.transferredBytes - file1Size
                        }
                        else -> {
                            mockFiles[0].status = TransferStatus.Completed
                            mockFiles[0].bytesTransferred = file1Size
                            mockFiles[1].status = TransferStatus.Completed
                            mockFiles[1].bytesTransferred = file2Size
                            mockFiles[2].status = TransferStatus.InProgress
                            mockFiles[2].bytesTransferred = testSession.transferredBytes - file1Size - file2Size
                        }
                    }

                    updateProgress(context, testSession)
                    delay(100L)
                }

                if (isActive && isTestRunning.value) {
                    mockFiles.forEach {
                        it.status = TransferStatus.Completed
                        it.bytesTransferred = it.size
                    }
                    testSession.transferredBytes = totalBytes
                    testSession.status = TransferStatus.Completed
                    notifyResult(context, testSession)
                }
            } catch (_: Exception) {
            } finally {
                isTestRunning.value = false
                testJob = null
            }
        }
    }

    /**
     * 终止实时通知模拟测试，撤销胶囊并发送取消提示。
     */
    fun stopTestSimulation(context: Context) {
        val job = testJob
        testJob = null
        isTestRunning.value = false
        job?.cancel()

        val mockDevice = Device(
            alias = "测试设备 (Live Updates)",
            fingerprint = "test-fingerprint",
            port = 53317,
            protocol = "http",
            ip = "192.168.1.88",
            deviceModel = "Pixel / ColorOS"
        )
        val cancelSession = TransferSession(
            sessionId = TEST_SESSION_ID,
            device = mockDevice,
            isIncoming = false,
            files = emptyList(),
            totalBytes = 0L,
            status = TransferStatus.Canceled
        )
        notifyResult(context, cancelSession)
    }
}