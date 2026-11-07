package org.localsend.miuix.ui.effect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 界面实际使用的表面色是否属于深色主题。
 *
 * 应用内主题可强制浅色/深色/Monet，和系统明暗无关；流光背景的配色预设必须跟随渲染出的主题，
 * 否则强制深色 + 系统浅色时会用浅色渐变铺在深色表面上。
 */
fun isDarkSurface(surface: Color): Boolean = surface.luminance() < 0.5f
