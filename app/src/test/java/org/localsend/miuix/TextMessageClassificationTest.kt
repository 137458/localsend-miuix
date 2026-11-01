package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.model.FileDto
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.network.LocalSendServer

class TextMessageClassificationTest {
    @Test
    fun testExactInlineTextMessageMatches() {
        val message = "Hello from phone"
        val dto =
            FileDto(
                id = "1",
                fileName = "message.txt",
                size = message.toByteArray(Charsets.UTF_8).size.toLong(),
                fileType = "text/plain",
                preview = message,
            )
        assertTrue(LocalSendServer.isInlineTextMessage(dto))
    }

    @Test
    fun testTextFileWithTruncatedPreviewIsNotInlineMessage() {
        val preview = "First 2000 characters..."
        val dto =
            FileDto(
                id = "2",
                fileName = "book.txt",
                size = 50000L,
                fileType = "text/plain",
                preview = preview,
            )
        // 普通文本文件或长文本，preview 字节数小于文件 size，绝不能当成内联消息回 204，必须分配 token 上传
        assertFalse(LocalSendServer.isInlineTextMessage(dto))
    }

    @Test
    fun testEmptyOrNullPreviewIsNotInlineMessage() {
        val dto =
            FileDto(
                id = "3",
                fileName = "empty.txt",
                size = 0L,
                fileType = "text/plain",
                preview = null,
            )
        assertFalse(LocalSendServer.isInlineTextMessage(dto))
    }

    @Test
    fun testLegacyBareTextFileTypeStaysCompatible() {
        // 旧版客户端发送裸枚举名 "text"，协议规范要求 fileType 为 MIME，但仍需兼容以保证双向互通
        val message = "legacy text"
        val dto =
            FileDto(
                id = "5",
                fileName = "message.txt",
                size = message.toByteArray(Charsets.UTF_8).size.toLong(),
                fileType = "text",
                preview = message,
            )
        assertTrue(LocalSendServer.isInlineTextMessage(dto))
    }

    @Test
    fun testTextSubtypeMimeIsInlineMessage() {
        val message = "# title"
        val dto =
            FileDto(
                id = "6",
                fileName = "note.md",
                size = message.toByteArray(Charsets.UTF_8).size.toLong(),
                fileType = "text/markdown",
                preview = message,
            )
        assertTrue(LocalSendServer.isInlineTextMessage(dto))
    }

    @Test
    fun testSenderDtoIsRecognizedAsInlineMessageByReceiver() {
        // 端到端契约：本机 toDto() 产出的字段，必须能被本机接收端判定为内联文本消息（与官方 MIME 约定对齐）
        val message = "跨端互通的纯文本消息"
        val item =
            FileItem(
                id = "7",
                name = "text_1.txt",
                size = message.toByteArray(Charsets.UTF_8).size.toLong(),
                textContent = message,
                mimeType = "text/plain",
            )
        assertTrue(LocalSendServer.isInlineTextMessage(item.toDto()))
    }

    @Test
    fun testBinaryFileWithPreviewIsNotInlineMessage() {
        val dto =
            FileDto(
                id = "4",
                fileName = "photo.jpg",
                size = 10L,
                fileType = "image/jpeg",
                preview = "1234567890",
            )
        assertFalse(LocalSendServer.isInlineTextMessage(dto))
    }
}
