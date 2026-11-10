package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.model.MainTabs

class NavigationTabsTest {
    @Test
    fun mainTabsIndicesMatchHomeHistorySettingsOrder() {
        assertEquals(0, MainTabs.HOME)
        assertEquals(1, MainTabs.HISTORY)
        assertEquals(2, MainTabs.SETTINGS)
    }
}
