package org.localsend.miuix.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import org.localsend.miuix.model.FileItem
import java.io.File

object ShareIntentHelper {

    /**
     * 判断传入的 action 字符串是否属于收发/分享/打开文件 action。
     */
    fun isShareAction(action: String?): Boolean {
        if (action == null) return false
        return action == Intent.ACTION_SEND ||
                action == Intent.ACTION_SEND_MULTIPLE ||
                action == Intent.ACTION_VIEW
    }

    /**
     * 判断传入的 [Intent] 是否属于外部应用发起的收发/分享/打开文件意图。
     */
    fun isShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return isShareAction(intent.action)
    }

    /**
     * 从外部传入的 [Intent] 中解析出待发送的 [FileItem] 列表。
     * 支持 ACTION_SEND（单文件/纯文本）、ACTION_SEND_MULTIPLE（多文件）和 ACTION_VIEW（从文件管理器打开）。
     */
    fun extractShareItems(context: Context, intent: Intent?): List<FileItem> {
        if (intent == null || !isShareIntent(intent)) return emptyList()

        val action = intent.action
        val items = mutableListOf<FileItem>()
        val uris = mutableListOf<Uri>()

        // 1. 提取 Extra Streams 与 ClipData 中的 URI
        when (action) {
            Intent.ACTION_SEND -> {
                getStreamUri(intent)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                uris.addAll(getStreamUris(intent))
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
        }

        // 补充从 ClipData 中获取（很多应用仅将 Uri 挂在 ClipData 下）
        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                val clipUri = clipData.getItemAt(i)?.uri
                if (clipUri != null && !uris.contains(clipUri)) {
                    uris.add(clipUri)
                }
            }
        }

        // 将所有解析出的 URI 转换为 FileItem
        for (uri in uris) {
            try {
                val fileItem = resolveFileItemFromUri(context, uri)
                items.add(fileItem)
            } catch (ignored: Exception) {
                // 单个文件解析异常不影响其它文件
            }
        }

        // 2. 提取文本内容（仅针对 ACTION_SEND 或无文件时的备选）
        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: (if (uris.isEmpty()) extractTextFromClipData(intent) else null)

        val trimmedText = rawText?.trim()
        if (!trimmedText.isNullOrEmpty()) {
            // 避免把与文件 URI 相同的字符串误作为文本添加
            val isSameAsUri = uris.any { it.toString() == trimmedText }
            if (!isSameAsUri) {
                val bytes = trimmedText.toByteArray(Charsets.UTF_8)
                items.add(
                    FileItem(
                        name = "text_${System.currentTimeMillis()}.txt",
                        size = bytes.size.toLong(),
                        textContent = trimmedText,
                        mimeType = "text/plain"
                    )
                )
            }
        }

        return items
    }

    private fun extractTextFromClipData(intent: Intent): String? {
        val clipData = intent.clipData ?: return null
        val sb = StringBuilder()
        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i)?.text?.toString()
            if (!text.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(text)
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun getStreamUri(intent: Intent): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getStreamUris(intent: Intent): List<Uri> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 将 Uri 解析为带有真实文件名、文件大小与 MIME 类型的 [FileItem]。
     */
    fun resolveFileItemFromUri(context: Context, uri: Uri): FileItem {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        var mimeType = "application/octet-stream"

        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex) ?: name
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                name = uri.lastPathSegment ?: name
            }

            // 若游标未查到大小，尝试 openAssetFileDescriptor 探测
            if (size <= 0L) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val len = afd.length
                        if (len > 0L) size = len
                    }
                } catch (ignored: Exception) {}
            }

            // 若仍未查到大小，尝试 DocumentFile
            if (size <= 0L) {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    if (docFile != null) {
                        if (name.startsWith("file_") && !docFile.name.isNullOrBlank()) {
                            name = docFile.name!!
                        }
                        if (docFile.length() > 0L) {
                            size = docFile.length()
                        }
                    }
                } catch (ignored: Exception) {}
            }

            mimeType = context.contentResolver.getType(uri) ?: mimeType
        } else if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    if (!extension.isNullOrEmpty()) {
                        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: mimeType
                    }
                }
            }
        }

        // 若 MIME 类型仍为未知且文件名有后缀，做二次探测
        if (mimeType == "application/octet-stream" && name.contains(".")) {
            val ext = name.substringAfterLast('.').lowercase()
            val detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (detected != null) {
                mimeType = detected
            }
        }

        return FileItem(
            name = name,
            size = size,
            uri = uri,
            mimeType = mimeType
        )
    }
}
