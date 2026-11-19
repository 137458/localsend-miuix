package org.localsend.miuix

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.transfer.pipeUploadBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.random.Random

/**
 * 接收管道的对外契约：读协程收包、写协程落盘，两者解耦，但字节序、进度语义与错误传播
 * 必须与"读一块写一块"的逐块直写完全一致——解耦只允许改变时序，不允许改变结果。
 */
class UploadPipeTest {
    private val mebibyte = 1024 * 1024

    @Test
    fun copiesBodyVerbatimAndPublishesProgressInOrder() =
        runBlocking {
            val payload = Random(20261119L).nextBytes(3 * mebibyte)
            val sink = ByteArrayOutputStream()
            val hashed = ByteArrayOutputStream()
            val progress = ArrayList<Long>()

            val received =
                pipeUploadBody(
                    channel = ByteReadChannel(payload),
                    sink = sink,
                    onBytesReceived = { progress += it },
                    onChunk = { bytes, length -> hashed.write(bytes, 0, length) },
                )

            assertArrayEquals("落盘字节必须与请求体逐字节一致", payload, sink.toByteArray())
            assertArrayEquals("摘要侧看到的字节顺序必须与落盘一致", payload, hashed.toByteArray())
            assertEquals("返回的累计字节数必须等于请求体长度", payload.size.toLong(), received)
            assertEquals("最后一次进度回调必须等于请求体长度", payload.size.toLong(), progress.last())
            assertTrue("进度必须严格单调递增，实际 $progress", progress.zipWithNext().all { (a, b) -> a < b })
        }

    @Test
    fun slowSinkLosesNothingAndKeepsOrder() =
        runBlocking {
            // 落盘每块慢 5ms：读侧靠有界队列回压，但不得丢字节、不得乱序
            val payload = Random(7L).nextBytes(2 * mebibyte)
            val sink = SlowSink(5L)

            val received = pipeUploadBody(ByteReadChannel(payload), sink, {}, { _, _ -> })

            assertEquals("慢盘只允许反压，不允许丢字节", payload.size.toLong(), received)
            assertArrayEquals("慢盘下的字节序必须与请求体一致", payload, sink.written.toByteArray())
        }

    @Test
    fun sinkFailureSurfacesAsIoFailure() {
        val failure =
            runCatching {
                runBlocking {
                    pipeUploadBody(
                        ByteReadChannel(Random(1L).nextBytes(2 * mebibyte)),
                        FailingSink(),
                        {},
                        { _, _ -> },
                    )
                }
            }.exceptionOrNull()

        // 落盘失败必须保留 IOException 身份：上传路由靠它区分"磁盘写失败"与"接收方取消"
        assertTrue("落盘失败必须以 IOException 冒泡，实际 $failure", failure is IOException)
        assertEquals("落盘失败必须保留原始错误信息", "disk full", failure?.message)
    }

    /** 每块写入前睡 [delayMs]，模拟 MediaStore/FUSE 落盘慢于收包。 */
    private class SlowSink(
        private val delayMs: Long,
    ) : OutputStream() {
        val written = ByteArrayOutputStream()

        override fun write(
            b: Int,
        ) {
            written.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            written.write(b, off, len)
            Thread.sleep(delayMs)
        }
    }

    /** 落盘直接失败：模拟存储写满、目标被删除等不可恢复错误。 */
    private class FailingSink : OutputStream() {
        override fun write(
            b: Int,
        ) = throw IOException("disk full")

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = throw IOException("disk full")
    }
}
