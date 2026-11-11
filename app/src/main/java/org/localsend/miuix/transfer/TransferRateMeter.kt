package org.localsend.miuix.transfer

import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * 传输速率估算器：滑动窗口取平均，再做一阶低通平滑。
 *
 * 直接对相邻两次读数求瞬时速率时，采样抖动会让读数在数倍之间摆动，进而带得剩余时间乱跳。
 * 这里先按 [DEFAULT_WINDOW_MS] 的滑动窗口算平均速率，再用时间常数 [DEFAULT_TIME_CONSTANT_MS]
 * 的一阶低通压平残余波动；起步 [DEFAULT_WARMUP_MS] 内返回 0，让 UI 显示"计算中"而不是假读数。
 *
 * 调用约定：按 [SAMPLE_INTERVAL_MS] 节流调用 [sample]，传入单调递增的累计字节数；
 * 传输收尾时用 [finish] 取全程平均速率（短传输没有足够采样点时也能给出非 0 读数）。
 * 纯 Kotlin 实现，不依赖 Android API，便于单元测试。
 */
class TransferRateMeter(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val warmupMs: Long = DEFAULT_WARMUP_MS,
    private val timeConstantMs: Double = DEFAULT_TIME_CONSTANT_MS,
) {
    private data class Sample(
        val timeMs: Long,
        val bytes: Long,
    )

    private val samples = ArrayList<Sample>()
    private var smoothedRate = 0.0
    private var lastReading = 0L
    private var hasSample = false
    private var firstSampleMs = 0L
    private var firstSampleBytes = 0L

    /**
     * 记录一次采样并返回平滑后的速率（字节/秒）。
     *
     * 预热期内返回 0，但采样本身照常入队，保证预热结束时窗口已经填满。
     * 时钟回退（系统校时、并发来源的时钟抖动）时沿用上一次读数，不抛异常也不产出负值。
     */
    fun sample(
        nowMs: Long,
        cumulativeBytes: Long,
    ): Long {
        val previous = samples.lastOrNull()
        if (previous != null && nowMs < previous.timeMs) return lastReading
        val elapsedMs = if (previous == null) 0L else nowMs - previous.timeMs
        if (previous != null && elapsedMs == 0L) {
            // 同一毫秒内重复采样：只刷新末条样本，避免制造跨度为 0 的窗口
            samples[samples.lastIndex] = Sample(nowMs, cumulativeBytes)
        } else {
            samples.add(Sample(nowMs, cumulativeBytes))
        }
        if (!hasSample) {
            hasSample = true
            firstSampleMs = nowMs
            firstSampleBytes = cumulativeBytes
        }
        discardExpired(nowMs)
        if (nowMs - firstSampleMs < warmupMs) {
            lastReading = 0L
            return 0L
        }
        smoothedRate = lowPass(windowRate(), elapsedMs)
        lastReading = smoothedRate.roundToLong().coerceAtLeast(0L)
        return lastReading
    }

    /**
     * 传输收尾时的全程平均速率（字节/秒），忽略预热与低通。
     *
     * 用整段传输的字节差除以实际耗时，因此哪怕文件小到只采到一两个点，也不会显示 0 B/s。
     */
    fun finish(
        nowMs: Long,
        cumulativeBytes: Long,
    ): Long {
        if (!hasSample) return 0L
        val elapsedMs = (nowMs - firstSampleMs).coerceAtLeast(1L)
        val deltaBytes = (cumulativeBytes - firstSampleBytes).coerceAtLeast(0L)
        return deltaBytes * 1000L / elapsedMs
    }

    private fun windowRate(): Double {
        val first = samples.first()
        val last = samples.last()
        val spanMs = last.timeMs - first.timeMs
        if (spanMs <= 0L) return 0.0
        val deltaBytes = last.bytes - first.bytes
        if (deltaBytes <= 0L) return 0.0
        return deltaBytes.toDouble() * 1000.0 / spanMs.toDouble()
    }

    private fun lowPass(
        rate: Double,
        elapsedMs: Long,
    ): Double {
        // 首个有效样本直接取窗口速率，避免从 0 缓慢爬升
        if (smoothedRate <= 0.0) return rate
        if (elapsedMs <= 0L) return smoothedRate
        // 采样间隔远大于时间常数时指数项已衰减到可忽略，直接对齐当前窗口速率
        if (elapsedMs >= timeConstantMs * DIRECT_SNAP_FACTOR) return rate
        val alpha = 1.0 - exp(-elapsedMs.toDouble() / timeConstantMs)
        return smoothedRate + alpha * (rate - smoothedRate)
    }

    /** 淘汰窗口之外的样本，但至少保留 2 个，保证窗口跨度不为 0。 */
    private fun discardExpired(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (samples.size > 2 && samples[0].timeMs < cutoff) samples.removeAt(0)
    }

    companion object {
        /** 调用方发布进度的节流间隔：既是 UI 刷新节奏，也是采样节奏。 */
        const val SAMPLE_INTERVAL_MS = 100L

        /** 滑动窗口跨度：覆盖 2s 的字节差，足够压掉一个 RTT 级别的抖动。 */
        const val DEFAULT_WINDOW_MS = 2_000L

        /** 起步预热：这段时间只积累样本不出读数。 */
        const val DEFAULT_WARMUP_MS = 500L

        /** 一阶低通的时间常数：约 1s 后跟上窗口速率。 */
        const val DEFAULT_TIME_CONSTANT_MS = 1_000.0

        /** dt ≥ 3τ 时指数项已衰减到 5% 以下，此时直接取窗口速率。 */
        private const val DIRECT_SNAP_FACTOR = 3.0
    }
}
