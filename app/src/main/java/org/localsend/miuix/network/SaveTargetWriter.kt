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
import org.localsend.miuix.util.SavePathHelper
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * SaveTarget 落盘：MediaStore 公共目录与 SAF 目录树的写入流获取、
 * 重名规避、IS_PENDING 确认与失败回滚删除。
 */
internal class SaveTargetWriter(
    private val context: Context,
) {
    /**
     * 打开文件写入流。默认走公共 Download（MediaStore），用户自定义时走 SAF 目录树。
     * 返回目标流；MediaStore 路径写入完成后需调用 [confirmMediaStoreWrite] 清除 IS_PENDING 标记。
     *
     * [deferCommit] 为 true 时用于「流式校验待确认」的文件：SAF 目标先以临时名落盘，
     * 校验通过后由 [finishDeferredWrite] 重命名为最终名；MediaStore 本身以 IS_PENDING 隐藏，
     * 因此无需改名，只延迟 clearing pending。
     */
    internal fun openSaveStream(
        fileItem: FileItem,
        target: SaveTarget,
        deferCommit: Boolean = false,
        getAutoCategorizeMedia: () -> Boolean,
    ): OutputStream =
        when (target) {
            is SaveTarget.MediaStoreTarget -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException(context.getString(R.string.msg_mediastore_requires_q))
                }
                val pathInfo =
                    org.localsend.miuix.util.SavePathHelper
                        .resolve(fileItem.name)
                val isCategorized = getAutoCategorizeMedia()
                val lowerMime = fileItem.mimeType.lowercase()
                val (baseDir, collection) =
                    if (isCategorized && lowerMime.startsWith("image/")) {
                        Environment.DIRECTORY_PICTURES + "/LocalSend" to MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else if (isCategorized && lowerMime.startsWith("video/")) {
                        Environment.DIRECTORY_MOVIES + "/LocalSend" to MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else if (isCategorized && lowerMime.startsWith("audio/")) {
                        Environment.DIRECTORY_MUSIC + "/LocalSend" to MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        Environment.DIRECTORY_DOWNLOADS + "/LocalSend" to MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                val relativePath =
                    org.localsend.miuix.util.SavePathHelper
                        .buildMediaStoreRelativePath(baseDir, pathInfo.subDirectory)
                val displayName = uniqueMediaName(pathInfo.fileName, relativePath, collection)
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, fileItem.mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                val uri =
                    context.contentResolver.insert(collection, values)
                        ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_public_download))
                // 记录实际写入的 Uri（文件名可能被系统自动加后缀），完成后用于清除 PENDING
                fileItem.mediaStoreUri = uri
                fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$displayName" else displayName
                context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException(context.getString(R.string.msg_cannot_open_public_download))
            }
            is SaveTarget.UriTarget -> {
                val root =
                    DocumentFile.fromTreeUri(context, target.treeUri)
                        ?: throw IllegalStateException(context.getString(R.string.msg_cannot_access_custom_dir))
                val pathInfo =
                    org.localsend.miuix.util.SavePathHelper
                        .resolve(fileItem.name)
                val segments =
                    org.localsend.miuix.util.SavePathHelper
                        .splitSegments(pathInfo.subDirectory)
                var currentDir = root
                for (segment in segments) {
                    val found = currentDir.findFile(segment)
                    currentDir =
                        if (found != null && found.isDirectory) {
                            found
                        } else {
                            currentDir.createDirectory(segment)
                                ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
                        }
                }
                val existing = currentDir.listFiles().mapNotNull { it.name }.toHashSet()
                val uniqueName = uniqueTreeName(pathInfo.fileName, existing)
                // 待确认的写入使用本应用专有临时名，避免损坏内容以最终文件名可见；确认后重命名或复制转正
                val createName = if (deferCommit) DeferredTempNames.of(uniqueName, fileItem.id) else uniqueName
                val created =
                    currentDir.createFile(fileItem.mimeType, createName)
                        ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
                // 记录最终名：确认时按此重命名；删除时以 mediaStoreUri 为准，不依赖此名
                fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$uniqueName" else uniqueName
                fileItem.mediaStoreUri = created.uri
                context.contentResolver.openOutputStream(created.uri)
                    ?: throw IllegalStateException(context.getString(R.string.msg_cannot_create_custom_file))
            }
        }

    /** MediaStore 写入完成后清除 IS_PENDING，使文件在文件管理器中立即可见；返回是否确认成功。 */
    internal fun confirmMediaStoreWrite(fileItem: FileItem): Boolean {
        val uri = fileItem.mediaStoreUri ?: return false
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (ignored: Exception) {
            false
        }
    }

    /**
     * 待确认文件转正：MediaStore 清除 pending；SAF 优先重命名为最终名，
     * provider 不支持重命名时退化为“复制到最终位置后再删临时文件”。
     */
    internal fun finishDeferredWrite(
        fileItem: FileItem,
        target: SaveTarget,
    ): Boolean =
        when (target) {
            is SaveTarget.MediaStoreTarget -> confirmMediaStoreWrite(fileItem)
            is SaveTarget.UriTarget -> {
                val uri = fileItem.mediaStoreUri ?: return false
                val tempDoc = DocumentFile.fromSingleUri(context, uri) ?: return false
                commitDeferredWithFallback(
                    rename = { renameDeferredFile(fileItem, tempDoc) },
                    copyToFinal = { copyDeferredFile(fileItem, tempDoc) },
                    deleteTemp = { tempDoc.delete() },
                )
            }
        }

    /**
     * 复制回退：新建最终文件并流式写入，成功后才删除临时文件。
     * 任一步失败即删除可能存在的半成品，返回 false，由调用方回滚临时文件。
     */
    private fun copyDeferredFile(
        fileItem: FileItem,
        tempDoc: DocumentFile,
    ): Boolean {
        val parent = tempDoc.parentFile ?: return false
        val pathInfo = SavePathHelper.resolve(fileItem.name)
        val existing =
            parent
                .listFiles()
                .mapNotNull { it.name }
                .filter { it != tempDoc.name }
                .toHashSet()
        val uniqueName = uniqueTreeName(pathInfo.fileName, existing)
        val created = parent.createFile(fileItem.mimeType, uniqueName) ?: return false
        val copied =
            try {
                context.contentResolver.openInputStream(tempDoc.uri)?.use { input ->
                    context.contentResolver.openOutputStream(created.uri)?.use { output ->
                        input.copyTo(output, 128 * 1024)
                        true
                    } ?: false
                } ?: false
            } catch (ignored: Exception) {
                false
            }
        if (!copied) {
            // 宁可失败也不留半份最终文件
            runCatching { created.delete() }
            return false
        }
        runCatching { tempDoc.delete() }
        fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$uniqueName" else uniqueName
        fileItem.mediaStoreUri = created.uri
        return true
    }

    /**
     * 清理上次进程被杀遗留的待确认临时文件：只扫描当前 SAF 目标目录，深度与条目数有上限，
     * 且只删除命名符合 [DeferredTempNames] 的文件。MediaStore 目标没有此类临时文件名，直接跳过。
     */
    internal fun sweepOrphanedTempFiles(target: SaveTarget): Int {
        if (target !is SaveTarget.UriTarget) return 0
        val root =
            runCatching { DocumentFile.fromTreeUri(context, target.treeUri) }.getOrNull()
                ?: return 0
        return TempFileSweeper().sweep(DocumentSweepNode(root))
    }

    private class DocumentSweepNode(
        private val doc: DocumentFile,
    ) : SweepNode {
        override val name: String? get() = runCatching { doc.name }.getOrNull()

        override val isDirectory: Boolean get() = runCatching { doc.isDirectory }.getOrDefault(false)

        override fun children(): List<SweepNode> = doc.listFiles().map { DocumentSweepNode(it) }

        override fun delete(): Boolean = doc.delete()
    }

    private fun renameDeferredFile(
        fileItem: FileItem,
        tempDoc: DocumentFile,
    ): Boolean {
        val parent = tempDoc.parentFile ?: return false
        val pathInfo = SavePathHelper.resolve(fileItem.name)
        // 待确认期间目录可能新增文件，转正前重新查重
        val existing =
            parent
                .listFiles()
                .mapNotNull { it.name }
                .filter { it != tempDoc.name }
                .toHashSet()
        val uniqueName = uniqueTreeName(pathInfo.fileName, existing)
        if (!tempDoc.renameTo(uniqueName)) return false
        fileItem.name = if (pathInfo.subDirectory.isNotEmpty()) "${pathInfo.subDirectory}/$uniqueName" else uniqueName
        fileItem.mediaStoreUri = tempDoc.uri
        return true
    }

    private val allocatedMediaNames = ConcurrentHashMap.newKeySet<String>()

    /** 基于 MediaStore 指定目标集合与子目录列表生成不重复的文件名。 */
    private fun uniqueMediaName(
        name: String,
        relativePath: String = Environment.DIRECTORY_DOWNLOADS + "/LocalSend/",
        collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else Uri.EMPTY,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return name
        val existing = HashSet<String>()
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(relativePath)
            context.contentResolver
                .query(collection, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (index != -1 && !cursor.isNull(index)) {
                            existing.add(cursor.getString(index))
                        }
                    }
                }
        } catch (ignored: Exception) {
        }
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

    private fun uniqueTreeName(
        name: String,
        existing: Set<String>,
    ): String {
        if (name !in existing) return name
        val base = name.substringBeforeLast('.', "").ifEmpty { name }
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while ("$base ($counter)$ext" in existing) counter++
        return "$base ($counter)$ext"
    }

    /** 删除已写入的文件（用于传输取消或 sha256 校验失败后的临时文件清理）。 */
    internal fun deleteSavedFile(
        fileItem: FileItem,
        target: SaveTarget,
    ) {
        try {
            val uri = fileItem.mediaStoreUri
            if (uri != null) {
                if (target is SaveTarget.MediaStoreTarget) {
                    context.contentResolver.delete(uri, null, null)
                } else if (target is SaveTarget.UriTarget) {
                    try {
                        DocumentFile.fromSingleUri(context, uri)?.delete()
                    } catch (_: Exception) {
                    }
                }
            }
            if (target is SaveTarget.UriTarget) {
                val root = DocumentFile.fromTreeUri(context, target.treeUri)
                if (root != null) {
                    val pathInfo =
                        org.localsend.miuix.util.SavePathHelper
                            .resolve(fileItem.name)
                    val segments =
                        org.localsend.miuix.util.SavePathHelper
                            .splitSegments(pathInfo.subDirectory)
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
        } catch (ignored: Exception) {
        }
    }

    /** 释放已分配的 MediaStore 文件名占位（服务停止时调用）。 */
    internal fun clearAllocatedNames() {
        allocatedMediaNames.clear()
    }
}
