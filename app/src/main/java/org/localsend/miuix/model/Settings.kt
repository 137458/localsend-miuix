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
    val autoFinish: Boolean = false,
    val useHttps: Boolean = false,
    // 接收方 PIN 保护：非空时，发送方需在 prepare-upload/download 携带 ?pin= 且匹配才被接受
    val pin: String? = null,
    val themeModeIndex: Int = 0 // 0: System, 1: Light, 2: Dark, 3: MonetSystem, 4: MonetLight, 5: MonetDark
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
