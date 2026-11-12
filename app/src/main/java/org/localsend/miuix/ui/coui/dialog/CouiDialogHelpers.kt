package org.localsend.miuix.ui.coui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionColors
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.ui.coui.component.couiMinTouchTarget
import org.localsend.miuix.ui.coui.theme.couiDangerColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close

/**
 * 标记窗口根是否已挂入 `ProvideCouiMenuBackdrop`：库的 `LocalCouiMenuBackdrop` 为 `internal`，
 * 由根宿主在 `ProvideCouiMenuBackdrop` 作用域内将本标记置为 `true`，供弹窗安全判定玻璃材质。
 */
internal val LocalCouiWindowBackdropAvailable = staticCompositionLocalOf { false }

/**
 * 大屏居中、手机贴底：按 600dp 宽度阈值决定 coUI 弹窗停靠位置（与项目平板判定口径一致）。
 */
@Composable
internal fun rememberCouiDialogPlacement(): CouiDialogPlacement =
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        CouiDialogPlacement.Center
    } else {
        CouiDialogPlacement.Bottom
    }

/**
 * 弹窗玻璃材质：仅在 API 33+ 且窗口根已挂入 `ProvideCouiMenuBackdrop` 时启用，
 * 避免违反 `CouiDialogWindow.kt` 对 `LocalCouiMenuBackdrop` 非空的前置检查导致崩溃。
 */
@Composable
internal fun rememberCouiDialogMaterial(): CouiDialogMaterial? {
    val dark = CouiTheme.isDark
    val hasBackdrop = LocalCouiWindowBackdropAvailable.current
    return if (CouiDialogDefaults.isMaterialSupported() && hasBackdrop) {
        CouiDialogDefaults.material(dark)
    } else {
        null
    }
}

/**
 * 统一主操作与破坏性主操作配色，供全皮肤弹窗共用。
 */
@Composable
internal fun couiPrimaryActionColors(danger: Boolean = false): CouiDialogActionColors {
    val accent =
        if (danger) {
            CouiDialogDefaults.colors(CouiTheme.isDark).destructive
        } else {
            CouiTheme.colorScheme.accent
        }
    return CouiDialogDefaults.primaryActionColors(accent)
}

/**
 * coUI 内容弹窗通用骨架：统一承载可见性动画、贴底/居中放置策略、API 33+ 玻璃材质，
 * 以及与标题对齐的内容水平留白。
 */
@Composable
internal fun CouiContentDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    confirmAction: CouiDialogAction,
    dismissAction: CouiDialogAction? = null,
    neutralAction: CouiDialogAction? = null,
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        confirmAction = confirmAction,
        title = title,
        message = message,
        dismissAction = dismissAction,
        neutralAction = neutralAction,
        placement = rememberCouiDialogPlacement(),
        material = rememberCouiDialogMaterial(),
        visible = show,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** coUI 弹窗内分组卡片：在半透明或浅/深弹窗表面上以轻量容器底色区分层级。 */
@Composable
internal fun CouiDialogCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColor =
        CouiTheme.colorScheme.onSurface.copy(
            alpha = if (CouiTheme.isDark) 0.08f else 0.045f,
        )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CouiShape(16.dp))
                .background(cardColor)
                .padding(contentPadding),
        content = content,
    )
}

/** coUI 弹窗内「标签 - 值」信息行。 */
@Composable
internal fun CouiDialogInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CouiTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            kind = CouiTextKind.ItemSummary,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** coUI 弹窗内筛选/快速选择胶囊标签。 */
@Composable
internal fun CouiDialogChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val bgColor =
        if (selected) {
            scheme.accentOnSurface.copy(alpha = if (CouiTheme.isDark) 0.22f else 0.12f)
        } else {
            scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.08f else 0.05f)
        }
    val textColor = if (selected) scheme.accentOnSurface else scheme.onSurface
    Box(
        modifier =
            modifier
                .clip(CouiCapsuleShape)
                .background(bgColor)
                .couiButtonFeedback(CouiTheme.isDark, source)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemSummary,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** coUI 弹窗内辅助胶囊动作按钮（用于卡内复制、安装 APK 等次级或嵌入式动作）。 */
@Composable
internal fun CouiDialogPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = CouiTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val bgColor =
        when {
            !enabled -> scheme.onSurface.copy(alpha = 0.06f)
            danger -> couiDangerColor().copy(alpha = if (CouiTheme.isDark) 0.18f else 0.10f)
            primary -> scheme.accent
            else -> scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.10f else 0.06f)
        }
    val textColor =
        when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            danger -> couiDangerColor()
            primary -> scheme.onAccent
            else -> scheme.onSurface
        }
    Box(
        modifier =
            modifier
                .clip(CouiCapsuleShape)
                .background(bgColor)
                .couiButtonFeedback(CouiTheme.isDark, source)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).defaultMinSize(minWidth = 64.dp, minHeight = 36.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemSummary,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * coUI 弹窗文本输入域：支持 `KeyboardOptions`、多行输入与字符长度限制，
 * 填补库 `InputDialog` 无法定制键盘及多行域的缺口，同时视觉完全贴合 coUI 主题色与圆角。
 */
@Composable
internal fun CouiDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    maxLength: Int? = null,
    clearContentDescription: String? = null,
) {
    val scheme = CouiTheme.colorScheme
    val dark = CouiTheme.isDark
    var focused by remember { mutableStateOf(false) }
    val containerColor = scheme.onSurface.copy(alpha = if (dark) 0.09f else 0.05f)
    val borderColor = if (focused) scheme.accentOnSurface else scheme.outlineVariant.copy(alpha = 0.22f)
    val fieldShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!label.isNullOrEmpty()) {
            Text(
                text = label,
                kind = CouiTextKind.ItemSummary,
                color = if (focused) scheme.accentOnSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.defaultMinSize(minHeight = 50.dp) else Modifier)
                    .clip(fieldShape)
                    .background(containerColor)
                    .border(width = if (focused) 1.5.dp else 1.dp, color = borderColor, shape = fieldShape)
                    .padding(
                        start = 14.dp,
                        end = if (value.isNotEmpty() && singleLine) 4.dp else 14.dp,
                        top = if (singleLine) 4.dp else 12.dp,
                        bottom = if (singleLine) 4.dp else 12.dp,
                    ),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                    Text(
                        text = placeholder,
                        kind = CouiTextKind.ItemTitle,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = if (singleLine) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        if (maxLength == null || next.length <= maxLength) {
                            onValueChange(next)
                        }
                    },
                    textStyle =
                        rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Normal,
                        ),
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(scheme.accentOnSurface),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                )
            }
            if (value.isNotEmpty() && singleLine) {
                Box(
                    modifier =
                        Modifier
                            .clickable(role = Role.Button) { onValueChange("") }
                            .couiMinTouchTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = clearContentDescription,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** coUI 风格的细长胶囊线性进度条（库无原生线性进度指示器时替代 Miuix LinearProgressIndicator）。 */
@Composable
internal fun CouiDialogProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiTheme.colorScheme
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CouiCapsuleShape)
                .background(scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.12f else 0.08f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(clamped)
                    .height(6.dp)
                    .clip(CouiCapsuleShape)
                    .background(scheme.accent),
        )
    }
}
