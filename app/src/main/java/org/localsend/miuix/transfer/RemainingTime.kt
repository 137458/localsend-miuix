package org.localsend.miuix.transfer

import org.localsend.miuix.model.TransferStatus

/**
 * Remaining-time label for an in-progress transfer. UI maps this to localized strings;
 * tests assert the label, not a hardcoded language.
 */
sealed class RemainingTime {
    data object Hidden : RemainingTime()

    data object Calculating : RemainingTime()

    data object AlmostDone : RemainingTime()

    data class Seconds(
        val value: Long,
    ) : RemainingTime()

    data class Minutes(
        val minutes: Long,
        val seconds: Long,
    ) : RemainingTime()

    data class Hours(
        val hours: Long,
        val minutes: Long,
    ) : RemainingTime()

    companion object {
        fun of(
            status: TransferStatus,
            speed: Long,
            totalBytes: Long,
            transferredBytes: Long,
        ): RemainingTime {
            if (status != TransferStatus.InProgress) return Hidden
            if (speed <= 0L || totalBytes <= 0L) return Calculating
            val remainingBytes = (totalBytes - transferredBytes).coerceAtLeast(0L)
            if (remainingBytes == 0L) return AlmostDone
            val seconds = remainingBytes / speed
            return when {
                seconds < 60 -> Seconds(seconds)
                seconds < 3600 -> Minutes(seconds / 60, seconds % 60)
                else -> Hours(seconds / 3600, (seconds % 3600) / 60)
            }
        }
    }
}

/**
 * 展示用粗化：把精确 ETA 收敛到稳定的文本粒度。
 *
 * [RemainingTime.of] 每秒重算一次，速度在相邻采样间小幅抖动时，精确秒数会被反复改写
 * （13s → 14s → 12s），预估值看起来一直在跳。展示层因此按刻度归并：文案只在跨过刻度时变化，
 * 而 [RemainingTime.of] 的精确语义保持不变，供需要细粒度数值的调用方继续使用。
 *
 * 规则：
 * - [RemainingTime.Seconds]：10 秒内保持原样（临近结束时倒计时本身有意义）；
 *   其余向上取整到 5 的倍数（10、15、…、55），取整后达到 60 则归为 [RemainingTime.Minutes] 1 分。
 * - [RemainingTime.Minutes]：秒数达到 30 进位到下一分钟（四舍五入到分钟），结果只保留分钟；
 *   进位后达到 60 分则归为 [RemainingTime.Hours] 1 小时。
 * - [RemainingTime.Hours]、[RemainingTime.Hidden]、[RemainingTime.Calculating]、
 *   [RemainingTime.AlmostDone] 不做处理。
 */
fun RemainingTime.coarsenedForDisplay(): RemainingTime =
    when (this) {
        is RemainingTime.Seconds -> {
            if (value < 10L) {
                RemainingTime.Seconds(value)
            } else {
                val rounded = ((value + 4L) / 5L) * 5L
                if (rounded >= 60L) RemainingTime.Minutes(1L, 0L) else RemainingTime.Seconds(rounded)
            }
        }
        is RemainingTime.Minutes -> {
            val roundedMinutes = if (seconds >= 30L) minutes + 1L else minutes
            if (roundedMinutes >= 60L) RemainingTime.Hours(1L, 0L) else RemainingTime.Minutes(roundedMinutes, 0L)
        }
        is RemainingTime.Hours -> RemainingTime.Hours(hours, minutes)
        RemainingTime.Hidden, RemainingTime.Calculating, RemainingTime.AlmostDone -> this
    }
