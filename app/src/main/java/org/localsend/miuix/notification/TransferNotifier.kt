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
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.MainActivity

/**
 * 传输通知管理器。用于在后台或快速保存自动接收时，向用户提示传输进度与结果，
 * 并接入 HyperOS 焦点通知与原生 Android 16+ Live Updates 胶囊显示。
 */
object TransferNotifier {

    const val CHANNEL_RECEIVE = "localsend_receive"
    const val CHANNEL_SEND = "localsend_send"
    const val CHANNEL_SERVICE = "localsend_service"
    const val CHANNEL_LIVE = "localsend_live_channel"

    const val NOTIF_ID_FOREGROUND_SERVICE = 1001
    private const val NOTIF_ID_RECEIVE_BASE = 2000
    private const val NOTIF_ID_SEND_BASE = 3000

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
            context.getString(R.string.notif_channel_live_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_live_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val receiveChannel = NotificationChannel(
            CHANNEL_RECEIVE,
            context.getString(R.string.notif_channel_receive_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_receive_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val sendChannel = NotificationChannel(
            CHANNEL_SEND,
            context.getString(R.string.notif_channel_send_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_send_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notif_channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_service_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(liveChannel)
        nm.createNotificationChannel(receiveChannel)
        nm.createNotificationChannel(sendChannel)
        nm.createNotificationChannel(serviceChannel)
    }

    fun buildForegroundNotification(context: Context, sessionCount: Int = 1): android.app.Notification {
        val title = context.getString(R.string.notif_foreground_title)
        val text = if (sessionCount > 1) {
            context.getString(R.string.notif_foreground_multiple, sessionCount)
        } else {
            context.getString(R.string.notif_foreground_single)
        }
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
     * 跳转至系统当前应用的通知设置页面，以便用户手动授权或开启各渠道通知与焦点通知开关。
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
            context.getString(R.string.notif_incoming_text_title, session.device.alias)
        } else {
            context.getString(R.string.notif_incoming_file_title, session.device.alias)
        }
        val text = if (session.isTextMessage) {
            session.singleTextMessageContent?.take(80) ?: context.getString(R.string.notif_plain_text_message)
        } else if (session.files.size == 1) {
            "${session.files.first().name} (${session.formattedTotalSize})"
        } else {
            context.getString(R.string.notif_files_count_and_size, session.files.size, session.formattedTotalSize)
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
        val actionText = if (session.isIncoming) {
            context.getString(R.string.live_receiving)
        } else {
            context.getString(R.string.live_sending)
        }

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
        // 传输已终结，先清除进行中的焦点胶囊/进度通知
        nm.cancel(notifId)

        val channel = if (session.isIncoming) CHANNEL_RECEIVE else CHANNEL_SEND
        val (title, content) = when (session.status) {
            TransferStatus.Completed -> {
                if (session.isIncoming) {
                    if (session.isTextMessage) {
                        context.getString(R.string.notif_receive_completed_text, session.device.alias) to (session.singleTextMessageContent?.take(100) ?: context.getString(R.string.notif_plain_text_message))
                    } else {
                        context.getString(R.string.notif_receive_completed_files_title) to context.getString(R.string.notif_receive_completed_files_desc, session.device.alias, session.files.size)
                    }
                } else {
                    if (session.isTextMessage) {
                        context.getString(R.string.notif_send_completed_text_title) to context.getString(R.string.notif_send_completed_text_desc, session.device.alias)
                    } else {
                        context.getString(R.string.notif_send_completed_files_title) to context.getString(R.string.notif_send_completed_files_desc, session.files.size, session.device.alias)
                    }
                }
            }
            TransferStatus.Canceled -> {
                (if (session.isIncoming) context.getString(R.string.notif_canceled_receive) else context.getString(R.string.notif_canceled_send)) to context.getString(R.string.notif_canceled_peer_tag, session.device.alias)
            }
            TransferStatus.Failed -> {
                (if (session.isIncoming) context.getString(R.string.notif_failed_receive) else context.getString(R.string.notif_failed_send)) to (session.errorMessage ?: context.getString(R.string.notif_failed_default_err))
            }
            else -> return
        }
        val resultIcon = if (session.isIncoming) R.drawable.ic_stat_receive else R.drawable.ic_stat_send
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(resultIcon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(appPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
        nm.notify(notifId, builder.build())
    }
}