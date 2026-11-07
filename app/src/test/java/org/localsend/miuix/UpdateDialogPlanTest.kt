package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.ui.component.UpdateNotesFold
import org.localsend.miuix.ui.component.UpdatePrimaryAction
import org.localsend.miuix.ui.component.buildUpdateDialogPlan
import org.localsend.miuix.ui.component.updateBrowserUrl
import org.localsend.miuix.ui.component.updateDialogDismissText
import org.localsend.miuix.ui.component.updateDialogNotesFold
import org.localsend.miuix.ui.component.updateDialogSummary
import org.localsend.miuix.ui.component.updateProgressDetail
import org.localsend.miuix.ui.component.updateProgressFraction
import org.localsend.miuix.updates.UpdateAsset
import org.localsend.miuix.updates.UpdateDownloadState

/** 弹窗动作只从下载状态推导，UI 不做任何 if/else 判断。 */
class UpdateDialogPlanTest {
    private val apk =
        UpdateAsset(
            name = "localsend.apk",
            size = 100L,
            downloadUrl = "https://github.com/x/y/releases/download/v1.2.3/localsend.apk",
            digest = null,
        )

    private val release =
        UpdateCheckResult(
            currentVersion = "1.1.0",
            latestVersion = "1.2.3",
            releaseTitle = "v1.2.3",
            changelog = "第一行\n第二行",
            publishedAt = "2026-03-04T10:00:00Z",
            releaseUrl = "https://github.com/x/y/releases/tag/v1.2.3",
            apk = apk,
            hasUpdate = true,
        )

    private val downloading =
        UpdateDownloadState.Downloading(
            downloaded = 87L,
            total = 100L,
            speedBytesPerSec = 2_500_000L,
        )

    @Test
    fun `summary is one line with current latest and date`() {
        assertEquals("当前 1.1.0 → 最新 1.2.3 · 2026-03-04", updateDialogSummary(release))
        assertEquals(
            "当前 1.1.0 → 最新 1.2.3",
            updateDialogSummary(release.copy(publishedAt = "")),
        )
    }

    @Test
    fun `idle offers download with later as secondary and ignore link`() {
        val plan = buildUpdateDialogPlan(release, UpdateDownloadState.Idle, canIgnore = true)
        assertEquals("立即下载", plan.primaryText)
        assertEquals(UpdatePrimaryAction.DOWNLOAD, plan.primaryAction)
        assertTrue(plan.primaryEnabled)
        assertEquals("稍后", plan.dismissText)
        assertFalse(plan.showCancelDownload)
        assertFalse(plan.showBrowserFallback)
        assertTrue(plan.showIgnore)
        assertFalse(plan.showProgress)
    }

    @Test
    fun `downloading disables primary shows percent and offers cancel plus background`() {
        val plan = buildUpdateDialogPlan(release, downloading, canIgnore = true)
        assertEquals("下载中 87%", plan.primaryText)
        assertEquals(UpdatePrimaryAction.DOWNLOAD, plan.primaryAction)
        assertFalse(plan.primaryEnabled)
        assertEquals("后台下载", plan.dismissText)
        assertTrue(plan.showCancelDownload)
        assertTrue(plan.showProgress)
        assertEquals(0.87f, plan.progressFraction!!, 0.0001f)
        assertEquals("87 B / 100 B · 2.4 MB/s", plan.progressDetail)
        assertFalse(plan.showIgnore)
        assertFalse(plan.showBrowserFallback)
    }

    @Test
    fun `failed offers retry and browser fallback without dead progress`() {
        val plan = buildUpdateDialogPlan(release, UpdateDownloadState.Failed("网络连接中断，请检查网络后重试"), canIgnore = false)
        assertEquals("重试", plan.primaryText)
        assertEquals(UpdatePrimaryAction.RETRY, plan.primaryAction)
        assertTrue(plan.primaryEnabled)
        assertTrue(plan.showBrowserFallback)
        assertFalse(plan.showProgress)
        assertFalse(plan.showIgnore)
    }

    @Test
    fun `done offers install`() {
        val plan = buildUpdateDialogPlan(release, UpdateDownloadState.Done(file = java.io.File("a.apk")), canIgnore = true)
        assertEquals("立即安装", plan.primaryText)
        assertEquals(UpdatePrimaryAction.INSTALL, plan.primaryAction)
        assertTrue(plan.primaryEnabled)
        assertFalse(plan.showIgnore)
    }

    @Test
    fun `no apk falls back to the release page`() {
        val plan = buildUpdateDialogPlan(release.copy(apk = null), UpdateDownloadState.Idle, canIgnore = true)
        assertEquals("前往 Releases 页面", plan.primaryText)
        assertEquals(UpdatePrimaryAction.OPEN_RELEASE, plan.primaryAction)
        assertFalse(plan.showBrowserFallback)
    }

    @Test
    fun `browser url prefers the apk direct link`() {
        assertEquals(apk.downloadUrl, updateBrowserUrl(release))
        assertEquals(release.releaseUrl, updateBrowserUrl(release.copy(apk = null)))
    }

    @Test
    fun `dismiss text follows download state`() {
        assertEquals("稍后", updateDialogDismissText(UpdateDownloadState.Idle))
        assertEquals("后台下载", updateDialogDismissText(downloading))
    }

    @Test
    fun `progress helpers tolerate unknown total`() {
        assertEquals(0f, updateProgressFraction(downloading.copy(total = 0L)), 0f)
        assertEquals("87 B", updateProgressDetail(downloading.copy(total = 0L, speedBytesPerSec = 0L)))
    }

    @Test
    fun `notes fold only keeps twelve lines until expanded`() {
        val markdown = (1..20).joinToString("\n") { "第 $it 行" }
        val folded: UpdateNotesFold = updateDialogNotesFold(markdown, expanded = false)
        assertEquals(12, folded.visible.lines().size)
        assertEquals(8, folded.hiddenLines)
        assertTrue(folded.hasMore)

        val expanded = updateDialogNotesFold(markdown, expanded = true)
        assertEquals(20, expanded.visible.lines().size)
        assertEquals(0, expanded.hiddenLines)
        assertFalse(expanded.hasMore)
    }

    @Test
    fun `notes fold keeps short notes untouched`() {
        val folded = updateDialogNotesFold("只有一行", expanded = false)
        assertEquals("只有一行", folded.visible)
        assertEquals(0, folded.hiddenLines)
        assertFalse(folded.hasMore)
        assertNull(null)
    }
}
