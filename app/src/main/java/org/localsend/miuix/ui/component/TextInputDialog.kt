package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 弹窗输入框的统一外观：占位标签、行数、键盘类型与可选长度上限。 */
@Composable
internal fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    maxLength: Int? = null,
) {
    TextField(
        value = value,
        onValueChange = { newValue ->
            if (maxLength == null || newValue.length <= maxLength) {
                onValueChange(newValue)
            }
        },
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 文本输入弹窗的统一骨架：标题、可选说明、输入框、可选附加内容与取消/确认按钮。
 * [onConfirm] 返回 true 时才关闭弹窗，返回 false 表示输入未通过校验、保持打开。
 */
@Composable
internal fun TextInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean,
    confirmText: String = stringResource(R.string.btn_confirm),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    maxLength: Int? = null,
    description: String? = null,
    descriptionColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    extraContent: (@Composable () -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            if (description != null) {
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.body2,
                    color = descriptionColor,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            DialogTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                keyboardType = keyboardType,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                maxLength = maxLength,
            )

            if (extraContent != null) {
                extraContent()
            }

            Spacer(modifier = Modifier.height(20.dp))

            DialogButtonRow(
                secondaryText = stringResource(R.string.btn_cancel),
                onSecondary = onDismissRequest,
                primaryText = confirmText,
                onPrimary = { if (onConfirm()) onDismissRequest() },
                primaryColors = ButtonDefaults.buttonColorsPrimary(),
            )
        }
    }
}
