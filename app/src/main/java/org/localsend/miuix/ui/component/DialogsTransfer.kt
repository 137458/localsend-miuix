package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SendTextDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(show) { mutableStateOf("") }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_send_text_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.dialog_send_text_label),
                useLabelAsPlaceholder = true,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            onConfirm(trimmed)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dialog_send_text_btn_send))
                }
            }
        }
    }
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
        // 关闭弹窗（点击外部或返回键）只是收起提示，不等于拒绝：请求仍由系统保持待处理，
        // 接收页卡片上的"接收"按钮与通知栏操作都还能继续处理，只有超时或用户显式拒绝才回 403。
        onDismissRequest = onDismiss,
    ) {
        if (session != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.dialog_incoming_from_device, session.device.alias, session.device.ip),
                    style = MiuixTheme.textStyles.body1,
                )

                if (session.isTextMessage) {
                    val defaultTextMsg = stringResource(R.string.notif_plain_text_message)
                    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: defaultTextMsg
                    val detectedUrl =
                        remember(previewText) {
                            val trimmed = previewText.trim()
                            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                                if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                                    trimmed
                                } else {
                                    val matcher =
                                        android.util.Patterns.WEB_URL
                                            .matcher(trimmed)
                                    if (matcher.find()) matcher.group() else null
                                }
                            } else {
                                val matcher =
                                    android.util.Patterns.WEB_URL
                                        .matcher(trimmed)
                                if (matcher.find()) {
                                    val found = matcher.group()
                                    if (found.startsWith("http://", ignoreCase = true) || found.startsWith("https://", ignoreCase = true)) {
                                        found
                                    } else {
                                        "https://$found"
                                    }
                                } else {
                                    null
                                }
                            }
                        }

                    Text(
                        text = stringResource(R.string.dialog_incoming_text_length, previewText.length),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = previewText,
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 8,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onDecline,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.btn_decline))
                        }

                        Button(
                            onClick = onAcceptAndCopy,
                            colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.btn_copy))
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
                                    onAccept(null)
                                },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.btn_open))
                            }
                        }
                    }
                } else {
                    val totalFilesCount = session.files.size
                    val isAllSelected = selectedFileIds.size == totalFilesCount && totalFilesCount > 0

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                        )

                        if (totalFilesCount > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedFileIds = if (isAllSelected) emptySet() else session.files.map { it.id }.toSet()
                                        }.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Checkbox(
                                    state =
                                        androidx.compose.ui.state
                                            .ToggleableState(isAllSelected),
                                    onClick = {
                                        selectedFileIds = if (isAllSelected) emptySet() else session.files.map { it.id }.toSet()
                                    },
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(session.files, key = { it.id }) { file ->
                                val isChecked = selectedFileIds.contains(file.id)
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedFileIds =
                                                    if (isChecked) {
                                                        selectedFileIds - file.id
                                                    } else {
                                                        selectedFileIds + file.id
                                                    }
                                            }.padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (totalFilesCount > 1) {
                                        Checkbox(
                                            state =
                                                androidx.compose.ui.state
                                                    .ToggleableState(isChecked),
                                            onClick = {
                                                selectedFileIds =
                                                    if (isChecked) {
                                                        selectedFileIds - file.id
                                                    } else {
                                                        selectedFileIds + file.id
                                                    }
                                            },
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onDecline,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.btn_decline))
                        }
                        Button(
                            onClick = { onAccept(selectedFileIds) },
                            enabled = selectedFileIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.btn_accept))
                        }
                    }
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
    top.yukonga.miuix.kmp.window.WindowBottomSheet(
        show = show,
        title = stringResource(R.string.sheet_add_content_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_file_title),
                    summary = stringResource(R.string.sheet_add_file_summary),
                    onClick = {
                        onDismissRequest()
                        onPickFiles()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_folder_title),
                    summary = stringResource(R.string.sheet_add_folder_summary),
                    onClick = {
                        onDismissRequest()
                        onPickFolder()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_media_title),
                    summary = stringResource(R.string.sheet_add_media_summary),
                    onClick = {
                        onDismissRequest()
                        onPickMedia()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_apps_title),
                    summary = stringResource(R.string.sheet_add_apps_summary),
                    onClick = {
                        onDismissRequest()
                        onPickApps()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_text_title),
                    summary = stringResource(R.string.sheet_add_text_summary),
                    onClick = {
                        onDismissRequest()
                        onSendText()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_clipboard_title),
                    summary = stringResource(R.string.sheet_add_clipboard_summary),
                    onClick = {
                        onDismissRequest()
                        onPasteClipboard()
                    },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
