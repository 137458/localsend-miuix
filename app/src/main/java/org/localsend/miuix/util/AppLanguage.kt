package org.localsend.miuix.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言切换：无 appcompat，纯函数推导语言标签，并在 attachBaseContext 阶段包装 Context。
 * 该阶段不能依赖任何已初始化的 manager，只能直接读 SharedPreferences。
 */
object AppLanguage {
    /** AppSettings.language 的取值：跟随系统。 */
    const val SETTING_SYSTEM = "system"

    /** AppSettings.language 的取值：简体中文。 */
    const val SETTING_ZH = "zh"

    /** AppSettings.language 的取值：英文。 */
    const val SETTING_EN = "en"

    /** "zh"→"zh"，"en"→"en"，其他（含 "system"）→null：跟随系统时不包装 locale。 */
    fun tagFor(setting: String): String? =
        when (setting) {
            SETTING_ZH -> SETTING_ZH
            SETTING_EN -> SETTING_EN
            else -> null
        }

    /** [tag] 非 null 时把对应 LocaleList 写入 [configuration]；null 时保持不变。 */
    fun applyTo(
        configuration: Configuration,
        tag: String?,
    ) {
        if (tag == null) return
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
    }

    /** 返回带目标语言的 context；无需切换时原样返回。 */
    fun wrap(
        context: Context,
        setting: String,
    ): Context {
        val tag = tagFor(setting) ?: return context
        val configuration = Configuration(context.resources.configuration)
        applyTo(configuration, tag)
        return context.createConfigurationContext(configuration)
    }
}
