package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.localsend.miuix.ui.util.UiTimeFormat
import java.util.Locale
import java.util.TimeZone

class UiTimeFormatTest {
    // 2026-11-08T14:30:00+08:00 固定时间戳
    private val timestamp = 1_794_119_400_000L

    private fun <T> withUtcPlus8(block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            return block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun formatUsesSuppliedLocaleDigits() {
        val en = UiTimeFormat.format("MM-dd HH:mm", timestamp, Locale.ENGLISH)
        val ar = UiTimeFormat.format("MM-dd HH:mm", timestamp, Locale("ar"))
        assertNotEquals("阿拉伯语 locale 的数字系统不同，排版必须不同", en, ar)
    }

    @Test
    fun formatRendersExpectedFields() =
        withUtcPlus8 {
            assertEquals("11-08 14:30", UiTimeFormat.format("MM-dd HH:mm", timestamp, Locale.ENGLISH))
            assertEquals("2026-11-08 14:30:00", UiTimeFormat.format("yyyy-MM-dd HH:mm:ss", timestamp, Locale.ENGLISH))
        }

    @Test
    fun firstLocaleFallsBackToDefaultWhenMissing() {
        val fallback = Locale.JAPAN
        assertEquals(Locale.KOREA, UiTimeFormat.firstLocaleOr(listOf(Locale.KOREA), fallback))
        assertEquals(fallback, UiTimeFormat.firstLocaleOr(emptyList(), fallback))
    }
}
