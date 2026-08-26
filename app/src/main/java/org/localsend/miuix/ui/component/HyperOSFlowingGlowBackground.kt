package org.localsend.miuix.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * HyperOS 澎湃风格动态流光弥散背景
 * 
 * 模拟 Xiaomi HyperOS 系统更新页面的标志性多色动态光晕背景：
 * - 4 重柔和呼吸弥散光斑（澎湃蓝、极光青、霓虹紫与中心高光）
 * - 差异化周期动力学运动方程，平滑交织流动
 * - 自适应深色（Dark）与浅色（Light）模式，保证前景内容高对比度与通透感
 */
@Composable
fun HyperOSFlowingGlowBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HyperOSFlowingGlowTransition")

    // 动力学运动与呼吸缩放参数
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    // 依据当前系统主题自适应调配流光透明度与基底色
    val baseBgColor = if (isDark) {
        Color(0xFF0C0E14)
    } else {
        MiuixTheme.colorScheme.background
    }

    // 光斑配色方案
    val primaryBlue = if (isDark) Color(0xFF0A66FF) else Color(0xFF007AFF)
    val cyanAurora = if (isDark) Color(0xFF00E5FF) else Color(0xFF00C4B4)
    val neonPurple = if (isDark) Color(0xFF7E57C2) else Color(0xFF9C27B0)
    val centerGlow = if (isDark) Color(0xFF388E3C) else Color(0xFF4FC3F7)

    val alphaMultiplier = if (isDark) 0.42f else 0.22f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val minDim = minOf(width, height)

            if (minDim <= 0) return@Canvas

            // 1. 光斑 1：主澎湃蓝光斑（沿大椭圆轨道逆时针运动）
            val rad1 = Math.toRadians(phase1.toDouble())
            val c1X = width * 0.5f + width * 0.22f * cos(rad1).toFloat()
            val c1Y = height * 0.35f + height * 0.18f * sin(rad1).toFloat()
            val r1 = minDim * 0.65f * breatheScale
            drawGlowingOrb(
                center = Offset(c1X, c1Y),
                radius = r1,
                color = primaryBlue,
                alpha = alphaMultiplier * 0.85f
            )

            // 2. 光斑 2：极光青色光斑（沿双纽线/八字轨迹运动）
            val rad2 = Math.toRadians(phase2.toDouble())
            val c2X = width * 0.5f + width * 0.28f * sin(rad2).toFloat()
            val c2Y = height * 0.40f + height * 0.15f * sin(rad2 * 2.0).toFloat()
            val r2 = minDim * 0.55f * (2.0f - breatheScale)
            drawGlowingOrb(
                center = Offset(c2X, c2Y),
                radius = r2,
                color = cyanAurora,
                alpha = alphaMultiplier * 0.70f
            )

            // 3. 光斑 3：霓虹紫光斑（沿底部偏右浮动）
            val rad3 = Math.toRadians(phase1 * 0.7 + 90.0)
            val c3X = width * 0.55f + width * 0.20f * cos(rad3).toFloat()
            val c3Y = height * 0.55f + height * 0.15f * sin(rad3).toFloat()
            val r3 = minDim * 0.70f * breatheScale
            drawGlowingOrb(
                center = Offset(c3X, c3Y),
                radius = r3,
                color = neonPurple,
                alpha = alphaMultiplier * 0.60f
            )

            // 4. 光斑 4：中心核心呼吸柔光
            val c4X = width * 0.5f
            val c4Y = height * 0.38f
            val r4 = minDim * 0.40f * breatheScale
            drawGlowingOrb(
                center = Offset(c4X, c4Y),
                radius = r4,
                color = centerGlow,
                alpha = alphaMultiplier * 0.45f
            )
        }

        // 内容槽位
        content()
    }
}

/**
 * 绘制柔和衰减的多层径向渐变光晕球
 */
private fun DrawScope.drawGlowingOrb(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    val brush = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = alpha),
            color.copy(alpha = alpha * 0.55f),
            color.copy(alpha = alpha * 0.20f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    drawCircle(
        brush = brush,
        radius = radius,
        center = center,
        blendMode = BlendMode.SrcOver
    )
}
