package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.ui.component.NoteSegment
import org.localsend.miuix.ui.component.parseNoteInline

/** Release 说明只识别链接、粗体、内联代码三种行内标记，未闭合标记按原样输出。 */
class NoteInlineParserTest {
    @Test
    fun `parses bold code and links in order`() {
        val segments = parseNoteInline("修 **闪退** 与 `sha256`，见 [说明](https://example.com/a)")
        assertEquals(
            listOf(
                NoteSegment.Plain("修 "),
                NoteSegment.Bold("闪退"),
                NoteSegment.Plain(" 与 "),
                NoteSegment.Code("sha256"),
                NoteSegment.Plain("，见 "),
                NoteSegment.Link(label = "说明", url = "https://example.com/a"),
            ),
            segments,
        )
    }

    @Test
    fun `keeps unterminated markers literal`() {
        assertEquals(listOf(NoteSegment.Plain("**未闭合")), parseNoteInline("**未闭合"))
        assertEquals(listOf(NoteSegment.Plain("`未闭合")), parseNoteInline("`未闭合"))
        assertEquals(listOf(NoteSegment.Plain("[没有地址]")), parseNoteInline("[没有地址]"))
        assertEquals(listOf(NoteSegment.Plain("[](url)")), parseNoteInline("[](url)"))
    }

    @Test
    fun `does not nest bold inside link labels`() {
        assertEquals(
            listOf(NoteSegment.Link(label = "**粗体**", url = "https://example.com")),
            parseNoteInline("[**粗体**](https://example.com)"),
        )
    }

    @Test
    fun `plain text stays a single segment`() {
        assertEquals(listOf(NoteSegment.Plain("普通说明")), parseNoteInline("普通说明"))
        assertEquals(emptyList<NoteSegment>(), parseNoteInline(""))
    }
}
