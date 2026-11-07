package org.localsend.miuix.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 界面时间戳排版：应用内语言是在 attachBaseContext 阶段包装 Configuration 实现的，
 * Locale.getDefault() 仍是系统语言，两者可能不一致；时间戳数字系统须跟界面语言走。
 */
object UiTimeFormat {
    /** 取配置 locale 列表的首项；为空时回退系统默认。 */
    fun firstLocaleOr(
        locales: List<Locale>,
        fallback: Locale,
    ): Locale = locales.firstOrNull() ?: fallback

    /** 按 [locale] 与 [pattern] 排版 [timestampMillis]。 */
    fun format(
        pattern: String,
        timestampMillis: Long,
        locale: Locale,
    ): String = SimpleDateFormat(pattern, locale).format(Date(timestampMillis))
}

/** 组合期取当前界面配置的 locale 排版时间戳；配置变化（含应用内切语言）时自动重排。 */
@Composable
fun rememberUiTimeText(
    pattern: String,
    timestampMillis: Long,
): String {
    val configuration = LocalConfiguration.current
    return remember(configuration, timestampMillis, pattern) {
        val localeList = configuration.locales
        val locales = (0 until localeList.size()).map { localeList.get(it) }
        UiTimeFormat.format(pattern, timestampMillis, UiTimeFormat.firstLocaleOr(locales, Locale.getDefault()))
    }
}
