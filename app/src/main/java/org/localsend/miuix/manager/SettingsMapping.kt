package org.localsend.miuix.manager

import android.content.SharedPreferences
import org.localsend.miuix.core.PreferenceKeys
import org.localsend.miuix.model.AppSettings
import org.localsend.miuix.model.DeviceType

/** 从持久化恢复设置；别名首次生成后即固化，避免冷启动每次都随机更换。 */
internal fun resolveInitialAlias(prefs: SharedPreferences): String =
    prefs.getString(PreferenceKeys.KEY_ALIAS, null) ?: run {
        val generated = AppSettings.generateDefaultAlias()
        prefs.edit().putString(PreferenceKeys.KEY_ALIAS, generated).apply()
        generated
    }

/** 将持久化配置映射为 [AppSettings]。 */
internal fun loadSettings(
    prefs: SharedPreferences,
    alias: String,
    defaultDownloadPath: String,
): AppSettings =
    AppSettings(
        alias = alias,
        port = prefs.getInt(PreferenceKeys.KEY_PORT, 53317),
        quickSave = prefs.getBoolean(PreferenceKeys.KEY_QUICK_SAVE, false),
        autoCopyText = prefs.getBoolean(PreferenceKeys.KEY_AUTO_COPY_TEXT, false),
        saveToHistory = prefs.getBoolean(PreferenceKeys.KEY_SAVE_TO_HISTORY, true),
        useHttps = prefs.getBoolean(PreferenceKeys.KEY_USE_HTTPS, false),
        deviceType = DeviceType.fromString(prefs.getString(PreferenceKeys.KEY_DEVICE_TYPE, DeviceType.mobile.value)),
        download = prefs.getBoolean(PreferenceKeys.KEY_DOWNLOAD, false),
        pin = prefs.getString(PreferenceKeys.KEY_PIN, null),
        themeModeIndex = prefs.getInt(PreferenceKeys.KEY_THEME, 0),
        downloadTreeUri = prefs.getString(PreferenceKeys.KEY_TREE_URI, null),
        downloadDisplay = prefs.getString(PreferenceKeys.KEY_DOWNLOAD_DISPLAY, null),
        downloadPath = defaultDownloadPath,
        vibrateOnComplete = prefs.getBoolean(PreferenceKeys.KEY_VIBRATE, true),
        lastSelectedTabIndex = prefs.getInt(PreferenceKeys.KEY_LAST_TAB, 0),
        autoCheckUpdate = prefs.getBoolean(PreferenceKeys.KEY_AUTO_CHECK_UPDATE, true),
        ignoredVersion = prefs.getString(PreferenceKeys.KEY_IGNORED_VERSION, null),
        promptedUpdateVersion = prefs.getString(PreferenceKeys.KEY_PROMPTED_UPDATE_VERSION, null),
        isOs3Effect = prefs.getBoolean(PreferenceKeys.KEY_IS_OS3_EFFECT, true),
        wideScreenNavigationRail = prefs.getBoolean(PreferenceKeys.KEY_WIDE_SCREEN_NAVIGATION_RAIL, false),
        saveTextAsFile = prefs.getBoolean(PreferenceKeys.KEY_SAVE_TEXT_AS_FILE, false),
        autoCategorizeMedia = prefs.getBoolean(PreferenceKeys.KEY_AUTO_CATEGORIZE_MEDIA, false),
    )

/** 将 [AppSettings] 写回持久化配置。 */
internal fun persistSettingsTo(
    prefs: SharedPreferences,
    s: AppSettings,
) {
    prefs
        .edit()
        .putString(PreferenceKeys.KEY_ALIAS, s.alias)
        .putInt(PreferenceKeys.KEY_PORT, s.port)
        .putString(PreferenceKeys.KEY_TREE_URI, s.downloadTreeUri)
        .putString(PreferenceKeys.KEY_DOWNLOAD_DISPLAY, s.downloadDisplay)
        .putBoolean(PreferenceKeys.KEY_QUICK_SAVE, s.quickSave)
        .putBoolean(PreferenceKeys.KEY_AUTO_COPY_TEXT, s.autoCopyText)
        .putBoolean(PreferenceKeys.KEY_SAVE_TO_HISTORY, s.saveToHistory)
        .putBoolean(PreferenceKeys.KEY_USE_HTTPS, s.useHttps)
        .putString(PreferenceKeys.KEY_DEVICE_TYPE, s.deviceType.value)
        .putBoolean(PreferenceKeys.KEY_DOWNLOAD, s.download)
        .putString(PreferenceKeys.KEY_PIN, s.pin)
        .putInt(PreferenceKeys.KEY_THEME, s.themeModeIndex)
        .putBoolean(PreferenceKeys.KEY_VIBRATE, s.vibrateOnComplete)
        .putInt(PreferenceKeys.KEY_LAST_TAB, s.lastSelectedTabIndex)
        .putBoolean(PreferenceKeys.KEY_AUTO_CHECK_UPDATE, s.autoCheckUpdate)
        .putString(PreferenceKeys.KEY_IGNORED_VERSION, s.ignoredVersion)
        .putString(PreferenceKeys.KEY_PROMPTED_UPDATE_VERSION, s.promptedUpdateVersion)
        .putBoolean(PreferenceKeys.KEY_IS_OS3_EFFECT, s.isOs3Effect)
        .putBoolean(PreferenceKeys.KEY_WIDE_SCREEN_NAVIGATION_RAIL, s.wideScreenNavigationRail)
        .putBoolean(PreferenceKeys.KEY_SAVE_TEXT_AS_FILE, s.saveTextAsFile)
        .putBoolean(PreferenceKeys.KEY_AUTO_CATEGORIZE_MEDIA, s.autoCategorizeMedia)
        .apply()
}
