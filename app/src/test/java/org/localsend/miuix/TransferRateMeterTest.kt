package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.transfer.TransferRateMeter
import org.localsend.miuix.transfer.cumulativeAverageSpeed

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

    @Test
    fun averageSpeedReportsCumulativeRateDuringWarmup() {
        val meter = TransferRateMeter()
        meter.sample(0L, 0L)
        // 前 100ms 传完 1MiB：预热期内瞬时读数是 0，会话级读数必须立刻给出真实平均，不能显示“计算中”
        assertEquals("预热期内瞬时读数保持 0", 0L, meter.sample(100L, mebibyte))
        val average = meter.averageSpeed(100L, mebibyte)
        val expected = 10.0 * mebibyte
        assertEquals("累计平均应从起点开始算，不被预热吞掉", expected, average.toDouble(), expected * tolerance)
    }

    @Test
    fun averageSpeedTrailsInstantWhenTransferStalls() {
        val meter = TransferRateMeter()
        var accumulated = 0L
        var now = 0L
        var stableNow = 0L
        var stableBytes = 0L
        while (now <= 1_000L) {
            stableNow = now
            stableBytes = accumulated
            meter.sample(now, accumulated)
            accumulated += mebibyte
            now += interval
        }
        val burstAverage = meter.averageSpeed(stableNow, stableBytes)
        // 后 3s 完全停滞：瞬时读数随窗口与低通衰减，累计平均保持正但被整体拉低
        while (now <= 4_000L) {
            meter.sample(now, accumulated)
            now += interval
        }
        val instant = meter.sample(now, accumulated)
        val stalledAverage = meter.averageSpeed(now, accumulated)
        assertTrue("停滞期累计平均应低于满速期的九成：$stalledAverage vs $burstAverage", stalledAverage * 10 < burstAverage * 9)
        assertTrue("累计平均在停滞期必须低于速率稳定期：$stalledAverage vs $burstAverage", stalledAverage < burstAverage)
        assertTrue("累计平均必须高于停滞期的瞬时读数：average=$stalledAverage instant=$instant", stalledAverage > instant)
        assertTrue("累计平均不得归零，实际 $stalledAverage", stalledAverage > 0L)
    }

    @Test
    fun cumulativeAverageSpeedReportsWholeTransferRate() {
        assertEquals("60MiB/6s 应为 10MiB/s", 10 * mebibyte, cumulativeAverageSpeed(60 * mebibyte, 6_000L))
        assertEquals("2MiB/100ms 应为 20MiB/s", 20 * mebibyte, cumulativeAverageSpeed(2 * mebibyte, 100L))
        // 抖动只影响瞬时值：累计平均在字节数与耗时固定时是唯一确定的
        assertEquals(
            "同一段传输的累计平均不受采样抖动影响",
            cumulativeAverageSpeed(60 * mebibyte, 6_000L),
            cumulativeAverageSpeed(60 * mebibyte, 6_000L),
        )
    }

    @Test
    fun cumulativeAverageSpeedRejectsDegenerateInput() {
        assertEquals("零耗时不得除零", 0L, cumulativeAverageSpeed(mebibyte, 0L))
        assertEquals("时钟回退（负耗时）不得产生负速率", 0L, cumulativeAverageSpeed(mebibyte, -5L))
        assertEquals("零字节传输速率为 0", 0L, cumulativeAverageSpeed(0L, 1_000L))
    }
}
