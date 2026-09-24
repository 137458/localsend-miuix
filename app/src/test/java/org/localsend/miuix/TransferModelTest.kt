package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.RemainingTime

class TransferModelTest {

    private fun file(
        id: String,
        status: TransferStatus = TransferStatus.WaitingApproval,
        name: String = "$id.bin",
        size: Long = 100L
    ) = FileItem(id = id, name = name, size = size, status = status)

    private fun session(
        files: List<FileItem>,
        status: TransferStatus = TransferStatus.InProgress,
        totalBytes: Long = 1000L,
        transferredBytes: Long = 0L,
        speed: Long = 0L,
        errorMessage: String? = null
    ) = TransferSession(
        sessionId = "sess-1",
        device = Device(alias = "Peer", fingerprint = "fp", ip = "10.0.0.1"),
        isIncoming = false,
        files = files,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        speed = speed,
        status = status,
        errorMessage = errorMessage
    )

    @Test
    fun toDtoMarksTextMessageWithPreview() {
        val text = FileItem(id = "t1", name = "note.txt", size = 5L, textContent = "hello", mimeType = "text/plain")
        val dto = text.toDto()
        assertEquals("text", dto.fileType)
        assertEquals("hello", dto.preview)

        val longText = FileItem(id = "t2", name = "note.txt", size = 2500L, textContent = "x".repeat(2500), mimeType = "text/plain")
        assertEquals(2000, longText.toDto().preview?.length)
    }

    @Test
    fun toDtoKeepsBinaryMimeAndSha() {
        val binary = FileItem(id = "b1", name = "photo.jpg", size = 2048L, mimeType = "image/jpeg", expectedSha256 = "abc123")
        val dto = binary.toDto()
        assertEquals("image/jpeg", dto.fileType)
        assertNull(dto.preview)
        assertEquals("abc123", dto.sha256)
        assertFalse(binary.isTextMessage)
    }

    @Test
    fun isTextMessageDefaultTracksTextContent() {
        assertTrue(FileItem(id = "x", name = "n", textContent = "hi").isTextMessage)
        assertFalse(FileItem(id = "y", name = "n").isTextMessage)
    }

    @Test
    fun progressIsZeroForUnknownTotalAndClampedToUnit() {
        assertEquals(0.0, session(listOf(file("a")), totalBytes = 0L, transferredBytes = 0L).progress.toDouble(), 0.0001)
        assertEquals(0.0, session(listOf(file("a")), totalBytes = 1000L, transferredBytes = -50L).progress.toDouble(), 0.0001)
        assertEquals(1.0, session(listOf(file("a")), totalBytes = 1000L, transferredBytes = 1500L).progress.toDouble(), 0.0001)
        val quarter = session(listOf(file("a")), totalBytes = 1000L, transferredBytes = 250L)
        assertEquals(0.25, quarter.progress.toDouble(), 0.0001)
        assertEquals(25, quarter.progressPercent)
    }

    @Test
    fun currentFileIndexTracksInProgressThenLastCompleted() {
        val inProgress = session(
            listOf(file("a", TransferStatus.Completed), file("b", TransferStatus.InProgress), file("c"))
        )
        assertEquals(1, inProgress.currentFileIndex)
        assertEquals("b.bin", inProgress.currentFile?.name)

        val completed = session(
            listOf(file("a", TransferStatus.Completed), file("b", TransferStatus.Completed))
        )
        assertEquals(1, completed.currentFileIndex)

        val idle = session(listOf(file("a"), file("b")))
        assertEquals(0, idle.currentFileIndex)
        assertEquals("a.bin", idle.currentFile?.name)
    }

    @Test
    fun sessionTextMessageHelpersRequireSingleTextFile() {
        val single = session(listOf(FileItem(id = "t", name = "n", textContent = "hi")))
        assertTrue(single.isTextMessage)
        assertEquals("hi", single.singleTextMessageContent)

        val mixed = session(listOf(FileItem(id = "t", name = "n", textContent = "hi"), file("b")))
        assertFalse(mixed.isTextMessage)
        assertNull(mixed.singleTextMessageContent)
    }

    @Test
    fun partialFailureRequiresCompletedStatusAndMessage() {
        assertTrue(session(listOf(file("a")), status = TransferStatus.Completed, errorMessage = "1 of 2 failed").isPartialFailure)
        assertFalse(session(listOf(file("a")), status = TransferStatus.Completed, errorMessage = null).isPartialFailure)
        assertFalse(session(listOf(file("a")), status = TransferStatus.Completed, errorMessage = "  ").isPartialFailure)
        assertFalse(session(listOf(file("a")), status = TransferStatus.Failed, errorMessage = "boom").isPartialFailure)
    }

    @Test
    fun createSnapshotDeepCopiesFilesAndStampsSequence() {
        val original = file("a", TransferStatus.InProgress)
        val session = session(listOf(original))
        val snapshot = session.createSnapshot(seq = 42L)

        assertEquals(42L, snapshot.updateSeq)
        assertEquals("sess-1", snapshot.sessionId)
        assertNotSame(session.files.first(), snapshot.files.first())

        original.status = TransferStatus.Failed
        original.error = "post-snapshot mutation"
        assertEquals(TransferStatus.InProgress, snapshot.files.first().status)
        assertNull(snapshot.files.first().error)
    }

    @Test
    fun remainingTimeDelegatesToSharedCalculator() {
        val inProgress = session(listOf(file("a")), speed = 100L, totalBytes = 1000L, transferredBytes = 0L)
        assertEquals(RemainingTime.Seconds(10), inProgress.remainingTime)

        val waiting = session(listOf(file("a")), status = TransferStatus.WaitingApproval, speed = 100L, totalBytes = 1000L)
        assertEquals(RemainingTime.Hidden, waiting.remainingTime)
    }
}