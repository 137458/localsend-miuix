package org.localsend.miuix.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.transfer.RemainingTime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 待批准会话的「接收」按钮：仅在卡片提供接收回调时出现，尺寸与相邻按钮保持一致。
 */
@Composable
internal fun AcceptIconButton(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    IconButton(onClick = onAccept, modifier = modifier) {
        Icon(
            imageVector = AppIcons.Check,
            contentDescription = stringResource(R.string.btn_accept),
            tint = MiuixTheme.colorScheme.primary,
            modifier = iconModifier,
        )
    }
}

/**
 * 文件传输状态文案：等待确认、失败与取消三态收发措辞一致，统一收敛在此处；
 * 传输中与完成态因收发语义不同由调用方传入。
 */
@Composable
internal fun fileStatusText(
    session: TransferSession,
    inProgressText: String,
    completedText: String,
): String =
    when (session.status) {
        TransferStatus.WaitingApproval -> stringResource(R.string.session_waiting_peer)
        TransferStatus.InProgress -> inProgressText
        TransferStatus.Completed -> completedText
        TransferStatus.Failed -> stringResource(R.string.session_transfer_failed, session.errorMessage ?: stringResource(R.string.session_unknown_error))
        TransferStatus.Canceled -> stringResource(R.string.session_canceled)
    }

/**
 * 文件传输状态文案颜色：失败与部分失败统一用错误色、等待确认用主色；
 * 完成态与其余状态的配色收件与发件卡片不同，由调用方传入。
 */
@Composable
internal fun fileStatusColor(
    session: TransferSession,
    defaultColor: Color,
    completedColor: Color = defaultColor,
): Color =
    when {
        session.status == TransferStatus.Failed -> MiuixTheme.colorScheme.error
        session.isPartialFailure -> MiuixTheme.colorScheme.error
        session.status == TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
        session.status == TransferStatus.Completed -> completedColor
        else -> defaultColor
    }

@Composable
internal fun remainingTimeLabel(session: TransferSession): String =
    when (val remaining = session.remainingTime) {
        RemainingTime.Hidden -> ""
        RemainingTime.Calculating -> stringResource(R.string.eta_calculating)
        RemainingTime.AlmostDone -> stringResource(R.string.live_almost_done)
        is RemainingTime.Seconds -> stringResource(R.string.eta_seconds, remaining.value)
        is RemainingTime.Minutes -> stringResource(R.string.eta_minutes, remaining.minutes, remaining.seconds)
        is RemainingTime.Hours -> stringResource(R.string.eta_hours, remaining.hours, remaining.minutes)
    }

@Composable
internal fun FileDetailItem(
    file: FileItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumbnail(
            file = file,
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (file.isTextMessage) stringResource(R.string.send_type_text_message) else file.name,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${FileItem.formatFileSize(file.bytesTransferred)} / ${file.formattedSize}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (file.status == TransferStatus.InProgress && file.speed > 0) {
                    Text(
                        text = FileItem.formatSpeed(file.speed),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            if (file.status == TransferStatus.InProgress) {
                Spacer(modifier = Modifier.height(4.dp))
                val animatedFileProgress by animateFloatAsState(
                    targetValue = file.progress,
                    animationSpec = tween(durationMillis = 80, easing = LinearEasing),
                    label = "FileProgress",
                )
                LinearProgressIndicator(
                    progress = animatedFileProgress,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (file.status) {
            TransferStatus.Completed ->
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.status_completed),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            TransferStatus.InProgress ->
                Text(
                    text = "${(file.progress * 100).toInt()}%",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            TransferStatus.Failed ->
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = stringResource(R.string.status_failed),
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            TransferStatus.WaitingApproval, TransferStatus.Canceled ->
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = stringResource(R.string.status_waiting),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
        }
    }
}
