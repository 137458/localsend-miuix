package org.localsend.miuix

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.ui.effect.isDarkSurface

class ThemeSurfaceDarknessTest {
    @Test
    fun darkSurfaceIsReportedAsDark() {
        assertTrue("近黑表面属于深色主题", isDarkSurface(Color(0xFF141414)))
        assertTrue("带色调的深色表面仍属深色主题", isDarkSurface(Color(0xFF1B1B1F)))
    }

    @Test
    fun lightSurfaceIsReportedAsLight() {
        assertFalse("近白表面属于浅色主题", isDarkSurface(Color(0xFFFFFBFE)))
        assertFalse("带色调的浅灰表面仍属浅色主题", isDarkSurface(Color(0xFFF7F7F7)))
    }
}
