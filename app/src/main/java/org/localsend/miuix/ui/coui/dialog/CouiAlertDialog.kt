package org.localsend.miuix.ui.coui.dialog

import androidx.compose.runtime.Composable
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.Dialog

/**
 * coUI 警告弹窗：标题 + 正文 + 取消/确认，主操作可切换为破坏性配色。
 *
 * 窗口、遮罩、进出场动画与原厂按钮排版全部交给库的 [Dialog]，本封装只决定三件事：
 * 主操作固定为 Primary 且显式给出配色（库的 CouiDialogAction 要求 Primary 必须带 colors，
 * 见 Dialog.kt:41），取消动作省略时只留一个占满宽度的确认按钮，
 * danger 时主操作通过 [couiPrimaryActionColors] 统一取用破坏性色。
 *
 * [show] 传给库的 visible 槽位而不是用 if 包住调用：库只有在 visible 非空时才保留退场窗口
 * （Dialog.kt:48-49），因此本组件必须始终留在组合中。
 * 正文超长时库内部滚动，按钮区不随正文滚出窗口。
 */
@Composable
internal fun CouiAlertDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    danger: Boolean = false,
) {
    val confirmColors = couiPrimaryActionColors(danger = danger)
    Dialog(
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = confirmText,
                onClick = onConfirm,
                style = CouiDialogActionStyle.Primary,
                colors = confirmColors,
            ),
        title = title,
        message = message,
        dismissAction =
            dismissText?.let { text ->
                CouiDialogAction(text = text, onClick = onDismissRequest)
            },
        placement = rememberCouiDialogPlacement(),
        material = rememberCouiDialogMaterial(),
        visible = show,
    )
}
