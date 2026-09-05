package org.localsend.miuix.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.MainActivity

/**
 * 传输通知。用于在后台或快速保存自动接收时，向用户提示传输进度与结果。
 */
object TransferNotifier {

    const val CHANNEL_RECEIVE = "localsend_receive"
    const val CHANNEL_SEND = "localsend_send"
    const val CHANNEL_SERVICE = "localsend_service"

    const val NOTIF_ID_FOREGROUND_SERVICE = 1001
    private const val NOTIF_ID_RECEIVE_BASE = 2000
    private const val NOTIF_ID_SEND_BASE = 3000

    private var allowed = false

    /** 创建通知渠道，并请求/确认通知权限（Android 13+）。 */
    fun ensure(context: Context) = synchronized(this) {
        createChannel(context)
        allowed = hasPermission(context)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val receiveChannel = NotificationChannel(
            CHANNEL_RECEIVE,
            "接收通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "接收文件与文本时的进度与结果提示"
        }
        val sendChannel = NotificationChannel(
            CHANNEL_SEND,
            "发送通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "发送文件与文本时的进度与结果提示"
        }
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "后台传输服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持后台传输连接与防杀保活"
            setShowBadge(false)
        }
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

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 是否已获取通知权限（用于决定是否踢通知）。 */
    fun isAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return hasPermission(context)
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
        val speedText = if (session.speed > 0) " · ${session.formattedSpeed}" else ""
        val currentFileName = session.currentFile?.name
        val actionText = if (session.isIncoming) "正在接收" else "正在发送"
        val channel = if (session.isIncoming) CHANNEL_RECEIVE else CHANNEL_SEND
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle("$actionText ${session.device.alias} 的内容")
            .setContentText("${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize}$speedText")
            .apply {
                if (!currentFileName.isNullOrEmpty()) {
                    setSubText(currentFileName)
                }
            }
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(appPendingIntent(context))
            .setProgress(100, session.progressPercent, session.totalBytes <= 0)
        nm.notify(sessionNotifId(session), builder.build())
    }

    fun notifyResult(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            .setAutoCancel(true)
        nm.notify(sessionNotifId(session), builder.build())
    }
}