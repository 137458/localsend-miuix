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

        // 胶囊收起态左侧：状态与进度（例如：发送中 45% 或 接收完成）
        val capsuleLeftStatus = if (session.isTextMessage) {
            actionType
        } else if (session.status == org.localsend.miuix.model.TransferStatus.Completed) {
            if (session.isIncoming) "接收完成" else "发送完成"
        } else if (session.status == org.localsend.miuix.model.TransferStatus.WaitingApproval) {
            "等待确认"
        } else {
            "$actionType ${session.progressPercent}%"
        }

        // 胶囊收起态右侧：实时速度与剩余时间（例如：28.5 MB/s · 剩余3秒）
        val capsuleRightSpeedEta = if (session.isTextMessage) {
            "纯文本"
        } else if (session.status == org.localsend.miuix.model.TransferStatus.Completed) {
            "传输完毕"
        } else if (compactEta.isNotEmpty() && compactEta != "即将完成") {
            "$speedText · $compactEta"
        } else if (compactEta == "即将完成") {
            "$speedText · 即将完成"
        } else {
            speedText
        }

        // 展开态大标题：正在接收 (1/3) · 目标设备
        val fullActionType = if (session.isIncoming) "正在接收" else "正在发送"
        val expandedTitle = "$fullActionType$fileIndexInfo · ${session.device.alias}"

        // 展开态详细内容：45% · 54.2 MB / 120.5 MB · 28.5 MB/s · 剩余3秒
        val contentText = "${session.progressPercent}% · ${session.formattedTransferredSize} / ${session.formattedTotalSize} · $speedText$etaPart"

        // 展开态副标题：当前文件名或文本摘要
        val currentFileName = session.currentFile?.name
            ?: if (session.isTextMessage) "纯文本消息" else if (session.files.size > 1) "共 ${session.files.size} 个文件" else null

        // 状态栏胶囊与芯片文案（兼顾原生 Android 16 与 ColorOS）
        val chipText = "$capsuleLeftStatus · $capsuleRightSpeedEta"

        val smallIconRes = if (session.isIncoming) R.drawable.ic_stat_receive else R.drawable.ic_stat_send

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
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

        // 注入 ColorOS / OxygenOS 泛在服务流体云专有参数、小米 HyperOS 焦点通知与原生 Android 16 Promoted Extra
        val extras = Bundle().apply {
            // Android 16 显式标记
            putBoolean("android.requestPromotedOngoing", true)
            putInt("android.progress", session.progressPercent)
            putInt("android.progressMax", 100)
            putBoolean("android.progressIndeterminate", session.totalBytes <= 0)

            // ColorOS 泛在服务 / 流体云胶囊核心参数 (Pantanal / Aqua Dynamics)
            // 左边显示状态（例如：发送中 45%），右边显示速度和剩余时间（例如：28.5 MB/s · 剩余3秒）
            putString("oplus.view.type", "capsule")
            putBoolean("oplus.capsule.enable", true)

            // 1. ColorOS 胶囊左右两侧标准规范字段（leftText / rightText 双文本模式，不传 leftImg 避免覆盖状态文本）
            putString("oplus.capsule.leftText", capsuleLeftStatus)
            putString("oplus.capsule.rightText", capsuleRightSpeedEta)
            putString("oplus.capsule.left_text", capsuleLeftStatus)
            putString("oplus.capsule.right_text", capsuleRightSpeedEta)
            putString("oplus.capsule.title", capsuleLeftStatus)
            putString("oplus.capsule.content", capsuleRightSpeedEta)
            putString("oplus.capsule.status", if (session.progressPercent >= 100) "finished" else "running")
            putString("oplus.capsule.legacyText", "$capsuleLeftStatus · $capsuleRightSpeedEta")
            putString("oplus.capsule.legacy_text", "$capsuleLeftStatus · $capsuleRightSpeedEta")

            // 展开态卡片内容
            putString("oplus.capsule.ext_title", expandedTitle)
            putString("oplus.capsule.ext_content", contentText)

            // 2. 顶层扁平化兼容字段（部分 ColorOS / 系统框架直接在 extras 顶层索引）
            putString("leftText", capsuleLeftStatus)
            putString("rightText", capsuleRightSpeedEta)
            putString("left_text", capsuleLeftStatus)
            putString("right_text", capsuleRightSpeedEta)
            putString("capsule_left_text", capsuleLeftStatus)
            putString("capsule_right_text", capsuleRightSpeedEta)
            putString("legacyText", "$capsuleLeftStatus · $capsuleRightSpeedEta")

            // 3. 嵌套子 Bundle 结构兼容（针对 getBundle("oplus.capsule") 或 getBundle("capsule")）
            val capsuleSubBundle = Bundle().apply {
                putString("leftText", capsuleLeftStatus)
                putString("rightText", capsuleRightSpeedEta)
                putString("left_text", capsuleLeftStatus)
                putString("right_text", capsuleRightSpeedEta)
                putString("title", capsuleLeftStatus)
                putString("content", capsuleRightSpeedEta)
                putString("ext_title", expandedTitle)
                putString("ext_content", contentText)
                putString("status", if (session.progressPercent >= 100) "finished" else "running")
                putString("legacyText", "$capsuleLeftStatus · $capsuleRightSpeedEta")
            }
            putBundle("oplus.capsule", capsuleSubBundle)
            putBundle("capsule", capsuleSubBundle)

            // 4. OPPO 泛在服务意图共享 JSON 数据结构（IntelligentIntent / androidOppoIntelligentIntent）
            val intentJson = buildIntelligentIntentJson(
                sessionId = session.sessionId,
                leftText = capsuleLeftStatus,
                rightText = capsuleRightSpeedEta,
                title = expandedTitle,
                content = contentText,
                isFinished = session.progressPercent >= 100
            )
            putString("androidOppoIntelligentIntent", intentJson)
            putString("intelligent_intent", intentJson)
            putString("intelligentIntent", intentJson)
            putString("android.substName", "LocalSend")
            putString("intelligent_intent_type", "local_transfer")

            // 5. 小米 HyperOS 焦点通知 / 超级岛协议扩展参数
            val safeTitle = expandedTitle.replace("\"", "\\\"")
            val safeContent = contentText.replace("\"", "\\\"")
            val safeLeft = capsuleLeftStatus.replace("\"", "\\\"")
            val safeRight = capsuleRightSpeedEta.replace("\"", "\\\"")
            val focusJson = """
                {
                    "param_v2": {
                        "title": "$safeTitle",
                        "content": "$safeContent",
                        "sub_title": "$safeLeft",
                        "summary": "$safeRight",
                        "ticker": "$safeLeft · $safeRight"
                    }
                }
            """.trimIndent()
            putString("miui.focus.param", focusJson)
            putBoolean("miui.focus.enable", true)
        }
        builder.addExtras(extras)

        val notification = builder.build()

        // 附加 FLAG_ONGOING_EVENT 与 FLAG_PROMOTED_ONGOING 标志位（容错底层系统）
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or 262144

        return notification
    }

    /**
     * 构建 OPPO / ColorOS 泛在服务流体云 IntelligentIntent 意图共享 JSON。
     */
    private fun buildIntelligentIntentJson(
        sessionId: String,
        leftText: String,
        rightText: String,
        title: String,
        content: String,
        isFinished: Boolean
    ): String {
        val intentAction = if (isFinished) 2 else 1
        val timestamp = System.currentTimeMillis()
        val safeLeftText = leftText.replace("\"", "\\\"")
        val safeRightText = rightText.replace("\"", "\\\"")
        val safeTitle = title.replace("\"", "\\\"")
        val safeContent = content.replace("\"", "\\\"")
        val combinedText = "$safeLeftText · $safeRightText"

        return """
            {
                "intentName": "local_transfer",
                "identifier": "$sessionId",
                "intentAction": $intentAction,
                "timestamp": $timestamp,
                "serviceId": {
                    "fluidCloud": "transfer"
                },
                "intentEntity": {
                    "entityName": "SERVICE",
                    "entityId": "$sessionId",
                    "capsule": {
                        "leftText": "$safeLeftText",
                        "rightText": "$safeRightText"
                    },
                    "legacyText": "$combinedText",
                    "primary": {
                        "title": "$safeTitle",
                        "content": "$safeContent"
                    }
                }
            }
        """.trimIndent()
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
