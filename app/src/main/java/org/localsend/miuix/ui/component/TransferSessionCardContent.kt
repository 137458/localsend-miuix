package org.localsend.miuix.ui.component

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
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
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TextMessageCardContent(
    session: TransferSession,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: stringResource(R.string.notif_plain_text_message)

    // 1. 顶部状态栏（文本图标 + 对端别名 + 状态文本 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (session.isIncoming) stringResource(R.string.session_received_text_from, session.device.alias) else stringResource(R.string.session_send_to_alias, session.device.alias),
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text =
                        when (session.status) {
                            TransferStatus.WaitingApproval -> if (session.isIncoming) stringResource(R.string.session_waiting_self) else stringResource(R.string.session_waiting_peer_confirm)
                            TransferStatus.InProgress -> stringResource(R.string.session_syncing_text)
                            TransferStatus.Completed -> stringResource(R.string.session_text_completed)
                            TransferStatus.Failed -> stringResource(R.string.session_send_failed, session.errorMessage ?: stringResource(R.string.session_peer_declined))
                            TransferStatus.Canceled -> stringResource(R.string.session_canceled)
                        },
                    style = MiuixTheme.textStyles.footnote1,
                    color =
                        when (session.status) {
                            TransferStatus.Failed -> MiuixTheme.colorScheme.error
                            TransferStatus.WaitingApproval -> MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    if (session.status == TransferStatus.InProgress || session.status == TransferStatus.WaitingApproval) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2. 文本内容卡片
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (previewText.length > 2048) previewText.take(2048) + "…" else previewText,
            style = MiuixTheme.textStyles.body2,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }

    // 3. 复制 / 打开链接快捷操作
    if (session.isIncoming && (session.status == TransferStatus.Completed || session.status == TransferStatus.InProgress)) {
        val detectedUrl =
            remember(previewText) {
                val trimmed = previewText.take(2048).trim()
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
                        trimmed
                    } else {
                        val matcher =
                            android.util.Patterns.WEB_URL
                                .matcher(trimmed)
                        if (matcher.find()) matcher.group() else null
                    }
                } else {
                    val matcher =
                        android.util.Patterns.WEB_URL
                            .matcher(trimmed)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LocalSend Text", previewText))
                    android.widget.Toast
                        .makeText(context, context.getString(R.string.toast_copied_to_clipboard), android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.session_btn_copy_text))
            }

            if (detectedUrl != null) {
                Button(
                    onClick = {
                        try {
                            val intent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(detectedUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast
                                .makeText(context, context.getString(R.string.toast_cannot_open_link), android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.btn_open_link))
                }
            }
        }
    }
}

@Composable
internal fun FileTransferCardContent(
    session: TransferSession,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)?,
) {
    var previewInitialIndex by remember { mutableIntStateOf(-1) }

    // 1. 顶部状态栏（方向图标 + 对端别名 + 状态文本 + 取消按钮）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (session.isIncoming) Icons.Default.Download else Icons.Default.Upload,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (session.isIncoming) {
                            stringResource(R.string.session_from_alias, session.device.alias)
                        } else {
                            stringResource(R.string.session_send_to_alias, session.device.alias)
                        },
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text =
                        fileStatusText(
                            session = session,
                            inProgressText = stringResource(R.string.session_files_in_progress, session.files.size, session.formattedTotalSize),
                            // 部分文件失败时会话仍判完成，用聚合说明替换"传输完成"，避免用户以为文件已收齐
                            completedText =
                                session.errorMessage?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.session_files_completed, session.files.size, session.formattedTotalSize),
                        ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = fileStatusColor(session, defaultColor = MiuixTheme.colorScheme.onSurfaceVariantSummary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                        .clickable { previewInitialIndex = session.currentFileIndex.coerceIn(0, (session.files.size - 1).coerceAtLeast(0)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileThumbnail(
                    file = current,
                    size = 28.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (session.files.size > 1) {
                            stringResource(R.string.session_transferring_indexed, session.currentFileIndex + 1, session.files.size, current.name)
                        } else {
                            stringResource(R.string.session_transferring_single, current.name)
                        },
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${FileItem.formatFileSize(current.bytesTransferred)} / ${current.formattedSize}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    // 3. 总体进度条（平滑动画过渡与状态自适应）
    val animatedSessionProgress by animateFloatAsState(
        targetValue = if (session.status == TransferStatus.Completed) 1f else session.progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "SessionProgress",
    )

    Spacer(modifier = Modifier.height(10.dp))
    if (session.status == TransferStatus.WaitingApproval) {
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
        )
    } else {
        LinearProgressIndicator(
            progress = animatedSessionProgress,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
        )
    }

    // 4. 传输指标行（左侧：已传输/总大小 (百分比)；右侧：实时速率 • 剩余时间）
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (session.status == TransferStatus.InProgress) {
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

    // 5. 可折叠文件明细清单（Accordion）
    if (session.files.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
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
                modifier = Modifier.size(18.dp),
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
                        .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
