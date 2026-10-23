package org.localsend.miuix.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** 流式 SHA-256 摘要工具：固定缓冲区逐块更新，杜绝整文件载入内存。 */
object Sha256 {
    /** 逐块读取的缓冲区大小；测试需覆盖该边界的 ±1。 */
    const val BUFFER_SIZE = 128 * 1024

    /** 消费整个输入流并返回小写十六进制摘要；流由本函数负责关闭。 */
    fun hexDigest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                // 真实数据源可能短读（如 ContentResolver），必须按实际读取长度更新
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 计算整个文件的小写十六进制摘要。 */
    fun hexDigest(file: File): String = hexDigest(file.inputStream().buffered(BUFFER_SIZE))
}
