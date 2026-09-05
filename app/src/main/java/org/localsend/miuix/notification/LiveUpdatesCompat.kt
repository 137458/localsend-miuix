package org.localsend.miuix.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession

/**
 * 原生 Android 实时通知（Live Updates）与 ColorOS 流体云适配器。
 * 1. 原生 Android 16+ (API 36+)：直接采用官方 Live Updates 标准 API (Notification.ProgressStyle 与 FLAG_PROMOTED_ONGOING)；
 * 2. Android 8.0 ~ 15 / ColorOS 14/15/16：采用持续性 Ongoing 任务通知配合 ColorOS 泛在服务流体云参数，实现双兼容。
 */
object LiveUpdatesCompat {

    /**
     * 构建具备原生实时通知（Live Updates）与流体云胶囊特性的 Notification。
     */
    fun buildLiveNotification(
        context: Context,
        channelId: String,
        session: TransferSession,
        actionText: String,
        contentIntent: PendingIntent,
        cancelIntent: PendingIntent
    ): Notification {
        return if (Build.VERSION.SDK_INT >= 36) {
            Api36Impl.buildLiveUpdateNotification(
                context = context,
                channelId = channelId,
                session = session,
                actionText = actionText,
                contentIntent = contentIntent,
                cancelIntent = cancelIntent
            )
        } else {
            CompatImpl.buildCompatNotification(
                context = context,
                channelId = channelId,
                session = session,
                actionText = actionText,
                contentIntent = contentIntent,
                cancelIntent = cancelIntent
            )
        }
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

    @RequiresApi(36)
    private object Api36Impl {
        @SuppressLint("NewApi")
        fun buildLiveUpdateNotification(
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

            // 1. 原生 Android 16 ProgressStyle 样式
            val progressStyle = Notification.ProgressStyle()
                .setProgress(session.progressPercent)
                .setStyledByProgress(true)

            // 2. 原生 Notification.Builder 构建
            val builder = Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_receive)
                .setStyle(progressStyle)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .addAction(
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        "取消传输",
                        cancelIntent
                    ).build()
                )

            val notification = builder.build()

            // 3. 注入提升型持续通知标志（FLAG_PROMOTED_ONGOING = 0x40000 = 262144）
            // 促使原生系统在状态栏、息屏显示和锁屏以胶囊芯片（Status Bar Chip / Live Update）形式浮现
            val flagPromotedOngoing = 262144
            notification.flags = notification.flags or flagPromotedOngoing or Notification.FLAG_ONGOING_EVENT

            // 4. 同时注入 ColorOS / OxygenOS 流体云（Aqua Dynamics）参数，实现双兼容
            val extras = notification.extras
            extras.putString("oplus.view.type", "capsule")
            extras.putBoolean("oplus.capsule.enable", true)
            extras.putString("oplus.capsule.title", title)
            extras.putString("oplus.capsule.content", "${session.progressPercent}% · $speedText")
            extras.putString("oplus.capsule.status", if (session.progressPercent >= 100) "finished" else "running")
            extras.putString("android.substName", "LocalSend")
            extras.putInt("android.progressMax", 100)
            extras.putInt("android.progress", session.progressPercent)
            extras.putString("intelligent_intent_type", "local_transfer")

            return notification
        }
    }

    private object CompatImpl {
        fun buildCompatNotification(
            context: Context,
            channelId: String,
            session: TransferSession,
            actionText: String,
            contentIntent: PendingIntent,
            cancelIntent: PendingIntent
        ): Notification {
            val speedText = if (session.speed > 0) " · ${session.formattedSpeed}" else ""
            val currentFileName = session.currentFile?.name
            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("$actionText ${session.device.alias}")
                .setContentText("${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize}$speedText")
                .apply {
                    if (!currentFileName.isNullOrEmpty()) {
                        setSubText(currentFileName)
                    }
                }
                .setSmallIcon(R.drawable.ic_stat_receive)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(contentIntent)
                .setProgress(100, session.progressPercent, session.totalBytes <= 0)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "取消传输",
                    cancelIntent
                )

            // 注入 ColorOS 泛在服务流体云专有参数
            val extras = Bundle().apply {
                putString("oplus.view.type", "capsule")
                putBoolean("oplus.capsule.enable", true)
                putString("oplus.capsule.title", "$actionText ${session.device.alias}")
                putString("oplus.capsule.content", "${session.progressPercent}% · ${session.formattedSpeed}")
                putString("oplus.capsule.status", if (session.progressPercent >= 100) "finished" else "running")
                putString("android.substName", "LocalSend")
                putInt("android.progressMax", 100)
                putInt("android.progress", session.progressPercent)
                putString("intelligent_intent_type", "local_transfer")
            }
            builder.addExtras(extras)

            val notification = builder.build()
            // 尝试在底层附加 FLAG_PROMOTED_ONGOING 标志位
            notification.flags = notification.flags or 262144 or Notification.FLAG_ONGOING_EVENT
            return notification
        }
    }
}
