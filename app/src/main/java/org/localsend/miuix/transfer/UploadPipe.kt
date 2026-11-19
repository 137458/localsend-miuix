package org.localsend.miuix.transfer

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * 把上传请求体搬进落盘流，读与写拆到两个协程上跑。
 *
 * 接收协程只做一次内存拷贝就把缓冲区交给写协程，随后立刻回去继续收包，
 * 落盘（MediaStore / FUSE）、摘要与文本缓冲的耗时不再占住 TCP 在飞窗口。
 * 队列有界：堆积到 [RECEIVE_QUEUE_DEPTH] 块时接收侧才回压，内存上界固定为
 * 队列深度加一个在写缓冲区。
 *
 * @param onBytesReceived 在接收协程上回调累计接收字节数，供进度发布与取消检查使用
 * @param onChunk 在落盘协程上回调每块字节，供摘要计算与文本缓冲使用
 * @return 接收到的累计字节数
 * @throws java.io.IOException 落盘失败时按原类型冒泡，调用方据此区分写盘失败与接收方取消
 */
internal suspend fun pipeUploadBody(
    channel: ByteReadChannel,
    sink: OutputStream,
    onBytesReceived: (Long) -> Unit,
    onChunk: (ByteArray, Int) -> Unit,
): Long =
    coroutineScope {
        val queue = Channel<UploadChunk>(RECEIVE_QUEUE_DEPTH)
        // 缓冲区回收通道：写侧用完归还，读侧优先取用，避免每块都新分配半个 MiB
        val recycled = Channel<ByteArray>(RECEIVE_QUEUE_DEPTH)
        // 落盘失败要穿过取消传递：写协程自己的取消只是被牵连，不能冒充磁盘错误
        val sinkFailure = AtomicReference<Throwable?>(null)
        val writer =
            async(Dispatchers.IO) {
                try {
                    for (chunk in queue) {
                        sink.write(chunk.bytes, 0, chunk.length)
                        onChunk(chunk.bytes, chunk.length)
                        recycled.trySend(chunk.bytes)
                    }
                    sink.flush()
                } catch (t: Throwable) {
                    if (t !is CancellationException) sinkFailure.set(t)
                    throw t
                }
            }
        var received = 0L
        try {
            while (!channel.isClosedForRead) {
                val bytes = recycled.tryReceive().getOrNull() ?: ByteArray(RECEIVE_CHUNK_SIZE)
                val read = channel.readAvailable(bytes, 0, bytes.size)
                if (read <= 0) break
                queue.send(UploadChunk(bytes, read))
                received += read
                onBytesReceived(received)
            }
        } catch (e: CancellationException) {
            // 落盘协程先失败时会顺带取消读侧：把取消换回落盘的真实失败，
            // 否则上层只能看到"被取消"，把磁盘写失败误判成接收方取消
            throw sinkFailure.get() ?: e
        } finally {
            // 正常结束或被取消都要关队列，写协程才会退出循环做 flush
            queue.close()
        }
        // 收完后再等落盘收尾；写侧失败时 await 原样抛出该失败
        writer.await()
        received
    }

/** 上传体搬运块：缓冲区复用，[length] 与实际收到的字节数分离。 */
private class UploadChunk(
    val bytes: ByteArray,
    val length: Int,
)

/** 上传体读缓冲块大小：既是单次落盘写入的单位，也是 TCP 在飞窗口的决策粒度。 */
private const val RECEIVE_CHUNK_SIZE = 512 * 1024

/** 在飞队列深度：512 KiB × 4 = 2 MiB，比官方接收端（约 8 MiB 在飞）保守。 */
private const val RECEIVE_QUEUE_DEPTH = 4
