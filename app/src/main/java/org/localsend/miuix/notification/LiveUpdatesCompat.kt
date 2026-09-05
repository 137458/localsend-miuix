package org.localsend.miuix.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.localsend.miuix.model.TransferSession

/**
 * 实时活动与流体云适配器（Live Updates & Aqua Dynamics Compat）。
 * 深度兼容：
 * 1. ColorOS / OxygenOS 14/15/16 泛在服务流体云（Aqua Dynamics / 状态栏胶囊 / 锁屏卡片）；
 * 2. Android 16+ (API 36+) 原生 Live Updates API (Notification.ProgressStyle 与 Promoted Notifications)。
 */
object LiveUpdatesCompat {

    /**
     * 为传输进度通知附加 ColorOS 流体云（Aqua Dynamics）与原生 Live Updates 属性。
     */
    fun applyLiveUpdates(
        context: Context,
        builder: NotificationCompat.Builder,
        session: TransferSession,
        actionText: String
    ) {
        val speedText = if (session.speed > 0) session.formattedSpeed else "准备中"
        val capsuleTitle = "$actionText ${session.device.alias}"
        val capsuleContent = "${session.progressPercent}% · $speedText"

        // 1. 声明为持续性前台任务（系统胶囊化与流体云上浮核心必要条件）
        builder.setOngoing(true)
        builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
        builder.setOnlyAlertOnce(true)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // 2. ColorOS / OxygenOS (Pantanal / Aqua Dynamics) 专有流体云参数适配
        val colorOsExtras = Bundle().apply {
            // 声明流体云视图类型为胶囊/实时卡片
            putString("oplus.view.type", "capsule")
            putBoolean("oplus.capsule.enable", true)
            putString("oplus.capsule.title", capsuleTitle)
            putString("oplus.capsule.content", capsuleContent)
            putString("oplus.capsule.status", if (session.progressPercent >= 100) "finished" else "running")
            putString("android.substName", "LocalSend")
            putInt("android.progressMax", 100)
            putInt("android.progress", session.progressPercent)
            // 兼容性字段：OPPO 泛在服务卡片意图标识
            putString("intelligent_intent_type", "local_transfer")
        }
        builder.addExtras(colorOsExtras)

        // 3. Android 16+ (API 36+) 原生 Live Updates (ProgressStyle) 动态适配
        if (Build.VERSION.SDK_INT >= 36) {
            applyAndroid16ProgressStyle(builder, session)
        }
    }

    /**
     * 通过安全反射在 Android 16+ (API 36+) 上应用 Notification.ProgressStyle。
     * 使用反射可确保在 Android 8.0 ~ 15 设备上绝不会触发类加载崩溃或验证异常。
     */
    private fun applyAndroid16ProgressStyle(
        builder: NotificationCompat.Builder,
        session: TransferSession
    ) {
        try {
            val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val progressStyle = progressStyleClass.getConstructor().newInstance()
            val setProgressMethod = progressStyleClass.getMethod("setProgress", Int::class.javaPrimitiveType)
            setProgressMethod.invoke(progressStyle, session.progressPercent)

            // 将 ProgressStyle 结构注入底层 Notification Extras
            val extras = builder.extras
            extras.putString("android.template", progressStyleClass.name)
            extras.putInt("android.progress", session.progressPercent)
            extras.putInt("android.progressMax", 100)
            extras.putBoolean("android.progressIndeterminate", session.totalBytes <= 0)
        } catch (ignored: Throwable) {
            // 在不支持该 API 的系统或变体版本上静默回退为标准进度
        }
    }
}
