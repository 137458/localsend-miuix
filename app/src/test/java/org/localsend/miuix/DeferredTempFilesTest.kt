package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.DeferredTempNames
import org.localsend.miuix.network.SweepNode
import org.localsend.miuix.network.TempFileSweeper
import org.localsend.miuix.network.commitDeferredWithFallback

/** 覆盖临时文件命名、残留清理边界与 SAF 复制回退。 */
class DeferredTempFilesTest {
    private class FakeNode(
        override val name: String?,
        override val isDirectory: Boolean,
    ) : SweepNode {
        val kids = mutableListOf<FakeNode>()
        var deleted = false

        override fun children(): List<SweepNode> = kids

        override fun delete(): Boolean {
            deleted = true
            return true
        }

        fun file(name: String): FakeNode = FakeNode(name, isDirectory = false).also { kids.add(it) }

        fun dir(name: String): FakeNode = FakeNode(name, isDirectory = true).also { kids.add(it) }
    }

    @Test
    fun generatedTempNameIsRecognized() {
        val name = DeferredTempNames.of("movie.mp4", "3f7a9b21-1111-2222")

        assertTrue(DeferredTempNames.isTempFile(name))
        assertTrue("临时名必须保留最终名的可读前缀", name.startsWith("movie.mp4"))
    }

    @Test
    fun foreignNamesAreNotTreatedAsOwnTempFiles() {
        // 用户文件、其他应用的 .part、标记后不是 8 位字母数字的，全部不能当作残留
        assertFalse(DeferredTempNames.isTempFile("movie.mp4"))
        assertFalse(DeferredTempNames.isTempFile("movie.mp4.part-3f7a9b21"))
        assertFalse(DeferredTempNames.isTempFile("movie.mp4.localsend-miuix-tmp-abc"))
        assertFalse(DeferredTempNames.isTempFile("movie.mp4.localsend-miuix-tmp-12345678.bak"))
        assertFalse(DeferredTempNames.isTempFile(".localsend-miuix-tmp-12345678"))
        assertFalse(DeferredTempNames.isTempFile(null))
    }

    @Test
    fun sweepDeletesOnlyOwnTempFilesWithinScope() {
        val root = FakeNode("/", isDirectory = true)
        root.file("holiday.jpg")
        root.file("holiday.jpg.part-3f7a9b21")
        val own = root.file(DeferredTempNames.of("holiday.jpg", "3f7a9b21-aaaa"))
        root.dir("sub").file(DeferredTempNames.of("clip.mkv", "12345678-bbbb"))

        assertEquals(2, TempFileSweeper().sweep(root))

        assertTrue(own.deleted)
        assertFalse("用户文件绝不能被清理逻辑删除", root.kids.first { it.name == "holiday.jpg" }.deleted)
        assertFalse(root.kids.first { it.name == "holiday.jpg.part-3f7a9b21" }.deleted)
        assertFalse("目录本身永不删除", root.kids.first { it.name == "sub" }.deleted)
    }

    @Test
    fun sweepStopsAtDepthLimit() {
        val root = FakeNode("/", isDirectory = true)
        var cursor = root
        repeat(4) { level ->
            cursor = cursor.dir("level$level")
        }
        // 超过深度上限的目录不再下钻，其中的文件本轮不扫描
        cursor.file(DeferredTempNames.of("deep.bin", "abcdef12-cccc"))

        assertEquals(0, TempFileSweeper(maxDepth = 4).sweep(root))
    }

    @Test
    fun sweepStopsAtEntryLimit() {
        val root = FakeNode("/", isDirectory = true)
        root.file(DeferredTempNames.of("a.bin", "11111111-x"))
        val second = root.file(DeferredTempNames.of("b.bin", "22222222-y"))

        assertEquals(1, TempFileSweeper(maxEntries = 1).sweep(root))

        assertFalse("超出条目上限的文件本轮不处理", second.deleted)
    }

    @Test
    fun fallbackCopySucceedsWhenRenameUnsupported() {
        var copied = 0
        var tempDeleted = 0

        val committed =
            commitDeferredWithFallback(
                rename = { false },
                copyToFinal = {
                    copied++
                    true
                },
                deleteTemp = { tempDeleted++ },
            )

        assertTrue(committed)
        assertEquals(1, copied)
        assertEquals("复制成功后删除临时文件", 1, tempDeleted)
    }

    @Test
    fun renameSuccessSkipsCopy() {
        var copied = 0

        val committed =
            commitDeferredWithFallback(
                rename = { true },
                copyToFinal = {
                    copied++
                    true
                },
                deleteTemp = {},
            )

        assertTrue(committed)
        assertEquals("重命名成功时不应再复制", 0, copied)
    }

    @Test
    fun fallbackCopyFailureKeepsTempFileForRollback() {
        var tempDeleted = 0

        val committed =
            commitDeferredWithFallback(
                rename = { false },
                copyToFinal = { false },
                deleteTemp = { tempDeleted++ },
            )

        assertFalse("复制失败必须整体失败，不能留下半份最终文件", committed)
        assertEquals("临时文件保留给调用方回滚，不在此处误删", 0, tempDeleted)
    }

    @Test
    fun throwingCopyIsTreatedAsFailure() {
        val committed =
            commitDeferredWithFallback(
                rename = { false },
                copyToFinal = { throw java.io.IOException("provider failure") },
                deleteTemp = {},
            )

        assertFalse(committed)
    }
}
