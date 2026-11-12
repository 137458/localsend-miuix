package org.localsend.miuix.ui.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * coUI 单选列表弹窗：正文槽按顺序放 [Item] 行，选中项用强调色标题标记。
 *
 * 库没有 RadioButton；Checkbox.kt 的勾选框表达的是多选语义，拿来做单选会让无障碍读作可多选，
 * 因此选中态只用强调色与 semantics 的 selected 标记表达，不引入新的指示器绘制。
 * 行点击只回调 [onSelect]，是否关闭窗口由宿主决定（沿用 ChangeColorOS 的既有交互即可）。
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
        visible = show,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Item(
                modifier = Modifier.semantics { selected = isSelected },
                onClick = { onSelect(index) },
                title = {
                    Text(
                        text = option,
                        kind = CouiTextKind.ItemTitle,
                        color = if (isSelected) colorScheme.accent else colorScheme.onSurface,
                    )
                },
            )
        }
    }
}
