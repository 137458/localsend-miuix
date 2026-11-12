package org.localsend.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme
import top.yukonga.miuix.kmp.basic.Icon

/** 库浮动底栏的系统门槛：材质依赖 Android 14（API 34）新增的 Mesh 绘制 API。 */
private const val COUI_FLOATING_NAV_BAR_MIN_SDK = 34

/**
 * ColorOS 皮肤底栏（形态 A）：Android 14 及以上用 coUI-Kit 原厂浮动材质底栏，
 * Android 12–13 退回同色系平面胶囊，保证皮肤在 minSdk 31 上形态一致。
 *
 * [badgeVisible] 按 Tab 索引给出是否需要角标：库 [NavigationBar] 没有角标槽位，
 * 只有平面兜底栏自绘 coUI 通知点。
 */
@Composable
internal fun CouiSkinNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    badgeVisible: (Int) -> Boolean = { false },
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
            badgeVisible = badgeVisible,
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

/** 平面兜底：单行胶囊 + 选中态底色块，不依赖 Mesh 材质。 */
@Composable
private fun CouiPlainNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeVisible: (Int) -> Boolean = { false },
) {
    val scheme = CouiTheme.colorScheme
    val dark = CouiTheme.isDark
    val tint = if (dark) Color.White else Color.Black
    // 角标沿用 coUI 固定色板的红，CouiColorScheme 本身没有通知/错误语义色
    val badgeColor = if (dark) CouiColor.Night.RED else CouiColor.Day.RED
    Row(
        modifier =
            modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.container),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                // 图标容器与图标同尺寸，角标按 TopEnd 对齐图标右上角
                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.title,
                        tint = tint.copy(alpha = if (selected) 1f else 0.6f),
                        modifier = Modifier.size(24.dp),
                    )
                    if (badgeVisible(index)) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(8.dp)
                                    .background(badgeColor, CouiShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}
