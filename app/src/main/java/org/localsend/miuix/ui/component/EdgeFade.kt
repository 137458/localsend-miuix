package org.localsend.miuix.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** 边缘羽化底座：离屏合成后用 DstIn 遮罩把内容在指定边缘淡出。 */
internal fun Modifier.edgeFadeMask(brushFactory: DrawScope.() -> Brush): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent
        drawRect(brush = brushFactory(), blendMode = BlendMode.DstIn)
    }

/**
 * 竖向边缘羽化：只在还有内容的一侧铺渐变，滚到顶/底时对应一侧完全实心。
 */
internal fun Modifier.verticalEdgeFade(
    top: Boolean,
    bottom: Boolean,
    fade: Dp,
): Modifier {
    if (!top && !bottom) return this
    return edgeFadeMask {
        val fadePx = fade.toPx().coerceAtMost(size.height / 2f)
        val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
        when {
            top && bottom ->
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    stop to Color.Black,
                    1f - stop to Color.Black,
                    1f to Color.Transparent,
                )
            top -> Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black)
            else -> Brush.verticalGradient(1f - stop to Color.Black, 1f to Color.Transparent)
        }
    }
}
