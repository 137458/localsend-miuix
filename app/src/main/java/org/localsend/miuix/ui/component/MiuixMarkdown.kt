package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专为 Miuix (HyperOS) 风格打造的原生 Markdown 渲染组件
 *
 * 支持特性：
 * 1. H1 ~ H4 多级标题排版（带层级字号与主色 Accent 指示标）
 * 2. 无序列表（• 悬挂缩进）与有序列表（1. 2. 3.）
 * 3. 行内富文本：**加粗**、*斜体*、~~删除线~~、`行内代码标签`、[超链接](url)
 * 4. 引用块（> Blockquote，带左侧 Accent 边框与柔和底色）
 * 5. 代码块（``` Code Block，等宽字体与圆角卡片）
 * 6. 分割线（---）
 * 7. 100% 遵循 MiuixTheme 主题色彩与字号体系
 */
@Composable
fun MiuixMarkdown(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        3 -> 15.sp
                        else -> 14.sp
                    }
                    val topPadding = if (block.level <= 2) 10.dp else 4.dp
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
                    ) {
                        // HyperOS 风格小色条指示器
                        Box(
                            modifier = Modifier
                                .size(width = 3.5.dp, height = if (block.level <= 2) 16.dp else 13.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MiuixInlineText(
                            text = block.content,
                            baseStyle = TextStyle(
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (block.isOrdered) 4.dp else 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (block.isOrdered) {
                            Text(
                                text = "${block.orderNumber}.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp, end = 8.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary)
                            )
                        }
                        MiuixInlineText(
                            text = block.content,
                            baseStyle = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.8f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MiuixInlineText(
                                text = block.content,
                                baseStyle = TextStyle(
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            )
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = block.content,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
                is MarkdownBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.3f))
                            .padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    MiuixInlineText(
                        text = block.content,
                        baseStyle = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

/**
 * 行内 Markdown 富文本渲染器（支持加粗、斜体、代码、超链接）
 */
@Composable
private fun MiuixInlineText(
    text: String,
    baseStyle: TextStyle
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val codeBgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val onSurfaceColor = baseStyle.color

    val annotatedString = remember(text, primaryColor, onSurfaceColor) {
        buildAnnotatedMarkdown(text, primaryColor, onSurfaceColor, codeBgColor)
    }

    BasicText(
        text = annotatedString,
        style = baseStyle
    )
}

/**
 * 将行内 Markdown 字符串编译为 Compose AnnotatedString
 */
private fun buildAnnotatedMarkdown(
    text: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    codeBgColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        // 正则表达式匹配行内标记：代码块、加粗、斜体、删除线、超链接
        val inlineRegex = Regex("(`[^`]+`)|(\\*\\*([^*]+)\\*\\*)|(__([^_]+)__)|(\\*([^*]+)\\*)|(_([^_]+)_)|(~~([^~]+)~~)|(\\[([^\\]]+)\\]\\(([^)]+)\\))")
        val matches = inlineRegex.findAll(text)

        matches.forEach { match ->
            val range = match.range
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }

            val fullMatch = match.value
            when {
                // `行内代码`
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val codeContent = fullMatch.removeSurrounding("`")
                    val start = length
                    append(" $codeContent ")
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            background = codeBgColor,
                            color = primaryColor
                        ),
                        start = start,
                        end = length
                    )
                }
                // **加粗** 或 __加粗__
                (fullMatch.startsWith("**") && fullMatch.endsWith("**")) ||
                (fullMatch.startsWith("__") && fullMatch.endsWith("__")) -> {
                    val boldContent = fullMatch.substring(2, fullMatch.length - 2)
                    val start = length
                    append(boldContent)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = onSurfaceColor),
                        start = start,
                        end = length
                    )
                }
                // *斜体* 或 _斜体_
                (fullMatch.startsWith("*") && fullMatch.endsWith("*")) ||
                (fullMatch.startsWith("_") && fullMatch.endsWith("_")) -> {
                    val italicContent = fullMatch.substring(1, fullMatch.length - 1)
                    val start = length
                    append(italicContent)
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        start = start,
                        end = length
                    )
                }
                // ~~删除线~~
                fullMatch.startsWith("~~") && fullMatch.endsWith("~~") -> {
                    val strikeContent = fullMatch.substring(2, fullMatch.length - 2)
                    val start = length
                    append(strikeContent)
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        start = start,
                        end = length
                    )
                }
                // [链接文本](url)
                fullMatch.startsWith("[") && fullMatch.contains("](") && fullMatch.endsWith(")") -> {
                    val linkText = fullMatch.substring(1, fullMatch.indexOf("]("))
                    val url = fullMatch.substring(fullMatch.indexOf("](") + 2, fullMatch.length - 1)
                    val start = length
                    append(linkText)
                    addLink(
                        url = LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ),
                        start = start,
                        end = length
                    )
                }
                else -> {
                    append(fullMatch)
                }
            }
            cursor = range.last + 1
        }

        if (cursor < len) {
            append(text.substring(cursor))
        }
    }
}

/**
 * 抽象 Markdown 块定义
 */
private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: String) : MarkdownBlock
    data class ListItem(val isOrdered: Boolean, val orderNumber: Int, val content: String) : MarkdownBlock
    data class Blockquote(val content: String) : MarkdownBlock
    data class CodeBlock(val content: String) : MarkdownBlock
    data class Paragraph(val content: String) : MarkdownBlock
    object Divider : MarkdownBlock
}

/**
 * 结构化解析 Markdown 文本为块列表
 */
private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    var inCodeBlock = false
    val codeBlockBuilder = StringBuilder()

    for (rawLine in lines) {
        val line = rawLine.trimEnd()

        // 1. 代码块围栏 ```
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeBlockBuilder.toString().trimEnd()))
                codeBlockBuilder.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeBlockBuilder.clear()
            }
            continue
        }

        if (inCodeBlock) {
            codeBlockBuilder.append(rawLine).append("\n")
            continue
        }

        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // 2. 分割线 --- 或 ***
        if (trimmed.matches(Regex("^([-*_])\\1{2,}$"))) {
            blocks.add(MarkdownBlock.Divider)
            continue
        }

        // 3. 标题 # ~ ####
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, title))
            continue
        }

        // 4. 无序列表 - 或 * 或 +
        val unorderedMatch = Regex("^[-*+]\\s+(.+)$").find(trimmed)
        if (unorderedMatch != null) {
            blocks.add(MarkdownBlock.ListItem(isOrdered = false, orderNumber = 0, content = unorderedMatch.groupValues[1].trim()))
            continue
        }

        // 5. 有序列表 1. 2. 等
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            blocks.add(MarkdownBlock.ListItem(isOrdered = true, orderNumber = num, content = orderedMatch.groupValues[2].trim()))
            continue
        }

        // 6. 引用块 >
        if (trimmed.startsWith(">")) {
            val quoteContent = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteContent))
            continue
        }

        // 7. 普通段落
        blocks.add(MarkdownBlock.Paragraph(trimmed))
    }

    if (inCodeBlock && codeBlockBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeBlockBuilder.toString().trimEnd()))
    }

    return blocks
}
