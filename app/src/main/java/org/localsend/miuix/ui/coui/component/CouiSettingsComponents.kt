package org.localsend.miuix.ui.coui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

// coUI-Kit 的 layout 包只提供 Group/Item/Divider/Footer 四个原语，没有 Preference 体系，
// 行内标题、说明与取值需要页面自己拼。这里把三种文本收敛成统一入口，
// 保证同一类信息在四张卡片里用同一字阶与同一颜色。

/** 行标题。字阶交给 coUI 主题下发，避免每行各写一份字号。 */
@Composable
internal fun CouiRowTitle(text: String) {
    Text(text = text, kind = CouiTextKind.ItemTitle)
}

/** 行说明。次要色表达从属关系。 */
@Composable
internal fun CouiRowSummary(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.ItemSummary,
        color = CouiTheme.colorScheme.onSurfaceVariant,
    )
}

/** 行尾当前值。只读展示，点击语义归整行。 */
@Composable
internal fun CouiRowValue(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.ItemValue,
        color = CouiTheme.colorScheme.onSurfaceVariant,
    )
}

/** 可空文案转成 Item 的 summary 插槽：空文案返回 null，行内不留说明占位。 */
private fun couiSummaryOrNull(text: String?): (@Composable () -> Unit)? = text?.let(::couiSummary)

private fun couiSummary(text: String): (@Composable () -> Unit) = { CouiRowSummary(text) }

/** 可点击行：标题 + 可选说明，用于跳转类设置项。 */
@Composable
internal fun CouiArrowRow(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    // 先落成局部 val，lambda 里才能对可空值做智能转换
    Item(
        title = { CouiRowTitle(title) },
        summary = couiSummaryOrNull(summary),
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * 开关行：整行可切换，[Switch] 只呈现状态。
 *
 * 开关自身不接点击，否则一次点击会被行与开关各消费一次。
 */
@Composable
internal fun CouiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Item(
        modifier =
            Modifier.toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        title = { CouiRowTitle(title) },
        summary = couiSummaryOrNull(summary),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * 枚举选择行：点整行弹出 coUI 菜单，选中项打勾。
 *
 * [options] 的值与文案分开传，值参与相等判断，文案只是展示。
 */
@Composable
internal fun <T> CouiChoiceRow(
    title: String,
    value: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val anchor = rememberCouiMenuAnchor()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .couiMenuAnchor(anchor),
    ) {
        Item(
            title = { CouiRowTitle(title) },
            summary = couiSummaryOrNull(summary),
            trailing = { CouiRowValue(value) },
            enabled = enabled,
            onClick = { anchor.open { expanded = true } },
        )
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            headerText = title,
            items =
                options.map { (option, label) ->
                    CouiMenuItem(
                        value = option,
                        text = label,
                        selected = option == current,
                    )
                },
            onItemClick = { item ->
                expanded = false
                if (item.value != current) onSelect(item.value)
            },
        )
    }
}
