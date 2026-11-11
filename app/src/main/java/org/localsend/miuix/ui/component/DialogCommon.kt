package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 弹窗视觉节奏的单一来源。
 *
 * WindowDialog / WindowBottomSheet 的内容区已由组件库套上 24dp 内边距，
 * 因此弹窗内部不再叠加左右边距，只用这里定义的纵向间距与卡片内边距。
 */
internal object DialogTokens {
    /** 同一区块内相邻元素（标题与值、两行文本）的间距。 */
    val BlockGap: Dp = 8.dp

    /** 不同区块（说明、卡片、输入框、动作区）之间的间距。 */
    val SectionGap: Dp = 16.dp

    /** 纵向排列按钮之间的间距。 */
    val ActionGap: Dp = 8.dp

    /** 横向双按钮之间的间距。 */
    val ButtonGap: Dp = 12.dp

    /** 弹窗内卡片的内边距。 */
    val CardPadding: Dp = 14.dp

    /** 弹窗内卡片的圆角，与列表卡片的 16dp 保持一致。 */
    val CardCorner: Dp = 16.dp

    /** 标签、最近记录等小胶囊的圆角。 */
    val ChipCorner: Dp = 8.dp
}

/**
 * 弹窗内容区可用的最大高度。
 *
 * WindowDialog 在小屏不做限高，内容超过一屏会被窗口裁切；需要滚动的区块
 * 统一用这个上限，保证「标题 + 内容 + 动作区」始终能完整落在一屏内。
 *
 * @param fraction 占当前屏幕高度的比例。
 * @param min 下限，避免横屏或分屏时把内容压得过矮。
 * @param max 上限，避免大屏上内容区被拉得过高。
 */
@Composable
internal fun rememberDialogContentMaxHeight(
    fraction: Float = 0.42f,
    min: Dp = 120.dp,
    max: Dp = 400.dp,
): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight * fraction).coerceIn(min, max)
}

/**
 * 弹窗内的可滚动内容区：限高 + 纵向滚动。
 *
 * 仅用于纯静态内容；含 LazyColumn 的弹窗请直接给列表设置
 * [rememberDialogContentMaxHeight] 得到的 heightIn 上限，避免滚动嵌套冲突。
 */
@Composable
internal fun DialogBody(
    modifier: Modifier = Modifier,
    maxHeight: Dp = rememberDialogContentMaxHeight(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/** 弹窗内信息卡片：统一圆角、内边距与填充色。 */
@Composable
internal fun DialogCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = DialogTokens.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 弹窗底色与 surfaceContainer 同值，默认卡片在弹窗里完全看不见；
    // 改用 secondaryContainer（与输入框、搜索框同色）保证区块有可见的分组边界。
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content,
        )
    }
}

/** 弹窗内的「标签 - 值」行：标签弱化、值右对齐且单行省略。 */
@Composable
internal fun DialogInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
        )
    }
}

/** 弹窗内的小胶囊按钮，用于近期 IP 等候选值。 */
@Composable
internal fun DialogChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val background =
        if (selected) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        }
    val contentColor =
        if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceSecondary
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                .background(background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 对话框底部双按钮行：左侧次要操作、右侧主操作（可为危险色），供确认类弹窗复用。 */
@Composable
internal fun DialogButtonRow(
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    primaryColors: ButtonColors = ButtonDefaults.buttonColorsPrimary(),
    spacing: Dp = DialogTokens.ButtonGap,
    primaryEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Button(
            onClick = onSecondary,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(1f),
        ) {
            Text(secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = primaryColors,
            modifier = Modifier.weight(1f),
        ) {
            Text(primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 纵向排列的弹窗动作区：主按钮在上，次级动作为文字按钮，逐个占满宽度。 */
@Composable
internal fun DialogActionColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DialogTokens.ActionGap),
        content = content,
    )
}

/** 动作区里的文字按钮：整行可点、内容居中，高度与主按钮留白一致。 */
@Composable
internal fun DialogTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
) {
    TextButton(
        text = text,
        onClick = onClick,
        colors = colors,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 危险操作用的主按钮配色。 */
@Composable
internal fun dialogDangerButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        color = MiuixTheme.colorScheme.error,
        contentColor = MiuixTheme.colorScheme.onError,
    )

/** 危险操作在文字按钮上的配色。 */
@Composable
internal fun dialogDangerTextColors(): TextButtonColors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)

/**
 * 统一确认弹窗：标题 + 说明 + 等宽双按钮。
 *
 * 说明文本走 WindowDialog 的 summary 槽位（居中 body1），不再在内容区另起一段，
 * 保证所有「删除 / 清空 / 重置」类确认弹窗的层级与间距完全一致。
 *
 * @param danger 主操作是否为破坏性操作，为 true 时主按钮使用错误色。
 */
@Composable
internal fun ConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String,
    danger: Boolean = false,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismissRequest,
    ) {
        DialogButtonRow(
            secondaryText = dismissText,
            onSecondary = onDismissRequest,
            primaryText = confirmText,
            onPrimary = onConfirm,
            primaryColors = if (danger) dialogDangerButtonColors() else ButtonDefaults.buttonColorsPrimary(),
        )
    }
}
