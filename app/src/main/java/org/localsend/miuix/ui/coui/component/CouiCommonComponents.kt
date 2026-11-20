package org.localsend.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.TopAppBarButtonGroup
import org.localsend.miuix.R
import org.localsend.miuix.ui.component.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import kotlin.math.roundToInt

private const val COUI_TOP_BAR_BUTTON_MIN_SDK = 34
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
    badgeCount: Int? = null,
) {
    val source = remember { MutableInteractionSource() }
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
            CouiBadgedIcon(
                icon = icon,
                contentDescription = contentDescription,
                tint = tint,
                badgeCount = badgeCount,
            )
        }
    }
}

/**
 * 统一大标题顶栏：全 ColorOS 皮肤页面共用同一 `26.sp` HomeTitle 展开字阶，
 * 避免中英文长标题在有右侧动作组时截断，并同步传给 `expandedTitleStyle` 保证折叠缩放比例一致。
 */
@Composable
internal fun CouiScreenLargeTopAppBar(
    title: String,
    scrollBehavior: CouiTopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val expandedTitleStyle =
        rememberCouiTextStyle(CouiTextKind.HomeTitle).copy(
            fontSize = 26.sp,
            color = CouiTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    LargeTopAppBar(
        modifier = modifier,
        title = {
            BasicText(
                text = title,
                style = expandedTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedTitleStyle = expandedTitleStyle,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 顶栏多个动作的胶囊分组：Android 14 及以上使用库原厂 [TopAppBarButtonGroup] 呈现连续玻璃胶囊底座，
 * 低版本退回紧凑间距行。须置于 `TopAppBar` / `LargeTopAppBar` 栏内使用。
 */
@Composable
internal fun CouiTopBarActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (Build.VERSION.SDK_INT >= COUI_TOP_BAR_BUTTON_MIN_SDK) {
        CouiLibraryTopBarActionGroup(modifier = modifier, content = content)
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@RequiresApi(COUI_TOP_BAR_BUTTON_MIN_SDK)
@Composable
private fun CouiLibraryTopBarActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TopAppBarButtonGroup(modifier = modifier, content = content)
}

/**
 * 顶栏专用图标动作：Android 14 及以上使用库原厂 [TopAppBarButton]（液态聚光与弹跳反馈），
 * 低版本回退至平面圆形按压反馈 [CouiIconAction]。须置于 `TopAppBar` / `LargeTopAppBar` 栏内使用。
 */
@Composable
internal fun CouiTopBarIconAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    badgeCount: Int? = null,
) {
    if (Build.VERSION.SDK_INT >= COUI_TOP_BAR_BUTTON_MIN_SDK) {
        CouiLibraryTopBarIconAction(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            tint = tint,
            enabled = enabled,
            badgeCount = badgeCount,
        )
    } else {
        CouiIconAction(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint ?: CouiTheme.colorScheme.onSurface,
            onClick = onClick,
            modifier = modifier,
            badgeCount = badgeCount,
        )
    }
}

@RequiresApi(COUI_TOP_BAR_BUTTON_MIN_SDK)
@Composable
private fun CouiLibraryTopBarIconAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    badgeCount: Int? = null,
) {
    TopAppBarButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        CouiBadgedIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint ?: CouiTheme.colorScheme.onSurface,
            badgeCount = badgeCount,
        )
    }
}

/**
 * 顶栏接收中入口：与相邻编辑、刷新按钮同形同底座的接收图标，右上角叠一枚计数角标，各主页面复用。
 *
 * 数量角标自绘（coUI 没有 Badge 原语，见 docs/research/coui-capability-gaps.md）：沿用按钮形态，
 * 用 accent 底色承载数量而不借危险红，避免角标被读成告警。命中区与相邻按钮一致（48dp）。
 */
@Composable
internal fun CouiIncomingBadgeAction(
    incomingTaskCount: Int,
    onClick: () -> Unit,
) {
    if (incomingTaskCount <= 0) return
    CouiTopBarIconAction(
        icon = AppIcons.Receive,
        // 计数语义只挂在这一处：角标自身不参与朗读，避免与数字重复
        contentDescription = stringResource(R.string.action_incoming_open_hint, incomingTaskCount),
        tint = CouiTheme.colorScheme.accentOnSurface,
        onClick = onClick,
        badgeCount = incomingTaskCount,
    )
}

/**
 * 22dp 图标 + 可选数量角标。角标按图标定位，两个顶栏按钮分支（库玻璃按钮与低版本平面圆钮）因此共用同一套几何。
 */
@Composable
private fun CouiBadgedIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    badgeCount: Int?,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (badgeCount != null && badgeCount > 0) {
            CouiCountBadge(
                count = badgeCount,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp),
            )
        }
    }
}

/**
 * 图标角标：accent 底 + onAccent 文字，14dp 高胶囊，多字符靠内边距撑宽，超过 9 显示 "9+"。
 */
@Composable
private fun CouiCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val textStyle =
        rememberCouiTextStyle(CouiTextKind.ItemSummary).copy(
            fontSize = 9.sp,
            color = CouiTheme.colorScheme.onAccent,
            textAlign = TextAlign.Center,
        )
    Box(
        modifier =
            modifier
                .height(14.dp)
                .widthIn(min = 14.dp)
                .clip(CouiCapsuleShape)
                .background(CouiTheme.colorScheme.accent)
                .clearAndSetSemantics { }
                .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (count > 9) "9+" else count.toString(),
            style = textStyle,
            maxLines = 1,
        )
    }
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
