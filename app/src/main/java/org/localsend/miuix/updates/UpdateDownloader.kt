package org.localsend.miuix.updates

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class UpdateTransferException(
    val failure: UpdateDownloadFailure,
    cause: Throwable? = null,
) : Exception(cause)

/**
 * 进程级下载器：关掉弹窗不会取消，更新页与弹窗共用同一份 [state]。
 */
class UpdateDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private var job: kotlinx.coroutines.Job? = null
    private var generation = 0

    @Synchronized
    fun start(asset: UpdateAsset) {
        if (_state.value is UpdateDownloadState.Downloading) return
        generation += 1
        val attempt = generation
        _state.value = UpdateDownloadState.Idle
        job = scope.launch(Dispatchers.IO) { runDownload(asset, attempt) }
    }

    @Synchronized
    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        _state.value = UpdateDownloadState.Idle
    }

    @Synchronized
    fun reset() {
        if (_state.value is UpdateDownloadState.Downloading) return
        _state.value = UpdateDownloadState.Idle
    }

    private fun publish(
        attempt: Int,
        state: UpdateDownloadState,
    ) {
        synchronized(this) {
            if (attempt == generation) _state.value = state
        }
    }

    private suspend fun runDownload(
        asset: UpdateAsset,
        attempt: Int,
    ) {
        val dir = File(context.cacheDir, "update")
        val safeName = asset.name.substringAfterLast('/').ifBlank { "localsend-update.apk" }
        // 每次尝试独立文件，取消/重试不会互相踩到半包
        val target = File(dir, "$attempt-$safeName")
        var connection: HttpURLConnection? = null

        try {
            if (!dir.exists() && !dir.mkdirs()) {
                throw UpdateTransferException(UpdateDownloadFailure.WRITE)
            }
            // 上一次完成或中断的包不再需要
            dir.listFiles()?.forEach { it.delete() }

            connection = open(asset.downloadUrl)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            publish(attempt, UpdateDownloadState.Downloading(downloaded = 0L, total = total, speedBytesPerSec = 0L))

            var downloaded = 0L
            var speed = 0L
            var speedTick = SystemClock.elapsedRealtime()
            var speedBytes = 0L

            connection.inputStream.use { input ->
                val output =
                    try {
                        FileOutputStream(target)
                    } catch (e: IOException) {
                        throw UpdateTransferException(UpdateDownloadFailure.WRITE, e)
                    }
                output.use { out ->
                    val bufferBytes = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read =
                            try {
                                input.read(bufferBytes)
                            } catch (e: IOException) {
                                throw UpdateTransferException(UpdateDownloadFailure.NETWORK, e)
                            }
                        if (read == -1) break
                        try {
                            out.write(bufferBytes, 0, read)
                        } catch (e: IOException) {
                            throw UpdateTransferException(UpdateDownloadFailure.WRITE, e)
                        }
                        downloaded += read
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - speedTick
                        if (elapsed >= 400L) {
                            val deltaBytes = downloaded - speedBytes
                            if (deltaBytes > 0L) {
                                val instant = deltaBytes * 1000L / elapsed
                                speed = if (speed <= 0L) instant else (speed * 7 + instant * 3) / 10
                            }
                            speedTick = now
                            speedBytes = downloaded
                        }
                        publish(
                            attempt,
                            UpdateDownloadState.Downloading(
                                downloaded = downloaded,
                                total = total,
                                speedBytesPerSec = speed,
                            ),
                        )
                    }
                    try {
                        out.flush()
                    } catch (e: IOException) {
                        throw UpdateTransferException(UpdateDownloadFailure.WRITE, e)
                    }
                }
            }

            if (!downloadedSizeMatches(asset.size, downloaded)) {
                throw UpdateTransferException(UpdateDownloadFailure.CHECKSUM)
            }
            val check =
                try {
                    ApkDigest.verify(target, asset.digest)
                } catch (e: IOException) {
                    throw UpdateTransferException(UpdateDownloadFailure.CHECKSUM, e)
                }
            if (check is DigestCheck.Mismatched) {
                throw UpdateTransferException(UpdateDownloadFailure.CHECKSUM)
            }

            publish(attempt, UpdateDownloadState.Done(target))
        } catch (e: CancellationException) {
            target.delete()
            publish(attempt, UpdateDownloadState.Idle)
            throw e
        } catch (e: UpdateTransferException) {
            target.delete()
            publish(attempt, UpdateDownloadState.Failed(e.failure.message))
        } catch (e: Exception) {
            target.delete()
            publish(attempt, UpdateDownloadState.Failed(UpdateDownloadFailure.NETWORK.message))
        } finally {
            connection?.disconnect()
            synchronized(this) { if (attempt == generation) job = null }
        }
    }

    private fun open(url: String): HttpURLConnection {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "LocalSend-Miuix-App")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                throw UpdateTransferException(UpdateDownloadFailure.NETWORK)
            }
            return connection
        } catch (e: UpdateTransferException) {
            throw e
        } catch (e: IOException) {
            throw UpdateTransferException(UpdateDownloadFailure.NETWORK, e)
        }
    }
}
