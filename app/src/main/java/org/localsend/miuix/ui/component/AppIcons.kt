package org.localsend.miuix.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import org.localsend.miuix.model.DeviceType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit

object AppIcons {
    val Send: ImageVector = Icons.AutoMirrored.Filled.Send
    val Receive: ImageVector = Icons.Default.Download
    val Settings: ImageVector = MiuixIcons.Settings
    val Refresh: ImageVector = Icons.Default.Refresh
    val Scan: ImageVector = Icons.Default.QrCodeScanner
    val Folder: ImageVector = Icons.Default.Folder
    val Text: ImageVector = Icons.Default.TextFields
    val Wifi: ImageVector = Icons.Default.Wifi
    val Link: ImageVector = Icons.Default.Link
    val Copy: ImageVector = Icons.Default.ContentCopy
    val History: ImageVector = Icons.Default.History

    fun getDeviceIcon(type: DeviceType): ImageVector {
        return when (type) {
            DeviceType.mobile -> Icons.Default.PhoneAndroid
            DeviceType.desktop -> Icons.Default.Computer
            DeviceType.web -> Icons.Default.Language
            DeviceType.headless, DeviceType.server -> Icons.Default.Storage
        }
    }

    fun getFileIcon(mimeType: String, fileName: String): ImageVector {
        return when {
            mimeType.startsWith("image/") || fileName.endsWith(".png", true) || fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".webp", true) -> Icons.Default.Image
            mimeType.startsWith("video/") || fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".mov", true) -> Icons.Default.Movie
            mimeType.startsWith("audio/") || fileName.endsWith(".mp3", true) || fileName.endsWith(".flac", true) || fileName.endsWith(".wav", true) -> Icons.Default.MusicNote
            mimeType.startsWith("text/") || fileName.endsWith(".txt", true) || fileName.endsWith(".md", true) -> Icons.Default.TextFields
            else -> Icons.Default.Description
        }
    }
}
