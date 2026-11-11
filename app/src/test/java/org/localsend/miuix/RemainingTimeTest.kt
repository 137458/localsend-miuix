package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.RemainingTime
import org.localsend.miuix.transfer.coarsenedForDisplay

class RemainingTimeTest {
    @Test
    fun inProgressUsesIndependentEtaMath() {
        assertEquals(
            RemainingTime.Seconds(3),
            RemainingTime.of(
                status = TransferStatus.InProgress,
                speed = 1000,
                totalBytes = 5000,
                transferredBytes = 2000,
            ),
        )
        assertEquals(
            RemainingTime.Minutes(2, 5),
            RemainingTime.of(
                status = TransferStatus.InProgress,
                speed = 1,
                totalBytes = 125,
                transferredBytes = 0,
            ),
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
            TransferStatus.Failed,
        )) {
            assertEquals(
                "status=$status must hide the label",
                RemainingTime.Hidden,
                RemainingTime.of(status, speed = 1000, totalBytes = 1000, transferredBytes = 0),
            )
        }
    }

    @Test
    fun nonPositiveSpeedOrTotalIsCalculating() {
        assertEquals(
            RemainingTime.Calculating,
            RemainingTime.of(TransferStatus.InProgress, speed = -10, totalBytes = 5000, transferredBytes = 0),
        )
        assertEquals(
            RemainingTime.Calculating,
            RemainingTime.of(TransferStatus.InProgress, speed = 100, totalBytes = 0, transferredBytes = 0),
        )
    }

    @Test
    fun transferredBeyondTotalIsAlmostDone() {
        assertEquals(
            RemainingTime.AlmostDone,
            RemainingTime.of(TransferStatus.InProgress, speed = 100, totalBytes = 1000, transferredBytes = 1200),
        )
    }

    @Test
    fun minuteAndHourBoundaries() {
        assertEquals(
            RemainingTime.Seconds(59),
            RemainingTime.of(TransferStatus.InProgress, speed = 10, totalBytes = 590, transferredBytes = 0),
        )
        assertEquals(
            RemainingTime.Minutes(minutes = 1, seconds = 0),
            RemainingTime.of(TransferStatus.InProgress, speed = 10, totalBytes = 600, transferredBytes = 0),
        )
        assertEquals(
            RemainingTime.Minutes(minutes = 59, seconds = 59),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 3599, transferredBytes = 0),
        )
        assertEquals(
            RemainingTime.Hours(hours = 1, minutes = 0),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 3600, transferredBytes = 0),
        )
        assertEquals(
            RemainingTime.Hours(hours = 2, minutes = 1),
            RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 7260, transferredBytes = 0),
        )
    }

    @Test
    fun coarsenedSecondsStaysExactBelowTenAndRoundsUpToFiveSecondSteps() {
        assertEquals(RemainingTime.Seconds(9), RemainingTime.Seconds(9).coarsenedForDisplay())
        assertEquals(RemainingTime.Seconds(10), RemainingTime.Seconds(10).coarsenedForDisplay())
        assertEquals(RemainingTime.Seconds(15), RemainingTime.Seconds(12).coarsenedForDisplay())
        assertEquals(RemainingTime.Seconds(55), RemainingTime.Seconds(55).coarsenedForDisplay())
        assertEquals(RemainingTime.Minutes(1, 0), RemainingTime.Seconds(58).coarsenedForDisplay())
    }

    @Test
    fun coarsenedMinutesRoundsSecondsHalfUpAndDropsTheSecondDigit() {
        assertEquals(RemainingTime.Minutes(3, 0), RemainingTime.Minutes(3, 14).coarsenedForDisplay())
        assertEquals(RemainingTime.Minutes(3, 0), RemainingTime.Minutes(3, 29).coarsenedForDisplay())
        assertEquals(RemainingTime.Minutes(4, 0), RemainingTime.Minutes(3, 44).coarsenedForDisplay())
        assertEquals(RemainingTime.Hours(1, 0), RemainingTime.Minutes(59, 45).coarsenedForDisplay())
    }

    @Test
    fun coarseningLeavesHoursAndNonNumericStatesUntouched() {
        assertEquals(RemainingTime.Hours(2, 1), RemainingTime.Hours(2, 1).coarsenedForDisplay())
        assertEquals(RemainingTime.Calculating, RemainingTime.Calculating.coarsenedForDisplay())
        assertEquals(RemainingTime.AlmostDone, RemainingTime.AlmostDone.coarsenedForDisplay())
        assertEquals(RemainingTime.Hidden, RemainingTime.Hidden.coarsenedForDisplay())
    }

    @Test
    fun coarseningKeepsExactOfResultAndOnlyCoarsensForDisplay() {
        val exact = RemainingTime.of(TransferStatus.InProgress, speed = 1, totalBytes = 125, transferredBytes = 0)
        assertEquals(RemainingTime.Minutes(2, 5), exact)
        assertEquals(RemainingTime.Minutes(2, 0), exact.coarsenedForDisplay())
    }
}
