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

    @Test
    fun resolveNormalizesWindowsSeparators() {
        val windows = SavePathHelper.resolve("C:\\Users\\me\\photo.jpg")
        assertEquals("C:/Users/me", windows.subDirectory)
        assertEquals("photo.jpg", windows.fileName)
    }

    @Test
    fun resolveStripsCurrentDirMarkersAndWhitespace() {
        val messy = SavePathHelper.resolve("/a//b/./c/")
        assertEquals("a/b", messy.subDirectory)
        assertEquals("c", messy.fileName)

        val trimmed = SavePathHelper.resolve("   only.txt   ")
        assertEquals("", trimmed.subDirectory)
        assertEquals("only.txt", trimmed.fileName)
    }

    @Test
    fun resolveNeutralizesInteriorTraversalSegments() {
        val mixed = SavePathHelper.resolve("a/../b/../../c.txt")
        assertEquals("a/b", mixed.subDirectory)
        assertEquals("c.txt", mixed.fileName)
    }

    @Test
    fun resolveFallsBackToUnnamedForEmptyInput() {
        assertEquals(SavePathHelper.PathComponents("", "unnamed"), SavePathHelper.resolve(""))
        assertEquals(SavePathHelper.PathComponents("", "unnamed"), SavePathHelper.resolve("   "))
        assertEquals(SavePathHelper.PathComponents("", "unnamed"), SavePathHelper.resolve(".."))
        assertEquals(SavePathHelper.PathComponents("", "unnamed"), SavePathHelper.resolve("///"))
    }

    @Test
    fun buildMediaStoreRelativePathCleansBaseAndSub() {
        assertEquals(
            "Download/LocalSend/",
            SavePathHelper.buildMediaStoreRelativePath("Download/LocalSend///", "   "),
        )
        assertEquals(
            "Download/LocalSend/sub/dir/",
            SavePathHelper.buildMediaStoreRelativePath("Download/LocalSend", " sub/dir/ "),
        )
    }

    @Test
    fun splitSegmentsDropsEmptyAndDotSegments() {
        assertEquals(listOf("a", "b", "c"), SavePathHelper.splitSegments("a/./b//../c"))
        assertEquals(emptyList<String>(), SavePathHelper.splitSegments("./../"))
    }
}
