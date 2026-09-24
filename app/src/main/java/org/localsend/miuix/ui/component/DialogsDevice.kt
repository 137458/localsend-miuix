package org.localsend.miuix.ui.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
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