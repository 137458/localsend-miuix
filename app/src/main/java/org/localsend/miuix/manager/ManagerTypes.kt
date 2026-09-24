package org.localsend.miuix.manager

import org.localsend.miuix.model.Device

data class AppInfoItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val apkSize: Long,
    val isSystemApp: Boolean
)

data class PinPromptRequest(
    val sessionId: String,
    val device: Device,
    val onPinEntered: (String) -> Unit,
    val onDismiss: () -> Unit
)