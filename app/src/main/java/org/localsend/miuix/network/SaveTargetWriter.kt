package org.localsend.miuix.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.SaveTarget
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * SaveTarget 落盘：MediaStore 公共目录与 SAF 目录树的写入流获取、
 * 重名规避、IS_PENDING 确认与失败回滚删除。
 */
internal class SaveTargetWriter(private val context: Context) {

    /**
     * 打开文件写入流。默认走公共 Download（MediaStore），用户自定义时走 SAF 目录树。
     * 返回目标流；MediaStore 路径写入完成后需调用 [confirmMediaStoreWrite] 清除 IS_PENDING 标记。
     */
    internal fun openSaveStream(fileItem: FileItem, target: SaveTarget, getAutoCategorizeMedia: () -> Boolean): OutputStream = when (target) {
        is SaveTarget.MediaStoreTarget -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IllegalStateException(context.getString(R.string.msg_mediastore_requires_q))
            }
            val pathInfo = org.localsend.miuix.util.SavePathHelper.resolve(fileItem.name)
            val isCategorized = getAutoCategorizeMedia()
            val lowerMime = fileItem.mimeType.lowercase()
            val (baseDir, collection) = if (isCategorized && lowerMime.startsWith("image/")) {
                Environment.DIRECTORY_PICTURES + "/LocalSend" to MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else if (isCategorized && lowerMime.startsWith("video/")) {
                Environment.DIRECTORY_MOVIES + "/LocalSend" to MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else if (isCategorized && lowerMime.startsWith("audio/")) {
                Environment.DIRECTORY_MUSIC + "/LocalSend" to MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                Environment.DIRECTORY_DOWNLOADS + "/LocalSend" to MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = org.localsend.miuix.util.SavePathHelper.buildMediaStoreRelativePath(baseDir, pathInfo.subDirectory)
            val displayName = uniqueMediaName(pathInfo.fileName, relativePath, collection)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, fileItem.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_public_download))
            // 记录实际写入的 Uri（文件名可能被系统自动加后缀），完成后用于清除 PENDING
            fileItem.mediaStoreUri = uri
            fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$displayName" else displayName
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_open_public_download))
        }
        is SaveTarget.UriTarget -> {
            val root = DocumentFile.fromTreeUri(context, target.treeUri)
                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_access_custom_dir))
            val pathInfo = org.localsend.miuix.util.SavePathHelper.resolve(fileItem.name)
            val segments = org.localsend.miuix.util.SavePathHelper.splitSegments(pathInfo.subDirectory)
            var currentDir = root
            for (segment in segments) {
                val found = currentDir.findFile(segment)
                currentDir = if (found != null && found.isDirectory) {
                    found
                } else {
                    currentDir.createDirectory(segment)
                        ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
                }
            }
            val existing = currentDir.listFiles().mapNotNull { it.name }.toHashSet()
            val uniqueName = uniqueTreeName(pathInfo.fileName, existing)
            val created = currentDir.createFile(fileItem.mimeType, uniqueName)
                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
            fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$uniqueName" else uniqueName
            fileItem.mediaStoreUri = created.uri
            context.contentResolver.openOutputStream(created.uri)
                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
        }
    }

    /** MediaStore 写入完成后清除 IS_PENDING，使文件在文件管理器中立即可见。 */
    internal fun confirmMediaStoreWrite(fileItem: FileItem) {
        val uri = fileItem.mediaStoreUri ?: return
        try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        } catch (ignored: Exception) {}
    }

    private val allocatedMediaNames = ConcurrentHashMap.newKeySet<String>()

    /** 基于 MediaStore 指定目标集合与子目录列表生成不重复的文件名。 */
    private fun uniqueMediaName(
        name: String,
        relativePath: String = Environment.DIRECTORY_DOWNLOADS + "/LocalSend/",
        collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else Uri.EMPTY
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return name
        val existing = HashSet<String>()
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(relativePath)
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (index != -1 && !cursor.isNull(index)) {
                            existing.add(cursor.getString(index))
                        }
                    }
                }
        } catch (ignored: Exception) {}
        existing.addAll(allocatedMediaNames)
        var result = name
        if (result in existing) {
            val base = name.substringBeforeLast('.', "").ifEmpty { name }
            val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
            var counter = 1
            while ("$base ($counter)$ext" in existing) counter++
            result = "$base ($counter)$ext"
        }
        allocatedMediaNames.add(result)
        return result
    }

    private fun uniqueTreeName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val base = name.substringBeforeLast('.', "").ifEmpty { name }
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while ("$base ($counter)$ext" in existing) counter++
        return "$base ($counter)$ext"
    }

    /** 删除已写入的文件（用于传输取消或 sha256 校验失败后的临时文件清理）。 */
    internal fun deleteSavedFile(fileItem: FileItem, target: SaveTarget) {
        try {
            val uri = fileItem.mediaStoreUri
            if (uri != null) {
                if (target is SaveTarget.MediaStoreTarget) {
                    context.contentResolver.delete(uri, null, null)
                } else if (target is SaveTarget.UriTarget) {
                    try {
                        DocumentFile.fromSingleUri(context, uri)?.delete()
                    } catch (_: Exception) {}
                }
            }
            if (target is SaveTarget.UriTarget) {
                val root = DocumentFile.fromTreeUri(context, target.treeUri)
                if (root != null) {
                    val pathInfo = org.localsend.miuix.util.SavePathHelper.resolve(fileItem.name)
                    val segments = org.localsend.miuix.util.SavePathHelper.splitSegments(pathInfo.subDirectory)
                    var currentDir: DocumentFile? = root
                    for (segment in segments) {
                        currentDir = currentDir?.findFile(segment)
                        if (currentDir == null || !currentDir.isDirectory) {
                            currentDir = null
                            break
                        }
                    }
                    currentDir?.findFile(pathInfo.fileName)?.delete()
                }
            }
        } catch (ignored: Exception) {}
    }

    /** 释放已分配的 MediaStore 文件名占位（服务停止时调用）。 */
    internal fun clearAllocatedNames() {
        allocatedMediaNames.clear()
    }
}