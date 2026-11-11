package org.localsend.miuix.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * 手动输入目标地址的弹窗：IP 地址、端口与近期 IP 三段，顺序为「地址 -> 端口 -> 近期记录」。
 * 近期 IP 数量不定，用横向滚动承载，避免窄屏下固定横排溢出。
 */
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
            ) {
                DialogTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = stringResource(R.string.dialog_manual_ip_port_label),
                    keyboardType = KeyboardType.Number,
                )

                if (recentIps.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_manual_ip_recent_history),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                        ) {
                            recentIps.forEach { historyIp ->
                                DialogChip(
                                    text = historyIp,
                                    onClick = { ip = historyIp },
                                    selected = ip == historyIp,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
