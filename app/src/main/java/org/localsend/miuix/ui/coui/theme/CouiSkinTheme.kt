package org.localsend.miuix.ui.coui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTypography
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.itos.coui.theme.CouiTheme as CouiKitTheme

/**
 * ColorOS 色板：把 Miuix 皮肤色映射到 coUI-Kit 的 [CouiColorScheme]。
 *
 * 颜色真源仍只有 [Colors] 一处，切深浅或切动态取色时两套皮肤读同一份取值。
 * [CouiColorScheme.background] 是页面底色，[CouiColorScheme.surface]/[CouiColorScheme.container]
 * 是组件表面，[CouiColorScheme.card] 是分组列表卡片；materialTint 保持透明，
 * 让浮动栏与弹窗直接采样页面真实颜色，不再叠一层宿主染色。
 */
internal fun couiColorSchemeOf(
    colors: Colors,
    isDark: Boolean,
): CouiColorScheme =
    CouiColorScheme(
        accent = colors.primary,
        onAccent = colors.onPrimary,
        surface = colors.surface,
        container = colors.surfaceContainer,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onBackgroundVariant,
        outlineVariant = colors.dividerLine,
        accentOnSurface = if (isDark) colors.onPrimaryVariant else colors.primary,
        menuSurface = colors.surfaceContainer,
        checkboxAccent = colors.primary,
        menuCheckboxAccent = if (isDark) colors.onPrimaryVariant else colors.primary,
        background = colors.background,
        card = if (isDark) colors.surfaceContainerHigh else colors.surface,
    )

/**
 * ColorOS 皮肤的主题入口：色板由 [MiuixTheme] 派生，再交给 coUI-Kit 的 CouiTheme 下发。
 *
 * 深浅标记由 coUI-Kit 自己从 [CouiColorScheme.background] 判断，这里只在色板变化时重建，
 * 避免每帧重新构造 13 色对象导致下游整树重组。
 */
@Composable
fun CouiSkinTheme(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val colorScheme = remember(colors, dark) { couiColorSchemeOf(colors, dark) }
    val typography = remember { CouiTypography() }
    CouiKitTheme(
        darkTheme = dark,
        colorScheme = colorScheme,
        typography = typography,
        materialTint = Color.Transparent,
        content = content,
    )
}
