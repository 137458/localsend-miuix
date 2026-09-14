package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun RenameDeviceDialog(
    show: Boolean,
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(show, initialName) { mutableStateOf(initialName) }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_rename_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.dialog_rename_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onConfirm(trimmed)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        }
    }
}

@Composable
fun PortDialog(
    show: Boolean,
    initialPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var port by remember(show, initialPort) { mutableStateOf(initialPort.toString()) }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_port_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = port,
                onValueChange = { newValue -> if (newValue.length <= 5) port = newValue },
                label = stringResource(R.string.dialog_port_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val newPort = port.trim().toIntOrNull()
                        if (newPort != null && newPort in 1..65535) {
                            onConfirm(newPort)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        }
    }
}

@Composable
fun ManualIpDialog(
    show: Boolean,
    recentIps: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onSend: (ip: String, port: Int) -> Unit
) {
    var ip by remember(show) { mutableStateOf(recentIps.firstOrNull().orEmpty()) }
    var port by remember(show) { mutableStateOf("53317") }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_manual_ip_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = ip,
                onValueChange = { ip = it },
                label = stringResource(R.string.dialog_manual_ip_address_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            if (recentIps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_manual_ip_recent_history),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentIps.take(4).forEach { histIp ->
                        val isSelected = ip == histIp
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MiuixTheme.colorScheme.surfaceContainer
                                )
                                .clickable { ip = histIp }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = histIp,
                                style = MiuixTheme.textStyles.footnote1,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = port,
                onValueChange = { port = it },
                label = stringResource(R.string.dialog_manual_ip_port_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val targetIp = ip.trim()
                        val targetPort = port.trim().toIntOrNull() ?: 53317
                        if (targetIp.isNotEmpty()) {
                            onSend(targetIp, targetPort)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.dialog_manual_ip_btn_send))
                }
            }
        }
    }
}

@Composable
fun SendTextDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(show) { mutableStateOf("") }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_send_text_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.dialog_send_text_label),
                useLabelAsPlaceholder = true,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.dialog_send_text_btn_send))
                }
            }
        }
    }
}

@Composable
fun PinDialog(
    show: Boolean,
    initialPin: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var pin by remember(show, initialPin) { mutableStateOf(initialPin ?: "") }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_pin_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_pin_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = pin,
                onValueChange = { pin = it },
                label = stringResource(R.string.dialog_pin_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val trimmed = pin.trim().ifEmpty { null }
                        onConfirm(trimmed)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}

@Composable
fun TargetDevicePinDialog(
    show: Boolean,
    targetAlias: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    if (show) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.dialog_target_pin_title),
            onDismissRequest = onDismissRequest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_target_pin_desc, targetAlias),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = stringResource(R.string.dialog_target_pin_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Button(
                        onClick = {
                            onConfirm(pin.trim())
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun CertFingerprintDialog(
    show: Boolean,
    fingerprint: String,
    onDismissRequest: () -> Unit,
    onRegenerate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_cert_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_cert_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_cert_label),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fingerprint.ifEmpty { stringResource(R.string.dialog_cert_empty) },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Certificate Fingerprint", fingerprint))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.dialog_cert_btn_copy))
                }

                Button(
                    onClick = {
                        onRegenerate()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error, contentColor = MiuixTheme.colorScheme.onError),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.dialog_cert_btn_regenerate))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_close))
            }
        }
    }
}

@Composable
fun IncomingTransferDialog(
    session: TransferSession?,
    onAccept: () -> Unit,
    onAcceptAndCopy: () -> Unit,
    onDecline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    WindowDialog(
        show = session != null,
        title = if (session?.isTextMessage == true) stringResource(R.string.dialog_incoming_text_title) else stringResource(R.string.dialog_incoming_files_title),
        onDismissRequest = onDecline
    ) {
        if (session != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_incoming_from_device, session.device.alias, session.device.ip),
                    style = MiuixTheme.textStyles.body1
                )

                if (session.isTextMessage) {
                    val defaultTextMsg = stringResource(R.string.notif_plain_text_message)
                    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: defaultTextMsg
                    val detectedUrl = remember(previewText) {
                        val trimmed = previewText.trim()
                        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                            if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                                trimmed
                            } else {
                                val matcher = android.util.Patterns.WEB_URL.matcher(trimmed)
                                if (matcher.find()) matcher.group() else null
                            }
                        } else {
                            val matcher = android.util.Patterns.WEB_URL.matcher(trimmed)
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
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = previewText,
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 8
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDecline,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_decline))
                        }

                        Button(
                            onClick = onAcceptAndCopy,
                            colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_copy))
                        }

                        if (detectedUrl != null) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detectedUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show()
                                    }
                                    onAccept()
                                },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_open))
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.dialog_incoming_files_summary, session.files.size, session.formattedTotalSize),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(session.files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FileThumbnail(
                                        file = file,
                                        size = 28.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = file.name,
                                        style = MiuixTheme.textStyles.body1,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = file.formattedSize,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDecline,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_decline))
                        }
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f)
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
    onPasteClipboard: () -> Unit
) {
    top.yukonga.miuix.kmp.window.WindowBottomSheet(
        show = show,
        title = stringResource(R.string.sheet_add_content_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_file_title),
                    summary = stringResource(R.string.sheet_add_file_summary),
                    onClick = {
                        onDismissRequest()
                        onPickFiles()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_folder_title),
                    summary = stringResource(R.string.sheet_add_folder_summary),
                    onClick = {
                        onDismissRequest()
                        onPickFolder()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_media_title),
                    summary = stringResource(R.string.sheet_add_media_summary),
                    onClick = {
                        onDismissRequest()
                        onPickMedia()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_apps_title),
                    summary = stringResource(R.string.sheet_add_apps_summary),
                    onClick = {
                        onDismissRequest()
                        onPickApps()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_text_title),
                    summary = stringResource(R.string.sheet_add_text_summary),
                    onClick = {
                        onDismissRequest()
                        onSendText()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.sheet_add_clipboard_title),
                    summary = stringResource(R.string.sheet_add_clipboard_summary),
                    onClick = {
                        onDismissRequest()
                        onPasteClipboard()
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
