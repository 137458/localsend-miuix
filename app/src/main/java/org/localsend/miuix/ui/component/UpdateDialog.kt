package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localsend.miuix.R
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.updates.UpdateDownloadState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

/**
 * 整合版更新弹窗：一行版本跃迁摘要 + 可折叠说明 + 纵向主次动作。
 *
 * - 关闭弹窗不取消下载，状态由进程级单例持有，更新页可继续看到进度。
 * - 说明区是弹窗内唯一的滚动容器：限高跟随 [rememberDialogContentMaxHeight]，
 *   上下留 [UPDATE_NOTES_FADE_DP] 羽化，避免把 160/240dp 写死。
 * - 动作区统一由 [DialogActionColumn] 收口：主操作独占一行，其余为文字按钮。
 */
@Composable
fun UpdateDialog(
    show: Boolean,
    releaseInfo: UpdateCheckResult,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onIgnore: ((version: String) -> Unit)? = null,
) {
    var expanded by remember(releaseInfo.latestVersion) { mutableStateOf(false) }
    val plan =
        remember(releaseInfo, downloadState, onIgnore) {
            buildUpdateDialogPlan(releaseInfo, downloadState, canIgnore = onIgnore != null)
        }
    val notes =
        remember(releaseInfo.changelog, expanded) {
            updateDialogNotesFold(releaseInfo.changelog, expanded)
        }
    val scrollState = rememberScrollState()
    val notesMaxHeight = rememberDialogContentMaxHeight(fraction = 0.4f, min = 160.dp, max = 360.dp)

    fun runPrimary() {
        when (plan.primaryAction) {
            UpdatePrimaryAction.DOWNLOAD, UpdatePrimaryAction.RETRY -> onDownload()
            UpdatePrimaryAction.INSTALL -> (downloadState as? UpdateDownloadState.Done)?.let { onInstall(it.file) }
            UpdatePrimaryAction.OPEN_RELEASE -> onOpenBrowser(releaseInfo.releaseUrl)
        }
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.update_dialog_title),
        summary = plan.summary,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = notesMaxHeight)
                        .clip(RoundedCornerShape(DialogTokens.CardCorner))
                        .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                        .verticalEdgeFade(
                            top = scrollState.canScrollBackward,
                            bottom = scrollState.canScrollForward,
                            fade = UPDATE_NOTES_FADE_DP.dp,
                        ).verticalScroll(scrollState)
                        .padding(DialogTokens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
            ) {
                if (plan.showProgress) {
                    plan.progressDetail?.let {
                        Text(
                            text = it,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    LinearProgressIndicator(progress = plan.progressFraction ?: 0f, modifier = Modifier.fillMaxWidth())
                }

                if (downloadState is UpdateDownloadState.Failed) {
                    Text(
                        text = downloadState.message,
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                if (plan.hasNotes) {
                    MarkdownText(
                        markdown = notes.visible,
                        modifier = Modifier.fillMaxWidth(),
                        baseFontSize = 13,
                    )
                    if (notes.hasMore) {
                        Text(
                            text =
                                if (expanded) {
                                    stringResource(R.string.update_dialog_notes_collapse)
                                } else {
                                    stringResource(R.string.update_dialog_notes_expand, notes.hiddenLines)
                                },
                            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                                    .clickable(role = Role.Button) { expanded = !expanded }
                                    .padding(vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.update_dialog_no_changelog),
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            DialogActionColumn {
                Button(
                    onClick = { runPrimary() },
                    enabled = plan.primaryEnabled,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(plan.primaryText)
                }
                if (plan.showBrowserFallback) {
                    DialogTextAction(
                        text = stringResource(R.string.update_dialog_btn_browser),
                        onClick = { onOpenBrowser(updateBrowserUrl(releaseInfo)) },
                    )
                }
                if (plan.showCancelDownload) {
                    DialogTextAction(
                        text = stringResource(R.string.update_dialog_btn_cancel_download),
                        onClick = onCancelDownload,
                    )
                }
                if (plan.showIgnore && onIgnore != null) {
                    DialogTextAction(
                        text = stringResource(R.string.update_dialog_ignore_version),
                        onClick = { onIgnore(releaseInfo.latestVersion) },
                    )
                }
                DialogTextAction(text = plan.dismissText, onClick = onDismiss)
            }
        }
    }
}
