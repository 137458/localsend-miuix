package org.localsend.miuix.transfer

/**
 * 上传落盘后的大小校验。
 *
 * 发送方在 prepare-upload 里声明了 size；声明为 0 时（部分发送端不填）无法核对，
 * 视为通过；一旦声明 > 0，收到字节数必须与声明完全一致。
 */
fun isReceivedSizeConsistent(
    declaredSize: Long,
    receivedBytes: Long,
): Boolean = declaredSize <= 0L || receivedBytes == declaredSize
