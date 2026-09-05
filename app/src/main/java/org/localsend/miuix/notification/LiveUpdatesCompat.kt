package org.localsend.miuix.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession

/**
 * 原生 Android 实时通知（Live Updates）与 ColorOS 流体云适配器。
 * 遵循 Android 16+ (API 36+) 官方规范，与 ColorOS 14/15/16 深度对齐：
 * 1. 采用 NotificationCompat.Builder 配置 setRequestPromotedOngoing(true) 与 setShortCriticalText(...)；
 * 2. 注入 NotificationCompat.ProgressStyle 实时进度样式；
 * 3. 兼容注入 ColorOS / OxygenOS 泛在服务流体云（Aqua Dynamics）胶囊参数。
 */
object LiveUpdatesCompat {

    /**
     * 构建具备原生实时活动（Live Updates）与流体云胶囊特性的 Notification。
     */
    fun buildLiveNotification(
        context: Context,
        channelId: String,
        session: TransferSession,
        actionText: String,
        contentIntent: PendingIntent,
        cancelIntent: PendingIntent
    ): Notification {
        val speedText = if (session.speed > 0) session.formattedSpeed else "准备中"
        val title = "$actionText ${session.device.alias}"
        val text = "${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize} · $speedText"
        val shortText = "${session.progressPercent}% · $speedText"
        val currentFileName = session.currentFile?.name

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortText)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消传输",
                cancelIntent
            )

        if (!currentFileName.isNullOrEmpty()) {
            builder.setSubText(currentFileName)
        }

        // Android 16+ ProgressStyle 样式
        try {
            val progressStyle = NotificationCompat.ProgressStyle()
                .setProgress(session.progressPercent)
                .setStyledByProgress(true)
            builder.setStyle(progressStyle)
        } catch (_: Throwable) {
            builder.setProgress(100, session.progressPercent, session.totalBytes <= 0)
        }

        // 注入 ColorOS / OxygenOS 泛在服务流体云专有参数与底层 Android 16 Promoted Extra
        val extras = Bundle().apply {
            // Android 16 显式标记
            putBoolean("android.requestPromotedOngoing", true)
            putInt("android.progress", session.progressPercent)
            putInt("android.progressMax", 100)
            putBoolean("android.progressIndeterminate", session.totalBytes <= 0)

            // ColorOS 泛在服务 / 流体云胶囊参数
            putString("oplus.view.type", "capsule")
            putBoolean("oplus.capsule.enable", true)
            putString("oplus.capsule.title", title)
            putString("oplus.capsule.content", shortText)
            putString("oplus.capsule.status", if (session.progressPercent >= 100) "finished" else "running")
            putString("android.substName", "LocalSend")
            putString("intelligent_intent_type", "local_transfer")
        }
        builder.addExtras(extras)

        val notification = builder.build()

        // 附加 FLAG_ONGOING_EVENT 与 FLAG_PROMOTED_ONGOING 标志位（容错底层系统）
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or 262144

        return notification
    }

    /**
     * 检查系统是否允许本应用发布提升型持续实时通知（API 36+）。
     */
    fun canPostLiveUpdates(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 36) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return nm?.canPostPromotedNotifications() ?: true
        }
        return true
    }
}
