package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.network.LocalSendServer

class SelectiveAcceptanceTest {

    @Test
    fun testSelectiveAcceptanceFiltersTokens() {
        val files = listOf(
            FileItem(id = "file-1", name = "doc.pdf", size = 1000, mimeType = "application/pdf"),
            FileItem(id = "file-2", name = "huge_video.mp4", size = 500000, mimeType = "video/mp4"),
            FileItem(id = "file-3", name = "image.png", size = 2000, mimeType = "image/png")
        )

        // 用户只选择了 file-1 和 file-3，剔除了 file-2
        val selectedIds = setOf("file-1", "file-3")
        val decision = LocalSendServer.resolvePrepareUploadDecision(
            files = files,
            saveTextAsFile = false,
            allowedFileIds = selectedIds
        )

        assertFalse(decision.shouldRespondNoContent)
        assertEquals(2, decision.tokenMap.size)
        assertTrue(decision.tokenMap.containsKey("file-1"))
        assertTrue(decision.tokenMap.containsKey("file-3"))
        assertFalse(decision.tokenMap.containsKey("file-2"))
    }

    @Test
    fun testDefaultAllSelectedWhenAllowedIdsIsNull() {
        val files = listOf(
            FileItem(id = "f1", name = "1.txt", size = 100, mimeType = "text/plain"),
            FileItem(id = "f2", name = "2.png", size = 200, mimeType = "image/png")
        )
        val decision = LocalSendServer.resolvePrepareUploadDecision(
            files = files,
            saveTextAsFile = true,
            allowedFileIds = null
        )
        assertEquals(2, decision.tokenMap.size)
        assertTrue(decision.tokenMap.containsKey("f1"))
        assertTrue(decision.tokenMap.containsKey("f2"))
    }

    @Test
    fun testSelectiveAcceptanceTotalBytesMatchesSelectedOnly() {
        val files = listOf(
            FileItem(id = "f1", name = "1.txt", size = 100, mimeType = "text/plain"),
            FileItem(id = "f2", name = "2.png", size = 200, mimeType = "image/png"),
            FileItem(id = "f3", name = "3.pdf", size = 300, mimeType = "application/pdf")
        )
        val selectedIds = setOf("f1", "f3")
        val total = LocalSendServer.calculateEffectiveTotalBytes(files, selectedIds)
        assertEquals(400L, total)
        val allTotal = LocalSendServer.calculateEffectiveTotalBytes(files, null)
        assertEquals(600L, allTotal)
    }
}
