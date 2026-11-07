package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.IntegrityStatus
import org.localsend.miuix.network.ChecksumPlan
import org.localsend.miuix.network.LocalSendClient

class LocalSendClientSha256PrefillTest {
    private fun file(
        id: String,
        size: Long = 512L,
        sha256: String? = null,
        stamp: String? = null,
    ) = FileItem(id = id, name = "$id.bin", size = size, expectedSha256 = sha256, sha256SourceStamp = stamp)

    @Test
    fun fillsOnlyMissingHashesAndSkipsExisting() {
        val a = file("a")
        val b = file("b", sha256 = "preset", stamp = "512:1")
        val computed = mutableListOf<String>()
        LocalSendClient.fillMissingSha256(
            listOf(a, b),
            sourceStamp = { item -> if (item.id == "b") "512:1" else null },
        ) { item ->
            computed.add(item.id)
            item.id + "-hash"
        }
        assertEquals("a-hash", a.expectedSha256)
        assertEquals("preset", b.expectedSha256)
        assertEquals(listOf("a"), computed)
    }

    @Test
    fun computeFailureStoresNullWithoutCrash() {
        val a = file("a")
        LocalSendClient.fillMissingSha256(listOf(a)) { null }
        assertNull(a.expectedSha256)
    }

    @Test
    fun skipsFilesBeyondSizeOrCountThreshold() {
        // 单份超过 2 MB：不声明，避免握手前整文件预读
        val big = file("big", size = LocalSendClient.SHA256_PREFILL_MAX_BYTES + 1)
        LocalSendClient.fillMissingSha256(listOf(big)) { "should-not-run" }
        assertNull(big.expectedSha256)
        assertEquals(IntegrityStatus.None, big.integrity)

        // 文件数超过 3：全部不声明（多文件逐份预读同样拖慢握手）
        val batch = (1..4).map { file("f$it", size = 1024L) }
        LocalSendClient.fillMissingSha256(batch) { "should-not-run" }
        assertTrue(batch.all { it.expectedSha256 == null })

        // 门槛内的小文件照常声明
        val small = file("small", size = 1024L)
        LocalSendClient.fillMissingSha256(listOf(small)) { "small-hash" }
        assertEquals("small-hash", small.expectedSha256)
        assertEquals(IntegrityStatus.Declared, small.integrity)
    }

    @Test
    fun checksumPlanIsTheSingleDecisionPoint() {
        // 门槛内：预置声明
        val small = file("small", size = 1024L)
        assertEquals(ChecksumPlan.Declared, LocalSendClient.checksumPlan(listOf(small), small))

        // 单份超 2 MB：流式校验
        val big = file("big", size = LocalSendClient.SHA256_PREFILL_MAX_BYTES + 1)
        assertEquals(ChecksumPlan.Streamed, LocalSendClient.checksumPlan(listOf(big), big))

        // 批次超过 3 份：小文件也改流式
        val batch = (1..4).map { file("f$it", size = 1024L) }
        assertTrue(batch.all { LocalSendClient.checksumPlan(batch, it) == ChecksumPlan.Streamed })

        // 内联文本内容恒定，始终走声明路径
        val text = FileItem(id = "text", name = "text.txt", size = 4L * 1024 * 1024, textContent = "hi")
        assertEquals(ChecksumPlan.Declared, LocalSendClient.checksumPlan(listOf(text), text))
    }

    @Test
    fun inlineTextIsAlwaysDeclaredRegardlessOfSize() {
        // 文本内容在内存中恒定，不受 2 MB 门槛限制
        val text = FileItem(id = "text", name = "text.txt", size = 4L * 1024 * 1024, textContent = "hi")
        LocalSendClient.fillMissingSha256(listOf(text)) { "text-hash" }
        assertEquals("text-hash", text.expectedSha256)
    }

    @Test
    fun recomputesWhenSourceStampChangesAndRevokesWhenFileGrewPastThreshold() {
        // 源文件被改写：指纹变化必须重算，声明值才与上传内容同源
        val rewritten = file("rewritten", sha256 = "old-hash", stamp = "512:10")
        LocalSendClient.fillMissingSha256(listOf(rewritten), sourceStamp = { "512:11" }) { "new-hash" }
        assertEquals("new-hash", rewritten.expectedSha256)
        assertEquals("512:11", rewritten.sha256SourceStamp)

        // 指纹未变：复用已有摘要，不重复读盘
        val untouched = file("untouched", sha256 = "keep", stamp = "512:20")
        LocalSendClient.fillMissingSha256(listOf(untouched), sourceStamp = { "512:20" }) { "should-not-run" }
        assertEquals("keep", untouched.expectedSha256)

        // 添加后被追加写入、已超出声明门槛：撤销过期声明，避免陈旧摘要触发接收端 422
        val grown = file("grown", sha256 = "stale", stamp = "512:30")
        LocalSendClient.fillMissingSha256(
            listOf(grown),
            sourceStamp = { "${LocalSendClient.SHA256_PREFILL_MAX_BYTES + 1024}:31" },
        ) { "should-not-run" }
        assertNull(grown.expectedSha256)
        assertNull(grown.sha256SourceStamp)
    }

    @Test
    fun revokesWarmedHashWhenBatchGrowsPastCountThresholdAndSourceChanged() {
        // 单文件时预热出的摘要，后续批次变大（4 份 > 3）时不再补算：
        // 指纹已变必须撤销，否则过期摘要会随握手发出，接收端校验失败整份文件作废
        val warmed = file("warmed", sha256 = "stale", stamp = "512:40")
        val others = (1..3).map { file("o$it", size = 1024L) }
        LocalSendClient.fillMissingSha256(
            listOf(warmed) + others,
            sourceStamp = { item -> if (item.id == "warmed") "512:41" else null },
        ) { "should-not-run" }
        assertNull(warmed.expectedSha256)
        assertNull(warmed.sha256SourceStamp)
        assertTrue(others.all { it.expectedSha256 == null })
    }

    @Test
    fun revokesWarmedHashWhenBatchGrowsPastCountThresholdEvenIfSourceUnchanged() {
        // 批次超过数量门槛后该条目改用流式校验：即使指纹未变也不能把早先预热出的摘要带进握手，
        // 否则同一文件会同时存在“握手声明校验”和“上传流式校验”两套判定
        val warmed = file("warmed", sha256 = "stale", stamp = "512:50")
        val others = (1..3).map { file("o$it", size = 1024L) }
        LocalSendClient.fillMissingSha256(
            listOf(warmed) + others,
            sourceStamp = { item -> if (item.id == "warmed") "512:50" else null },
        ) { "should-not-run" }
        assertNull(warmed.expectedSha256)
        assertNull(warmed.sha256SourceStamp)
        assertEquals(IntegrityStatus.None, warmed.integrity)
    }

    @Test
    fun stopsBeforeNextFileWhenCanceled() {
        val first = file("first")
        val second = file("second")
        var canceled = false
        val computed = mutableListOf<String>()
        LocalSendClient.fillMissingSha256(
            listOf(first, second),
            shouldCancel = { canceled },
        ) { item ->
            computed.add(item.id)
            canceled = true
            item.id + "-hash"
        }
        assertEquals(listOf("first"), computed)
        assertEquals("first-hash", first.expectedSha256)
        assertNull(second.expectedSha256)
    }
}
