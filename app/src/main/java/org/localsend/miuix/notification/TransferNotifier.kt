package org.localsend.miuix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus

/**
 * 传输通知。用于在应用退到后台 / 快速保存自动接收时，向用户提示接收进度与结果。
 *
 * 与官方 LocalSend 对齐：仅在发生接收时弹通知，不引入常驻前台服务。
 */
object TransferNotifier {

    const val CHANNEL_RECEIVE = "localsend_receive"

    /** 各加密会话的接收通知 id，互相独立。 */
    const val NOTIF_ID_RECEIVE_START = 2001

    /** 会话通知 id（进度/结果）。易造成通知堆积，故只保留最近若干 id。 */
    private const val NOTIF_ID_PROGRESS = 3001

    private var allowed = false

    /** 创建通知渠道，并请求/确认通知权限（Android 13+）。 */
    fun ensure(context: Context) = synchronized(this) {
        createChannel(context)
        allowed = hasPermission(context)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_RECEIVE,
            "接收文件",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "接收文件时的进度与结果提示"
        }
        nm.createNotificationChannel(channel)
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

    fun notifyIncoming(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = "收到来自 ${session.device.alias} 的文件"
        val text = if (session.files.size == 1) {
            "${session.files.first().name} (${session.formattedTotalSize})"
        } else {
            "共 ${session.files.size} 个文件 (${session.formattedTotalSize})"
        }
        val builder = notif(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        nm.notify(NOTIF_ID_RECEIVE_START, builder.build())
    }

    fun updateProgress(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val speedText = if (session.speed > 0) " · ${session.formattedSpeed}" else ""
        val currentFileName = session.currentFile?.name
        val builder = notif(context)
            .setContentTitle("正在接收 ${session.device.alias} 的文件")
            .setContentText("${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize}$speedText")
            .apply {
                if (!currentFileName.isNullOrEmpty()) {
                    setSubText(currentFileName)
                }
            }
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setProgress(100, session.progressPercent, session.totalBytes <= 0)
        nm.notify(NOTIF_ID_PROGRESS, builder.build())
    }

    fun notifyResult(context: Context, session: TransferSession) {
        if (!isAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val (title, content) = when (session.status) {
            TransferStatus.Completed -> "文件接收完成" to "已接收来自 ${session.device.alias} 的 ${session.files.size} 个文件"
            TransferStatus.Canceled -> "文件接收已取消" to "来源：${session.device.alias}"
            TransferStatus.Failed -> "文件接收失败" to "来源：${session.device.alias}"
            else -> return
        }
        val builder = notif(context)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        nm.notify(NOTIF_ID_PROGRESS, builder.build())
    }

    private fun notif(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_RECEIVE)
            .setContentIntent(null)
    }
}