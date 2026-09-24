package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.RemainingTime

class RemainingTimeTest {

    @Test
    fun inProgressUsesIndependentEtaMath() {
        assertEquals(
            RemainingTime.Seconds(3),
            RemainingTime.of(
                status = TransferStatus.InProgress,
                speed = 1000,
                totalBytes = 5000,
                transferredBytes = 2000
            )
        )
        assertEquals(
            RemainingTime.Minutes(2, 5),
            RemainingTime.of(
                status = TransferStatus.InProgress,
                speed = 1,
                totalBytes = 125,
                transferredBytes = 0
            )
        )
        assertEquals(RemainingTime.Calculating, RemainingTime.of(TransferStatus.InProgress, 0, 100, 10))
        assertEquals(RemainingTime.AlmostDone, RemainingTime.of(TransferStatus.InProgress, 10, 100, 100))
        assertEquals(RemainingTime.Hidden, RemainingTime.of(TransferStatus.Completed, 10, 100, 100))
    }

    @Test
    fun nonInProgressStatusesAreAlwaysHidden() {
        for (status in listOf(
            TransferStatus.WaitingApproval,
            TransferStatus.Canceled,
            TransferStatus.Failed
        )) {
            assertEquals(
                "status=$status must hide the label",
                RemainingTime.Hidden,
                RemainingTime.of(status, speed = 1000, totalBytes = 1000, transferredBytes = 0)
            )
        }
    }

    @Test
    fun nonPositiveSpeedOrTotalIsCalculating() {
        assertEquals(
            RemainingTime.Calculating,
            RemainingTime.of(TransferStatus.InProgress, speed = -10, totalBytes = 5000, transferredBytes = 0)
        )
        assertEquals(
            RemainingTime.Calculating,
            RemainingTime.of(TransferStatus.InProgress, speed = 100, totalBytes = 0, transferredBytes = 0)
        )
    }

    @Test
    fun transferredBeyondTotalIsAlmostDone() {
        assertEquals(
            RemainingTime.AlmostDone,
            RemainingTime.of(TransferStatus.InProgress, speed = 100, totalBytes = 1000, transferredBytes = 1200)
        )
    }

    @Test
    fun minuteAndHourBoundaries() {
        assertEquals(
            RemainingTime.Seconds(59),
            RemainingTime.of(TransferStatus.InProgress, speed = 10, totalBytes = 590, transferredBytes = 0)
        )
        assertEquals(
            RemainingTime.Minutes(minutes = 1, seconds = 0),
            RemainingTime.of(TransferStatus.InProgress, speed = 10, totalBytes = 600, transferredBytes = 0)
        )
        assertEquals(
            RemainingTime.Minutes(minutes = 59, seconds = 59),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 3599, transferredBytes = 0)
        )
        assertEquals(
            RemainingTime.Hours(hours = 1, minutes = 0),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 3600, transferredBytes = 0)
        )
        assertEquals(
            RemainingTime.Hours(hours = 2, minutes = 1),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 7260, transferredBytes = 0)
        )
    }
}
