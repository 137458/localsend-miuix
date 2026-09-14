package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.AppSettings

class AppSettingsTest {

    @Test
    fun testDefaultSettingsIncludeUpdateAndEffectFlags() {
        val settings = AppSettings()
        assertTrue(settings.autoCheckUpdate)
        assertNull(settings.ignoredVersion)
        assertTrue(settings.isOs3Effect)
        assertEquals(false, settings.wideScreenNavigationRail)
    }

    @Test
    fun testUpdateSettingsModifications() {
        val settings = AppSettings()
        val updated = settings.copy(
            autoCheckUpdate = false,
            ignoredVersion = "v1.2.3",
            isOs3Effect = false,
            wideScreenNavigationRail = true
        )
        assertEquals(false, updated.autoCheckUpdate)
        assertEquals("v1.2.3", updated.ignoredVersion)
        assertEquals(false, updated.isOs3Effect)
        assertEquals(true, updated.wideScreenNavigationRail)
    }
}
