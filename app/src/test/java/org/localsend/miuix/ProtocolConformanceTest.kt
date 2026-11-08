package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.core.NetworkConstants
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.attachmentContentDisposition
import org.localsend.miuix.network.findPendingCancelTarget
import org.localsend.miuix.network.isUploadFileAcceptable

class ProtocolConformanceTest {
    @Test
    fun deviceDtoDefaultsToProtocolVersion22() {
        val dto =
            DeviceDto(
                alias = "peer",
                fingerprint = "ABC",
            )

        assertEquals("2.2", NetworkConstants.PROTOCOL_VERSION)
        assertEquals(NetworkConstants.PROTOCOL_VERSION, dto.version)
        assertEquals("desktop", dto.deviceType)
    }

    @Test
    fun uploadAcceptsRetryableStatesAndRejectsReplayOfFinishedFiles() {
        assertTrue(isUploadFileAcceptable(TransferStatus.WaitingApproval))
        assertTrue(isUploadFileAcceptable(TransferStatus.InProgress))
        assertTrue(isUploadFileAcceptable(TransferStatus.Failed))
        assertFalse(isUploadFileAcceptable(TransferStatus.Completed))
        assertFalse(isUploadFileAcceptable(TransferStatus.Canceled))
    }

    @Test
    fun pendingCancelMatchesSenderIpAndOptionalSessionId() {
        val pending = session("pending-1", "192.168.1.10", TransferStatus.WaitingApproval)
        val sameIpOtherSession = session("pending-2", "192.168.1.10", TransferStatus.WaitingApproval)

        assertEquals(pending, findPendingCancelTarget(listOf(pending), null, "192.168.1.10"))
        assertEquals(pending, findPendingCancelTarget(listOf(pending), "pending-1", "192.168.1.10"))
        assertNull(findPendingCancelTarget(listOf(pending), "pending-3", "192.168.1.10"))
        assertNull(findPendingCancelTarget(listOf(pending, sameIpOtherSession), "pending-1", "192.168.1.20"))
    }

    @Test
    fun pendingCancelIgnoresActiveSessions() {
        val active = session("active-1", "192.168.1.10", TransferStatus.InProgress)

        assertNull(findPendingCancelTarget(listOf(active), null, "192.168.1.10"))
        assertNull(findPendingCancelTarget(listOf(active), "active-1", "192.168.1.10"))
    }

    @Test
    fun downloadDispositionIsAttachmentWithPercentEncodedFileName() {
        val header = attachmentContentDisposition("hello world/\"quote\".txt")

        assertTrue(header.startsWith("attachment; filename=\""))
        assertFalse(header.substringAfter("filename=\"").contains(" "))
        assertTrue(header.contains("%20"))
        assertTrue(header.contains("hello%20world-quote.txt"))
    }

    private fun session(
        id: String,
        ip: String,
        status: TransferStatus,
    ): TransferSession =
        TransferSession(
            sessionId = id,
            device = Device(alias = "peer", fingerprint = "fp-$id", ip = ip),
            isIncoming = true,
            files = emptyList(),
            totalBytes = 0L,
            status = status,
        )
}
