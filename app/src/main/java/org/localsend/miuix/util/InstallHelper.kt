package org.localsend.miuix.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 单个文件条目的可访问来源：真实磁盘文件或 content 流。 */
sealed interface InstallSource {
    /** 真实磁盘文件（走 FileProvider 共享）。 */
    data class DiskFile(
        val path: String,
    ) : InstallSource

    /** content:// 流（MediaStore/SAF），按历史记录既有 ACTION_VIEW 模式共享。 */
    data class ContentUri(
        val uriString: String,
    ) : InstallSource
}

/** 传输结果条目中与打开/安装判定相关的最小信息。 */
data class OpenableFile(
    val name: String,
    val path: String?,
    val uriString: String?,
    val mimeType: String = "application/octet-stream",
)

/** APK 安装引导与完成通知“直达文件”的挑选逻辑；纯判定函数保持 JVM 可测。 */
object InstallHelper {
    /** Android 包安装器识别的 APK MIME 类型。 */
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** 大小写不敏感的 .apk 后缀判定。 */
    fun isApk(fileName: String): Boolean = fileName.endsWith(".apk", ignoreCase = true)

    /** 从文件名列表中选出第一个 apk 条目（无则 null）。 */
    fun firstApkName(fileNames: List<String>): String? = fileNames.firstOrNull { isApk(it) }

    /** 以 FileProvider URI 构造安装意图（apk 落盘为真实文件时使用）；路径未被 file_paths 覆盖等映射失败返回 null。 */
    fun buildInstallIntent(
        context: Context,
        file: File,
    ): Intent? =
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            buildInstallIntent(context, uri)
        } catch (e: Exception) {
            null
        }

    /** 以既有 content uri 构造安装意图（MediaStore/SAF 落盘时使用）。 */
    fun buildInstallIntent(
        context: Context,
        uri: Uri,
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/** 判定单个条目的可访问来源：磁盘文件需真实存在，否则退回 content uri；均不可达返回 null。 */
fun pickFileSource(
    path: String?,
    uriString: String?,
): InstallSource? {
    if (path != null && File(path).isFile) return InstallSource.DiskFile(path)
    if (!uriString.isNullOrBlank()) return InstallSource.ContentUri(uriString)
    return null
}

/** 按条目顺序挑选第一个可打开的文件（多文件场景即主文件）；均不可达返回 null。 */
fun pickOpenableFile(files: List<OpenableFile>): OpenablePick? {
    for (file in files) {
        val source = pickFileSource(file.path, file.uriString) ?: continue
        return OpenablePick(file, source)
    }
    return null
}

/** 挑选结果：被选中的条目与其可访问来源。 */
data class OpenablePick(
    val file: OpenableFile,
    val source: InstallSource,
)
