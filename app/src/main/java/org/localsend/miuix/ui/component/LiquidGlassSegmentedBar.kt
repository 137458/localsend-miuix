package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶部液态玻璃分段胶囊条：复用 [LiquidGlassBottomBar] 的三层光学折射与阻尼滑块管线，
 * 调整为顶栏紧凑比例并隔离主 Pager 滑动手势。
 */
@Composable
fun LiquidGlassSegmentedBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    colors: FloatingBottomBarColors =
        FloatingBottomBarDefaults.colors(
            activeContentColor = MiuixTheme.colorScheme.onBackground,
        ),
) {
    if (tabs.isEmpty()) return

    val hapticFeedback = LocalHapticFeedback.current
    val onSelectedUpdated by rememberUpdatedState(onSelected)
    val directSelector = remember { FloatingBottomBarDirectSelector() }

    LiquidGlassBottomBar(
        selectedIndex = { selectedIndex },
        onSelected = { index ->
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onSelectedUpdated(index)
        },
        backdrop = backdrop,
        tabsCount = tabs.size,
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        directSelector = directSelector,
        barHeight = 44.dp,
        indicatorHeight = 36.dp,
        baseRefraction = 16.dp,
        indicatorPressedScale = 42f / 36f,
        followTabGesture = false,
        fillMaxWidth = true,
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            FloatingBottomBarItem(
                onClick = {
                    directSelector(index)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectedUpdated(index)
                },
                modifier = Modifier.semantics { selected = isSelected },
                minWidth = 0.dp,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
