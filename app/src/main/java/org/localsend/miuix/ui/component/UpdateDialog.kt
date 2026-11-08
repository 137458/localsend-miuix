package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

/**
 * 整合版更新弹窗：一行版本跃迁摘要 + 可折叠说明 + 纵向主次动作。
 *
 * - 关闭弹窗不取消下载，状态由进程级单例持有，更新页可继续看到进度。
 * - 说明区是弹窗内唯一的滚动容器，限高 240dp，上下 40dp 羽化。
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = UPDATE_NOTES_MAX_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .verticalEdgeFade(
                            top = scrollState.canScrollBackward,
                            bottom = scrollState.canScrollForward,
                            fade = UPDATE_NOTES_FADE_DP.dp,
                        ).verticalScroll(scrollState)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (plan.showProgress) {
                    plan.progressDetail?.let {
                        Text(
                            text = it,
                            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    LinearProgressIndicator(progress = plan.progressFraction ?: 0f, modifier = Modifier.fillMaxWidth())
                }

                if (downloadState is UpdateDownloadState.Failed) {
                    Text(
                        text = downloadState.message,
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
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
                                    "收起说明"
                                } else {
                                    "展开全部（还有 ${notes.hiddenLines} 行）"
                                },
                            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
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

                if (plan.showIgnore && onIgnore != null) {
                    Text(
                        text = "忽略此版本",
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { onIgnore(releaseInfo.latestVersion) }
                                .padding(vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { runPrimary() },
                    enabled = plan.primaryEnabled,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(plan.primaryText)
                }

                if (plan.showBrowserFallback) {
                    TextButton(
                        text = stringResource(R.string.update_dialog_btn_browser),
                        onClick = { onOpenBrowser(updateBrowserUrl(releaseInfo)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (plan.showCancelDownload) {
                    TextButton(
                        text = stringResource(R.string.update_dialog_btn_cancel_download),
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = plan.dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
