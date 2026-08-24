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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.localsend.miuix.ui.animation.DampedDragAnimation
import org.localsend.miuix.ui.animation.InteractiveHighlight
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
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

val LocalLiquidBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalLiquidBarTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@Composable
fun LiquidGlassBottomBar(
    items: List<NavigationItem>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    badge: (Int) -> (@Composable () -> Unit)? = { null },
) {
    val isInDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val pillShape = remember { CircleShape }
    val isLiquidGlassMode = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
                val padding = with(density) { 3.dp.toPx() }
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

    val baseHighlight = remember { iosIndicatorSpecular.copy(alpha = 0.75f) }
    val pillHighlight = remember { iosIndicatorSpecular }

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
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val currentBadge = badge(index)
                if (currentBadge != null) {
                    Box {
                        Icon(
                            modifier = Modifier.size(22.dp),
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
                        modifier = Modifier.size(22.dp),
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = activeColor
                    )
                }
                Text(
                    text = item.label,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = activeColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }

    Box(
        modifier = modifier
            .widthIn(min = 210.dp, max = 240.dp)
            .height(56.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 1. Base Layer
        CompositionLocalProvider(LocalLiquidBarContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 6.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 8.dp,
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
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 20.dp.toPx(),
                                        refractionAmount = 20.dp.toPx(),
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val s = lerp(1f, 1f + 12.dp.toPx() / width, dampedDragAnimation.pressProgress)
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
                                    blur(20.dp.toPx(), 20.dp.toPx())
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
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )
        }

        // 2. Active Layer (Hidden for tabsBackdrop recording)
        if (isLiquidGlassMode && backdrop != null && combinedBackdrop != null) {
            CompositionLocalProvider(
                LocalLiquidBarTabScale provides {
                    lerp(1f, 1.15f, dampedDragAnimation.pressProgress)
                },
                LocalLiquidBarContentColor provides primaryColor
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .fillMaxSize()
                        .graphicsLayer { translationX = panelOffset }
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }
        }

        // 3. Sliding Liquid Glass Indicator Layer
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (isLiquidGlassMode && combinedBackdrop != null) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
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
                                    refractionHeight = 8.dp.toPx() * progress,
                                    refractionAmount = 12.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.copy(alpha = dampedDragAnimation.pressProgress) },
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
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 6.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(50.dp)
                        .width(tabWidthDp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(dampedDragAnimation.modifier)
                        .clip(pillShape)
                        .background(primaryColor.copy(alpha = 0.15f), pillShape)
                        .height(50.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CompositionLocalProvider(LocalLiquidBarContentColor provides primaryColor) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 6.dp.toPx()).toDp() })
                                .height(50.dp)
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
