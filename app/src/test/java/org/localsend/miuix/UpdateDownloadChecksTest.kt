package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.updates.downloadedSizeMatches

/** 实际字节数校验：远端给了 size 就必须下满；size 为 0 表示未知，不能拿来拦下载。 */
class UpdateDownloadChecksTest {
    @Test
    fun `known size must match exactly`() {
        assertTrue(downloadedSizeMatches(expected = 1024L, actual = 1024L))
        assertFalse(downloadedSizeMatches(expected = 1024L, actual = 1023L))
        assertFalse(downloadedSizeMatches(expected = 1024L, actual = 1025L))
    }

    @Test
    fun `unknown size never fails the download`() {
        assertTrue(downloadedSizeMatches(expected = 0L, actual = 123L))
        assertTrue(downloadedSizeMatches(expected = -1L, actual = 0L))
    }
}
