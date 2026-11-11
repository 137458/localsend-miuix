package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.localsend.miuix.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

/** 弹窗输入框的统一外观：占位标签、行数、键盘类型与可选长度上限。 */
@Composable
internal fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
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
        label = label ?: "",
        // 常驻浮动标签：值非空时标签缩小到输入框上方，而不是像占位符一样消失，
        // 否则端口这类预填字段只剩一串数字、看不出含义。
        useLabelAsPlaceholder = false,
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
 *
 * 说明文案统一交给 WindowDialog 的 summary 居中展示，内容区只保留输入相关元素，
 * 避免同一层级的说明文字在不同弹窗里出现两种排版。
 */
@Composable
internal fun TextInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean,
    confirmText: String = stringResource(R.string.btn_confirm),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    maxLength: Int? = null,
    description: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = description,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            // 输入区：输入框与附加内容同属一块，内部用 BlockGap 弱分隔。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
            ) {
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
            }

            // 动作区永远是最后一块，靠根 Column 的 SectionGap 与内容区分隔。
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
