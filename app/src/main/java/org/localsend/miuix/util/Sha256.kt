package org.localsend.miuix.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 摘要工具：支持内存字节数组、输入流逐块流式更新以及文件摘要。 */
object Sha256 {
    /** 逐块读取的缓冲区大小；测试需覆盖该边界的 ±1。 */
    const val BUFFER_SIZE = 128 * 1024

    /** 把已计算出的摘要字节编码为小写十六进制；供流式上传与落盘校验复用。 */
    fun hex(digest: ByteArray): String = digest.joinToString("") { "%02x".format(it) }

    /** 计算内存字节数组的小写十六进制摘要。 */
    fun hexDigest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

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
        return hex(digest.digest())
    }

    /** 计算整个文件的小写十六进制摘要。 */
    fun hexDigest(file: File): String = hexDigest(file.inputStream().buffered(BUFFER_SIZE))
}
