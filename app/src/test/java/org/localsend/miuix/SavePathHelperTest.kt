package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.util.SavePathHelper

class SavePathHelperTest {

    @Test
    fun resolveRootFileName() {
        val res = SavePathHelper.resolve("document.pdf")
        assertEquals("", res.subDirectory)
        assertEquals("document.pdf", res.fileName)
    }

    @Test
    fun resolveSingleLevelFolder() {
        val res = SavePathHelper.resolve("sub/document.pdf")
        assertEquals("sub", res.subDirectory)
        assertEquals("document.pdf", res.fileName)
    }

    @Test
    fun resolveMultiLevelFolder() {
        val res = SavePathHelper.resolve("folder/nested/sub/document.pdf")
        assertEquals("folder/nested/sub", res.subDirectory)
        assertEquals("document.pdf", res.fileName)
    }

    @Test
    fun resolveRedundantSlashes() {
        val res = SavePathHelper.resolve("//folder///sub//document.pdf")
        assertEquals("folder/sub", res.subDirectory)
        assertEquals("document.pdf", res.fileName)
    }

    @Test
    fun resolvePathTraversalAttack() {
        val res = SavePathHelper.resolve("../../etc/passwd/secret.txt")
        assertEquals("etc/passwd", res.subDirectory)
        assertEquals("secret.txt", res.fileName)
    }

    @Test
    fun buildMediaStoreRelativePath() {
        assertEquals("Download/LocalSend/", SavePathHelper.buildMediaStoreRelativePath("Download/LocalSend", ""))
        assertEquals("Download/LocalSend/sub/docs/", SavePathHelper.buildMediaStoreRelativePath("Download/LocalSend", "sub/docs"))
    }

    @Test
    fun splitSegments() {
        assertEquals(listOf("sub", "nested", "folder"), SavePathHelper.splitSegments("sub/nested/folder"))
        assertEquals(emptyList<String>(), SavePathHelper.splitSegments(""))
    }
}
