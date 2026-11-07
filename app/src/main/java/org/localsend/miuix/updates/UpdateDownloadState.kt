package org.localsend.miuix.updates

import java.io.File

/**
 * 进程级下载状态。弹窗关闭不改变状态，更新页与弹窗都从同一份流读取。
 */
sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState

    data class Downloading(
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
    ) : UpdateDownloadState

    data class Done(
        val file: File,
    ) : UpdateDownloadState

    data class Failed(
        val message: String,
    ) : UpdateDownloadState
}

/** 失败分类固定文案：网络、校验、写盘，不把底层异常直接抛给用户。 */
enum class UpdateDownloadFailure(
    val message: String,
) {
    NETWORK("网络连接中断，请检查网络后重试"),
    CHECKSUM("安装包校验未通过，请重试或用浏览器下载"),
    WRITE("安装包写入失败，请检查存储空间"),
}
