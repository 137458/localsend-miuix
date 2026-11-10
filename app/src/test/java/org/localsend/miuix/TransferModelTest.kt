package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.MAX_INLINE_TEXT_SIZE
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.model.countCategories
import org.localsend.miuix.transfer.RemainingTime

class TransferModelTest {
    private fun file(
        id: String,
        status: TransferStatus = TransferStatus.WaitingApproval,
        name: String = "$id.bin",
        size: Long = 100L,
    ) = FileItem(id = id, name = name, size = size, status = status)

    private fun session(
        files: List<FileItem>,
        status: TransferStatus = TransferStatus.InProgress,
        totalBytes: Long = 1000L,
        transferredBytes: Long = 0L,
        speed: Long = 0L,
        errorMessage: String? = null,
    ) = TransferSession(
        sessionId = "sess-1",
        device = Device(alias = "Peer", fingerprint = "fp", ip = "10.0.0.1"),
        isIncoming = false,
        files = files,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        speed = speed,
        status = status,
        errorMessage = errorMessage,
    )

    @Test
    fun toDtoEncodesTextMessageAsPlainTextMimeWithFullPreview() {
        val text = FileItem(id = "t1", name = "note.txt", size = 5L, textContent = "hello", mimeType = "text/plain")
        val dto = text.toDto()
        // 协议 fileType 必须是 MIME：官方接收端 decodeFromMime 只认前缀，裸 "text" 会被当成 other 并落盘为文件
        assertEquals("text/plain", dto.fileType)
        assertEquals("hello", dto.preview)
        assertEquals(5L, dto.size)
    }

    @Test
    fun toDtoKeepsFullTextWithoutPreviewTruncation() {
        val longText = "x".repeat(5000)
        val dto =
            FileItem(id = "t2", name = "note.txt", size = 5000L, textContent = longText, mimeType = "text/plain")
                .toDto()
        // preview 必须完整，不得截断：对端把 preview 当整条消息内容
        assertEquals(longText, dto.preview)
        assertEquals(5000L, dto.size)
    }

    @Test
    fun toDtoCountsInlineTextSizeInUtf8Bytes() {
        val message = "跨端纯文本消息"
        val bytes = message.toByteArray(Charsets.UTF_8)
        // 传入字符数而非字节数的错误 size，toDto 必须以真实 UTF-8 字节数声明，保证 preview 字节数 == size
        val dto =
            FileItem(
                id = "t3",
                name = "note.txt",
                size = message.length.toLong(),
                textContent = message,
                mimeType = "text/plain",
            ).toDto()
        assertEquals(bytes.size.toLong(), dto.size)
        assertEquals(message, dto.preview)
    }

    @Test
    fun toDtoDegradesOversizedTextToRegularFile() {
        val oversized = "x".repeat((MAX_INLINE_TEXT_SIZE + 1).toInt())
        val item =
            FileItem(
                id = "t4",
                name = "note.txt",
                size = oversized.length.toLong(),
                textContent = oversized,
                mimeType = "text/plain",
            )
        val dto = item.toDto()
        // 超出内联上限时不得发出被截断的消息，降级为普通 txt 文件走上传
        assertNull(item.inlineTextContent)
        assertNull(dto.preview)
        assertEquals("text/plain", dto.fileType)
        assertEquals(oversized.length.toLong(), dto.size)
    }

    @Test
    fun inlineTextContentOnlyExposesMessageText() {
        assertNull(FileItem(id = "b1", name = "a.txt").inlineTextContent)
        assertNull(FileItem(id = "b2", name = "a.txt", isTextMessage = false).inlineTextContent)
        assertEquals("hi", FileItem(id = "b3", name = "a.txt", textContent = "hi").inlineTextContent)
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
        val inProgress =
            session(
                listOf(file("a", TransferStatus.Completed), file("b", TransferStatus.InProgress), file("c")),
            )
        assertEquals(1, inProgress.currentFileIndex)
        assertEquals("b.bin", inProgress.currentFile?.name)

        val completed =
            session(
                listOf(file("a", TransferStatus.Completed), file("b", TransferStatus.Completed)),
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

    @Test
    fun countCategoriesGroupsFileTypesCorrectly() {
        val files =
            listOf(
                FileItem(name = "photo.jpg", mimeType = "image/jpeg"),
                FileItem(name = "clip.mp4", mimeType = "video/mp4"),
                FileItem(name = "song.mp3", mimeType = "audio/mpeg"),
                FileItem(name = "note.txt", mimeType = "text/plain", textContent = "hello"),
                FileItem(name = "app.apk", mimeType = "application/vnd.android.package-archive"),
                FileItem(name = "doc.pdf", mimeType = "application/pdf"),
            )
        val counts = files.countCategories()
        assertEquals(1, counts.images)
        assertEquals(1, counts.videos)
        assertEquals(1, counts.audios)
        assertEquals(1, counts.texts)
        assertEquals(1, counts.apks)
        assertEquals(1, counts.others)
    }
}
