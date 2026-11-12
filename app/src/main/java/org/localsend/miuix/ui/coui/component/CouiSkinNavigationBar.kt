package org.localsend.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import top.yukonga.miuix.kmp.basic.Icon

/** 库浮动底栏的系统门槛：材质依赖 Android 14（API 34）新增的 Mesh 绘制 API。 */
private const val COUI_FLOATING_NAV_BAR_MIN_SDK = 34

/**
 * ColorOS 皮肤底栏（形态 A）：Android 14 及以上用 coUI-Kit 原厂浮动材质底栏，
 * Android 12–13 退回同形制平面胶囊，保证皮肤在 minSdk 31 上形态、无障碍与键盘导航一致。
 *
 * 库 [NavigationBar] 没有角标槽位，待接收提示统一由各页面顶栏图标承担。
 */
@Composable
internal fun CouiSkinNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= COUI_FLOATING_NAV_BAR_MIN_SDK) {
        CouiLibraryNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            backdrop = backdrop,
            modifier = modifier,
        )
    } else {
        CouiPlainNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
        )
    }
}

/** 原厂浮动底栏：先判版本再进库分支，@RequiresApi 守卫必须是 SDK_INT 比较，否则 Lint 报 CallRequiresApi。 */
@RequiresApi(COUI_FLOATING_NAV_BAR_MIN_SDK)
@Composable
private fun CouiLibraryNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { CouiFloatingRenderer() }
    NavigationBar(
        modifier = modifier,
        backdrop = backdrop,
        renderer = renderer,
        items = items,
        selectedIndex = selectedIndex,
        dark = CouiTheme.isDark,
        onSelection = onSelect,
    )
}

/** 平面兜底：56dp CouiCapsuleShape 胶囊 + 图标与 10sp 标签 + 完整 Tab 语义与键盘切换。 */
@Composable
private fun CouiPlainNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiTheme.colorScheme
    val dark = CouiTheme.isDark
    val tint = if (dark) Color.White else Color.Black
    val selectedColor = tint.copy(alpha = 230f / 255f)
    val unselectedColor = tint.copy(alpha = 184f / 255f)
    val density = LocalDensity.current
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else 8.dp
    val maxWidth = 24.dp + 70.dp * items.size + 8.dp * (items.size - 1)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CouiCapsuleShape)
                    .background(scheme.container)
                    .selectableGroup()
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val contentColor = if (isSelected) selectedColor else unselectedColor
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CouiCapsuleShape)
                            .background(if (isSelected) tint.copy(alpha = 0.12f) else Color.Transparent)
                            .couiButtonFeedback(dark = dark, interactionSource = interactionSource)
                            .selectable(
                                selected = isSelected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { if (!isSelected) onSelect(index) },
                            ).semantics(mergeDescendants = true) {
                                selected = isSelected
                            }.onKeyEvent {
                                if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                                    if (!isSelected) onSelect(index)
                                    true
                                } else {
                                    false
                                }
                            }.focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = item.title,
                            style =
                                TextStyle(
                                    color = contentColor,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
