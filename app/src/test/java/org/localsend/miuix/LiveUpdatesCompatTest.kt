package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.notification.LiveUpdatesCompat

class LiveUpdatesCompatTest {

    private val testDevice = Device(
        alias = "测试设备",
        fingerprint = "fp-123",
        port = 53317,
        protocol = "http",
        ip = "192.168.1.50"
    )

    @Test
    fun testFormatChipSpeedEta_inProgress() {
        val session = TransferSession(
            sessionId = "s1",
            device = testDevice,
            isIncoming = false,
            files = listOf(FileItem(name = "video.mp4", size = 100L * 1024 * 1024)),
            totalBytes = 100L * 1024 * 1024,
            transferredBytes = 50L * 1024 * 1024,
            speed = 25L * 1024 * 1024, // 25 MB/s
            status = TransferStatus.InProgress
        )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        // 50MB remaining at 25MB/s -> 2s ETA
        assertTrue("Chip text should contain speed: $chipText", chipText.contains("25M/s") || chipText.contains("25"))
        assertTrue("Chip text should contain eta: $chipText", chipText.contains("2s"))
        assertTrue("Chip text should be compact (<= 12 chars): $chipText", chipText.length <= 12)
    }

    @Test
    fun testFormatChipSpeedEta_completed() {
        val session = TransferSession(
            sessionId = "s2",
            device = testDevice,
            isIncoming = false,
            files = listOf(FileItem(name = "test.txt", size = 1024)),
            totalBytes = 1024,
            transferredBytes = 1024,
            speed = 0L,
            status = TransferStatus.Completed
        )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        assertEquals("已完成", chipText)
    }

    @Test
    fun testFormatChipSpeedEta_textMessage() {
        val textFile = FileItem(name = "msg.txt", size = 10, textContent = "Hello")
        val session = TransferSession(
            sessionId = "s3",
            device = testDevice,
            isIncoming = true,
            files = listOf(textFile),
            totalBytes = 10,
            status = TransferStatus.InProgress
        )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        assertEquals("文本", chipText)
    }
}
