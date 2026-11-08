package org.localsend.miuix.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 会话总进度条：等待批准时显示不确定进度，其余状态按会话进度平滑过渡。 */
@Composable
internal fun SessionProgressBar(
    session: TransferSession,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (session.status == TransferStatus.Completed) 1f else session.progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "SessionProgress",
    )
    val barModifier =
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))

    if (session.status == TransferStatus.WaitingApproval) {
        LinearProgressIndicator(modifier = barModifier)
    } else {
        LinearProgressIndicator(progress = animatedProgress, modifier = barModifier)
    }
}

/** 会话指标行：左侧已传输/总量与百分比，右侧传输中显示速率与剩余时间。 */
@Composable
internal fun SessionMetricsRow(
    session: TransferSession,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${session.formattedTransferredSize} / ${session.formattedTotalSize} (${session.progressPercent}%)",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (session.status == TransferStatus.InProgress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.formattedSpeed,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
                val remainingLabel = remainingTimeLabel(session)
                if (remainingLabel.isNotEmpty()) {
                    Text(
                        text = " • $remainingLabel",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 可折叠的会话文件清单；[onPreview] 回调被点击的文件下标。 */
@Composable
internal fun SessionFileList(
    session: TransferSession,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    contentTopPadding: Dp = 6.dp,
    itemSpacing: Dp = 6.dp,
) {
    if (session.files.isEmpty()) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { onToggleExpanded() }
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.session_file_list_title, session.files.count { it.status == TransferStatus.Completed }, session.files.size),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Icon(
            imageVector = if (isExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
            contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(iconSize),
        )
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = contentTopPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            session.files.forEachIndexed { index, file ->
                androidx.compose.runtime.key(file.id) {
                    FileDetailItem(
                        file = file,
                        onClick = { onPreview(index) },
                    )
                }
            }
        }
    }
}
