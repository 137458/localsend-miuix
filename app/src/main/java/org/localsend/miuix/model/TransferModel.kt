package org.localsend.miuix.model

import android.net.Uri
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FileDto(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String = "application/octet-stream",
    val sha256: String? = null,
    val preview: String? = null
)

@Serializable
data class PrepareUploadRequestDto(
    val info: DeviceDto,
    val files: Map<String, FileDto>
)

@Serializable
data class PrepareUploadResponseDto(
    val sessionId: String,
    val files: Map<String, String> // fileId to token
)

/** 协议 §5.2：Download(Web Share) 元数据响应的 info 部分（对齐 /info 与 /prepare-download 需包含的字段）。 */
@Serializable
data class PrepareDownloadResponseDto(
    val info: DeviceDto,
    val sessionId: String,
    val files: Map<String, FileDto>
)

/**
 * Web Share（Download API）的共享会话。
 *
 * 我方作为 sender，把一组选中文件共享给局域网内的浏览器（接收方不装 LocalSend）。
 * 协议 §5 明确 Download 恒走明文 HTTP（浏览器拒绝自签证书），故 [downloadLink] 固定为 http。
 */
data class ShareSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val files: List<FileItem>
) {
    /** 接收方浏览器访问的入口地址（协议 §5.1）。根据服务协议动态决定 http/https，端口取端口号。 */
    fun downloadLink(protocol: String = "http", ip: String, port: Int): String = "$protocol://$ip:$port"
}

@Serializable
enum class TransferStatus {
    WaitingApproval,
    InProgress,
    Completed,
    Canceled,
    Failed
}

data class FileItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val size: Long = 0L,
    val uri: Uri? = null,
    val path: String? = null,
    var textContent: String? = null,
    val mimeType: String = "application/octet-stream",
    val token: String? = null,
    // 发送方在 prepare-upload 中声明的 sha256（可选）；接收方写入后校验，不匹配则回 422
    var expectedSha256: String? = null,
    var status: TransferStatus = TransferStatus.WaitingApproval,
    var progress: Float = 0f,
    var bytesTransferred: Long = 0L,
    var speed: Long = 0L,
    var error: String? = null,
    // 通过 MediaStore 写入公共目录时，记录插入出的 Uri，用于完成后清除 IS_PENDING 标记
    var mediaStoreUri: Uri? = null
) {
    val isTextMessage: Boolean
        get() = textContent != null || mimeType.startsWith("text/")

    fun toDto(): FileDto {
        return FileDto(
            id = id,
            fileName = name,
            size = size,
            fileType = mimeType,
            sha256 = expectedSha256,
            preview = textContent?.take(2000)
        )
    }

    val formattedSize: String
        get() = formatFileSize(size)

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
            return String.format(
                java.util.Locale.US,
                "%.1f %sB",
                bytes.toDouble() / (1L shl (z * 10)),
                " KMGTPE"[z]
            )
        }
    }
}

data class TransferSession(
    val sessionId: String,
    val device: Device,
    val isIncoming: Boolean,
    val files: List<FileItem>,
    val totalBytes: Long,
    var transferredBytes: Long = 0L,
    var speed: Long = 0L,
    var status: TransferStatus = TransferStatus.WaitingApproval,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var errorMessage: String? = null,
    val updateSeq: Long = 0L
) {
    fun createSnapshot(seq: Long = System.nanoTime()): TransferSession {
        return copy(
            files = files.map { it.copy() },
            updateSeq = seq
        )
    }

    val isTextMessage: Boolean
        get() = files.size == 1 && files.first().isTextMessage

    val singleTextMessageContent: String?
        get() = if (isTextMessage) files.first().textContent else null

    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedSpeed: String
        get() = "${FileItem.formatFileSize(speed)}/s"

    val formattedTotalSize: String
        get() = FileItem.formatFileSize(totalBytes)

    val formattedTransferredSize: String
        get() = FileItem.formatFileSize(transferredBytes)

    val currentFileIndex: Int
        get() {
            val inProgressIndex = files.indexOfFirst { it.status == TransferStatus.InProgress }
            if (inProgressIndex >= 0) return inProgressIndex
            val lastCompleted = files.indexOfLast { it.status == TransferStatus.Completed }
            return if (lastCompleted >= 0) lastCompleted else 0
        }

    val currentFile: FileItem?
        get() = files.firstOrNull { it.status == TransferStatus.InProgress } ?: files.getOrNull(currentFileIndex)

    val remainingTimeFormatted: String
        get() {
            if (status != TransferStatus.InProgress) return ""
            if (speed <= 0 || totalBytes <= 0) return "计算中..."
            val remainingBytes = (totalBytes - transferredBytes).coerceAtLeast(0L)
            if (remainingBytes == 0L) return "即将完成"
            val seconds = remainingBytes / speed
            return when {
                seconds < 60 -> "剩余约 ${seconds}秒"
                seconds < 3600 -> "剩余约 ${seconds / 60}分${seconds % 60}秒"
                else -> "剩余约 ${seconds / 3600}小时${(seconds % 3600) / 60}分"
            }
        }
}

@Serializable
data class HistoryFileEntry(
    val name: String,
    val size: Long,
    val uriString: String? = null,
    val path: String? = null,
    val mimeType: String = "application/octet-stream"
) {
    val uri: Uri?
        get() = uriString?.let { Uri.parse(it) }

    constructor(
        name: String,
        size: Long,
        uri: Uri?,
        path: String?,
        mimeType: String = "application/octet-stream"
    ) : this(
        name = name,
        size = size,
        uriString = uri?.toString(),
        path = path,
        mimeType = mimeType
    )
}

@Serializable
data class TransferHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val deviceAlias: String,
    val deviceIp: String,
    val isIncoming: Boolean,
    val fileCount: Int,
    val totalSize: Long,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val fileNames: List<String>,
    val textContent: String? = null,
    val isTextMessage: Boolean = false,
    val fileEntries: List<HistoryFileEntry> = emptyList()
) {
    val formattedSize: String
        get() = FileItem.formatFileSize(totalSize)
}
