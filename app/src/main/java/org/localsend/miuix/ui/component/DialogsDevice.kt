package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.core.NetworkConstants
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RenameDeviceDialog(
    show: Boolean,
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(show, initialName) { mutableStateOf(initialName) }

    TextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_rename_title),
        value = name,
        onValueChange = { name = it },
        label = stringResource(R.string.dialog_rename_label),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val trimmed = name.trim()
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
fun PortDialog(
    show: Boolean,
    initialPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var port by remember(show, initialPort) { mutableStateOf(initialPort.toString()) }

    TextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_port_title),
        value = port,
        onValueChange = { port = it },
        label = stringResource(R.string.dialog_port_label),
        keyboardType = KeyboardType.Number,
        maxLength = 5,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val newPort = port.trim().toIntOrNull()
            if (newPort != null && newPort in 1..65535) {
                onConfirm(newPort)
                true
            } else {
                false
            }
        },
    )
}

@Composable
fun ManualIpDialog(
    show: Boolean,
    recentIps: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onSend: (ip: String, port: Int) -> Unit,
) {
    var ip by remember(show) { mutableStateOf(recentIps.firstOrNull().orEmpty()) }
    var port by remember(show) { mutableStateOf(NetworkConstants.DEFAULT_PORT.toString()) }

    TextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_manual_ip_title),
        value = ip,
        onValueChange = { ip = it },
        label = stringResource(R.string.dialog_manual_ip_address_label),
        keyboardType = KeyboardType.Uri,
        confirmText = stringResource(R.string.dialog_manual_ip_btn_send),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val targetIp = ip.trim()
            if (targetIp.isNotEmpty()) {
                onSend(targetIp, port.trim().toIntOrNull() ?: NetworkConstants.DEFAULT_PORT)
                true
            } else {
                false
            }
        },
        extraContent = {
            if (recentIps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_manual_ip_recent_history),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recentIps.take(4).forEach { histIp ->
                        val isSelected = ip == histIp
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) {
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else {
                                            MiuixTheme.colorScheme.surfaceContainer
                                        },
                                    ).clickable(role = Role.Button) { ip = histIp }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = histIp,
                                style = MiuixTheme.textStyles.footnote1,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            DialogTextField(
                value = port,
                onValueChange = { port = it },
                label = stringResource(R.string.dialog_manual_ip_port_label),
                keyboardType = KeyboardType.Number,
            )
        },
    )
}
