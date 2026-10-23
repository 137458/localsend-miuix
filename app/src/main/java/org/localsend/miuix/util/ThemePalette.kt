package org.localsend.miuix.util

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 固定主题色板：种子色 + 主色系配色。
 * seed 用于 Monet 模式（ThemeController.keyColor），浅/深色配色用于 System/Light/Dark 模式。
 */
data class ThemePalette(
    val seed: Color,
    val lightColors: Colors,
    val darkColors: Colors,
)

/** 主色系六槽位；其余颜色槽沿用官方默认 scheme。 */
private data class PrimaryFamily(
    val primary: Color,
    val onPrimary: Color,
    val primaryVariant: Color,
    val onPrimaryVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

// 与设置页主题色下拉顺序一一对应（theme_color_dynamic 之后的 theme_color_blue..red）
private val paletteSeeds =
    listOf(
        Color(0xFF2196F3), // 蓝
        Color(0xFF9C27B0), // 紫
        Color(0xFF00BCD4), // 青
        Color(0xFF4CAF50), // 绿
        Color(0xFFFF9800), // 橙
        Color(0xFFF44336), // 红
    )

// 各种子色的浅色主色系；值由 Miuix 取色管线（colorsFromSeed，Spec2021/TonalSpot，isDark=false）离线派生
private val lightFamilies =
    listOf(
        PrimaryFamily(Color(0xFF36618E), Color(0xFFFFFFFF), Color(0xFFD1E4FF), Color(0xFF001D36), Color(0xFFD1E4FF), Color(0xFF194975)), // 蓝
        PrimaryFamily(Color(0xFF7B4E7F), Color(0xFFFFFFFF), Color(0xFFFFD6FE), Color(0xFF310937), Color(0xFFFFD6FE), Color(0xFF613766)), // 紫
        PrimaryFamily(Color(0xFF006876), Color(0xFFFFFFFF), Color(0xFFA1EFFF), Color(0xFF001F25), Color(0xFFA1EFFF), Color(0xFF004E59)), // 青
        PrimaryFamily(Color(0xFF3B6939), Color(0xFFFFFFFF), Color(0xFFBCF0B4), Color(0xFF002204), Color(0xFFBCF0B4), Color(0xFF235024)), // 绿
        PrimaryFamily(Color(0xFF855318), Color(0xFFFFFFFF), Color(0xFFFFDCBE), Color(0xFF2C1600), Color(0xFFFFDCBE), Color(0xFF693C00)), // 橙
        PrimaryFamily(Color(0xFF904A42), Color(0xFFFFFFFF), Color(0xFFFFDAD5), Color(0xFF3B0906), Color(0xFFFFDAD5), Color(0xFF73342C)), // 红
    )

// 各种子色的深色主色系；同上，isDark=true
private val darkFamilies =
    listOf(
        PrimaryFamily(Color(0xFFA0CAFD), Color(0xFF003258), Color(0xFFD1E4FF), Color(0xFF001D36), Color(0xFF194975), Color(0xFFD1E4FF)), // 蓝
        PrimaryFamily(Color(0xFFEBB5ED), Color(0xFF49204E), Color(0xFFFFD6FE), Color(0xFF310937), Color(0xFF613766), Color(0xFFFFD6FE)), // 紫
        PrimaryFamily(Color(0xFF83D3E3), Color(0xFF00363E), Color(0xFFA1EFFF), Color(0xFF001F25), Color(0xFF004E59), Color(0xFFA1EFFF)), // 青
        PrimaryFamily(Color(0xFFA1D39A), Color(0xFF0A390F), Color(0xFFBCF0B4), Color(0xFF002204), Color(0xFF235024), Color(0xFFBCF0B4)), // 绿
        PrimaryFamily(Color(0xFFFDB975), Color(0xFF4A2800), Color(0xFFFFDCBE), Color(0xFF2C1600), Color(0xFF693C00), Color(0xFFFFDCBE)), // 橙
        PrimaryFamily(Color(0xFFFFB4A9), Color(0xFF561E18), Color(0xFFFFDAD5), Color(0xFF3B0906), Color(0xFF73342C), Color(0xFFFFDAD5)), // 红
    )

/**
 * 主题色下拉索引 -> 固定色板。
 * 0 表示动态取色，返回 null；越界值先收敛到 [0, paletteSeeds.size] 再解析。
 */
fun themePaletteForIndex(index: Int): ThemePalette? {
    val seedIndex = index.coerceIn(0, paletteSeeds.size)
    if (seedIndex == 0) return null
    val seed = paletteSeeds[seedIndex - 1]
    val light = lightFamilies[seedIndex - 1]
    val dark = darkFamilies[seedIndex - 1]
    return ThemePalette(
        seed = seed,
        lightColors =
            lightColorScheme(
                primary = light.primary,
                onPrimary = light.onPrimary,
                primaryVariant = light.primaryVariant,
                onPrimaryVariant = light.onPrimaryVariant,
                primaryContainer = light.primaryContainer,
                onPrimaryContainer = light.onPrimaryContainer,
            ),
        darkColors =
            darkColorScheme(
                primary = dark.primary,
                onPrimary = dark.onPrimary,
                primaryVariant = dark.primaryVariant,
                onPrimaryVariant = dark.onPrimaryVariant,
                primaryContainer = dark.primaryContainer,
                onPrimaryContainer = dark.onPrimaryContainer,
            ),
    )
}
