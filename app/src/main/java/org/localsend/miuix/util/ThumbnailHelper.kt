package org.localsend.miuix.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import org.localsend.miuix.model.FileItem
import java.io.File

object ThumbnailHelper {

    // 内存缓存最大保留 60 张缩略图
    private val memoryCache = LruCache<String, Bitmap>(60)

    fun isImage(name: String, mimeType: String): Boolean {
        val mime = mimeType.lowercase()
        val n = name.lowercase()
        return mime.startsWith("image/") ||
                n.endsWith(".jpg") || n.endsWith(".jpeg") ||
                n.endsWith(".png") || n.endsWith(".webp") ||
                n.endsWith(".gif") || n.endsWith(".heic") ||
                n.endsWith(".bmp")
    }

    fun isVideo(name: String, mimeType: String): Boolean {
        val mime = mimeType.lowercase()
        val n = name.lowercase()
        return mime.startsWith("video/") ||
                n.endsWith(".mp4") || n.endsWith(".mkv") ||
                n.endsWith(".mov") || n.endsWith(".3gp") ||
                n.endsWith(".webm") || n.endsWith(".avi")
    }

    fun isApk(name: String, mimeType: String): Boolean {
        val mime = mimeType.lowercase()
        val n = name.lowercase()
        return mime == "application/vnd.android.package-archive" || n.endsWith(".apk")
    }

    fun isImage(file: FileItem): Boolean = isImage(file.name, file.mimeType)

    fun isVideo(file: FileItem): Boolean = isVideo(file.name, file.mimeType)

    fun isApk(file: FileItem): Boolean = isApk(file.name, file.mimeType)

    /**
     * 加载用于列表展示的紧凑缩略图 (约 128x128 像素)。
     */
    fun loadThumbnail(context: Context, file: FileItem, targetSize: Int = 128): Bitmap? {
        val cacheKey = "thumb_${file.id}_${file.uri?.toString() ?: file.path ?: file.name}"
        memoryCache.get(cacheKey)?.let { return it }

        val bitmap: Bitmap? = when {
            isImage(file) -> loadImageThumbnail(context, file, targetSize)
            isVideo(file) -> loadVideoThumbnail(context, file, targetSize)
            isApk(file) -> loadApkIcon(context, file, targetSize)
            else -> null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun loadThumbnail(
        context: Context,
        name: String,
        mimeType: String,
        uri: android.net.Uri?,
        path: String?,
        targetSize: Int = 128
    ): Bitmap? {
        val dummy = FileItem(
            name = name,
            size = 0L,
            mimeType = mimeType,
            uri = uri,
            path = path
        )
        return loadThumbnail(context, dummy, targetSize)
    }

    /**
     * 加载用于弹窗预览的较高清晰度位图 (约 800x800 像素以控制内存)。
     */
    fun loadPreviewImage(context: Context, file: FileItem, maxDimension: Int = 800): Bitmap? {
        val cacheKey = "preview_${file.id}_${file.uri?.toString() ?: file.path ?: file.name}"
        memoryCache.get(cacheKey)?.let { return it }

        val bitmap = if (isImage(file)) {
            loadImageThumbnail(context, file, maxDimension)
        } else if (isVideo(file)) {
            loadVideoThumbnail(context, file, maxDimension)
        } else {
            null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun loadImageThumbnail(context: Context, file: FileItem, targetSize: Int): Bitmap? {
        val uri = file.uri
        val path = file.path

        if (uri != null) {
            // Android 10+ (API 29+) 原生 loadThumbnail 高速硬件解码
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
                } catch (ignored: Exception) {}
            }

            // 通用解码降级
            try {
                if (uri.scheme == "file") {
                    val filePath = uri.path
                    if (!filePath.isNullOrEmpty()) {
                        return decodeSampledBitmapFromFile(filePath, targetSize, targetSize)
                    }
                }

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                    options.inJustDecodeBounds = false

                    context.contentResolver.openInputStream(uri)?.use { secondStream ->
                        return BitmapFactory.decodeStream(secondStream, null, options)
                    }
                }
            } catch (ignored: Exception) {}
        } else if (!path.isNullOrEmpty()) {
            return decodeSampledBitmapFromFile(path, targetSize, targetSize)
        }
        return null
    }

    private fun loadVideoThumbnail(context: Context, file: FileItem, targetSize: Int): Bitmap? {
        val uri = file.uri
        val path = file.path

        if (uri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
                } catch (ignored: Exception) {}
            }
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.frameAtTime
                retriever.release()
                if (frame != null) {
                    return Bitmap.createScaledBitmap(frame, targetSize, targetSize, true)
                }
            } catch (ignored: Exception) {}
        } else if (!path.isNullOrEmpty()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val frame = retriever.frameAtTime
                retriever.release()
                if (frame != null) {
                    return Bitmap.createScaledBitmap(frame, targetSize, targetSize, true)
                }
            } catch (ignored: Exception) {}
        }
        return null
    }

    private fun loadApkIcon(context: Context, file: FileItem, targetSize: Int): Bitmap? {
        val path = file.path ?: file.uri?.path ?: return null
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageArchiveInfo(path, 0) ?: return null
            val appInfo = pi.applicationInfo ?: return null
            appInfo.sourceDir = path
            appInfo.publicSourceDir = path
            val icon = appInfo.loadIcon(pm)
            icon.toBitmap(width = targetSize, height = targetSize, config = Bitmap.Config.ARGB_8888)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
