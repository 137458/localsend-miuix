package org.localsend.miuix.ui.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.localsend.miuix.ui.animation.DampedDragAnimation
import org.localsend.miuix.ui.animation.InteractiveHighlight
import org.localsend.miuix.ui.effect.isRuntimeShaderSupported
import org.localsend.miuix.ui.libs.liquid.InnerShadow
import org.localsend.miuix.ui.libs.liquid.innerShadow
import org.localsend.miuix.ui.libs.liquid.lens
import org.localsend.miuix.ui.libs.liquid.rememberCombinedBackdrop
import org.localsend.miuix.ui.libs.liquid.vibrancy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

val LocalLiquidBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalLiquidBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 官方 Miuix / HyperOS 规范液态玻璃（Liquid Glass）悬浮胶囊底栏。
 * 遵循 InstallerX-Revived 与 AndroidLiquidGlass 生产级双重 Backdrop 架构与折射规范。
 */
@Composable
fun LiquidGlassBottomBar(
    modifier: Modifier = Modifier,
    items: List<NavigationItem>,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop?,
    badge: (Int) -> (@Composable () -> Unit)? = { null },
) {
    val isInDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val pillShape = remember { CircleShape }

    val isLiquidGlassMode = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()

    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val primaryColor = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val containerColor = if (isLiquidGlassMode) surfaceContainer.copy(0.4f) else surfaceContainer

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 3.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.15f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                animateToValue(targetIndex.toFloat())
                onSelected(targetIndex)
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    val interactiveHighlight = if (isLiquidGlassMode) {
        remember(animationScope, tabWidthPx) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }
    } else null

    val combinedBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else null

    val tabsContent: @Composable RowScope.() -> Unit = {
        val tabScale = LocalLiquidBarTabScale.current
        val activeColor = LocalLiquidBarContentColor.current
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            onSelected(index)
                        },
                    )
                    .fillMaxHeight()
                    .weight(1f)
                    .defaultMinSize(minWidth = 78.dp)
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val currentBadge = badge(index)
                if (currentBadge != null) {
                    Box {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = activeColor
                        )
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            currentBadge()
                        }
                    }
                } else {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = activeColor
                    )
                }
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    color = activeColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }

    val blur4Px = with(density) { 4.dp.toPx() }
    val blur20Px = with(density) { 20.dp.toPx() }
    val lens24Px = with(density) { 24.dp.toPx() }
    val pad40Px = with(density) { 40.dp.toPx() }
    val lensHeight8Px = with(density) { 8.dp.toPx() }
    val lensAmount12Px = with(density) { 12.dp.toPx() }
    val scale16Px = with(density) { 16.dp.toPx() }

    Box(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .height(62.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ── 1. Base Layer: 底栏外壳（基础层，承载未激活文字与折射底层） ──
        CompositionLocalProvider(LocalLiquidBarContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        if (isLiquidGlassMode && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    padding = maxOf(padding, pad40Px)
                                    vibrancy()
                                    blur(blur4Px, blur4Px)
                                    lens(
                                        refractionHeight = lens24Px,
                                        refractionAmount = lens24Px,
                                    )
                                },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val s = lerp(1f, 1f + scale16Px / width, dampedDragAnimation.pressProgress)
                                    scaleX = s
                                    scaleY = s
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                        } else if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    blur(blur20Px, blur20Px)
                                },
                                onDrawSurface = {
                                    drawRect(containerColor.copy(alpha = 0.65f))
                                },
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        }
                    )
                    .then(if (isLiquidGlassMode && interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                    .height(62.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )
        }

        // ── 2. Active Layer: 离屏高亮 Tab 采样层（供 tabsBackdrop 记录） ──
        if (isLiquidGlassMode && backdrop != null && combinedBackdrop != null) {
            CompositionLocalProvider(
                LocalLiquidBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalLiquidBarContentColor provides primaryColor
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(blur4Px, blur4Px)
                                lens(refractionHeight = lens24Px, refractionAmount = lens24Px)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .fillMaxHeight()
                        .height(62.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }
        }

        // ── 3. Indicator Layer: 滑动液态玻璃透镜胶囊滑块 ──
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (isLiquidGlassMode && combinedBackdrop != null) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(interactiveHighlight?.gestureModifier ?: Modifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = lensHeight8Px * (1f + 0.25f * progress),
                                    refractionAmount = lensAmount12Px * (1f + 0.25f * progress),
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 4.dp + 4.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.12f + 0.08f * dampedDragAnimation.pressProgress),
                                alpha = 0.6f + 0.4f * dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(54.dp)
                        .width(tabWidthDp)
                )
            } else {
                // 降级模式胶囊滑块
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(dampedDragAnimation.modifier)
                        .clip(pillShape)
                        .background(primaryColor.copy(alpha = 0.15f), pillShape)
                        .height(54.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CompositionLocalProvider(LocalLiquidBarContentColor provides primaryColor) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                .height(54.dp)
                                .graphicsLayer {
                                    val progressOffset = dampedDragAnimation.value * tabWidthPx
                                    translationX = if (isLtr) -progressOffset else progressOffset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = tabsContent,
                        )
                    }
                }
            }
        }
    }
}
