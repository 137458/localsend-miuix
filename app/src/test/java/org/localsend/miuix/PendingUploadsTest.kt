package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.localsend.miuix.core.ChecksumHeaders
import org.localsend.miuix.network.CommitState
import org.localsend.miuix.network.PendingUploads

/** 覆盖接收端“先落临时文件、校验通过才转正”的决策表，包括幂等、状态查询、越权与超时清理。 */
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
        deviceIp: String = "192.168.1.10",
        createdAt: Long = 0L,
        commitResult: Boolean = true,
    ) = PendingUploads.Entry(
        sessionId = sessionId,
        fileId = fileId,
        token = token,
        deviceIp = deviceIp,
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

        assertEquals(CommitState.COMMITTED, result.state)
        assertEquals("校验通过时只转正，不删除", 1, probe.commits)
        assertEquals(0, probe.discards)
    }

    @Test
    fun repeatedCommitReturnsSameSuccessWithoutTouchingFileAgain() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        val first = uploads.commit("session-1", "file-1", "token-1")
        val second = uploads.commit("session-1", "file-1", "token-1")

        assertEquals(CommitState.COMMITTED, first.state)
        assertEquals("确认接口必须幂等：重复 commit 返回同样的成功结果", CommitState.COMMITTED, second.state)
        assertNull("幂等重放不再携带条目", second.entry)
        assertEquals("幂等重放不会再次操作文件", 1, probe.commits)
        assertEquals(0, probe.discards)
    }

    @Test
    fun failedCommitDiscardsTemporaryFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe, commitResult = false))

        val result = uploads.commit("session-1", "file-1", "token-1")

        assertEquals(CommitState.DISCARDED, result.state)
        assertEquals("转正动作确实执行过，只是失败", 1, probe.commits)
        assertEquals("转正失败必须回滚，不留半成品", 1, probe.discards)
    }

    @Test
    fun rejectDiscardsTemporaryFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        assertEquals(CommitState.DISCARDED, uploads.reject("session-1", "file-1", "token-1"))

        assertEquals(0, probe.commits)
        assertEquals("摘要不一致必须删除临时文件", 1, probe.discards)
    }

    @Test
    fun rejectAfterCommitCannotDeleteFinalFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))
        assertEquals(CommitState.COMMITTED, uploads.commit("session-1", "file-1", "token-1").state)

        val rejected = uploads.reject("session-1", "file-1", "token-1")

        assertEquals("已转正的文件不能被后续丢弃请求删除", CommitState.COMMITTED, rejected)
        assertEquals("丢弃动作不得执行", 0, probe.discards)
    }

    @Test
    fun statusReportsPendingThenFinalState() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        assertEquals(CommitState.PENDING, uploads.state("session-1", "file-1", "token-1"))
        uploads.commit("session-1", "file-1", "token-1")
        assertEquals(CommitState.COMMITTED, uploads.state("session-1", "file-1", "token-1"))
        assertNull("伪造 token 不得查询结果", uploads.state("session-1", "file-1", "forged"))
        assertNull(uploads.state("session-1", "other-file", "token-1"))
    }

    @Test
    fun statusReportsDiscardedAfterReject() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))
        uploads.reject("session-1", "file-1", "token-1")

        assertEquals(CommitState.DISCARDED, uploads.state("session-1", "file-1", "token-1"))
    }

    @Test
    fun wrongTokenCannotTouchPendingFile() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe))

        // 伪造的 token / 其他会话的 fileId 均不得命中，既不转正也不删除
        assertNull(uploads.commit("session-1", "file-1", "forged").state)
        assertNull(uploads.reject("session-1", "file-1", "forged"))
        assertNull(uploads.commit("session-1", "other-file", "token-1").state)

        assertEquals(0, probe.commits)
        assertEquals(0, probe.discards)
        // 条目未被破坏，合法请求仍可正常处理
        assertEquals(CommitState.COMMITTED, uploads.commit("session-1", "file-1", "token-1").state)
        assertEquals(1, probe.commits)
    }

    @Test
    fun ownerIpIsOnlyVisibleWithMatchingToken() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe, deviceIp = "192.168.1.10"))

        assertEquals("192.168.1.10", uploads.ownerIp("session-1", "file-1", "token-1"))
        assertNull("越权 token 不得查到归属 IP", uploads.ownerIp("session-1", "file-1", "forged"))
        uploads.commit("session-1", "file-1", "token-1")
        assertEquals("会话结束后仍可凭原 token 校验来源", "192.168.1.10", uploads.ownerIp("session-1", "file-1", "token-1"))
    }

    @Test
    fun expiredPendingIsDiscardedWithoutConfirmation() {
        val probe = Probe()
        var now = 1_000L
        val uploads = PendingUploads { now }
        uploads.register(entry(probe, createdAt = now))

        now += ChecksumHeaders.PENDING_TTL_MS - 1
        assertEquals(0, uploads.discardExpired().size)
        assertEquals(CommitState.PENDING, uploads.state("session-1", "file-1", "token-1"))

        now += 1
        val expired = uploads.discardExpired()
        assertEquals(1, expired.size)
        assertEquals("超时未确认必须自动丢弃", 1, probe.discards)
        assertEquals(0, probe.commits)
        assertEquals("超时后状态可查询为已丢弃", CommitState.DISCARDED, uploads.state("session-1", "file-1", "token-1"))
        assertEquals(0, uploads.discardExpired().size)
    }

    @Test
    fun resolutionIsPrunedAfterItsTtl() {
        var now = 1_000L
        val uploads = PendingUploads { now }
        uploads.register(entry(Probe()))
        uploads.commit("session-1", "file-1", "token-1")
        assertEquals(CommitState.COMMITTED, uploads.state("session-1", "file-1", "token-1"))

        now += ChecksumHeaders.RESOLUTION_TTL_MS
        uploads.discardExpired()
        assertNull("过期墓碑必须清理，避免内存无界增长", uploads.state("session-1", "file-1", "token-1"))
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
        assertEquals(CommitState.COMMITTED, uploads.commit("session-2", "file-2", "token-1").state)
    }

    @Test
    fun discardAllClearsPendingFiles() {
        val probe = Probe()
        val uploads = PendingUploads { 100L }
        uploads.register(entry(probe, sessionId = "session-1", fileId = "file-1"))
        uploads.register(entry(probe, sessionId = "session-2", fileId = "file-2"))

        val all = uploads.discardAll()

        assertEquals(2, all.size)
        assertEquals(2, probe.discards)
        assertNull(uploads.state("session-1", "file-1", "token-1"))
        assertNull(uploads.state("session-2", "file-2", "token-1"))
    }
}
