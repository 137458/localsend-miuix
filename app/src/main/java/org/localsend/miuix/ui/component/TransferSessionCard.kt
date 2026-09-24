package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/**
 * 传输会话卡片：
 * - 纯文本消息：展示文本预览、对端别名与确认状态，不展示冗余的字节进度条与速率；
 * - 文件传输：展示正在收发文件的全景进度（当前文件、总体进度条、已传/总量、百分比、速率、剩余时间与可折叠文件清单）。
 */
@Composable
fun TransferSessionCard(
    session: TransferSession,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (session.isTextMessage) {
                TextMessageCardContent(session = session, onCancel = onCancel, onAccept = onAccept)
            } else {
                FileTransferCardContent(
                    session = session,
                    isExpanded = isExpanded,
                    onToggleExpanded = { isExpanded = !isExpanded },
                    onCancel = onCancel,
                    onAccept = onAccept
                )
            }
        }
    }
}

/**
 * 待批准会话的「接收」按钮：仅在卡片提供接收回调时出现，尺寸与相邻按钮保持一致。
 */
@Composable
private fun AcceptIconButton(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    IconButton(onClick = onAccept, modifier = modifier) {
        Icon(
            imageVector = AppIcons.Check,
            contentDescription = stringResource(R.string.btn_accept),
            tint = MiuixTheme.colorScheme.primary,
            modifier = iconModifier
        )
    }
}

/**
 * 文件传输状态文案：等待确认、失败与取消三态收发措辞一致，统一收敛在此处；
 * 传输中与完成态因收发语义不同由调用方传入。
 */
@Composable
private fun fileStatusText(
    session: TransferSession,
    inProgressText: String,
    completedText: String
): String = when (session.status) {
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
private fun fileStatusColor(
    session: TransferSession,
    defaultColor: Color,
    completedColor: Color = defaultColor
): Color = when {
    session.status == TransferStatus.Failed -> MiuixTheme.colorScheme.error
    session.isPartialFailure -> MiuixTheme.colorScheme.error
    session.status == TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
    session.status == TransferStatus.Completed -> completedColor
    else -> defaultColor
}

@Composable
private fun TextMessageCardContent(
    session: TransferSession,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: stringResource(R.string.notif_plain_text_message)

    // 1. 顶部状态栏（文本图标 + 对端别名 + 状态文本 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (session.isIncoming) stringResource(R.string.session_received_text_from, session.device.alias) else stringResource(R.string.session_send_to_alias, session.device.alias),
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (session.status) {
                        TransferStatus.WaitingApproval -> if (session.isIncoming) stringResource(R.string.session_waiting_self) else stringResource(R.string.session_waiting_peer_confirm)
                        TransferStatus.InProgress -> stringResource(R.string.session_syncing_text)
                        TransferStatus.Completed -> stringResource(R.string.session_text_completed)
                        TransferStatus.Failed -> stringResource(R.string.session_send_failed, session.errorMessage ?: stringResource(R.string.session_peer_declined))
                        TransferStatus.Canceled -> stringResource(R.string.session_canceled)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = when (session.status) {
                        TransferStatus.Failed -> MiuixTheme.colorScheme.error
                        TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 待批准的自收请求可直接在卡片上接收，无需回到弹窗
                if (session.isIncoming && session.status == TransferStatus.WaitingApproval && onAccept != null) {
                    AcceptIconButton(onAccept = onAccept)
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2. 文本内容卡片
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (previewText.length > 2048) previewText.take(2048) + "…" else previewText,
            style = MiuixTheme.textStyles.body2,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface
        )
    }

    // 3. 复制 / 打开链接快捷操作
    if (session.isIncoming && (session.status == TransferStatus.Completed || session.status == TransferStatus.InProgress)) {
        val detectedUrl = remember(previewText) {
            val trimmed = previewText.take(2048).trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                    trimmed
                } else {
                    val matcher = android.util.Patterns.WEB_URL.matcher(trimmed)
                    if (matcher.find()) matcher.group() else null
                }
            } else {
                val matcher = android.util.Patterns.WEB_URL.matcher(trimmed)
                if (matcher.find()) {
                    val found = matcher.group()
                    if (found.startsWith("http://", ignoreCase = true) || found.startsWith("https://", ignoreCase = true)) {
                        found
                    } else {
                        "https://$found"
                    }
                } else {
                    null
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LocalSend Text", previewText))
                    android.widget.Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.session_btn_copy_text))
            }

            if (detectedUrl != null) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detectedUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(stringResource(R.string.btn_open_link))
                }
            }
        }
    }
}

@Composable
private fun FileTransferCardContent(
    session: TransferSession,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?
) {
    var previewInitialIndex by remember { mutableIntStateOf(-1) }

    // 1. 顶部状态栏（方向图标 + 对端别名 + 状态文本 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (session.isIncoming) Icons.Default.Download else Icons.Default.Upload,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (session.isIncoming) {
                        stringResource(R.string.session_from_alias, session.device.alias)
                    } else {
                        stringResource(R.string.session_send_to_alias, session.device.alias)
                    },
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fileStatusText(
                        session = session,
                        inProgressText = stringResource(R.string.session_files_in_progress, session.files.size, session.formattedTotalSize),
                        // 部分文件失败时会话仍判完成，用聚合说明替换"传输完成"，避免用户以为文件已收齐
                        completedText = session.errorMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.session_files_completed, session.files.size, session.formattedTotalSize)
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = fileStatusColor(session, defaultColor = MiuixTheme.colorScheme.onSurfaceVariantSummary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 待批准的自收请求可直接在卡片上接收，无需回到弹窗
                if (session.isIncoming && session.status == TransferStatus.WaitingApproval && onAccept != null) {
                    AcceptIconButton(onAccept = onAccept)
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    // 2. 当前正在传输的文件提示横幅（传输中且有文件在跑）
    if (session.status == TransferStatus.InProgress) {
        val current = session.currentFile
        if (current != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                    .clickable { previewInitialIndex = session.currentFileIndex.coerceIn(0, (session.files.size - 1).coerceAtLeast(0)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileThumbnail(
                    file = current,
                    size = 28.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (session.files.size > 1) {
                        stringResource(R.string.session_transferring_indexed, session.currentFileIndex + 1, session.files.size, current.name)
                    } else {
                        stringResource(R.string.session_transferring_single, current.name)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${FileItem.formatFileSize(current.bytesTransferred)} / ${current.formattedSize}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }

    // 3. 总体进度条（平滑动画过渡与状态自适应）
    val animatedSessionProgress by animateFloatAsState(
        targetValue = if (session.status == TransferStatus.Completed) 1f else session.progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "SessionProgress"
    )

    Spacer(modifier = Modifier.height(10.dp))
    if (session.status == TransferStatus.WaitingApproval) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    } else {
        LinearProgressIndicator(
            progress = animatedSessionProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }

    // 4. 传输指标行（左侧：已传输/总大小 (百分比)；右侧：实时速率 • 剩余时间）
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${session.formattedTransferredSize} / ${session.formattedTotalSize} (${session.progressPercent}%)",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (session.status == TransferStatus.InProgress) {
                Text(
                    text = session.formattedSpeed,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary
                )
                val remainingLabel = remainingTimeLabel(session)
                if (remainingLabel.isNotEmpty()) {
                    Text(
                        text = " • $remainingLabel",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    // 5. 可折叠文件明细清单（Accordion）
    if (session.files.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleExpanded() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_file_list_title, session.files.count { it.status == TransferStatus.Completed }, session.files.size),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                session.files.forEachIndexed { index, file ->
                    androidx.compose.runtime.key(file.id) {
                        FileDetailItem(
                            file = file,
                            onClick = { previewInitialIndex = index }
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
            onDismissRequest = { previewInitialIndex = -1 }
        )
    }
}

@Composable
private fun remainingTimeLabel(session: TransferSession): String {
    return when (val remaining = session.remainingTime) {
        RemainingTime.Hidden -> ""
        RemainingTime.Calculating -> stringResource(R.string.eta_calculating)
        RemainingTime.AlmostDone -> stringResource(R.string.live_almost_done)
        is RemainingTime.Seconds -> stringResource(R.string.eta_seconds, remaining.value)
        is RemainingTime.Minutes -> stringResource(R.string.eta_minutes, remaining.minutes, remaining.seconds)
        is RemainingTime.Hours -> stringResource(R.string.eta_hours, remaining.hours, remaining.minutes)
    }
}

@Composable
private fun FileDetailItem(
    file: FileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumbnail(
            file = file,
            size = 32.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (file.isTextMessage) stringResource(R.string.send_type_text_message) else file.name,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${FileItem.formatFileSize(file.bytesTransferred)} / ${file.formattedSize}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                if (file.status == TransferStatus.InProgress && file.speed > 0) {
                    Text(
                        text = FileItem.formatSpeed(file.speed),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
            if (file.status == TransferStatus.InProgress) {
                Spacer(modifier = Modifier.height(4.dp))
                val animatedFileProgress by animateFloatAsState(
                    targetValue = file.progress,
                    animationSpec = tween(durationMillis = 80, easing = LinearEasing),
                    label = "FileProgress"
                )
                LinearProgressIndicator(
                    progress = animatedFileProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (file.status) {
            TransferStatus.Completed -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.status_completed),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            TransferStatus.InProgress -> Text(
                text = "${(file.progress * 100).toInt()}%",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary
            )
            TransferStatus.Failed -> Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(R.string.status_failed),
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            TransferStatus.WaitingApproval, TransferStatus.Canceled -> Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = stringResource(R.string.status_waiting),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 内嵌在设备 Card 下方的传输进度组件（直接在同一个设备 Card 内部渲染）。
 */
@Composable
fun InlineTransferProgress(
    session: TransferSession,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onAccept: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (session.isTextMessage) {
            InlineTextMessageProgress(session = session, onCancel = onCancel)
        } else {
            InlineFileTransferProgress(
                session = session,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded },
                onCancel = onCancel,
                onAccept = onAccept
            )
        }
    }
}

@Composable
private fun InlineTextMessageProgress(
    session: TransferSession,
    onCancel: () -> Unit
) {
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: stringResource(R.string.send_type_text_message)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (session.status) {
                    TransferStatus.WaitingApproval -> stringResource(R.string.session_waiting_peer_confirm)
                    TransferStatus.InProgress -> stringResource(R.string.session_syncing_text)
                    TransferStatus.Completed -> stringResource(R.string.session_text_delivered)
                    TransferStatus.Failed -> stringResource(R.string.session_send_failed, session.errorMessage ?: stringResource(R.string.session_peer_declined))
                    TransferStatus.Canceled -> stringResource(R.string.session_canceled)
                },
                style = MiuixTheme.textStyles.footnote1,
                color = when (session.status) {
                    TransferStatus.Failed -> MiuixTheme.colorScheme.error
                    TransferStatus.Completed -> MiuixTheme.colorScheme.primary
                    TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel_transfer),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = previewText,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InlineFileTransferProgress(
    session: TransferSession,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?
) {
    var previewInitialIndex by remember { mutableIntStateOf(-1) }

    // 1. 状态行（状态描述 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            when (session.status) {
                TransferStatus.Completed -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                TransferStatus.Failed -> Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                TransferStatus.WaitingApproval -> Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                TransferStatus.InProgress -> Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                TransferStatus.Canceled -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            val currentFile = session.currentFile
            val inProgressText = when {
                currentFile != null && session.files.size > 1 -> stringResource(R.string.session_transferring_indexed, session.currentFileIndex + 1, session.files.size, currentFile.name)
                currentFile != null -> stringResource(R.string.session_transferring_single, currentFile.name)
                else -> stringResource(R.string.session_preparing_transfer)
            }
            Text(
                text = fileStatusText(
                    session = session,
                    inProgressText = inProgressText,
                    // 部分文件失败时会话仍判完成，用聚合说明替换"传输完成"，避免用户以为文件已送达
                    completedText = session.errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.session_transfer_complete_check, session.files.size, session.formattedTotalSize)
                ),
                style = MiuixTheme.textStyles.footnote1,
                color = fileStatusColor(
                    session,
                    defaultColor = MiuixTheme.colorScheme.onSurface,
                    completedColor = MiuixTheme.colorScheme.primary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 待批准的自收请求可直接在卡片上接收，无需回到弹窗
                if (session.isIncoming && session.status == TransferStatus.WaitingApproval && onAccept != null) {
                    AcceptIconButton(
                        onAccept = onAccept,
                        modifier = Modifier.size(30.dp),
                        iconModifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // 2. 动态平滑进度条
    val animatedSessionProgress by animateFloatAsState(
        targetValue = if (session.status == TransferStatus.Completed) 1f else session.progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "InlineSessionProgress"
    )

    Spacer(modifier = Modifier.height(8.dp))
    if (session.status == TransferStatus.WaitingApproval) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
        )
    } else {
        LinearProgressIndicator(
            progress = animatedSessionProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
        )
    }

    // 3. 传输指标行（左：已传/总大小 (百分比)；右：实时速率 • 剩余时间）
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${session.formattedTransferredSize} / ${session.formattedTotalSize} (${session.progressPercent}%)",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        if (session.status == TransferStatus.InProgress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.formattedSpeed,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary
                )
                val remainingLabel = remainingTimeLabel(session)
                if (remainingLabel.isNotEmpty()) {
                    Text(
                        text = " • $remainingLabel",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    // 4. 可折叠清单
    if (session.files.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleExpanded() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_file_list_title, session.files.count { it.status == TransferStatus.Completed }, session.files.size),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                session.files.forEachIndexed { index, file ->
                    androidx.compose.runtime.key(file.id) {
                        FileDetailItem(
                            file = file,
                            onClick = { previewInitialIndex = index }
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
            onDismissRequest = { previewInitialIndex = -1 }
        )
    }
}