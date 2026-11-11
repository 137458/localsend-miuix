// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SendTextDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(show) { mutableStateOf("") }

    TextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_send_text_title),
        value = text,
        onValueChange = { text = it },
        confirmText = stringResource(R.string.dialog_send_text_btn_send),
        singleLine = false,
        minLines = 3,
        maxLines = 6,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) {
                onConfirm(trimmed)
                true
            } else {
                false
            }
        },
    )
}

@Composable
fun IncomingTransferDialog(
    session: TransferSession?,
    onAccept: (selectedFileIds: Set<String>?) -> Unit,
    onAcceptAndCopy: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedFileIds by remember(session?.sessionId) {
        mutableStateOf(session?.files?.map { it.id }?.toSet() ?: emptySet())
    }

    WindowDialog(
        show = session != null,
        title = if (session?.isTextMessage == true) stringResource(R.string.dialog_incoming_text_title) else stringResource(R.string.dialog_incoming_files_title),
        summary = if (session != null) stringResource(R.string.dialog_incoming_from_device, session.device.alias, session.device.ip) else "",
        // 关闭弹窗（点击外部或返回键）只是收起提示，不等于拒绝：请求仍由系统保持待处理，
        // 接收页卡片上的"接收"按钮与通知栏操作都还能继续处理，只有超时或用户显式拒绝才回 403。
        onDismissRequest = onDismiss,
    ) {
        if (session != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
            ) {
                if (session.isTextMessage) {
                    val defaultTextMsg = stringResource(R.string.notif_plain_text_message)
                    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: defaultTextMsg
                    val detectedUrl =
                        remember(previewText) { detectWebUrlInText(previewText) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_incoming_text_length, previewText.length),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )

                        DialogCard {
                            DialogBody(maxHeight = rememberDialogContentMaxHeight(0.3f, 100.dp, 240.dp)) {
                                Text(
                                    text = previewText,
                                    style = MiuixTheme.textStyles.body1,
                                )
                            }
                        }
                    }

                    DialogActionColumn {
                        Button(
                            onClick = onAcceptAndCopy,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.btn_copy))
                        }

                        if (detectedUrl != null) {
                            DialogTextAction(
                                text = stringResource(R.string.btn_open),
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
                                    onAccept(null)
                                },
                            )
                        }

                        DialogTextAction(
                            text = stringResource(R.string.btn_decline),
                            onClick = onDecline,
                            colors = dialogDangerTextColors(),
                        )
                    }
                } else {
                    val totalFilesCount = session.files.size
                    val isAllSelected = selectedFileIds.size == totalFilesCount && totalFilesCount > 0

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
                                    if (totalFilesCount > 1) {
                                        stringResource(R.string.dialog_incoming_selective_count, selectedFileIds.size, totalFilesCount)
                                    } else {
                                        stringResource(R.string.dialog_incoming_files_summary, session.files.size, session.formattedTotalSize)
                                    },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f).padding(end = DialogTokens.BlockGap),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (totalFilesCount > 1) {
                                val toggleAll = {
                                    selectedFileIds = if (isAllSelected) emptySet() else session.files.map { it.id }.toSet()
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                                            .toggleable(
                                                value = isAllSelected,
                                                role = Role.Checkbox,
                                                onValueChange = { toggleAll() },
                                            ).padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Checkbox(
                                        state =
                                            androidx.compose.ui.state
                                                .ToggleableState(isAllSelected),
                                        onClick = {},
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.dialog_incoming_select_all),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // 卡片本身不提供内边距，行内的左右留白由 CardPadding 统一给出，避免双重内边距。
                        DialogCard(contentPadding = 0.dp) {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = rememberDialogContentMaxHeight(0.4f, 140.dp, 320.dp)),
                                contentPadding = PaddingValues(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(session.files, key = { it.id }) { file ->
                                    val isChecked = selectedFileIds.contains(file.id)
                                    val toggleFile = {
                                        selectedFileIds =
                                            if (isChecked) {
                                                selectedFileIds - file.id
                                            } else {
                                                selectedFileIds + file.id
                                            }
                                    }
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                                                .toggleable(
                                                    value = isChecked,
                                                    enabled = totalFilesCount > 1,
                                                    role = Role.Checkbox,
                                                    onValueChange = { toggleFile() },
                                                ).padding(horizontal = DialogTokens.CardPadding, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (totalFilesCount > 1) {
                                            Checkbox(
                                                state =
                                                    androidx.compose.ui.state
                                                        .ToggleableState(isChecked),
                                                onClick = {},
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        FileThumbnail(
                                            file = file,
                                            size = 28.dp,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.name,
                                            style = MiuixTheme.textStyles.body1,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = file.formattedSize,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DialogButtonRow(
                        secondaryText = stringResource(R.string.btn_decline),
                        onSecondary = onDecline,
                        primaryText = stringResource(R.string.btn_accept),
                        onPrimary = { onAccept(selectedFileIds) },
                        primaryEnabled = selectedFileIds.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
fun AddContentBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onPickApps: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.sheet_add_content_title),
        onDismissRequest = onDismissRequest,
    ) {
        // 卡片与 ArrowPreference 自带内边距，内容区不再叠加左右边距。
        DialogCard(contentPadding = 0.dp) {
            ArrowPreference(
                title = stringResource(R.string.sheet_add_file_title),
                onClick = {
                    onDismissRequest()
                    onPickFiles()
                },
            )
            ArrowPreference(
                title = stringResource(R.string.sheet_add_folder_title),
                onClick = {
                    onDismissRequest()
                    onPickFolder()
                },
            )
            ArrowPreference(
                title = stringResource(R.string.sheet_add_media_title),
                onClick = {
                    onDismissRequest()
                    onPickMedia()
                },
            )
            ArrowPreference(
                title = stringResource(R.string.sheet_add_apps_title),
                onClick = {
                    onDismissRequest()
                    onPickApps()
                },
            )
            ArrowPreference(
                title = stringResource(R.string.sheet_add_text_title),
                onClick = {
                    onDismissRequest()
                    onSendText()
                },
            )
            ArrowPreference(
                title = stringResource(R.string.sheet_add_clipboard_title),
                onClick = {
                    onDismissRequest()
                    onPasteClipboard()
                },
            )
        }
    }
}
