package org.localsend.miuix.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.transfer.RemainingTime

/**
 * 原生 Android 实时通知（Live Updates）与 HyperOS 焦点通知适配器。
 * 遵循 Android 16+ (API 36+) 官方规范，并良好兼容主流定制系统状态栏胶囊：
 * 1. 采用 NotificationCompat.Builder 配置 setRequestPromotedOngoing(true) 与 setShortCriticalText(...)；
 * 2. 注入 NotificationCompat.ProgressStyle 多文件分段实时进度与自适应样式；
 * 3. 规范化胶囊参数，优化状态栏胶囊与卡片层级展示。
 */
object LiveUpdatesCompat {
    /** JVM-test fallbacks when no Context is available. Production always passes a Context. */
    const val CHIP_TEXT = "Text"
    const val CHIP_COMPLETED = "Done"
    const val CHIP_WAITING = "Wait"
    const val CHIP_PREPARING = "..."
    const val CHIP_IN = "In "
    const val CHIP_OUT = "Out "

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    private fun getLargeIcon(context: Context): Bitmap? {
        if (cachedLargeIcon == null) {
            cachedLargeIcon =
                runCatching {
                    val drawable =
                        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                            ?: ContextCompat.getDrawable(context, R.drawable.ic_stat_receive)
                    drawable?.toBitmap(width = 128, height = 128)
                }.getOrNull()
        }
        return cachedLargeIcon
    }

    /**
     * 构建具备原生实时活动（Live Updates）与焦点胶囊特性的 Notification。
     */
    fun buildLiveNotification(
        context: Context,
        channelId: String,
        session: TransferSession,
        actionText: String,
        contentIntent: PendingIntent,
        cancelIntent: PendingIntent,
    ): Notification {
        val speedText = if (session.speed > 0) session.formattedSpeed else context.getString(R.string.live_preparing)
        val compactEta = formatCompactEta(session, context)

        // 展开态大标题：发送至 / 接收自 目标设备（简练纯粹，不折行）
        val direction = if (session.isIncoming) context.getString(R.string.live_from_prefix) else context.getString(R.string.live_to_prefix)
        val expandedTitle = "$direction ${session.device.alias}"

        // 展开态核心内容：当前文件名 + 文件序号序号一体化（例如：presentation_demo.mp4 (2/3)）
        val currentFileName =
            session.currentFile?.name
                ?: if (session.isTextMessage) {
                    (session.singleTextMessageContent?.take(40) ?: context.getString(R.string.notif_plain_text_message))
                } else if (session.files.size > 1) {
                    context.getString(R.string.live_files_count, session.files.size)
                } else {
                    context.getString(R.string.live_file_transfer)
                }
        val fileIndexStr = if (session.files.size > 1) " (${session.currentFileIndex + 1}/${session.files.size})" else ""
        val contentText = "$currentFileName$fileIndexStr"

        // 展开态辅助指标：紧贴进度条，展示数据量、速度与预计剩余时间（剔除多余冗余的百分比）
        val sizeInfo = "${session.formattedTransferredSize} / ${session.formattedTotalSize}"
        val metricsSubText =
            if (session.speed > 0) {
                if (compactEta.isNotEmpty()) "$sizeInfo · $speedText · $compactEta" else "$sizeInfo · $speedText"
            } else {
                sizeInfo
            }

        // 胶囊收起态右侧：状态简字 + 实时速度与剩余时间（例如：发 28M/s 3s）
        val chipSpeedEta = formatChipSpeedEta(session, context)

        val smallIconRes = if (session.isIncoming) R.drawable.ic_stat_receive else R.drawable.ic_stat_send

        // Android 16+ (API 36+) 原生 Live Updates 构建
        if (Build.VERSION.SDK_INT >= 36) {
            val nativeBuilder =
                Notification
                    .Builder(context, channelId)
                    .setSmallIcon(smallIconRes)
                    .setContentTitle(expandedTitle)
                    .setContentText(contentText)
                    .setSubText(metricsSubText)
                    .setColor(0xFF00897B.toInt()) // LocalSend 经典 Teal 品牌主色
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(contentIntent)
                    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                    .setShortCriticalText(chipSpeedEta)
                    .addAction(
                        Notification.Action
                            .Builder(
                                android.graphics.drawable.Icon
                                    .createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                                context.getString(R.string.action_cancel_transfer),
                                cancelIntent,
                            ).build(),
                    )

            // 提升型 ongoing 从 API 36.1 才提供；36.0 设备直接调用会 NoSuchMethodError。
            // 该字段同样自 API 36 存在，因此只在 SDK_INT >= 36 的块内读取
            val promotedOngoing = Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
            if (promotedOngoing) {
                nativeBuilder.setRequestPromotedOngoing(true)
            }

            getLargeIcon(context)?.let {
                nativeBuilder.setLargeIcon(
                    android.graphics.drawable.Icon
                        .createWithBitmap(it),
                )
            }

            try {
                val progressStyle =
                    Notification
                        .ProgressStyle()
                        .setProgress(session.progressPercent)
                        .setStyledByProgress(true)

                if (session.files.size > 1 && session.totalBytes > 0) {
                    val segments =
                        session.files.map { file ->
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
            // 未请求提升时不能再打提升标记，否则 36.0 上的通知会被系统按提升型处理
            notification.flags =
                notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                (if (promotedOngoing) Notification.FLAG_PROMOTED_ONGOING else 0)
            return notification
        }

        // Android < 36 兼容构建
        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(smallIconRes)
                .setContentTitle(expandedTitle)
                .setContentText(contentText)
                .setSubText(metricsSubText)
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
                    context.getString(R.string.action_cancel_transfer),
                    cancelIntent,
                )

        getLargeIcon(context)?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    /**
     * 格式化胶囊芯片短文本：状态简字 + 速度和剩余时间（控制在 8~10 字符内，适配状态栏芯片排版）。
     */
    fun formatChipSpeedEta(
        session: TransferSession,
        context: Context? = null,
    ): String {
        if (session.isTextMessage) return context?.getString(R.string.chip_text) ?: CHIP_TEXT
        if (session.status == org.localsend.miuix.model.TransferStatus.Completed) return context?.getString(R.string.chip_completed) ?: CHIP_COMPLETED
        if (session.status == org.localsend.miuix.model.TransferStatus.WaitingApproval) return context?.getString(R.string.chip_waiting_approval) ?: CHIP_WAITING
        if (session.speed <= 0) return context?.getString(R.string.live_preparing) ?: CHIP_PREPARING

        val speedStr = FileItem.formatSpeed(session.speed, compact = true)

        val etaStr =
            when (val eta = RemainingTime.of(session.status, session.speed, session.totalBytes, session.transferredBytes)) {
                is RemainingTime.Seconds -> if (eta.value > 0L) "${eta.value}s" else ""
                is RemainingTime.Minutes -> "${eta.minutes}m"
                is RemainingTime.Hours -> "${eta.hours}h"
                else -> ""
            }

        val prefix =
            if (session.isIncoming) {
                context?.getString(R.string.chip_incoming_prefix) ?: CHIP_IN
            } else {
                context?.getString(R.string.chip_outgoing_prefix) ?: CHIP_OUT
            }
        val speedEta = if (etaStr.isNotEmpty()) "$speedStr $etaStr" else speedStr
        return "$prefix$speedEta"
    }

    /**
     * 计算紧凑且直观的剩余时间文本（针对胶囊态排版优化）。
     */
    private fun formatCompactEta(
        session: TransferSession,
        context: Context,
    ): String =
        when (val eta = RemainingTime.of(session.status, session.speed, session.totalBytes, session.transferredBytes)) {
            RemainingTime.Hidden, RemainingTime.Calculating -> ""
            RemainingTime.AlmostDone -> context.getString(R.string.live_almost_done)
            is RemainingTime.Seconds ->
                if (eta.value <= 0L) {
                    context.getString(R.string.live_almost_done)
                } else {
                    context.getString(R.string.live_remaining_seconds, eta.value)
                }
            is RemainingTime.Minutes ->
                if (eta.seconds == 0L) {
                    context.getString(R.string.live_remaining_minutes, eta.minutes)
                } else {
                    context.getString(R.string.live_remaining_min_sec, eta.minutes, eta.seconds)
                }
            is RemainingTime.Hours -> context.getString(R.string.live_remaining_hours, eta.hours)
        }
}
