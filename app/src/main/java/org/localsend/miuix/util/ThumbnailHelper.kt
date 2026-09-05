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

    // 内存缓存以 KB 为单位，限制最大使用可用堆内存的 1/8（通常在 16MB ~ 64MB 之间），防止 OOM
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 64 * 1024)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

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
        val cacheKey = "thumb_${file.id}_${file.uri?.toString() ?: file.path ?: file.name}_$targetSize"
        memoryCache.get(cacheKey)?.let { return it }

        val bitmap: Bitmap? = try {
            when {
                isImage(file) -> loadImageThumbnail(context, file, targetSize)
                isVideo(file) -> loadVideoThumbnail(context, file, targetSize)
                isApk(file) -> loadApkIcon(context, file, targetSize)
                else -> null
            }
        } catch (t: Throwable) {
            null
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

        val bitmap = try {
            if (isImage(file)) {
                loadImageThumbnail(context, file, maxDimension)
            } else if (isVideo(file)) {
                loadVideoThumbnail(context, file, maxDimension)
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun loadImageThumbnail(context: Context, file: FileItem, targetSize: Int): Bitmap? {
        val uri = file.uri ?: file.mediaStoreUri
        val path = file.path

        if (uri != null) {
            // 1. 优先使用 openFileDescriptor：原生支持 seek，解码超大图、RAW/HEIC 时不会多次打开流且无额外内存开销
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fileDescriptor
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fd, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                        options.inJustDecodeBounds = false
                        if (targetSize <= 256) {
                            options.inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        try {
                            android.system.Os.lseek(fd, 0, android.system.OsConstants.SEEK_SET)
                        } catch (ignored: Throwable) {}
                        val bitmap = BitmapFactory.decodeFileDescriptor(fd, null, options)
                        if (bitmap != null) return bitmap
                    }
                }
            } catch (t: Throwable) {}

            // 2. Android 10+ (API 29+) 原生 loadThumbnail 高速硬件解码降级
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
                } catch (t: Throwable) {}
            }

            // 3. 本地 file:// 路径降级
            if (uri.scheme == "file") {
                val filePath = uri.path
                if (!filePath.isNullOrEmpty()) {
                    val bitmap = decodeSampledBitmapFromFile(filePath, targetSize, targetSize)
                    if (bitmap != null) return bitmap
                }
            }

            // 4. 通用带缓冲流式解码降级（支持 mark/reset）
            try {
                context.contentResolver.openInputStream(uri)?.buffered(128 * 1024)?.use { stream ->
                    stream.mark(128 * 1024)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                        options.inJustDecodeBounds = false
                        if (targetSize <= 256) {
                            options.inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        try {
                            stream.reset()
                            val bitmap = BitmapFactory.decodeStream(stream, null, options)
                            if (bitmap != null) return bitmap
                        } catch (e: Exception) {
                            context.contentResolver.openInputStream(uri)?.buffered(128 * 1024)?.use { secondStream ->
                                return BitmapFactory.decodeStream(secondStream, null, options)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}
        } else if (!path.isNullOrEmpty()) {
            return decodeSampledBitmapFromFile(path, targetSize, targetSize)
        }
        return null
    }

    private fun loadVideoThumbnail(context: Context, file: FileItem, targetSize: Int): Bitmap? {
        val uri = file.uri ?: file.mediaStoreUri
        val path = file.path

        if (uri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
                } catch (t: Throwable) {}
            }
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.frameAtTime
                retriever.release()
                if (frame != null) {
                    return Bitmap.createScaledBitmap(frame, targetSize, targetSize, true)
                }
            } catch (t: Throwable) {}
        } else if (!path.isNullOrEmpty()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val frame = retriever.frameAtTime
                retriever.release()
                if (frame != null) {
                    return Bitmap.createScaledBitmap(frame, targetSize, targetSize, true)
                }
            } catch (t: Throwable) {}
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
        } catch (t: Throwable) {
            null
        }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            if (reqWidth <= 256 && reqHeight <= 256) {
                options.inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, options)
        } catch (t: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        if (height <= 0 || width <= 0) return 1

        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            // 采用 maxOf 确保长边与短边都不会过大，彻底防止大尺寸全景/超高像素图片溢出内存
            inSampleSize = maxOf(heightRatio, widthRatio).coerceAtLeast(1)
        }
        var powerOf2 = 1
        while (powerOf2 * 2 <= inSampleSize) {
            powerOf2 *= 2
        }
        return powerOf2.coerceAtLeast(1)
    }
}
