package org.localsend.miuix

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.util.themePaletteForIndex
import kotlin.math.abs

/** 主题色板索引映射：0=动态取色(null)，1..6=蓝/紫/青/绿/橙/红，越界收敛到边界。 */
class ThemePaletteTest {
    private fun hueOfArgb(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return 0.0
        val d = max - min
        val sector =
            when (max) {
                r -> ((g - b) / d + 6.0) % 6.0
                g -> (b - r) / d + 2.0
                else -> (r - g) / d + 4.0
            }
        return sector * 60.0
    }

    private fun luminanceOfArgb(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun hueDelta(
        a: Double,
        b: Double,
    ): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    @Test
    fun `dynamic color index resolves to null palette`() {
        assertNull(themePaletteForIndex(0))
        // 负值越界向下收敛到 0，仍走动态取色
        assertNull(themePaletteForIndex(-1))
        assertNull(themePaletteForIndex(Int.MIN_VALUE))
    }

    @Test
    fun `out of range index clamps to highest palette`() {
        val highest = themePaletteForIndex(6)
        assertNotNull(highest)
        assertEquals(highest!!.lightColors.primary, themePaletteForIndex(7)!!.lightColors.primary)
        assertEquals(highest.darkColors.surface, themePaletteForIndex(100)!!.darkColors.surface)
        assertEquals(highest.lightColors.primary, themePaletteForIndex(Int.MAX_VALUE)!!.lightColors.primary)
    }

    @Test
    fun `every palette index produces distinct color scheme`() {
        for (index in 1..6) {
            assertNotNull("index=$index", themePaletteForIndex(index))
        }
        val palettes = (1..6).map { index -> themePaletteForIndex(index)!! }
        assertEquals(6, palettes.map { it.lightColors.primary }.distinct().size)
        assertEquals(6, palettes.map { it.darkColors.primary }.distinct().size)
    }

    @Test
    fun `palette primary hue matches selected color family`() {
        val expectedHueBands: Map<Int, List<ClosedRange<Double>>> =
            mapOf(
                1 to listOf(180.0..235.0), // 蓝
                2 to listOf(260.0..320.0), // 紫
                3 to listOf(170.0..205.0), // 青
                4 to listOf(90.0..165.0), // 绿
                5 to listOf(20.0..60.0), // 橙
                6 to listOf(340.0..360.0, 0.0..20.0), // 红色环绕 0°
            )
        for ((index, bands) in expectedHueBands) {
            val palette = themePaletteForIndex(index)
            assertNotNull("index=$index", palette)
            val primaryHue = hueOfArgb(palette!!.lightColors.primary.toArgb())
            val inBand = bands.any { primaryHue in it }
            assertTrue("index=$index primaryHue=$primaryHue not in $bands", inBand)
        }
    }

    @Test
    fun `palette provides coherent light and dark schemes`() {
        for (index in 1..6) {
            val palette = themePaletteForIndex(index)
            assertNotNull("index=$index", palette)
            val lightHue = hueOfArgb(palette!!.lightColors.primary.toArgb())
            val darkHue = hueOfArgb(palette.darkColors.primary.toArgb())
            assertTrue(
                "index=$index light/dark primary hue drift $lightHue vs $darkHue",
                hueDelta(lightHue, darkHue) <= 20.0,
            )
            val lightSurface = luminanceOfArgb(palette.lightColors.surface.toArgb())
            val darkSurface = luminanceOfArgb(palette.darkColors.surface.toArgb())
            assertTrue("index=$index dark surface not darker: $darkSurface vs $lightSurface", darkSurface < lightSurface)
        }
    }
}
