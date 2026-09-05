package org.localsend.miuix.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession

/**
 * 原生 Android 实时通知（Live Updates）与 ColorOS 流体云适配器。
 * 遵循 Android 16+ (API 36+) 官方规范，与 ColorOS 14/15/16 深度对齐：
 * 1. 采用 NotificationCompat.Builder 配置 setRequestPromotedOngoing(true) 与 setShortCriticalText(...)；
 * 2. 注入 NotificationCompat.ProgressStyle 多文件分段实时进度与自适应样式；
 * 3. 规范化 ColorOS / OxygenOS 泛在服务流体云（Aqua Dynamics）胶囊参数，优化状态栏胶囊与卡片层级展示。
 */
object LiveUpdatesCompat {

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    private fun getLargeIcon(context: Context): Bitmap? {
        if (cachedLargeIcon == null) {
            cachedLargeIcon = runCatching {
                val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    ?: ContextCompat.getDrawable(context, R.drawable.ic_stat_receive)
                drawable?.toBitmap(width = 128, height = 128)
            }.getOrNull()
        }
        return cachedLargeIcon
    }

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
        val etaText = session.remainingTimeFormatted
        val etaPart = if (etaText.isNotEmpty() && etaText != "计算中...") " · $etaText" else ""

        val actionType = if (session.isIncoming) "正在接收" else "正在发送"
        val fileIndexInfo = if (session.files.size > 1) " (${session.currentFileIndex + 1}/${session.files.size})" else ""

        // 展开态大标题：正在接收 (1/3) · 目标设备
        val expandedTitle = "$actionType$fileIndexInfo · ${session.device.alias}"

        // 展开态详细内容：45% · 54.2 MB / 120.5 MB · 28.5 MB/s · 剩余约 3秒
        val contentText = "${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize} · $speedText$etaPart"

        // 展开态副标题：当前文件名或文本摘要
        val currentFileName = session.currentFile?.name
            ?: if (session.isTextMessage) "纯文本消息" else if (session.files.size > 1) "共 ${session.files.size} 个文件" else null

        // 状态栏胶囊精简文本（字数受限，突出核心：45% · 28.5 MB/s）
        val chipText = "${session.progressPercent}% · $speedText"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_receive)
            .setContentTitle(expandedTitle)
            .setContentText(contentText)
            .setColor(0xFF00897B.toInt()) // LocalSend 经典 Teal 品牌主色
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chipText)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消传输",
                cancelIntent
            )

        getLargeIcon(context)?.let {
            builder.setLargeIcon(it)
        }

        if (!currentFileName.isNullOrEmpty()) {
            builder.setSubText(currentFileName)
        }

        // Android 16+ ProgressStyle 样式（多文件支持分段 ProgressSegments）
        try {
            val progressStyle = NotificationCompat.ProgressStyle()
                .setProgress(session.progressPercent)
                .setStyledByProgress(true)

            if (session.files.size > 1 && session.totalBytes > 0) {
                val segments = session.files.map { file ->
                    val weight = ((file.size.toDouble() / session.totalBytes) * 100).toInt().coerceAtLeast(1)
                    NotificationCompat.ProgressStyle.Segment(weight)
                }
                progressStyle.setProgressSegments(segments)
            }

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

            // ColorOS 泛在服务 / 流体云胶囊核心参数
            // 胶囊主标题仅保留简短动词（避免胶囊过宽被系统强制截断），在副文本或大卡片中完整展示设备与文件
            putString("oplus.view.type", "capsule")
            putBoolean("oplus.capsule.enable", true)
            putString("oplus.capsule.title", actionType)
            putString("oplus.capsule.content", chipText)
            putString("oplus.capsule.ext_title", expandedTitle)
            putString("oplus.capsule.ext_content", contentText)
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
