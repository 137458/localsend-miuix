package org.localsend.miuix

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.manager.loadSettings
import org.localsend.miuix.manager.persistSettingsTo
import org.localsend.miuix.model.AppSettings

class SettingsMappingTest {
    private class FakeEditor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = apply { values[key] = value }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = unsupported()

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = apply { values[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = apply { values[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = apply { values[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = apply { values[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { values.remove(key) }

        override fun clear(): SharedPreferences.Editor = apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() {}

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not needed in this test")
    }

    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, Any?> = values

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(values)

        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun loadSettingsUsesSpecifiedDefaultsForNewKeys() {
        val settings = loadSettings(FakeSharedPreferences(), alias = "Alias", defaultDownloadPath = "/tmp")
        assertEquals("system", settings.language)
        assertEquals(false, settings.quickSaveFromFavorites)
        assertEquals(false, settings.webShareAutoAccept)
        assertEquals(false, settings.autoFinish)
        assertEquals(0, settings.themeColorIndex)
        assertEquals("", settings.networkWhitelist)
        assertEquals("", settings.networkBlacklist)
        assertEquals(5, settings.discoveryTimeout)
        assertEquals("224.0.0.167", settings.multicastGroup)
    }

    @Test
    fun persistSettingsRoundTripsNewKeys() {
        val prefs = FakeSharedPreferences()
        val settings =
            AppSettings(
                language = "en",
                quickSaveFromFavorites = true,
                webShareAutoAccept = true,
                autoFinish = true,
                themeColorIndex = 4,
                networkWhitelist = "wlan0,192.168.",
                networkBlacklist = "eth1",
                discoveryTimeout = 12,
                multicastGroup = "ff02::1",
            )
        persistSettingsTo(prefs, settings)
        val loaded = loadSettings(prefs, alias = settings.alias, defaultDownloadPath = "/tmp")
        assertEquals("en", loaded.language)
        assertEquals(true, loaded.quickSaveFromFavorites)
        assertEquals(true, loaded.webShareAutoAccept)
        assertEquals(true, loaded.autoFinish)
        assertEquals(4, loaded.themeColorIndex)
        assertEquals("wlan0,192.168.", loaded.networkWhitelist)
        assertEquals("eth1", loaded.networkBlacklist)
        assertEquals(12, loaded.discoveryTimeout)
        assertEquals("ff02::1", loaded.multicastGroup)
    }
}
