package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.localsend.miuix.util.AppLanguage

class AppLanguageTest {
    @Test
    fun tagForMapsExplicitLanguageCodes() {
        assertEquals("zh", AppLanguage.tagFor("zh"))
        assertEquals("en", AppLanguage.tagFor("en"))
    }

    @Test
    fun tagForReturnsNullForSystemAndUnknownValues() {
        assertNull(AppLanguage.tagFor(AppLanguage.SETTING_SYSTEM))
        assertNull(AppLanguage.tagFor(""))
        assertNull(AppLanguage.tagFor("fr"))
        assertNull(AppLanguage.tagFor("ZH"))
        assertNull(AppLanguage.tagFor(" zh"))
    }

    @Test
    fun settingConstantsStayInSyncWithTagFor() {
        assertEquals(AppLanguage.SETTING_ZH, AppLanguage.tagFor(AppLanguage.SETTING_ZH))
        assertEquals(AppLanguage.SETTING_EN, AppLanguage.tagFor(AppLanguage.SETTING_EN))
        assertNull(AppLanguage.tagFor(AppLanguage.SETTING_SYSTEM))
    }
}
