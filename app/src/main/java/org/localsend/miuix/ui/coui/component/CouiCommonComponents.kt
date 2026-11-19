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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.itos.coui.text.Text
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
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp),
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
) {
    if (Build.VERSION.SDK_INT >= COUI_TOP_BAR_BUTTON_MIN_SDK) {
        CouiLibraryTopBarIconAction(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            tint = tint,
            enabled = enabled,
        )
    } else {
        CouiIconAction(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint ?: CouiTheme.colorScheme.onSurface,
            onClick = onClick,
            modifier = modifier,
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
) {
    TopAppBarButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: CouiTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 顶栏接收中入口：接收图标 + 计数胶囊，各主页面复用。
 *
 * 计数胶囊自绘（coUI 没有 Badge 原语，见 docs/research/coui-capability-gaps.md）：原先"图标按钮 + 红点"
 * 与相邻编辑/刷新按钮同形，用户看不出它是可点的计数，红点又借用危险色误导语义。
 * 胶囊为 28dp 高的 accent 底色，外层靠 [couiMinTouchTarget] 撑到 48dp 命中区。
 */
@Composable
internal fun CouiIncomingBadgeAction(
    incomingTaskCount: Int,
    onClick: () -> Unit,
) {
    if (incomingTaskCount <= 0) return
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = true,
                    role = Role.Button,
                    onClick = onClick,
                ).couiMinTouchTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(CouiCapsuleShape)
                    // accent 降透明度当容器底色：既区别于纯灰容器，也不与红点共用警示语义
                    .background(CouiTheme.colorScheme.accent.copy(alpha = 0.14f))
                    .couiButtonFeedback(CouiTheme.isDark, source)
                    .height(28.dp)
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = AppIcons.Receive,
                // 计数语义只挂在这一处，避免与数字文本重复朗读
                contentDescription = stringResource(R.string.action_incoming_open_hint, incomingTaskCount),
                tint = CouiTheme.colorScheme.accentOnSurface,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = if (incomingTaskCount > 9) "9+" else incomingTaskCount.toString(),
                kind = CouiTextKind.ItemSummary,
                color = CouiTheme.colorScheme.accentOnSurface,
                maxLines = 1,
            )
        }
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
