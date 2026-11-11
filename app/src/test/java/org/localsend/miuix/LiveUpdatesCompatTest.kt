package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.notification.LiveUpdatesCompat

class LiveUpdatesCompatTest {
    private val testDevice =
        Device(
            alias = "测试设备",
            fingerprint = "fp-123",
            port = 53317,
            protocol = "http",
            ip = "192.168.1.50",
        )

    @Test
    fun testFormatChipSpeedEta_inProgress() {
        val session =
            TransferSession(
                sessionId = "s1",
                device = testDevice,
                isIncoming = false,
                files = listOf(FileItem(name = "video.mp4", size = 100L * 1024 * 1024)),
                totalBytes = 100L * 1024 * 1024,
                transferredBytes = 50L * 1024 * 1024,
                speed = 25L * 1024 * 1024, // 25 MB/s
                status = TransferStatus.InProgress,
            )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        // 50MB remaining at 25MB/s -> 2s ETA. No Context: language-neutral fallbacks.
        assertTrue("Chip text should start with outgoing prefix: $chipText", chipText.startsWith(LiveUpdatesCompat.CHIP_OUT))
        assertTrue("Chip text should contain speed: $chipText", chipText.contains("25M/s") || chipText.contains("25"))
        assertTrue("Chip text should contain eta: $chipText", chipText.contains("2s"))
        assertTrue("Chip text should be compact (<= 12 chars): $chipText", chipText.length <= 12)

        val incomingSession = session.copy(isIncoming = true)
        val incomingChipText = LiveUpdatesCompat.formatChipSpeedEta(incomingSession)
        assertTrue("Incoming chip text should start with incoming prefix: $incomingChipText", incomingChipText.startsWith(LiveUpdatesCompat.CHIP_IN))
    }

    @Test
    fun testFormatChipSpeedEta_completed() {
        val session =
            TransferSession(
                sessionId = "s2",
                device = testDevice,
                isIncoming = false,
                files = listOf(FileItem(name = "test.txt", size = 1024)),
                totalBytes = 1024,
                transferredBytes = 1024,
                speed = 0L,
                status = TransferStatus.Completed,
            )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        assertEquals(LiveUpdatesCompat.CHIP_COMPLETED, chipText)
    }

    @Test
    fun testFormatChipSpeedEta_textMessage() {
        val textFile = FileItem(name = "msg.txt", size = 10, textContent = "Hello")
        val session =
            TransferSession(
                sessionId = "s3",
                device = testDevice,
                isIncoming = true,
                files = listOf(textFile),
                totalBytes = 10,
                status = TransferStatus.InProgress,
            )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        assertEquals(LiveUpdatesCompat.CHIP_TEXT, chipText)
    }

    @Test
    fun testFormatChipSpeedEta_minuteScaleIsCoarsenedToWholeMinutes() {
        // 100MB 待传、1MB/s -> 精确 100s (Minutes(1,40))，展示粗化后只报整分钟 Minutes(2,0)
        val session =
            TransferSession(
                sessionId = "s4",
                device = testDevice,
                isIncoming = false,
                files = listOf(FileItem(name = "big.bin", size = 100L * 1024 * 1024)),
                totalBytes = 100L * 1024 * 1024,
                transferredBytes = 0L,
                speed = 1L * 1024 * 1024,
                status = TransferStatus.InProgress,
            )

        val chipText = LiveUpdatesCompat.formatChipSpeedEta(session)
        assertTrue("Chip text should start with outgoing prefix: $chipText", chipText.startsWith(LiveUpdatesCompat.CHIP_OUT))
        assertTrue("Chip eta should be coarsened to whole minutes: $chipText", chipText.endsWith(" 2m"))
        assertFalse("Chip must not render a second-level eta: $chipText", Regex("\\d+s$").containsMatchIn(chipText))
        assertTrue("Chip text should be compact (<= 12 chars): $chipText", chipText.length <= 12)
    }
}
