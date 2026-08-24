package org.localsend.miuix.ui.component

import androidx.compose.animation.AnimatedVisibility
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

/**
 * 传输会话卡片：展示正在收发文件的全景进度（当前文件、总体进度条、已传/总量、百分比、速率、剩余时间与可折叠文件清单）。
 * 发送页与接收页共用，保证两端进度交互体验完全一致。
 */
@Composable
fun TransferSessionCard(
    session: TransferSession,
    onCancel: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.getFileIcon(current.mimeType, current.name),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
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

            // 3. 总体进度条
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = session.progress,
                modifier = Modifier.fillMaxWidth()
            )

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
                        .clickable { isExpanded = !isExpanded }
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
                                FileDetailItem(file = file)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDetailItem(file: FileItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.getFileIcon(file.mimeType, file.name),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
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
                LinearProgressIndicator(
                    progress = file.progress,
                    modifier = Modifier.fillMaxWidth()
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