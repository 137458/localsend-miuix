package org.localsend.miuix.ui.component

import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.updates.UpdateDownloadState
import kotlin.math.round

internal const val UPDATE_NOTES_PREVIEW_LINES = 12
internal const val UPDATE_NOTES_MAX_HEIGHT_DP = 240
internal const val UPDATE_NOTES_FADE_DP = 40

internal enum class UpdatePrimaryAction {
    DOWNLOAD,
    INSTALL,
    RETRY,
    OPEN_RELEASE,
}

internal data class UpdateNotesFold(
    val visible: String,
    val hiddenLines: Int,
    val hasMore: Boolean,
)

internal data class UpdateDialogPlan(
    val summary: String,
    val primaryText: String,
    val primaryAction: UpdatePrimaryAction,
    val primaryEnabled: Boolean,
    val dismissText: String,
    val showProgress: Boolean,
    val progressFraction: Float?,
    val progressDetail: String?,
    val showCancelDownload: Boolean,
    val showBrowserFallback: Boolean,
    val showIgnore: Boolean,
    val hasNotes: Boolean,
)

internal fun updateDialogSummary(release: UpdateCheckResult): String {
    val date = release.publishedAt.trim().take(10)
    val base = "当前 ${release.currentVersion} → 最新 ${release.latestVersion}"
    return if (date.isEmpty()) base else "$base · $date"
}

internal fun updateDialogDismissText(state: UpdateDownloadState): String = if (state is UpdateDownloadState.Downloading) "后台下载" else "稍后"

internal fun updateProgressFraction(state: UpdateDownloadState.Downloading): Float {
    if (state.total <= 0L) return 0f
    return (state.downloaded.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
}

internal fun updateProgressDetail(state: UpdateDownloadState.Downloading): String {
    val size =
        if (state.total > 0L) {
            "${FileItem.formatFileSize(state.downloaded)} / ${FileItem.formatFileSize(state.total)}"
        } else {
            FileItem.formatFileSize(state.downloaded)
        }
    return if (state.speedBytesPerSec > 0L) "$size · ${FileItem.formatSpeed(state.speedBytesPerSec)}" else size
}

internal fun updateBrowserUrl(release: UpdateCheckResult): String = release.apk?.downloadUrl ?: release.releaseUrl

internal fun updateDialogNotesFold(
    markdown: String,
    expanded: Boolean,
): UpdateNotesFold {
    val lines = markdown.lines()
    if (expanded || lines.size <= UPDATE_NOTES_PREVIEW_LINES) {
        return UpdateNotesFold(visible = markdown, hiddenLines = 0, hasMore = false)
    }
    val hidden = lines.size - UPDATE_NOTES_PREVIEW_LINES
    return UpdateNotesFold(
        visible = lines.take(UPDATE_NOTES_PREVIEW_LINES).joinToString("\n"),
        hiddenLines = hidden,
        hasMore = true,
    )
}

internal fun buildUpdateDialogPlan(
    release: UpdateCheckResult,
    state: UpdateDownloadState,
    canIgnore: Boolean,
): UpdateDialogPlan {
    val summary = updateDialogSummary(release)
    val hasNotes = release.changelog.isNotBlank()

    when (state) {
        is UpdateDownloadState.Downloading -> {
            val percent = round(updateProgressFraction(state) * 100).toInt()
            return UpdateDialogPlan(
                summary = summary,
                primaryText = "下载中 $percent%",
                primaryAction = UpdatePrimaryAction.DOWNLOAD,
                primaryEnabled = false,
                dismissText = "后台下载",
                showProgress = true,
                progressFraction = updateProgressFraction(state),
                progressDetail = updateProgressDetail(state),
                showCancelDownload = true,
                showBrowserFallback = false,
                showIgnore = false,
                hasNotes = hasNotes,
            )
        }

        is UpdateDownloadState.Done ->
            return UpdateDialogPlan(
                summary = summary,
                primaryText = "立即安装",
                primaryAction = UpdatePrimaryAction.INSTALL,
                primaryEnabled = true,
                dismissText = "稍后",
                showProgress = false,
                progressFraction = null,
                progressDetail = null,
                showCancelDownload = false,
                showBrowserFallback = false,
                showIgnore = false,
                hasNotes = hasNotes,
            )

        is UpdateDownloadState.Failed ->
            return UpdateDialogPlan(
                summary = summary,
                primaryText = "重试",
                primaryAction = UpdatePrimaryAction.RETRY,
                primaryEnabled = true,
                dismissText = "稍后",
                showProgress = false,
                progressFraction = null,
                progressDetail = null,
                showCancelDownload = false,
                showBrowserFallback = true,
                showIgnore = canIgnore,
                hasNotes = hasNotes,
            )

        UpdateDownloadState.Idle -> {
            val hasApk = release.apk != null
            return UpdateDialogPlan(
                summary = summary,
                primaryText = if (hasApk) "立即下载" else "前往 Releases 页面",
                primaryAction = if (hasApk) UpdatePrimaryAction.DOWNLOAD else UpdatePrimaryAction.OPEN_RELEASE,
                primaryEnabled = true,
                dismissText = "稍后",
                showProgress = false,
                progressFraction = null,
                progressDetail = null,
                showCancelDownload = false,
                showBrowserFallback = false,
                showIgnore = canIgnore,
                hasNotes = hasNotes,
            )
        }
    }
}
