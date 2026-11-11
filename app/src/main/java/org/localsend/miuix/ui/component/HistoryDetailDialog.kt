// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.component

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.util.rememberUiTimeText
import org.localsend.miuix.util.InstallHelper
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun HistoryDetailDialog(
    item: TransferHistoryItem?,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: (String) -> Unit,
    onResend: ((TransferHistoryItem) -> Unit)? = null,
) {
    if (item == null) return
    val context = LocalContext.current
    val formattedTime = rememberUiTimeText("yyyy-MM-dd HH:mm:ss", item.timestamp)
    val fileListMaxHeight = rememberDialogContentMaxHeight()

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (item.isTextMessage) stringResource(R.string.history_detail_text_title) else stringResource(R.string.history_detail_file_title),
    ) {
        // 内容区已由 WindowBottomSheet 提供 24dp 内边距，这里只负责区块节奏。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            // Section 1: 设备与状态信息卡
            DialogCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (item.isIncoming) {
                                    stringResource(R.string.history_detail_sender, item.deviceAlias)
                                } else {
                                    stringResource(R.string.history_detail_receiver, item.deviceAlias)
                                },
                            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text =
                                when (item.status) {
                                    TransferStatus.Completed -> stringResource(R.string.status_completed)
                                    TransferStatus.Failed -> stringResource(R.string.status_failed)
                                    TransferStatus.Canceled -> stringResource(R.string.status_canceled)
                                    else -> stringResource(R.string.status_processing)
                                },
                            style = MiuixTheme.textStyles.footnote1,
                            color =
                                when (item.status) {
                                    TransferStatus.Completed -> MiuixTheme.colorScheme.primary
                                    TransferStatus.Failed -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                        )
                    }

                    DialogInfoRow(label = stringResource(R.string.history_detail_device_ip), value = item.deviceIp)
                    DialogInfoRow(label = stringResource(R.string.history_detail_time), value = formattedTime)
                    // 文本消息只有一个 file entry，条目数与体积对用户无意义，仅文件传输显示总量
                    if (!item.isTextMessage) {
                        DialogInfoRow(
                            label = stringResource(R.string.history_detail_total),
                            value = stringResource(R.string.history_detail_total_summary, item.fileCount, item.formattedSize),
                        )
                    }
                }
            }

            // Section 2: 文本内容或文件列表
            if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                val textContent = item.textContent
                val detectedUrl = remember(textContent) { detectWebUrlInText(textContent) }
                val copyText: () -> Unit = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Text", textContent))
                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
                DialogCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    ) {
                        Text(
                            text = textContent,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 卡片内的操作跟随文本、右对齐收紧，避免在说明卡里再出现一块整宽主按钮与底部动作区抢视线
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DialogTokens.ActionGap, Alignment.End),
                        ) {
                            Button(
                                onClick = copyText,
                                colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(stringResource(R.string.history_detail_btn_copy_text))
                            }
                            if (detectedUrl != null) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent =
                                                Intent(Intent.ACTION_VIEW, Uri.parse(detectedUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(stringResource(R.string.btn_open_link))
                                }
                            }
                        }
                    }
                }
            } else if (item.fileEntries.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                ) {
                    Text(
                        text = stringResource(R.string.history_detail_included_files),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = fileListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    ) {
                        items(item.fileEntries) { file ->
                            Card(
                                onClick = {
                                    file.uri?.let { uri ->
                                        try {
                                            val intent =
                                                Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, file.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.toast_cannot_open_file), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(DialogTokens.CardPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FileThumbnailContent(
                                        name = file.name,
                                        mimeType = file.mimeType,
                                        uri = file.uri,
                                        path = file.path,
                                        isTextMessage = false,
                                        stateKey = file.uriString ?: file.name,
                                        size = 36.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MiuixTheme.textStyles.body2,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = FileItem.formatFileSize(file.size),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    // apk 条目提供“安装”直达按钮，失败以提示代替崩溃
                                    if (InstallHelper.isApk(file.name)) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { launchApkInstall(context, file.path, file.uriString) },
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) {
                                            Text(stringResource(R.string.btn_install_apk))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: 动作区，主操作独占一行，删除与关闭降级为文字按钮
            DialogActionColumn {
                if (onResend != null) {
                    Button(
                        onClick = { onResend(item) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.history_action_resend))
                    }
                } else {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.btn_close))
                    }
                }

                DialogTextAction(
                    text = stringResource(R.string.history_detail_btn_delete_record),
                    onClick = {
                        onDelete(item.id)
                        onDismissRequest()
                        Toast.makeText(context, context.getString(R.string.toast_history_item_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = dialogDangerTextColors(),
                )

                if (onResend != null) {
                    DialogTextAction(text = stringResource(R.string.btn_close), onClick = onDismissRequest)
                }
            }
        }
    }
}

/**
 * 触发 apk 安装：优先真实磁盘文件（FileProvider），否则退回 content uri。
 * 失败一律以 Toast 提示，不允许应用崩溃。
 */
private fun launchApkInstall(
    context: Context,
    path: String?,
    uriString: String?,
) {
    val intent =
        InstallHelper.buildInstallIntent(context, path, uriString) ?: run {
            Toast.makeText(context, context.getString(R.string.toast_install_source_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
    try {
        context.startActivity(intent)
    } catch (e: SecurityException) {
        Toast.makeText(context, context.getString(R.string.toast_install_permission_needed), Toast.LENGTH_SHORT).show()
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.toast_install_permission_needed), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_cannot_open_file), Toast.LENGTH_SHORT).show()
    }
}
