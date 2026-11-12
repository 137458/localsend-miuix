package org.localsend.miuix.ui.coui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.CouiTypography
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.itos.coui.theme.CouiTheme as CouiKitTheme

/**
 * ColorOS 色板：把 Miuix 皮肤色映射到 coUI-Kit 的 [CouiColorScheme]。
 *
 * 颜色真源仍只有 [Colors] 一处，切深浅或切动态取色时两套皮肤读同一份取值：
 * - [CouiColorScheme.background] 对齐 Miuix Scaffold 真实页面底色 `colors.surface`（浅 `#F7F7F7` / 深 `#000000`）；
 * - [CouiColorScheme.card] 与 `surface` 对齐 `colors.surfaceContainer`（浅 `#FFFFFF` / 深 `#242424`），在暗黑模式下与 `#000000` 页面形成清晰层级；
 * - [CouiColorScheme.container] 用于卡内嵌套容器（暗色 `#2D2D2D`、`surfaceContainerHighest`；浅色微调灰底），与卡片表面拉开层次。
 */
internal fun couiColorSchemeOf(
    colors: Colors,
    isDark: Boolean,
): CouiColorScheme =
    CouiColorScheme(
        accent = colors.primary,
        onAccent = colors.onPrimary,
        surface = colors.surfaceContainer,
        container =
            if (isDark) {
                colors.surfaceContainerHighest
            } else {
                lerp(colors.surface, colors.surfaceContainerHigh, 0.55f)
            },
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onSurfaceVariantSummary,
        outlineVariant = if (isDark) colors.dividerLine else colors.dividerLine.copy(alpha = 0.7f),
        accentOnSurface = if (isDark) colors.onPrimaryVariant else colors.primary,
        menuSurface = colors.surfaceContainer,
        checkboxAccent = colors.primary,
        menuCheckboxAccent = if (isDark) colors.onPrimaryVariant else colors.primary,
        background = colors.surface,
        card = colors.surfaceContainer,
    )

/**
 * ColorOS 皮肤统一的危险/错误色：coUI 的 [CouiColorScheme] 只有 13 个基础角色而不含 error 槽，
 * 因此在皮肤内统一取用库的固定红 [CouiColor.Day.RED] / [CouiColor.Night.RED]，避免混用 Miuix error。
 */
@Composable
internal fun couiDangerColor(isDark: Boolean = CouiTheme.isDark): Color = if (isDark) CouiColor.Night.RED else CouiColor.Day.RED

/** ColorOS 皮肤统一的在线/就绪强调色。 */
@Composable
internal fun couiSuccessColor(isDark: Boolean = CouiTheme.isDark): Color = if (isDark) CouiColor.Night.GREEN else CouiColor.Day.GREEN

/**
 * ColorOS 皮肤的主题入口：色板与字阶由 [MiuixTheme] 派生，再交给 coUI-Kit 的 CouiTheme 下发。
 *
 * coUI-Kit 的 `CouiTheme` 不会从 [CouiColorScheme.background] 自动反推深浅模式，因此这里显式
 * 从宿主 `surface` 亮度推导 `darkTheme` 并与色板一起传入；只在色板或字阶变化时重建对象，
 * 避免每帧重新构造导致下游整树重组。
 */
@Composable
fun CouiSkinTheme(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val dark = colors.surface.luminance() < 0.5f
    val colorScheme = remember(colors, dark) { couiColorSchemeOf(colors, dark) }
    val typography =
        remember(textStyles) {
            CouiTypography(
                body = textStyles.body1,
                label = textStyles.footnote1,
                menuItem = textStyles.body2,
                chipLabelFontFamily = textStyles.body2.fontFamily,
                dialogTitleFontFamily = textStyles.title4.fontFamily,
                dialogMessageFontFamily = textStyles.body2.fontFamily,
                dialogButtonFontFamily = textStyles.body1.fontFamily,
                dialogInputFontFamily = textStyles.body1.fontFamily,
                dialogCounterFontFamily = textStyles.footnote1.fontFamily,
            )
        }
    CouiKitTheme(
        darkTheme = dark,
        colorScheme = colorScheme,
        typography = typography,
        materialTint = Color.Transparent,
        content = content,
    )
}
