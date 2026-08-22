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
    val size: Long,
    val uri: Uri? = null,
    val path: String? = null,
    val textContent: String? = null,
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
    fun toDto(): FileDto {
        return FileDto(
            id = id,
            fileName = name,
            size = size,
            fileType = mimeType,
            sha256 = expectedSha256
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
    var errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = "${FileItem.formatFileSize(speed)}/s"

    val formattedTotalSize: String
        get() = FileItem.formatFileSize(totalBytes)

    val formattedTransferredSize: String
        get() = FileItem.formatFileSize(transferredBytes)
}

data class TransferHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val deviceAlias: String,
    val deviceIp: String,
    val isIncoming: Boolean,
    val fileCount: Int,
    val totalSize: Long,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val fileNames: List<String>
) {
    val formattedSize: String
        get() = FileItem.formatFileSize(totalSize)
}
