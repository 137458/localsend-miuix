package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.HistoryFileEntry
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.util.ThumbnailHelper
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDetailDialog(
    item: TransferHistoryItem?,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: (String) -> Unit
) {
    if (item == null) return
    val context = LocalContext.current
    val formattedTime = remember(item.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    }

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (item.isTextMessage) "文本消息详情" else "传输记录详情"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Section 1: Device & Status Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.isIncoming) "发送方: ${item.deviceAlias}" else "接收方: ${item.deviceAlias}",
                            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = when (item.status) {
                                TransferStatus.Completed -> "已完成"
                                TransferStatus.Failed -> "失败"
                                TransferStatus.Canceled -> "已取消"
                                else -> "处理中"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = when (item.status) {
                                TransferStatus.Completed -> Color(0xFF16A34A)
                                TransferStatus.Failed -> Color(0xFFDC2626)
                                else -> Color(0xFFF59E0B)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "设备 IP",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = item.deviceIp,
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "时间",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = formattedTime,
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "总计",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = "${item.fileCount} 项 · ${item.formattedSize}",
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Text Content or File List
            if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                Text(
                    text = "文本内容",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        val detectedUrl = remember(item.textContent) {
                            val trimmed = item.textContent.trim()
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

                        Text(
                            text = item.textContent,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Text", item.textContent))
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                colors = if (detectedUrl != null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("复制完整文本")
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
                                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
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
            } else if (item.fileEntries.isNotEmpty()) {
                Text(
                    text = "包含文件 (${item.fileEntries.size})",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(item.fileEntries) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    file.uri?.let { uri ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, file.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法打开此文件", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HistoryFileThumbnailView(
                                    file = file,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MiuixTheme.textStyles.body2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FileItem.formatFileSize(file.size),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Delete / Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDelete(item.id)
                        onDismissRequest()
                        Toast.makeText(context, "已删除该条记录", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除此记录")
                }

                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun HistoryFileThumbnailView(
    file: HistoryFileEntry,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val context = LocalContext.current
    val isMediaOrApk = ThumbnailHelper.isImage(file.name, file.mimeType) ||
            ThumbnailHelper.isVideo(file.name, file.mimeType) ||
            ThumbnailHelper.isApk(file.name, file.mimeType)

    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, file.name) {
        if (isMediaOrApk) {
            value = withContext(Dispatchers.IO) {
                ThumbnailHelper.loadThumbnail(
                    context,
                    file.name,
                    file.mimeType,
                    file.uri,
                    file.path,
                    (size.value * 2.5f).toInt().coerceAtLeast(96)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = file.name,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (ThumbnailHelper.isVideo(file.name, file.mimeType)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        } else {
            val icon = AppIcons.getFileIcon(file.mimeType, file.name)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
