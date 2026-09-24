package org.localsend.miuix.manager

import android.content.Context
import org.localsend.miuix.core.PreferenceKeys

/** 手动输入 IP 历史记录的持久化存储（最多保留最近 5 个不同 IP）。 */
internal class RecentIpStore(context: Context) {

    private val prefs = context.getSharedPreferences(PreferenceKeys.PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(PreferenceKeys.KEY_RECENT_MANUAL_IPS, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 将 [ip] 置顶去重后落盘，返回更新后的列表；空白 IP 不写入并原样返回 [current]。 */
    fun add(ip: String, current: List<String>): List<String> {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return current
        val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(5)
        prefs.edit().putString(PreferenceKeys.KEY_RECENT_MANUAL_IPS, updated.joinToString(",")).apply()
        return updated
    }
}