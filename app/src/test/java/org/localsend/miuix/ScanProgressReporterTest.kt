package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.network.ScanProgressReporter

class ScanProgressReporterTest {
    @Test
    fun reportsProgressThenOneCompletion() {
        val events = mutableListOf<Pair<Int, Int>>()
        val reporter = ScanProgressReporter { current, total -> events.add(current to total) }

        reporter.report(1, 4)
        reporter.report(3, 4)
        reporter.report(4, 4)
        reporter.finish(4)
        reporter.finish(4)

        assertEquals(listOf(1 to 4, 3 to 4, 4 to 4), events)
    }

    @Test
    fun earlyExitStillReportsCompletion() {
        // 无可用网卡（例如名单把接口全部排除）扫描直接早退：必须上报一次完成态，调用方才能复位 isScanning
        val events = mutableListOf<Pair<Int, Int>>()
        val reporter = ScanProgressReporter { current, total -> events.add(current to total) }

        reporter.finish()
        reporter.finish()

        assertEquals(listOf(0 to 0), events)
    }

    @Test
    fun failureAfterProgressStillReportsCompletionOnce() {
        // 异常退出路径：已上报过进度后，仍需且仅需一次完成态
        val events = mutableListOf<Pair<Int, Int>>()
        val reporter = ScanProgressReporter { current, total -> events.add(current to total) }

        reporter.report(2, 8)
        reporter.finish(8)
        reporter.report(5, 8)

        assertEquals(listOf(2 to 8, 8 to 8), events)
    }

    @Test
    fun abandonedRoundStaysSilent() {
        // 被新一轮扫描取代的旧轮次：退出时既不能回吐进度，也不能把新一轮的“扫描中”状态复位
        val events = mutableListOf<Pair<Int, Int>>()
        val reporter = ScanProgressReporter { current, total -> events.add(current to total) }

        reporter.report(1, 4)
        reporter.abandon()
        reporter.report(2, 4)
        reporter.finish(4)

        assertEquals(listOf(1 to 4), events)
    }
}
