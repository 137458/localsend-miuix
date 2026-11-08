package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.WebShareHtmlRenderer
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
    fun chineseHeaderKeepsChinesePageCopy() {
        val copy = WebShareCopy.fromAcceptLanguage("zh-CN,zh;q=0.9,en;q=0.8")
        assertEquals("zh-CN", copy.htmlLang)
        assertEquals("下载", copy.download)
        assertTrue(copy.dropzoneText.contains("拖拽"))
    }

    @Test
    fun nonChineseLanguagesAndMissingHeaderFallBackToEnglish() {
        assertEquals("en", WebShareCopy.fromAcceptLanguage(null).htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("   ").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("en-US,en;q=0.9").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("de-DE,de;q=0.9").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("fr-FR,fr;q=0.8").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("ja-JP,ja;q=0.9").htmlLang)
        assertEquals("en", WebShareCopy.fromAcceptLanguage("xx-YY").htmlLang)
        assertEquals("zh-CN", WebShareCopy.fromAcceptLanguage("zh-TW").htmlLang)
    }

    @Test
    fun qualityValuesPreferHigherLanguage() {
        assertFalse(WebShareCopy.prefersChinese("en;q=0.8,zh;q=0.4"))
        assertTrue(WebShareCopy.prefersChinese("zh;q=0.9,en;q=0.3"))
    }

    @Test
    fun browserModelUsesCopyLanguage() {
        assertEquals(
            "Mac 浏览器",
            WebShareCopy.browserModel("Mozilla/5.0 (Macintosh; Intel Mac OS X)", WebShareCopy.Chinese),
        )
        assertEquals(
            "Mac browser",
            WebShareCopy.browserModel("Mozilla/5.0 (Macintosh; Intel Mac OS X)", WebShareCopy.English),
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

    @Test
    fun blankOrNilHeaderIsNotChinese() {
        assertFalse(WebShareCopy.prefersChinese(null))
        assertFalse(WebShareCopy.prefersChinese(""))
        assertFalse(WebShareCopy.prefersChinese("   "))
    }

    @Test
    fun qualityWeightsWithTiePreferChinese() {
        assertFalse(WebShareCopy.prefersChinese("en-US,en;q=0.9"))
        assertFalse(WebShareCopy.prefersChinese("zh;q=0.5,en;q=0.9"))
        // 等权重时按中文处理，英文必须严格更高才选中
        assertTrue(WebShareCopy.prefersChinese("en,zh"))
    }

    @Test
    fun fromAcceptLanguageResolvesHtmlLang() {
        assertEquals("en", WebShareCopy.fromAcceptLanguage("en-GB").htmlLang)
        assertEquals("zh-CN", WebShareCopy.fromAcceptLanguage("zh-Hans-CN").htmlLang)
    }

    @Test
    fun browserModelMapsUserAgentFamilies() {
        val zh = WebShareCopy.Chinese
        assertEquals(zh.modelPc, WebShareCopy.browserModel("Mozilla/5.0 (Windows NT 10.0; Win64)", zh))
        assertEquals(zh.modelIphone, WebShareCopy.browserModel("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)", zh))
        assertEquals(zh.modelIpad, WebShareCopy.browserModel("Mozilla/5.0 (iPad; CPU OS 17_0)", zh))
        assertEquals(zh.modelAndroid, WebShareCopy.browserModel("Mozilla/5.0 (Linux; Android 14)", zh))
        assertEquals(zh.modelWeb, WebShareCopy.browserModel("Unknown agent", zh))

        val en = WebShareCopy.English
        assertEquals("PC browser", WebShareCopy.browserModel("Mozilla/5.0 (Windows NT 10.0)", en))
        assertEquals("Linux browser", WebShareCopy.browserModel("Mozilla/5.0 (X11; Linux x86_64)", en))
    }

    @Test
    fun titleAndLabelHelpersEmbedArguments() {
        val copy = WebShareCopy.Chinese
        assertEquals("Pixel - LocalSend 局域网快传", copy.pageTitle("Pixel"))
        assertEquals("3 条", copy.textCountLabel(3))
        assertEquals(0, copy.textCountLabel(0).indexOf("0"))
    }

    @Test
    fun jsMapExposesAllLocalizedChromeStrings() {
        val copy = WebShareCopy.Chinese
        assertEquals(39, copy.jsMap().size)
        assertEquals(copy.copied, copy.jsMap()["copied"])
        assertEquals(copy.pinPrompt, copy.jsMap()["pinPrompt"])
        assertEquals(copy.modelAndroid, copy.jsMap()["modelAndroid"])
        assertEquals(copy.sendTextTab, copy.jsMap()["sendTextTab"])
        assertEquals(copy.preview, copy.jsMap()["preview"])
        assertEquals(copy.cancel, copy.jsMap()["cancel"])
    }

    @Test
    fun enhancedWebShareCopyProvidesLocalizedText() {
        val zh = WebShareCopy.Chinese
        assertEquals("发送文本", zh.sendTextTab)
        assertEquals("上传文件", zh.sendFileTab)
        assertEquals("预览", zh.preview)
        assertEquals("取消", zh.cancel)

        val en = WebShareCopy.English
        assertEquals("Send Text", en.sendTextTab)
        assertEquals("Upload Files", en.sendFileTab)
        assertEquals("Preview", en.preview)
        assertEquals("Cancel", en.cancel)
    }

    @Test
    fun previewableFileTypesDistinguishMultimediaAndArchives() {
        assertTrue(WebShareHtmlRenderer.isPreviewable("image/png", "photo.png"))
        assertTrue(WebShareHtmlRenderer.isPreviewable("video/mp4", "clip.mp4"))
        assertTrue(WebShareHtmlRenderer.isPreviewable("audio/mpeg", "song.mp3"))
        assertTrue(WebShareHtmlRenderer.isPreviewable("text/plain", "notes.txt"))
        assertTrue(WebShareHtmlRenderer.isPreviewable("application/json", "config.json"))

        assertFalse(WebShareHtmlRenderer.isPreviewable("application/zip", "archive.zip"))
        assertFalse(WebShareHtmlRenderer.isPreviewable("application/vnd.android.package-archive", "app.apk"))
    }

    @Test
    fun toJsObjectEscapesQuotesBackslashesAndNewlines() {
        val copy =
            WebShareCopy.Chinese.copy(
                copied = "line1\nline2",
                copyFailed = "quote\"here",
                waitingConfirm = "back\\slash",
            )
        val js = copy.toJsObject()
        assertTrue(js.startsWith("{"))
        assertTrue(js.endsWith("}"))
        assertTrue(js.contains("\"copied\":\"line1\\nline2\""))
        assertTrue(js.contains("\"copyFailed\":\"quote\\\"here\""))
        assertTrue(js.contains("\"waitingConfirm\":\"back\\\\slash\""))
        // 原始值不受转义影响
        assertEquals("line1\nline2", copy.jsMap()["copied"])
    }

    @Test
    fun htmlRendererGeneratesInteractiveElements() {
        val session =
            org.localsend.miuix.model.ShareSession(
                sessionId = "s1",
                files =
                    listOf(
                        org.localsend.miuix.model.FileItem(
                            id = "f1",
                            name = "pic.png",
                            size = 1024,
                            mimeType = "image/png",
                            token = "t1",
                        ),
                    ),
            )
        val html = WebShareHtmlRenderer.buildWebShareHtml("DeviceA", session, WebShareCopy.Chinese)

        assertTrue(html.contains("previewModal"))
        assertTrue(html.contains("preview-btn"))
        assertTrue(html.contains("tabBtnFile"))
        assertTrue(html.contains("tabBtnText"))
        assertTrue(html.contains("sendTextInput"))
        assertTrue(html.contains("sendTextToPhone"))
        assertTrue(html.contains("updateBanner"))
        assertTrue(html.contains("cancelUpload"))
    }
}
