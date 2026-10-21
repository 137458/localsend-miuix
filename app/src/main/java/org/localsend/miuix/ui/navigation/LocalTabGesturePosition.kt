package org.localsend.miuix.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * 主页 Pager 手势翻页期间的连续标签位置（currentPage + currentPageOffsetFraction）；
 * 非手势滚动时为 null。底栏滑块在 draw 阶段读取：非 null 时直驱滑块实现零延迟跟手，
 * 点击切换仍走 DampedDragAnimation 的 spring 动画，两者互不打断。
 */
val LocalTabGesturePosition = compositionLocalOf { mutableStateOf<Float?>(null) }
