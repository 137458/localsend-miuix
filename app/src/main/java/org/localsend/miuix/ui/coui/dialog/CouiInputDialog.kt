package org.localsend.miuix.ui.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.InputDialog
import com.itos.coui.theme.CouiTheme

/**
 * coUI 单行输入弹窗：复用库 [InputDialog] 的 TextFieldValue 重载（InputDialog.kt:69），
 * 由库负责清除按钮、焦点线、计数位与键盘避让。
 *
 * value 用 [TextFieldValue] 而非 String：库的清除只回调空值，
 * 选区与输入法组合区必须由宿主持有，压成 String 会让每次重组重置光标。
 * [clearContentDescription] 是库的必填参数（InputDialog.kt:77），清除按钮的无障碍描述由调用方本地化。
 * [confirmEnabled] 映射到 CouiDialogAction.enabled：空输入的禁用条件属于业务判断，本层不代劳。
 * 库未暴露键盘类型（无 KeyboardOptions 槽位），需要数字键盘的调用点仍须留用原 Miuix 输入弹窗。
 */
@Composable
internal fun CouiInputDialog(
    show: Boolean,
    title: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    confirmText: String,
    clearContentDescription: String,
    dismissText: String? = null,
    message: String? = null,
    placeholder: String? = null,
    confirmEnabled: Boolean = true,
    autoFocus: Boolean = true,
) {
    InputDialog(
        value = value,
        onValueChange = onValueChange,
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled,
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
            ),
        clearContentDescription = clearContentDescription,
        title = title,
        message = message,
        placeholder = placeholder,
        dismissAction =
            dismissText?.let { text ->
                CouiDialogAction(text = text, onClick = onDismissRequest)
            },
        autoFocus = autoFocus,
        visible = show,
    )
}
