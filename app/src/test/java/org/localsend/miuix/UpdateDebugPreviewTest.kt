package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.ui.component.UPDATE_NOTES_PREVIEW_LINES
import org.localsend.miuix.ui.component.UpdatePrimaryAction
import org.localsend.miuix.ui.component.buildUpdateDialogPlan
import org.localsend.miuix.ui.component.updateDialogNotesFold
import org.localsend.miuix.ui.component.updateDialogSummary
import org.localsend.miuix.updates.UpdateDebugPreview
import org.localsend.miuix.updates.UpdateDownloadState

/** 测试弹窗入口只喂稳定快照：必须是「有新版本」、可测折叠，且绝不携带 APK 触发真实下载。 */
class UpdateDebugPreviewTest {
    private val currentVersion = "1.23.0"

    @Test
    fun `sample is always a strictly newer release than the current version`() {
        val sample = UpdateDebugPreview.sample(currentVersion)
        assertTrue(sample.hasUpdate)
        assertEquals(UpdateDebugPreview.LATEST_VERSION, sample.latestVersion)
        assertEquals(currentVersion, sample.currentVersion)
    }

    @Test
    fun `sample never carries an apk so the preview cannot start a real download`() {
        assertNull(UpdateDebugPreview.sample(currentVersion).apk)
    }

    @Test
    fun `sample changelog folds so the dialog can exercise expand and collapse`() {
        val sample = UpdateDebugPreview.sample(currentVersion)
        assertTrue(sample.changelog.lines().size > UPDATE_NOTES_PREVIEW_LINES)

        val folded = updateDialogNotesFold(sample.changelog, expanded = false)
        assertTrue(folded.hasMore)
        assertTrue(folded.hiddenLines > 0)
        assertEquals(sample.changelog, updateDialogNotesFold(sample.changelog, expanded = true).visible)
    }

    @Test
    fun `sample renders the current to latest summary without a date`() {
        val sample = UpdateDebugPreview.sample(currentVersion)
        assertEquals("当前 $currentVersion → 最新 ${UpdateDebugPreview.LATEST_VERSION}", updateDialogSummary(sample))
    }

    @Test
    fun `idle plan sends the preview to the releases page and keeps the ignore link`() {
        val sample = UpdateDebugPreview.sample(currentVersion)
        val plan = buildUpdateDialogPlan(sample, UpdateDownloadState.Idle, canIgnore = true)
        assertEquals("前往 Releases 页面", plan.primaryText)
        assertEquals(UpdatePrimaryAction.OPEN_RELEASE, plan.primaryAction)
        assertTrue(plan.primaryEnabled)
        assertTrue(plan.showIgnore)
        assertTrue(plan.hasNotes)
    }
}
