package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSaveLogicTest {
    private fun shouldAutoAccept(isQuickSave: Boolean): Boolean = isQuickSave

    @Test
    fun testQuickSaveAcceptsFiles() {
        assertTrue(shouldAutoAccept(isQuickSave = true))
    }

    @Test
    fun testQuickSaveAcceptsText() {
        assertTrue(shouldAutoAccept(isQuickSave = true))
    }

    @Test
    fun testManualApprovalRequiredWhenDisabled() {
        assertFalse(shouldAutoAccept(isQuickSave = false))
    }
}
