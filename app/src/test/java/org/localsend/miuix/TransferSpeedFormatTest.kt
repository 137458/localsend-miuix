package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.model.FileItem

class TransferSpeedFormatTest {

    @Test
    fun testFormatSpeed_full() {
        assertEquals("0 B/s", FileItem.formatSpeed(0L, compact = false))
        assertEquals("500 B/s", FileItem.formatSpeed(500L, compact = false))
        assertEquals("1.0 KB/s", FileItem.formatSpeed(1024L, compact = false))
        assertEquals("1.5 KB/s", FileItem.formatSpeed(1536L, compact = false))
        assertEquals("1.0 MB/s", FileItem.formatSpeed(1024L * 1024L, compact = false))
        assertEquals("2.5 MB/s", FileItem.formatSpeed((2.5 * 1024 * 1024).toLong(), compact = false))
        assertEquals("25.0 MB/s", FileItem.formatSpeed(25L * 1024 * 1024, compact = false))
    }

    @Test
    fun testFormatSpeed_compact() {
        assertEquals("0 B/s", FileItem.formatSpeed(0L, compact = true))
        assertEquals("500 B/s", FileItem.formatSpeed(500L, compact = true))
        assertEquals("1K/s", FileItem.formatSpeed(1024L, compact = true))
        assertEquals("1K/s", FileItem.formatSpeed(1536L, compact = true))
        assertEquals("1M/s", FileItem.formatSpeed(1024L * 1024L, compact = true))
        assertEquals("2.5M/s", FileItem.formatSpeed((2.5 * 1024 * 1024).toLong(), compact = true))
        assertEquals("25M/s", FileItem.formatSpeed(25L * 1024 * 1024, compact = true))
    }
}
