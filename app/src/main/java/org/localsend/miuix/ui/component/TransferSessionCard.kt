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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.TransferStatus
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
    onCancel: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (session.isTextMessage) {
                TextMessageCardContent(session = session, onCancel = onCancel)
            } else {
                FileTransferCardContent(
                    session = session,
                    isExpanded = isExpanded,
                    onToggleExpanded = { isExpanded = !isExpanded },
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun TextMessageCardContent(
    session: TransferSession,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: "纯文本消息"

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
                    text = if (session.isIncoming) "收到来自 ${session.device.alias} 的文本" else "发送至: ${session.device.alias}",
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (session.status) {
                        TransferStatus.WaitingApproval -> if (session.isIncoming) "等待您确认接收..." else "等待对方确认接收..."
                        TransferStatus.InProgress -> "正在同步文本..."
                        TransferStatus.Completed -> "文本传输完成"
                        TransferStatus.Failed -> "发送失败: ${session.errorMessage ?: "对方拒绝"}"
                        TransferStatus.Canceled -> "传输已取消"
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
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消传输",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
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
            text = previewText,
            style = MiuixTheme.textStyles.body2,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface
        )
    }

    // 3. 复制 / 打开链接快捷操作
    if (session.isIncoming && (session.status == TransferStatus.Completed || session.status == TransferStatus.InProgress)) {
        val detectedUrl = remember(previewText) {
            val trimmed = previewText.trim()
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
                    android.widget.Toast.makeText(context, "已复制文本到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制文本")
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
                            android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("打开链接")
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
    onCancel: () -> Unit
) {
    var previewingFile by remember { mutableStateOf<FileItem?>(null) }

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
                    text = if (session.isIncoming) "来自: ${session.device.alias}" else "发送至: ${session.device.alias}",
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (session.status) {
                        TransferStatus.WaitingApproval -> "等待对方同意接收..."
                        TransferStatus.InProgress -> "共 ${session.files.size} 个文件 • ${session.formattedTotalSize}"
                        TransferStatus.Completed -> "传输完成 • 共 ${session.files.size} 个文件 (${session.formattedTotalSize})"
                        TransferStatus.Failed -> "传输失败: ${session.errorMessage ?: "未知错误"}"
                        TransferStatus.Canceled -> "传输已取消"
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
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消传输",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
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
                    .clickable { previewingFile = current }
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
                        "正在传输 (${session.currentFileIndex + 1}/${session.files.size}): ${current.name}"
                    } else {
                        "正在传输: ${current.name}"
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
                if (session.remainingTimeFormatted.isNotEmpty()) {
                    Text(
                        text = " • ${session.remainingTimeFormatted}",
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
                text = "文件清单 (${session.files.count { it.status == TransferStatus.Completed }}/${session.files.size})",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "收起" else "展开",
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
                session.files.forEach { file ->
                    androidx.compose.runtime.key(file.id) {
                        FileDetailItem(
                            file = file,
                            onClick = { previewingFile = file }
                        )
                    }
                }
            }
        }
    }

    previewingFile?.let { file ->
        FilePreviewDialog(
            file = file,
            onDismissRequest = { previewingFile = null }
        )
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
                text = if (file.isTextMessage) "纯文本消息" else file.name,
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
                        text = "${FileItem.formatFileSize(file.speed)}/s",
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
                contentDescription = "已完成",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(18.dp)
            )
            TransferStatus.InProgress -> Text(
                text = "${(file.progress * 100).toInt()}%",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary
            )
            TransferStatus.Failed -> Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "失败",
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            TransferStatus.WaitingApproval, TransferStatus.Canceled -> Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = "等待中",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}