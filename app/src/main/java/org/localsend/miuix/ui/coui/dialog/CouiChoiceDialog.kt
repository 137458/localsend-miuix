package org.localsend.miuix.ui.coui.dialog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * coUI 单选列表弹窗：正文槽按顺序放带 [RadioButton] 的单选行。
 *
 * 库 `radiobutton/RadioButton.kt` 只负责绘制，单选语义（`selectableGroup`、`selectable` 与
 * `Role.RadioButton`）和整行按压反馈由宿主负责。行点击只回调 [onSelect]，是否关闭窗口由宿主决定。
 * 库的 Dialog 强制要求 confirmAction（Dialog.kt:55），这里把底部唯一动作固定成关闭按钮；
 * 选项较多时内容槽随 Dialog 内部滚动区一起滚动。
 */
@Composable
internal fun CouiChoiceDialog(
    show: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    closeText: String,
) {
    val colorScheme = CouiTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        confirmAction = CouiDialogAction(text = closeText, onClick = onDismissRequest),
        title = title,
        placement = rememberCouiDialogPlacement(),
        material = rememberCouiDialogMaterial(),
        visible = show,
    ) {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val source = remember { MutableInteractionSource() }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = CouiPageDefaults.rowMinHeight)
                            .couiButtonFeedback(
                                dark = CouiTheme.isDark,
                                interactionSource = source,
                                list = true,
                            ).selectable(
                                selected = isSelected,
                                interactionSource = source,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            ).padding(
                                horizontal = CouiDialogDefaults.contentPadding,
                                vertical = CouiPageDefaults.contentPadding,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = option,
                        kind = CouiTextKind.ItemTitle,
                        color = if (isSelected) colorScheme.accent else colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
