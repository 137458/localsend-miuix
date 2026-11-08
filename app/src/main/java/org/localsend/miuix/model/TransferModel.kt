package org.localsend.miuix.model

import android.net.Uri
import kotlinx.serialization.Serializable
import org.localsend.miuix.transfer.RemainingTime
import java.util.UUID

@Serializable
data class FileDto(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String = "application/octet-stream",
    val sha256: String? = null,
    val preview: String? = null,
)

@Serializable
data class PrepareUploadRequestDto(
    val info: DeviceDto,
    val files: Map<String, FileDto>,
)

@Serializable
data class PrepareUploadResponseDto(
    val sessionId: String,
    val files: Map<String, String>, // fileId to token
)

/** 协议 §5.2：Download(Web Share) 元数据响应的 info 部分（对齐 /info 与 /prepare-download 需包含的字段）。 */
@Serializable
data class PrepareDownloadResponseDto(
    val info: DeviceDto,
    val sessionId: String,
    val files: Map<String, FileDto>,
)

/**
 * Web Share（Download API）的共享会话。
 *
 * 我方作为 sender，把一组选中文件共享给局域网内的浏览器（接收方不装 LocalSend）。
 * 协议 §5 明确 Download 恒走明文 HTTP（浏览器拒绝自签证书）。
 */
data class ShareSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val files: List<FileItem>,
)

@Serializable
enum class TransferStatus {
    WaitingApproval,
    InProgress,
    Completed,
    Canceled,
    Failed,
}

/**
 * 纯文本消息内联传输或内存缓冲的最大安全大小（512 KB）。
 * 超过此大小的文件一律作为普通二进制文件流式存盘，严禁载入内存或误当作即时文本消息。
 */
const val MAX_INLINE_TEXT_SIZE = 512 * 1024L

/** 纯文本消息在协议中的 MIME 类型（官方客户端按 MIME 前缀解码 fileType）。 */
const val TEXT_MESSAGE_MIME = "text/plain"

/** 单份文件最终的完整性结论；Unverified 表示对端不支持回报摘要，传输成功但无校验。 */
enum class IntegrityStatus {
    /** 未参与完整性校验（如尚未开始或握手前被撤销声明）。 */
    None,

    /** 门槛内文件：已随 prepare-upload 声明 sha256，由接收端在落盘时校验。 */
    Declared,

    /** 超大文件：上传中流式计算摘要，接收端回报值一致。 */
    Verified,

    /** 超大文件：对端未回报摘要（官方/第三方接收端），降级为无校验但传输成功。 */
    Unverified,

    /** 超大文件：接收端回报摘要与发送端流式摘要不一致，该项显式失败。 */
    Mismatch,

    /** 超大文件：摘要一致，但确认转正请求未获响应且查询也无法定论；接收端会按保鲜期处理，不据此判坏。 */
    Unconfirmed,
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
    // 声明上述摘要时源文件的 (长度, 修改时间) 指纹；发送前指纹不一致即重算，保证声明值与上传内容同源
    var sha256SourceStamp: String? = null,
    // 传输结束后的完整性结论：区分已校验、对端不回报而降级、以及摘要不一致失败
    var integrity: IntegrityStatus = IntegrityStatus.None,
    var status: TransferStatus = TransferStatus.WaitingApproval,
    var progress: Float = 0f,
    var bytesTransferred: Long = 0L,
    var speed: Long = 0L,
    var error: String? = null,
    // 通过 MediaStore 写入公共目录时，记录插入出的 Uri，用于完成后清除 IS_PENDING 标记
    var mediaStoreUri: Uri? = null,
    val isTextMessage: Boolean = textContent != null,
) {
    /**
     * 可完整内联进 prepare-upload `preview` 的文本内容；不满足内联条件时为 null。
     *
     * 协议要求 preview 的 UTF-8 字节数与声明的 size 严格一致，接收端据此判定文本消息。
     * 超过 [MAX_INLINE_TEXT_SIZE] 的文本一律降级为普通文件上传，绝不截断后当作消息发送。
     */
    val inlineTextContent: String?
        get() {
            if (!isTextMessage) return null
            val content = textContent ?: return null
            return content.takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_INLINE_TEXT_SIZE }
        }

    fun toDto(): FileDto {
        val inlineText = inlineTextContent
        return FileDto(
            id = id,
            fileName = name,
            // 内联文本按真实 UTF-8 字节数声明，保证 preview 字节数与 size 严格一致；超限文本降级为普通文件沿用原 size
            size = if (inlineText != null) inlineText.toByteArray(Charsets.UTF_8).size.toLong() else size,
            // 协议 fileType 为 MIME：官方接收端按 MIME 前缀解码，裸枚举名 "text" 会被当成 other 并落盘为文件
            fileType = if (inlineText != null) TEXT_MESSAGE_MIME else mimeType,
            sha256 = expectedSha256,
            preview = inlineText,
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
                " KMGTPE"[z],
            )
        }

        fun formatSpeed(
            bytesPerSec: Long,
            compact: Boolean = false,
        ): String {
            if (bytesPerSec < 1024) return "$bytesPerSec B/s"
            return if (compact) {
                val mb = bytesPerSec.toDouble() / (1024.0 * 1024.0)
                if (mb >= 1.0) {
                    val rounded = kotlin.math.round(mb * 10) / 10.0
                    val numStr = if (rounded == kotlin.math.floor(rounded)) "${rounded.toInt()}" else "$rounded"
                    "${numStr}M/s"
                } else {
                    val kb = (bytesPerSec / 1024L).coerceAtLeast(1L)
                    "${kb}K/s"
                }
            } else {
                "${formatFileSize(bytesPerSec)}/s"
            }
        }
    }
}

data class TransferSession(
    val sessionId: String,
    val device: Device,
    val isIncoming: Boolean,
    val files: List<FileItem>,
    var totalBytes: Long,
    var transferredBytes: Long = 0L,
    var speed: Long = 0L,
    var status: TransferStatus = TransferStatus.WaitingApproval,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var errorMessage: String? = null,
    val updateSeq: Long = 0L,
    var lastActiveTime: Long = System.currentTimeMillis(),
) {
    fun createSnapshot(seq: Long = System.nanoTime()): TransferSession =
        copy(
            files = files.map { it.copy() },
            updateSeq = seq,
            lastActiveTime = lastActiveTime,
        )

    val isTextMessage: Boolean
        get() = files.size == 1 && files.first().isTextMessage

    /**
     * 部分文件失败：会话仍判完成（与官方 LocalSend 一致），由 [errorMessage] 携带失败说明。
     * 界面与通知据此区分“全部成功”与“部分失败”，不再各自重复同一判定。
     */
    val isPartialFailure: Boolean
        get() = status == TransferStatus.Completed && !errorMessage.isNullOrBlank()

    val singleTextMessageContent: String?
        get() = if (isTextMessage) files.first().textContent else null

    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedSpeed: String
        get() = FileItem.formatSpeed(speed)

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

    val remainingTime: RemainingTime
        get() = RemainingTime.of(status, speed, totalBytes, transferredBytes)
}

@Serializable
data class HistoryFileEntry(
    val name: String,
    val size: Long,
    val uriString: String? = null,
    val path: String? = null,
    val mimeType: String = "application/octet-stream",
) {
    val uri: Uri?
        get() = uriString?.let { Uri.parse(it) }

    constructor(
        name: String,
        size: Long,
        uri: Uri?,
        path: String?,
        mimeType: String = "application/octet-stream",
    ) : this(
        name = name,
        size = size,
        uriString = uri?.toString(),
        path = path,
        mimeType = mimeType,
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
    val fileEntries: List<HistoryFileEntry> = emptyList(),
) {
    val formattedSize: String
        get() = FileItem.formatFileSize(totalSize)
}
