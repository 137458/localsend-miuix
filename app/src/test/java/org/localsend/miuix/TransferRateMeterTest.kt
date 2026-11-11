package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.transfer.TransferRateMeter

/**
 * [TransferRateMeter] 的对外契约：2000ms 滑动窗口 + 一阶低通，预热期不出读数。
 * 断言只看 sample/finish 的返回值，不依赖内部状态。
 */
class TransferRateMeterTest {
    private val mebibyte = 1024L * 1024L
    private val interval = TransferRateMeter.SAMPLE_INTERVAL_MS
    private val tolerance = 0.05

    @Test
    fun warmupWindowReturnsZero() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        while (now <= 400L) {
            assertEquals("预热期 t=$now 必须返回 0，避免起步阶段的读数剧烈跳动", 0L, meter.sample(now, accumulated))
            accumulated += mebibyte
            now += interval
        }
        // 预热期结束后必须立刻给出真实读数，不能一直停在 0
        var reading = 0L
        while (now <= 700L) {
            reading = meter.sample(now, accumulated)
            accumulated += mebibyte
            now += interval
        }
        assertTrue("预热期结束后应给出正读数，实际 $reading", reading > 0L)
    }

    @Test
    fun steadyRateIsReportedWithinTolerance() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        var checked = 0
        while (now <= 3_000L) {
            val reading = meter.sample(now, accumulated)
            if (now >= 1_500L) {
                checked++
                assertTrue(
                    "稳定 10 MiB/s 时 t=$now 的读数 $reading B/s 超出 9.5~10.5 MiB/s",
                    reading >= (9.5 * mebibyte).toLong() && reading <= (10.5 * mebibyte).toLong(),
                )
            }
            accumulated += mebibyte
            now += interval
        }
        assertEquals("稳定段应覆盖 16 个采样点", 16, checked)
    }

    @Test
    fun jitteryInputKeepsReadingsTight() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        var index = 0
        val readings = mutableListOf<Long>()
        // 每 100ms 交替 +0.2MiB / +1.8MiB：瞬时速率在 2MiB/s 与 18MiB/s 之间抖动 9 倍，均值仍是 10MiB/s
        while (now <= 3_000L) {
            val reading = meter.sample(now, accumulated)
            if (now >= 1_500L && readings.size < 10) readings += reading
            accumulated += if (index % 2 == 0) mebibyte / 5 else mebibyte * 9 / 5
            index++
            now += interval
        }
        assertEquals("抖动用例应取满 10 个读数", 10, readings.size)
        val lowest = readings.minOrNull() ?: 0L
        val highest = readings.maxOrNull() ?: 0L
        val mean = readings.average()
        assertTrue("每个读数都必须是正速率，min=$lowest", lowest > 0L)
        assertTrue(
            "抖动输入的读数极差 ${highest - lowest} 超过均值 $mean 的 10%（min=$lowest, max=$highest）",
            (highest - lowest) <= mean * 0.1,
        )
        // 极差检查对"恒为 0"同样成立，因此再钉住均值贴近名义 10 MiB/s
        val nominal = 10.0 * mebibyte
        assertTrue("抖动输入的均值 $mean 应贴近名义速率 $nominal", mean >= nominal * 0.9 && mean <= nominal * 1.1)
    }

    @Test
    fun stallDecaysTowardsZero() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        var stable = 0L
        while (now <= 3_000L) {
            stable = meter.sample(now, accumulated)
            accumulated += mebibyte
            now += interval
        }
        // 停喂 2s：累计字节不再增长，滑动窗口逐渐被零增量填满
        var stalled = stable
        val stallEnd = now + 2_000L
        while (now <= stallEnd) {
            stalled = meter.sample(now, accumulated)
            now += interval
        }
        assertTrue("停喂 2s 后读数 $stalled 必须低于稳定值 $stable 的一半", stalled * 2 < stable)
        // 继续静止：读数最终归零，且不出现负值
        var idle = stalled
        val idleEnd = now + 20_000L
        while (now <= idleEnd) {
            idle = meter.sample(now, accumulated)
            now += interval
        }
        assertEquals("长时间停滞后读数应归零", 0L, idle)
    }

    @Test
    fun clockRollbackKeepsLastReading() {
        val meter = TransferRateMeter()
        meter.sample(0L, 0L)
        val forward = meter.sample(1_000L, 10 * mebibyte)
        assertTrue("正常前进的采样应给出正读数，实际 $forward", forward > 0L)
        val rolledBack = meter.sample(900L, 9 * mebibyte)
        assertTrue("时钟回退不得返回负值，实际 $rolledBack", rolledBack >= 0L)
        assertEquals("时钟回退应沿用上一次读数", forward, rolledBack)
    }

    @Test
    fun finishReportsAverageForShortTransfer() {
        val meter = TransferRateMeter()
        meter.sample(0L, 0L)
        meter.sample(100L, mebibyte)
        val speed = meter.finish(100L, mebibyte)
        assertTrue("1MiB/100ms 的小文件收尾不能显示 0 B/s，实际 $speed", speed > 0L)
        val expected = 10.0 * mebibyte
        assertEquals("finish 应给出全程平均速率", expected, speed.toDouble(), expected * tolerance)
    }

    @Test
    fun finishReturnsWholeTransferAverage() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        var lastNow = 0L
        var lastBytes = 0L
        while (now <= 3_000L) {
            lastNow = now
            lastBytes = accumulated
            meter.sample(now, accumulated)
            accumulated += mebibyte
            now += interval
        }
        val speed = meter.finish(lastNow, lastBytes)
        val expected = 10.0 * mebibyte
        assertEquals("finish 忽略预热与低通，直接给全程平均", expected, speed.toDouble(), expected * tolerance)
    }

    @Test
    fun finishWithoutSamplesReturnsZero() {
        assertEquals("从未采样时 finish 返回 0", 0L, TransferRateMeter().finish(500L, 10 * mebibyte))
    }
}
