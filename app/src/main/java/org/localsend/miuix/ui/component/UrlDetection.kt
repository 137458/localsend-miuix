package org.localsend.miuix.ui.component

import android.util.Patterns

/**
 * 从文本消息中提取第一个可打开的链接。
 *
 * 有明确 scheme 且不含空白时直接返回整串；否则用系统 WEB_URL 匹配，匹配结果缺 scheme 时补 https://。
 */
internal fun detectWebUrlInText(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
    if (hasScheme && !trimmed.contains(' ') && !trimmed.contains('\n')) return trimmed

    val matcher = Patterns.WEB_URL.matcher(trimmed)
    if (!matcher.find()) return null
    val found = matcher.group() ?: return null
    return if (found.startsWith("http://", ignoreCase = true) || found.startsWith("https://", ignoreCase = true)) {
        found
    } else {
        "https://$found"
    }
}
