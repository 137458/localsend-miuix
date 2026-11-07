package org.localsend.miuix.network

import org.localsend.miuix.core.ChecksumHeaders

/** 本次文件的确认状态；PENDING 表示临时文件仍在等待确认。 */
internal enum class CommitState {
    PENDING,
    COMMITTED,
    DISCARDED,
}

/**
 * 接收端「先落临时位置、校验通过再转正」的待确认表。
 *
 * 条目只在本次会话、本次文件内有效：外部请求只能凭 sessionId + fileId + token 命中，
 * 不接受任何路径参数，因此无法借清理接口删除任意文件。token 不匹配时条目保持原样，
 * 既不转正也不丢弃，避免越权请求造成误删或误转正。
 *
 * 已处理的结果在 [ChecksumHeaders.RESOLUTION_TTL_MS] 内保留为墓碑记录，
 * 使确认接口幂等可重放、状态可查询，用于发送方区分“确实失败”与“状态未知”。
 */
internal class PendingUploads(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    internal class Entry(
        val sessionId: String,
        val fileId: String,
        val token: String,
        val deviceIp: String,
        val createdAt: Long,
        private val commitAction: () -> Boolean,
        private val discardAction: () -> Unit,
    ) {
        fun commit(): Boolean = commitAction()

        fun discard() = discardAction()
    }

    /** 确认结果：state 为空表示未命中/越权；DISCARDED 表示文件已丢弃或转正失败。 */
    internal class CommitResult(
        val state: CommitState?,
        val entry: Entry?,
    )

    private class Resolution(
        val token: String,
        val state: CommitState,
        val deviceIp: String,
        val at: Long,
    )

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private val resolutions = HashMap<String, Resolution>()

    private fun key(
        sessionId: String,
        fileId: String,
    ): String = "$sessionId/$fileId"

    /** 登记待确认条目；同一文件的旧条目（如上传重试）先丢弃，避免临时文件泄漏。 */
    fun register(entry: Entry) {
        val previous =
            synchronized(lock) {
                resolutions.remove(key(entry.sessionId, entry.fileId))
                entries.put(key(entry.sessionId, entry.fileId), entry)
            }
        previous?.let { runCatching { it.discard() } }
    }

    /** 校验通过：转正；转正失败则丢弃临时文件，绝不留下内容不可信的文件。重复确认返回同一结果。 */
    fun commit(
        sessionId: String,
        fileId: String,
        token: String,
    ): CommitResult {
        val (entry, previousResolution) =
            synchronized(lock) {
                val existing = entries[key(sessionId, fileId)]
                if (existing != null) {
                    if (existing.token != token) return@synchronized (null to null)
                    entries.remove(key(sessionId, fileId))
                    existing to null
                } else {
                    null to resolutions[key(sessionId, fileId)]
                }
            }

        if (entry == null) {
            val resolution = previousResolution ?: return CommitResult(null, null)
            if (resolution.token != token) return CommitResult(null, null)
            return CommitResult(resolution.state, null)
        }

        val committed = runCatching { entry.commit() }.getOrDefault(false)
        if (!committed) runCatching { entry.discard() }
        val state = if (committed) CommitState.COMMITTED else CommitState.DISCARDED
        synchronized(lock) {
            resolutions[key(sessionId, fileId)] = Resolution(token, state, entry.deviceIp, clock())
        }
        return CommitResult(state, entry)
    }

    /** 校验失败：丢弃临时文件。已转正的条目不会被后续丢弃请求删除。token 不匹配时返回 null。 */
    fun reject(
        sessionId: String,
        fileId: String,
        token: String,
    ): CommitState? {
        val (entry, previousResolution) =
            synchronized(lock) {
                val existing = entries[key(sessionId, fileId)]
                if (existing != null) {
                    if (existing.token != token) return@synchronized (null to null)
                    entries.remove(key(sessionId, fileId))
                    existing to null
                } else {
                    null to resolutions[key(sessionId, fileId)]
                }
            }

        if (entry == null) {
            val resolution = previousResolution ?: return null
            return if (resolution.token == token) resolution.state else null
        }

        runCatching { entry.discard() }
        synchronized(lock) {
            resolutions[key(sessionId, fileId)] = Resolution(token, CommitState.DISCARDED, entry.deviceIp, clock())
        }
        return CommitState.DISCARDED
    }

    /** 查询本次文件的确认状态；越权或未知返回 null。 */
    fun state(
        sessionId: String,
        fileId: String,
        token: String,
    ): CommitState? {
        val entry = synchronized(lock) { entries[key(sessionId, fileId)] }
        if (entry != null) {
            return if (entry.token == token) CommitState.PENDING else null
        }
        val resolution = synchronized(lock) { resolutions[key(sessionId, fileId)] } ?: return null
        return if (resolution.token == token) resolution.state else null
    }

    /** 条目所属设备的 IP：会话已结束时仍能凭原 token 校验来源，保证确认/查询接口幂等可用。 */
    fun ownerIp(
        sessionId: String,
        fileId: String,
        token: String,
    ): String? {
        synchronized(lock) {
            entries[key(sessionId, fileId)]?.let { return if (it.token == token) it.deviceIp else null }
            val resolution = resolutions[key(sessionId, fileId)] ?: return null
            return if (resolution.token == token) resolution.deviceIp else null
        }
    }

    /** 上传重试前的内部清理（无外部请求入口），丢弃同文件的旧临时文件与旧结果。 */
    fun discardForFile(
        sessionId: String,
        fileId: String,
    ): Boolean {
        val entry =
            synchronized(lock) {
                resolutions.remove(key(sessionId, fileId))
                entries.remove(key(sessionId, fileId))
            } ?: return false
        runCatching { entry.discard() }
        return true
    }

    /** 会话结束/取消时清理该会话全部待确认文件。 */
    fun discardSession(sessionId: String): Int {
        val removed =
            synchronized(lock) {
                val matching = entries.values.filter { it.sessionId == sessionId }
                matching.forEach {
                    entries.remove(key(it.sessionId, it.fileId))
                    resolutions[key(it.sessionId, it.fileId)] = Resolution(it.token, CommitState.DISCARDED, it.deviceIp, clock())
                }
                matching
            }
        removed.forEach { runCatching { it.discard() } }
        return removed.size
    }

    /** 超时未确认的条目自动丢弃，防止发送方崩溃后残留错误文件；同时清理过期的结果墓碑。 */
    fun discardExpired(
        ttlMs: Long = ChecksumHeaders.PENDING_TTL_MS,
        resolutionTtlMs: Long = ChecksumHeaders.RESOLUTION_TTL_MS,
    ): List<Entry> {
        val now = clock()
        val cutoff = now - ttlMs
        val resolutionCutoff = now - resolutionTtlMs
        val expired =
            synchronized(lock) {
                resolutions.entries.removeAll { it.value.at <= resolutionCutoff }
                val matching = entries.values.filter { it.createdAt <= cutoff }
                matching.forEach {
                    entries.remove(key(it.sessionId, it.fileId))
                    resolutions[key(it.sessionId, it.fileId)] = Resolution(it.token, CommitState.DISCARDED, it.deviceIp, now)
                }
                matching
            }
        expired.forEach { runCatching { it.discard() } }
        return expired
    }

    /** 服务停止时丢弃全部待确认文件。 */
    fun discardAll(): List<Entry> {
        val all =
            synchronized(lock) {
                val allEntries = entries.values.toList()
                entries.clear()
                resolutions.clear()
                allEntries
            }
        all.forEach { runCatching { it.discard() } }
        return all
    }
}
