package org.localsend.miuix.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun InlineTextMessageProgress(
    session: TransferSession,
    onCancel: () -> Unit,
) {
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: stringResource(R.string.send_type_text_message)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text =
                    when (session.status) {
                        TransferStatus.WaitingApproval -> stringResource(R.string.session_waiting_peer_confirm)
                        TransferStatus.InProgress -> stringResource(R.string.session_syncing_text)
                        TransferStatus.Completed -> stringResource(R.string.session_text_delivered)
                        TransferStatus.Failed -> stringResource(R.string.session_send_failed, session.errorMessage ?: stringResource(R.string.session_peer_declined))
                        TransferStatus.Canceled -> stringResource(R.string.session_canceled)
                    },
                style = MiuixTheme.textStyles.footnote1,
                color =
                    when (session.status) {
                        TransferStatus.Failed -> MiuixTheme.colorScheme.error
                        TransferStatus.Completed -> MiuixTheme.colorScheme.primary
                        TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel_transfer),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = previewText,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun InlineFileTransferProgress(
    session: TransferSession,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?,
) {
    var previewInitialIndex by remember { mutableIntStateOf(-1) }

    // 1. 状态行（状态描述 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            when (session.status) {
                TransferStatus.Completed ->
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                TransferStatus.Failed ->
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                TransferStatus.WaitingApproval ->
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                TransferStatus.InProgress ->
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                TransferStatus.Canceled ->
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
            }
            Spacer(modifier = Modifier.width(6.dp))
            val currentFile = session.currentFile
            val inProgressText =
                when {
                    currentFile != null && session.files.size > 1 -> stringResource(R.string.session_transferring_indexed, session.currentFileIndex + 1, session.files.size, currentFile.name)
                    currentFile != null -> stringResource(R.string.session_transferring_single, currentFile.name)
                    else -> stringResource(R.string.session_preparing_transfer)
                }
            Text(
                text =
                    fileStatusText(
                        session = session,
                        inProgressText = inProgressText,
                        // 部分文件失败时会话仍判完成，用聚合说明替换"传输完成"，避免用户以为文件已送达
                        completedText =
                            session.errorMessage?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.session_transfer_complete_check, session.files.size, session.formattedTotalSize),
                    ),
                style = MiuixTheme.textStyles.footnote1,
                color =
                    fileStatusColor(
                        session,
                        defaultColor = MiuixTheme.colorScheme.onSurface,
                        completedColor = MiuixTheme.colorScheme.primary,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 待批准的自收请求可直接在卡片上接收，无需回到弹窗
                if (session.isIncoming && session.status == TransferStatus.WaitingApproval && onAccept != null) {
                    AcceptIconButton(
                        onAccept = onAccept,
                        modifier = Modifier.size(30.dp),
                        iconModifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    // 2. 动态平滑进度条
    val animatedSessionProgress by animateFloatAsState(
        targetValue = if (session.status == TransferStatus.Completed) 1f else session.progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "InlineSessionProgress",
    )

    Spacer(modifier = Modifier.height(8.dp))
    if (session.status == TransferStatus.WaitingApproval) {
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
        )
    } else {
        LinearProgressIndicator(
            progress = animatedSessionProgress,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
        )
    }

    // 3. 传输指标行（左：已传/总大小 (百分比)；右：实时速率 • 剩余时间）
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
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

    // 4. 可折叠清单
    if (session.files.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleExpanded() }
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
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp),
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
                        .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                session.files.forEachIndexed { index, file ->
                    androidx.compose.runtime.key(file.id) {
                        FileDetailItem(
                            file = file,
                            onClick = { previewInitialIndex = index },
                        )
                    }
                }
            }
        }
    }

    if (previewInitialIndex >= 0 && previewInitialIndex < session.files.size) {
        FilePreviewDialog(
            files = session.files,
            initialIndex = previewInitialIndex,
            onDismissRequest = { previewInitialIndex = -1 },
        )
    }
}
