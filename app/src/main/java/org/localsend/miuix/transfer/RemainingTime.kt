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
