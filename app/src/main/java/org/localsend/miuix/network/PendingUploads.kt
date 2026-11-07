package org.localsend.miuix.network

import org.localsend.miuix.core.ChecksumHeaders
import java.util.concurrent.ConcurrentHashMap

/**
 * 接收端「先落临时位置、校验通过再转正」的待确认表。
 *
 * 条目只在本次会话、本次文件内有效：外部请求只能凭 sessionId + fileId + token 命中，
 * 不接受任何路径参数，因此无法借清理接口删除任意文件。token 不匹配时条目保持原样，
 * 既不转正也不丢弃，避免越权请求造成误删或误转正。
 */
internal class PendingUploads(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    internal class Entry(
        val sessionId: String,
        val fileId: String,
        val token: String,
        val createdAt: Long,
        private val commitAction: () -> Boolean,
        private val discardAction: () -> Unit,
    ) {
        fun commit(): Boolean = commitAction()

        fun discard() = discardAction()
    }

    /** 确认结果：entry 为空表示未命中/越权；committed=false 表示转正失败且临时文件已被丢弃。 */
    internal class CommitResult(
        val entry: Entry?,
        val committed: Boolean,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun key(
        sessionId: String,
        fileId: String,
    ): String = "$sessionId/$fileId"

    /** 登记待确认条目；同一文件的旧条目（如上传重试）先丢弃，避免临时文件泄漏。 */
    fun register(entry: Entry) {
        val previous =
            synchronized(entries) {
                entries.put(key(entry.sessionId, entry.fileId), entry)
            }
        previous?.let { runCatching { it.discard() } }
    }

    /** 校验通过：转正；转正失败则丢弃临时文件，绝不留下内容不可信的文件。 */
    fun commit(
        sessionId: String,
        fileId: String,
        token: String,
    ): CommitResult {
        val entry = take(sessionId, fileId, token) ?: return CommitResult(null, false)
        val committed = runCatching { entry.commit() }.getOrDefault(false)
        if (!committed) runCatching { entry.discard() }
        return CommitResult(entry, committed)
    }

    /** 校验失败：丢弃临时文件。token 不匹配时返回 null，不做任何清理。 */
    fun reject(
        sessionId: String,
        fileId: String,
        token: String,
    ): Entry? {
        val entry = take(sessionId, fileId, token) ?: return null
        runCatching { entry.discard() }
        return entry
    }

    /** 上传重试前的内部清理（无外部请求入口），丢弃同文件的旧临时文件。 */
    fun discardForFile(
        sessionId: String,
        fileId: String,
    ): Boolean {
        val entry = synchronized(entries) { entries.remove(key(sessionId, fileId)) } ?: return false
        runCatching { entry.discard() }
        return true
    }

    /** 会话结束/取消时清理该会话全部待确认文件。 */
    fun discardSession(sessionId: String): Int {
        val removed =
            synchronized(entries) {
                entries.values
                    .filter { it.sessionId == sessionId }
                    .onEach { entries.remove(key(it.sessionId, it.fileId), it) }
            }
        removed.forEach { runCatching { it.discard() } }
        return removed.size
    }

    /** 超时未确认的条目自动丢弃，防止发送方崩溃后残留错误文件。 */
    fun discardExpired(ttlMs: Long = ChecksumHeaders.PENDING_TTL_MS): List<Entry> {
        val cutoff = clock() - ttlMs
        val expired =
            synchronized(entries) {
                entries.values
                    .filter { it.createdAt <= cutoff }
                    .onEach { entries.remove(key(it.sessionId, it.fileId), it) }
            }
        expired.forEach { runCatching { it.discard() } }
        return expired
    }

    /** 服务停止时丢弃全部待确认文件。 */
    fun discardAll(): List<Entry> {
        val all =
            synchronized(entries) {
                entries.values.toList().onEach { entries.remove(key(it.sessionId, it.fileId), it) }
            }
        all.forEach { runCatching { it.discard() } }
        return all
    }

    private fun take(
        sessionId: String,
        fileId: String,
        token: String,
    ): Entry? =
        synchronized(entries) {
            val entry = entries[key(sessionId, fileId)] ?: return null
            // 越权请求不取走条目：避免伪造请求导致文件被误删或误转正
            if (entry.token != token) return null
            entries.remove(key(sessionId, fileId), entry)
            entry
        }
}
