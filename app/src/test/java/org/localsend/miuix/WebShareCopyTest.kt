package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.webshare.WebShareCopy

class WebShareCopyTest {

    @Test
    fun englishBrowserGetsEnglishPageCopy() {
        val copy = WebShareCopy.fromAcceptLanguage("en-US,en;q=0.9,zh;q=0.8")
        assertEquals("en", copy.htmlLang)
        assertEquals("Download", copy.download)
        assertEquals("Click to choose files or drop them here", copy.dropzoneText)
        assertTrue(copy.pageTitle("Pixel").contains("LocalSend LAN Transfer"))
        assertFalse(copy.dropzoneText.contains("拖拽"))
    }

    @Test
    fun chineseOrMissingHeaderKeepsChinesePageCopy() {
        assertEquals("zh-CN", WebShareCopy.fromAcceptLanguage(null).htmlLang)
        assertEquals("zh-CN", WebShareCopy.fromAcceptLanguage("").htmlLang)
        val copy = WebShareCopy.fromAcceptLanguage("zh-CN,zh;q=0.9,en;q=0.8")
        assertEquals("下载", copy.download)
        assertTrue(copy.dropzoneText.contains("拖拽"))
    }

    @Test
    fun qualityValuesPreferHigherLanguage() {
        assertTrue(WebShareCopy.prefersEnglish("en;q=0.8,zh;q=0.4"))
        assertFalse(WebShareCopy.prefersEnglish("zh;q=0.9,en;q=0.3"))
    }

    @Test
    fun browserModelUsesCopyLanguage() {
        assertEquals(
            "Mac 浏览器",
            WebShareCopy.browserModel("Mozilla/5.0 (Macintosh; Intel Mac OS X)", WebShareCopy.Chinese)
        )
        assertEquals(
            "Mac browser",
            WebShareCopy.browserModel("Mozilla/5.0 (Macintosh; Intel Mac OS X)", WebShareCopy.English)
        )
    }

    @Test
    fun jsPayloadEscapesQuotesAndMatchesLanguage() {
        val en = WebShareCopy.English.toJsObject()
        assertTrue(en.contains("\"download\"").not())
        assertTrue(en.contains("\"copied\":\"Copied to clipboard\""))
        assertFalse(en.contains("已复制"))
        val zh = WebShareCopy.Chinese.toJsObject()
        assertTrue(zh.contains("\"copied\":\"已复制到剪贴板\""))
    }
}
