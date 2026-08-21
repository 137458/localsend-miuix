package org.localsend.miuix.model

import android.os.Build

data class AppSettings(
    val alias: String = generateDefaultAlias(),
    val port: Int = 53317,
    val multicastGroup: String = "224.0.0.167",
    val downloadPath: String = "",
    val quickSave: Boolean = false,
    val autoFinish: Boolean = false,
    val useHttps: Boolean = false,
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
