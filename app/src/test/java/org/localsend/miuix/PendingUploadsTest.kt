package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.core.ChecksumHeaders
import org.localsend.miuix.network.PendingUploads

/** 覆盖接收端“先落临时文件、校验通过才转正”的决策表，包括越权与超时清理。 */
class PendingUploadsTest {
    private class Probe {
        var commits = 0
        var discards = 0
    }

    private fun entry(
        probe: Probe,
        sessionId: String = "session-1",
        fileId: String = "file-1",
        token: String = "token-1",
        createdAt: Long = 0L,
        commitResult: Boolean = true,
    ) = PendingUploads.Entry(
        sessionId = sessionId,
        fileId = fileId,
        token = token,
        createdAt = createdAt,
        commitAction = {
            probe.commits++
            commitResult
        },
        discardAction = { probe.discards++ },
    )

    @Test
    fun commitKeepsFileAndConsumesEntry() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        val result = uploads.commit("session-1", "file-1", "token-1")

        assertTrue(result.committed)
        assertEquals("校验通过时只转正，不删除", 1, probe.commits)
        assertEquals(0, probe.discards)
        // 条目一次性消费，重复确认不会再次操作文件
        assertNull(uploads.commit("session-1", "file-1", "token-1").entry)
        assertEquals(1, probe.commits)
    }

    @Test
    fun failedCommitDiscardsTemporaryFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe, commitResult = false))

        val result = uploads.commit("session-1", "file-1", "token-1")

        assertFalse(result.committed)
        assertEquals("转正动作确实执行过，只是失败", 1, probe.commits)
        assertEquals("转正失败必须回滚，不留半成品", 1, probe.discards)
    }

    @Test
    fun rejectDiscardsTemporaryFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        assertTrue(uploads.reject("session-1", "file-1", "token-1") != null)

        assertEquals(0, probe.commits)
        assertEquals("摘要不一致必须删除临时文件", 1, probe.discards)
    }

    @Test
    fun wrongTokenCannotTouchPendingFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        // 伪造的 token / 其他会话的 fileId 均不得命中，既不转正也不删除
        assertNull(uploads.commit("session-1", "file-1", "forged").entry)
        assertNull(uploads.reject("session-1", "file-1", "forged"))
        assertNull(uploads.commit("session-1", "other-file", "token-1").entry)

        assertEquals(0, probe.commits)
        assertEquals(0, probe.discards)
        // 条目未被破坏，合法请求仍可正常处理
        assertTrue(uploads.commit("session-1", "file-1", "token-1").committed)
        assertEquals(1, probe.commits)
    }

    @Test
    fun expiredPendingIsDiscardedWithoutConfirmation() {
        val probe = Probe()
        var now = 1_000L
        val uploads = PendingUploads { now }
        uploads.register(entry(probe, createdAt = now))

        now += ChecksumHeaders.PENDING_TTL_MS - 1
        assertEquals(0, uploads.discardExpired().size)

        now += 1
        val expired = uploads.discardExpired()
        assertEquals(1, expired.size)
        assertEquals("超时未确认必须自动丢弃", 1, probe.discards)
        assertEquals(0, probe.commits)
        assertEquals(0, uploads.discardExpired().size)
    }

    @Test
    fun reRegisterDiscardsPreviousTemporaryFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))
        // 上传重试会覆盖同一条目，旧临时文件必须先删除
        uploads.register(entry(probe))

        assertEquals(1, probe.discards)
        assertEquals(0, probe.commits)
    }

    @Test
    fun discardSessionOnlyAffectsThatSession() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe, sessionId = "session-1", fileId = "file-1"))
        uploads.register(entry(probe, sessionId = "session-2", fileId = "file-2"))

        assertEquals(1, uploads.discardSession("session-1"))
        assertEquals(1, probe.discards)

        // 其他会话的待确认文件不受影响
        assertTrue(uploads.commit("session-2", "file-2", "token-1").committed)
    }
}
