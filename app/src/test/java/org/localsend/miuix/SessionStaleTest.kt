package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus

class SessionStaleTest {

    private val dummyDevice = Device(
        alias = "Peer",
        fingerprint = "fp",
        port = 53317,
        protocol = "http",
        download = false,
        ip = "192.168.1.10",
        deviceType = DeviceType.mobile
    )

    private fun isSessionStale(session: TransferSession, now: Long): Boolean {
        return (now - session.lastActiveTime > 30_000L) ||
            (session.endTime?.let { now - it > 5_000L } ?: false)
    }

    @Test
    fun testActiveSessionNotStale() {
        val now = 100_000L
        val session = TransferSession(
            sessionId = "s1",
            device = dummyDevice,
            isIncoming = true,
            files = emptyList(),
            totalBytes = 1024,
            transferredBytes = 500,
            lastActiveTime = now - 5_000L // 5秒前活跃
        )
        assertFalse(isSessionStale(session, now))
    }

    @Test
    fun testStalledSessionWithTransferredBytesIsDetectedStaleAfter30Seconds() {
        val now = 100_000L
        val session = TransferSession(
            sessionId = "s2",
            device = dummyDevice,
            isIncoming = true,
            files = emptyList(),
            totalBytes = 1024,
            transferredBytes = 500, // 哪怕已经传输了字节，只要网络断开 35 秒无活跃，必须被判定为 stale！
            lastActiveTime = now - 35_000L
        )
        assertTrue(isSessionStale(session, now))
    }

    @Test
    fun testCompletedSessionCleanedAfter5Seconds() {
        val now = 100_000L
        val session = TransferSession(
            sessionId = "s3",
            device = dummyDevice,
            isIncoming = true,
            files = emptyList(),
            totalBytes = 1024,
            transferredBytes = 1024,
            status = TransferStatus.Completed,
            endTime = now - 6_000L
        )
        assertTrue(isSessionStale(session, now))
    }
}
