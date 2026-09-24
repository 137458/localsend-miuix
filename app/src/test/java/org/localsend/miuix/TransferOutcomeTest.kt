package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.TransferOutcome

class TransferOutcomeTest {

    private val dummyDevice = Device(
        alias = "Peer",
        fingerprint = "fp",
        port = 53317,
        protocol = "http",
        download = false,
        ip = "192.168.1.10",
        deviceType = DeviceType.mobile
    )

    private fun aggregate(files: List<FileItem>, currentStatus: TransferStatus = TransferStatus.InProgress) =
        TransferOutcome.aggregate(
            files = files,
            currentStatus = currentStatus,
            allFailedMessage = "all-failed",
            partialFailedMessage = { failed, total -> "partial $failed/$total" }
        )

    private fun sessionOf(files: List<FileItem>, currentStatus: TransferStatus = TransferStatus.InProgress): TransferSession {
        val outcome = aggregate(files, currentStatus)
        return TransferSession(
            sessionId = "s1",
            device = dummyDevice,
            isIncoming = false,
            files = files,
            totalBytes = files.sumOf { it.size },
            status = outcome.status,
            errorMessage = outcome.errorMessage
        )
    }

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

    @Test
    fun partialFailureSessionIsFlaggedForUiAndNotification() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Completed),
            FileItem(id = "2", name = "b.txt", size = 1, status = TransferStatus.Failed, error = "closed")
        )
        assertTrue(sessionOf(files).isPartialFailure)
    }

    @Test
    fun fullSuccessSessionIsNotPartialFailure() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Completed),
            FileItem(id = "2", name = "b.txt", size = 1, status = TransferStatus.Completed)
        )
        val session = sessionOf(files)
        assertEquals(TransferStatus.Completed, session.status)
        assertFalse(session.isPartialFailure)
    }

    @Test
    fun canceledSessionIsNotPartialFailureEvenWithFailedFile() {
        val files = listOf(
            FileItem(id = "1", name = "a.txt", size = 1, status = TransferStatus.Failed, error = "closed")
        )
        val session = sessionOf(files, TransferStatus.Canceled)
        assertEquals(TransferStatus.Canceled, session.status)
        assertFalse(session.isPartialFailure)
    }
}
