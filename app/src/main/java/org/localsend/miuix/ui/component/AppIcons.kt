package org.localsend.miuix.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import org.localsend.miuix.model.DeviceType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings

object AppIcons {
    val Send: ImageVector = MiuixIcons.Send
    val Receive: ImageVector = MiuixIcons.Download
    val Settings: ImageVector = MiuixIcons.Settings
    val Refresh: ImageVector = MiuixIcons.Refresh
    val Scan: ImageVector = MiuixIcons.Scan
    val Folder: ImageVector = MiuixIcons.Folder
    val Text: ImageVector = Icons.Default.TextFields
    val Wifi: ImageVector = Icons.Default.Wifi
    val Link: ImageVector = MiuixIcons.Link
    val Copy: ImageVector = MiuixIcons.Copy
    val History: ImageVector = MiuixIcons.Recent
    val Star: ImageVector = MiuixIcons.Favorites
    val StarBorder: ImageVector = Icons.Default.StarBorder
    val Dialpad: ImageVector = Icons.Default.Dialpad
    val Check: ImageVector = MiuixIcons.Ok

    fun getDeviceIcon(type: DeviceType): ImageVector =
        when (type) {
            DeviceType.mobile -> MiuixIcons.Phone
            DeviceType.tablet -> Icons.Default.Tablet
            DeviceType.desktop -> Icons.Default.Computer
            DeviceType.web -> Icons.Default.Language
            DeviceType.headless, DeviceType.server -> Icons.Default.Storage
        }

    fun getFileIcon(
        mimeType: String,
        fileName: String,
    ): ImageVector {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            // Android APK 安装包
            lowerMime == "application/vnd.android.package-archive" || lowerName.endsWith(".apk") || lowerName.endsWith(".xapk") || lowerName.endsWith(".apks") -> Icons.Default.Android

            // 图片类型
            lowerMime.startsWith("image/") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".webp") ||
                lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".heic") ||
                lowerName.endsWith(".svg") ||
                lowerName.endsWith(".ico") -> MiuixIcons.Image

            // 视频类型
            lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") ||
                lowerName.endsWith(".avi") ||
                lowerName.endsWith(".flv") ||
                lowerName.endsWith(".wmv") ||
                lowerName.endsWith(".webm") ||
                lowerName.endsWith(".3gp") -> Icons.Default.Movie

            // 音频类型
            lowerMime.startsWith("audio/") ||
                lowerName.endsWith(".mp3") ||
                lowerName.endsWith(".flac") ||
                lowerName.endsWith(".wav") ||
                lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".aac") ||
                lowerName.endsWith(".ogg") ||
                lowerName.endsWith(".wma") -> MiuixIcons.Music

            // 文件夹 / 目录
            lowerMime == "directory" || lowerMime == "folder" || lowerName.endsWith("/") || lowerName.contains("/") -> MiuixIcons.Folder

            // 压缩包归档
            lowerMime.contains("zip") ||
                lowerMime.contains("tar") ||
                lowerMime.contains("compressed") ||
                lowerMime.contains("7z") ||
                lowerMime.contains("rar") ||
                lowerName.endsWith(".zip") ||
                lowerName.endsWith(".rar") ||
                lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") ||
                lowerName.endsWith(".gz") ||
                lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") -> Icons.Default.Archive

            // 文本与代码文档
            lowerMime.startsWith("text/") ||
                lowerMime == "application/json" ||
                lowerMime == "application/xml" ||
                lowerMime == "application/javascript" ||
                lowerMime == "application/x-sh" ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".log") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".kt") ||
                lowerName.endsWith(".java") ||
                lowerName.endsWith(".py") ||
                lowerName.endsWith(".js") ||
                lowerName.endsWith(".ts") ||
                lowerName.endsWith(".html") ||
                lowerName.endsWith(".css") ||
                lowerName.endsWith(".c") ||
                lowerName.endsWith(".cpp") ||
                lowerName.endsWith(".h") ||
                lowerName.endsWith(".sh") ||
                lowerName.endsWith(".bat") ||
                lowerName.endsWith(".sql") -> Icons.Default.TextFields

            // 通用文档类型
            else -> MiuixIcons.File
        }
    }
}
