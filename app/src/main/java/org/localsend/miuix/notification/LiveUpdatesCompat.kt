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
        val compactEta = formatCompactEta(session)
        val etaPart = if (compactEta.isNotEmpty()) " · $compactEta" else ""

        val actionType = if (session.isIncoming) "接收中" else "发送中"
        val fileIndexInfo = if (session.files.size > 1) " (${session.currentFileIndex + 1}/${session.files.size})" else ""

        // 胶囊收起态右侧：实时速度与剩余时间（例如：28M/s 3s）
        val chipSpeedEta = formatChipSpeedEta(session)

        // 展开态大标题：正在接收 (1/3) · 目标设备
        val fullActionType = if (session.isIncoming) "正在接收" else "正在发送"
        val expandedTitle = "$fullActionType$fileIndexInfo · ${session.device.alias}"

        // 展开态详细内容：45% · 54.2 MB / 120.5 MB · 28.5 MB/s · 剩余3秒
        val contentText = "${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize} · $speedText$etaPart"

        // 展开态副标题：当前文件名或文本摘要
        val currentFileName = session.currentFile?.name
            ?: if (session.isTextMessage) "纯文本消息" else if (session.files.size > 1) "共 ${session.files.size} 个文件" else null

        val smallIconRes = if (session.isIncoming) R.drawable.ic_stat_receive else R.drawable.ic_stat_send

        // Android 16+ (API 36+) 原生 Live Updates 构建
        if (Build.VERSION.SDK_INT >= 36) {
            val nativeBuilder = Notification.Builder(context, channelId)
                .setSmallIcon(smallIconRes)
                .setContentTitle(expandedTitle)
                .setContentText(contentText)
                .setColor(0xFF00897B.toInt()) // LocalSend 经典 Teal 品牌主色
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(chipSpeedEta)
                .addAction(
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        "取消传输",
                        cancelIntent
                    ).build()
                )

            getLargeIcon(context)?.let {
                nativeBuilder.setLargeIcon(android.graphics.drawable.Icon.createWithBitmap(it))
            }

            if (!currentFileName.isNullOrEmpty()) {
                nativeBuilder.setSubText(currentFileName)
            }

            try {
                val progressStyle = Notification.ProgressStyle()
                    .setProgress(session.progressPercent)
                    .setStyledByProgress(true)

                if (session.files.size > 1 && session.totalBytes > 0) {
                    val segments = session.files.map { file ->
                        val weight = ((file.size.toDouble() / session.totalBytes) * 100).toInt().coerceAtLeast(1)
                        Notification.ProgressStyle.Segment(weight)
                    }
                    progressStyle.setProgressSegments(segments)
                }

                nativeBuilder.setStyle(progressStyle)
            } catch (_: Throwable) {
                nativeBuilder.setProgress(100, session.progressPercent, session.totalBytes <= 0)
            }

            val notification = nativeBuilder.build()
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_PROMOTED_ONGOING
            return notification
        }

        // Android < 36 兼容构建
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(expandedTitle)
            .setContentText(contentText)
            .setColor(0xFF00897B.toInt())
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setProgress(100, session.progressPercent, session.totalBytes <= 0)
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

        return builder.build()
    }

    /**
     * 格式化胶囊芯片短文本：速度和剩余时间（控制在 8~10 字符内，适配状态栏芯片排版）。
     */
    fun formatChipSpeedEta(session: TransferSession): String {
        if (session.isTextMessage) return "文本"
        if (session.status == org.localsend.miuix.model.TransferStatus.Completed) return "已完成"
        if (session.status == org.localsend.miuix.model.TransferStatus.WaitingApproval) return "待确认"
        if (session.speed <= 0) return "准备中"

        val speedMb = session.speed.toDouble() / (1024.0 * 1024.0)
        val speedStr = if (speedMb >= 1.0) {
            val rounded = kotlin.math.round(speedMb * 10) / 10.0
            if (rounded == kotlin.math.floor(rounded)) "${rounded.toInt()}M/s" else "${rounded}M/s"
        } else {
            val speedKb = (session.speed / 1024L).coerceAtLeast(1L)
            "${speedKb}K/s"
        }

        val remainingBytes = (session.totalBytes - session.transferredBytes).coerceAtLeast(0L)
        val etaSec = if (session.speed > 0) remainingBytes / session.speed else 0L
        val etaStr = when {
            etaSec <= 0L -> ""
            etaSec < 60L -> "${etaSec}s"
            etaSec < 3600L -> "${etaSec / 60L}m"
            else -> "${etaSec / 3600L}h"
        }

        return if (etaStr.isNotEmpty()) "$speedStr $etaStr" else speedStr
    }

    /**
     * 计算紧凑且直观的剩余时间文本（针对胶囊态排版优化）。
     */
    private fun formatCompactEta(session: TransferSession): String {
        if (session.status != org.localsend.miuix.model.TransferStatus.InProgress) return ""
        if (session.speed <= 0 || session.totalBytes <= 0) return ""
        val remainingBytes = (session.totalBytes - session.transferredBytes).coerceAtLeast(0L)
        if (remainingBytes == 0L) return "即将完成"
        val seconds = remainingBytes / session.speed
        return when {
            seconds <= 0L -> "即将完成"
            seconds < 60 -> "剩余${seconds}秒"
            seconds < 3600 -> {
                val min = seconds / 60
                val sec = seconds % 60
                if (sec == 0L) "剩余${min}分钟" else "剩余${min}分${sec}秒"
            }
            else -> "剩余${seconds / 3600}小时"
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
}
