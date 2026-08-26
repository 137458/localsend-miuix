package org.localsend.miuix.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
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
            DeviceType.tablet -> Icons.Default.Tablet
            DeviceType.desktop -> Icons.Default.Computer
            DeviceType.web -> Icons.Default.Language
            DeviceType.headless, DeviceType.server -> Icons.Default.Storage
        }
    }

    fun getFileIcon(mimeType: String, fileName: String): ImageVector {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            // Android APK 安装包
            lowerMime == "application/vnd.android.package-archive" || lowerName.endsWith(".apk") || lowerName.endsWith(".xapk") || lowerName.endsWith(".apks") -> Icons.Default.Android

            // 图片类型
            lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".heic") || lowerName.endsWith(".svg") ||
                lowerName.endsWith(".ico") -> Icons.Default.Image

            // 视频类型
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".avi") || lowerName.endsWith(".flv") ||
                lowerName.endsWith(".wmv") || lowerName.endsWith(".webm") || lowerName.endsWith(".3gp") -> Icons.Default.Movie

            // 音频类型
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") ||
                lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".ogg") || lowerName.endsWith(".wma") -> Icons.Default.MusicNote

            // 文件夹 / 目录
            lowerMime == "directory" || lowerMime == "folder" || lowerName.endsWith("/") || lowerName.contains("/") -> Icons.Default.Folder

            // 压缩包归档
            lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("compressed") ||
                lowerMime.contains("7z") || lowerMime.contains("rar") ||
                lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") -> Icons.Default.Archive

            // 文本与代码文档
            lowerMime.startsWith("text/") || lowerMime == "application/json" || lowerMime == "application/xml" ||
                lowerMime == "application/javascript" || lowerMime == "application/x-sh" ||
                lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".log") ||
                lowerName.endsWith(".json") || lowerName.endsWith(".xml") || lowerName.endsWith(".kt") ||
                lowerName.endsWith(".java") || lowerName.endsWith(".py") || lowerName.endsWith(".js") ||
                lowerName.endsWith(".ts") || lowerName.endsWith(".html") || lowerName.endsWith(".css") ||
                lowerName.endsWith(".c") || lowerName.endsWith(".cpp") || lowerName.endsWith(".h") ||
                lowerName.endsWith(".sh") || lowerName.endsWith(".bat") || lowerName.endsWith(".sql") -> Icons.Default.TextFields

            // 通用文档类型
            else -> Icons.Default.Description
        }
    }
}
