package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.TransferOutcome

class TransferOutcomeTest {

    @Test
    fun allFailedMarksSessionFailed() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Failed, error = "reset"),
            FileItem(id = "2", name = "b.txt", size = 1, status = TransferStatus.Failed, error = "reset")
        )
        val outcome = TransferOutcome.aggregate(
            files = files,
            currentStatus = TransferStatus.InProgress,
            allFailedMessage = "all-failed",
            partialFailedMessage = { failed, total -> "partial $failed/$total" }
        )
        assertEquals(TransferStatus.Failed, outcome.status)
        assertEquals("reset", outcome.errorMessage)
    }

    @Test
    fun partialFailureStaysCompletedWithSummary() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Completed),
            FileItem(id = "2", name = "b.txt", size = 1, status = TransferStatus.Failed, error = "closed")
        )
        val outcome = TransferOutcome.aggregate(
            files = files,
            currentStatus = TransferStatus.InProgress,
            allFailedMessage = "all-failed",
            partialFailedMessage = { failed, total -> "partial $failed/$total" }
        )
        assertEquals(TransferStatus.Completed, outcome.status)
        assertEquals("partial 1/2", outcome.errorMessage)
    }

    @Test
    fun cancelIsNotOverwrittenByFileResults() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Failed)
        )
        val outcome = TransferOutcome.aggregate(
            files = files,
            currentStatus = TransferStatus.Canceled,
            allFailedMessage = "all-failed",
            partialFailedMessage = { failed, total -> "partial $failed/$total" }
        )
        assertEquals(TransferStatus.Canceled, outcome.status)
        assertNull(outcome.errorMessage)
    }
}
