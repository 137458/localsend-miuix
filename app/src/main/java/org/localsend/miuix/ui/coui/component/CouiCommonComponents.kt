package org.localsend.miuix.ui.coui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import org.localsend.miuix.R
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.coui.theme.couiDangerColor
import top.yukonga.miuix.kmp.basic.Icon
import kotlin.math.roundToInt

private val CouiMinTouchTargetSize = 48.dp

/**
 * 扩展最小交互命中尺寸至 48dp x 48dp 并居中放置子内容（对齐 coUI 内部 minimumInteractiveComponentSize）。
 * 须紧邻在 clickable/toggleable/selectable 之后，使点击与无障碍边界同步扩张而不改变视觉图形尺寸。
 */
internal fun Modifier.couiMinTouchTarget(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val minPx = CouiMinTouchTargetSize.roundToPx()
        val width = maxOf(placeable.width, minPx)
        val height = maxOf(placeable.height, minPx)
        layout(width, height) {
            placeable.place(
                ((width - placeable.width) / 2f).roundToInt(),
                ((height - placeable.height) / 2f).roundToInt(),
            )
        }
    }

/**
 * 顶栏/列表尾部圆形图标动作：视觉为 40dp 圆钮 + 22dp 图形，外部命中区经 [couiMinTouchTarget] 撑至 48dp。
 */
@Composable
internal fun CouiIconAction(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadgeDot: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val badgeColor = couiDangerColor()
    Box(
        modifier =
            modifier
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = true,
                    role = Role.Button,
                    onClick = onClick,
                ).couiMinTouchTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CouiShape(20.dp))
                    .couiButtonFeedback(CouiTheme.isDark, source),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            if (showBadgeDot) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-7).dp, y = 7.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                )
            }
        }
    }
}

/**
 * 紧凑顶栏标题：对齐库 `CouiTopAppBarDefaults.titleTextStyle()`（HomeTitle 字族、700 字重）。
 */
@Composable
internal fun CouiTopBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CouiTheme.colorScheme.onSurface,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = CouiTopAppBarDefaults.titleTextStyle().copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 顶栏待接收任务红点入口：各主页面复用，统一无障碍文案与红点样式。
 */
@Composable
internal fun CouiIncomingBadgeAction(
    incomingTaskCount: Int,
    onClick: () -> Unit,
) {
    if (incomingTaskCount <= 0) return
    CouiIconAction(
        icon = AppIcons.Receive,
        contentDescription = stringResource(R.string.receive_section_incoming_count, incomingTaskCount),
        tint = CouiTheme.colorScheme.accentOnSurface,
        onClick = onClick,
        showBadgeDot = true,
    )
}

/**
 * 选项卡语义与键盘交互封装：`onKeyEvent` 置于 `selectable` 之前以拦截焦点节点的 Enter/Space，
 * 并由 `selectable` 提供单一焦点节点与 `Role.Tab` 点击契约，避免叠挂 `.focusable()` 产生双焦点。
 */
internal fun Modifier.couiSelectableTab(
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    onSelect: () -> Unit,
): Modifier =
    semantics(mergeDescendants = true) {
        this.selected = selected
    }.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.Spacebar)) {
            if (!selected) {
                onSelect()
            }
            true
        } else {
            false
        }
    }.selectable(
        selected = selected,
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = { if (!selected) onSelect() },
    )
