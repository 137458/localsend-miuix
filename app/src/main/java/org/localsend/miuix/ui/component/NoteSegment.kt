package org.localsend.miuix.ui.component

/** Release 说明的行内片段，解析与 Compose 渲染分离，便于单测覆盖。 */
internal sealed interface NoteSegment {
    data class Plain(
        val text: String,
    ) : NoteSegment

    data class Bold(
        val text: String,
    ) : NoteSegment

    data class Code(
        val text: String,
    ) : NoteSegment

    data class Link(
        val label: String,
        val url: String,
    ) : NoteSegment
}

internal fun parseNoteInline(text: String): List<NoteSegment> {
    if (text.isEmpty()) return emptyList()

    val segments = mutableListOf<NoteSegment>()
    val plain = StringBuilder()
    var index = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            segments.add(NoteSegment.Plain(plain.toString()))
            plain.clear()
        }
    }

    while (index < text.length) {
        if (text[index] == '[') {
            val closingBracket = text.indexOf(']', index + 1)
            val hasTarget = closingBracket > index + 1 && closingBracket + 1 < text.length && text[closingBracket + 1] == '('
            val closingParen = if (hasTarget) text.indexOf(')', closingBracket + 2) else -1
            if (closingBracket > index + 1 && closingParen > closingBracket + 2) {
                val url = text.substring(closingBracket + 2, closingParen).trim()
                if (url.isNotEmpty()) {
                    flushPlain()
                    segments.add(NoteSegment.Link(label = text.substring(index + 1, closingBracket), url = url))
                    index = closingParen + 1
                    continue
                }
            }
        }

        if (text.startsWith("**", index)) {
            val closing = text.indexOf("**", index + 2)
            if (closing > index + 2) {
                flushPlain()
                segments.add(NoteSegment.Bold(text.substring(index + 2, closing)))
                index = closing + 2
                continue
            }
            plain.append("**")
            index += 2
            continue
        }

        if (text[index] == '`') {
            val closing = text.indexOf('`', index + 1)
            if (closing > index + 1) {
                flushPlain()
                segments.add(NoteSegment.Code(text.substring(index + 1, closing)))
                index = closing + 1
                continue
            }
            plain.append('`')
            index++
            continue
        }

        plain.append(text[index])
        index++
    }

    flushPlain()
    return segments
}
