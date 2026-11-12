package org.localsend.miuix.ui.coui.dialog

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
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.R
import org.localsend.miuix.core.NetworkConstants

/**
 * coUI 文本输入弹窗通用骨架：覆盖需定制 `KeyboardOptions`、多行域或附加内容的场景。
 * [onConfirm] 返回 true 时关闭弹窗，返回 false 表示校验未通过并保持打开。
 */
@Composable
internal fun CouiTextInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean,
    confirmText: String = stringResource(R.string.btn_confirm),
    confirmEnabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    maxLength: Int? = null,
    description: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val primaryActionColors = couiPrimaryActionColors()
    CouiContentDialog(
        show = show,
        title = title,
        message = description,
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = confirmText,
                onClick = {
                    if (onConfirm()) {
                        onDismissRequest()
                    }
                },
                enabled = confirmEnabled,
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            ),
        dismissAction =
            CouiDialogAction(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismissRequest,
            ),
    ) {
        CouiDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            keyboardType = keyboardType,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            maxLength = maxLength,
            clearContentDescription = stringResource(R.string.btn_clear),
        )
        if (extraContent != null) {
            extraContent()
        }
    }
}

/** coUI 服务端口设置弹窗。 */
@Composable
internal fun CouiPortDialog(
    show: Boolean,
    initialPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var port by remember(show, initialPort) { mutableStateOf(initialPort.toString()) }
    val parsedPort = port.trim().toIntOrNull()
    val valid = parsedPort != null && parsedPort in 1..65535

    CouiTextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_port_title),
        value = port,
        onValueChange = { port = it },
        label = stringResource(R.string.dialog_port_label),
        keyboardType = KeyboardType.Number,
        maxLength = 5,
        confirmEnabled = valid,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            if (valid) {
                onConfirm(parsedPort)
                true
            } else {
                false
            }
        },
    )
}

/** coUI 发送纯文本弹窗（支持 3-6 行多行域）。 */
@Composable
internal fun CouiSendTextDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(show) { mutableStateOf("") }

    CouiTextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_send_text_title),
        value = text,
        onValueChange = { text = it },
        placeholder = stringResource(R.string.dialog_send_text_title),
        confirmText = stringResource(R.string.dialog_send_text_btn_send),
        confirmEnabled = text.trim().isNotEmpty(),
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

/** coUI 手动输入目标 IP 与端口弹窗。 */
@Composable
internal fun CouiManualIpDialog(
    show: Boolean,
    recentIps: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onSend: (ip: String, port: Int) -> Unit,
) {
    var ip by remember(show) { mutableStateOf(recentIps.firstOrNull().orEmpty()) }
    var port by remember(show) { mutableStateOf(NetworkConstants.DEFAULT_PORT.toString()) }

    CouiTextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_manual_ip_title),
        value = ip,
        onValueChange = { ip = it },
        label = stringResource(R.string.dialog_manual_ip_address_label),
        keyboardType = KeyboardType.Uri,
        confirmText = stringResource(R.string.dialog_manual_ip_btn_send),
        confirmEnabled = ip.trim().isNotEmpty(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CouiDialogTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = stringResource(R.string.dialog_manual_ip_port_label),
                    keyboardType = KeyboardType.Number,
                    maxLength = 5,
                    clearContentDescription = stringResource(R.string.btn_clear),
                )
                if (recentIps.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_manual_ip_recent_history),
                            kind = CouiTextKind.ItemSummary,
                            color = CouiTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentIps.forEach { historyIp ->
                                CouiDialogChip(
                                    text = historyIp,
                                    selected = ip == historyIp,
                                    onClick = { ip = historyIp },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

/** coUI 本机接收 PIN 码设置弹窗。 */
@Composable
internal fun CouiPinDialog(
    show: Boolean,
    initialPin: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var pin by remember(show, initialPin) { mutableStateOf(initialPin ?: "") }

    CouiTextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_pin_title),
        value = pin,
        onValueChange = { pin = it },
        label = stringResource(R.string.dialog_pin_label),
        confirmText = stringResource(R.string.btn_save),
        keyboardType = KeyboardType.Number,
        description = stringResource(R.string.dialog_pin_desc),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            onConfirm(pin.trim().ifEmpty { null })
            true
        },
    )
}

/** coUI 目标设备 PIN 验证输入弹窗。 */
@Composable
internal fun CouiTargetDevicePinDialog(
    show: Boolean,
    targetAlias: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember(show, targetAlias) { mutableStateOf("") }

    CouiTextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_target_pin_title),
        value = pin,
        onValueChange = { pin = it },
        label = stringResource(R.string.dialog_target_pin_label),
        keyboardType = KeyboardType.Ascii,
        description = stringResource(R.string.dialog_target_pin_desc, targetAlias),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            onConfirm(pin.trim())
            true
        },
    )
}
