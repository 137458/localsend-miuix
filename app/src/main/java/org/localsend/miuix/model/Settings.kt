package org.localsend.miuix.model

import android.os.Build

data class AppSettings(
    val alias: String = generateDefaultAlias(),
    val port: Int = 53317,
    val multicastGroup: String = "224.0.0.167",
    val downloadPath: String = "",
    // SAF 目录树 Uri（用户自定义保存位置）。非空时优先使用该 Uri 作为保存目标
    val downloadTreeUri: String? = null,
    // 自定义保存目录的显示名（便于设置页展示，避免暴露原始 Uri）
    val downloadDisplay: String? = null,
    val quickSave: Boolean = false,
    val autoCopyText: Boolean = false, // 接收纯文本后自动写入剪贴板
    val saveToHistory: Boolean = true, // 是否记录传输历史
    val autoFinish: Boolean = false,
    val useHttps: Boolean = false,
    val deviceType: DeviceType = DeviceType.mobile,
    // 是否允许其他方通过 Download(Web Share) API 请求本机文件，announce 时置入 download 字段
    val download: Boolean = false,
    // 接收方 PIN 保护：非空时，发送方需在 prepare-upload/download 携带 ?pin= 且匹配才被接受
    val pin: String? = null,
    val themeModeIndex: Int = 0, // 0: System, 1: Light, 2: Dark, 3: MonetSystem, 4: MonetLight, 5: MonetDark
    val vibrateOnComplete: Boolean = true,
    val lastSelectedTabIndex: Int = 0 // 记录离开时的 Tab 索引，进程恢复时自动还原页面
) {
    companion object {
        private val ADJECTIVES = listOf(
            "Cool", "Fast", "Smart", "Brave", "Silent", "Cosmic", "Lunar", "Solar", "Happy", "Lucky",
            "Swift", "Bright", "Mighty", "Gentle", "Magic", "Hyper", "Vibrant", "Active", "Dynamic"
        )
        private val NOUNS = listOf(
            "Xiaomi", "Dragon", "Phoenix", "Tiger", "Falcon", "Panda", "Fox", "Wolf", "Eagle", "Lion",
            "Device", "HyperOS", "Pixel", "Nova", "Star", "Comet", "Rocket", "Storm", "Spark"
        )

        fun generateDefaultAlias(): String {
            val adj = ADJECTIVES.random()
            val noun = NOUNS.random()
            return "$adj $noun"
        }
    }
}
