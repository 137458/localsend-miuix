package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.util.ThumbnailHelper
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun FileThumbnail(
    file: FileItem,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val context = LocalContext.current
    val isImageOrVideoOrApk = ThumbnailHelper.isImage(file) ||
            ThumbnailHelper.isVideo(file) ||
            ThumbnailHelper.isApk(file)

    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, file.id) {
        if (isImageOrVideoOrApk) {
            value = withContext(Dispatchers.IO) {
                ThumbnailHelper.loadThumbnail(context, file, targetSize = (size.value * 2.5f).toInt().coerceAtLeast(96))
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
            // 视频类型额外显示半透明播放图标标记
            if (ThumbnailHelper.isVideo(file)) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "视频",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            val icon = if (file.isTextMessage) AppIcons.Text else AppIcons.getFileIcon(file.mimeType, file.name)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@Composable
fun FilePreviewDialog(
    file: FileItem?,
    onDismissRequest: () -> Unit
) {
    if (file == null) return
    val context = LocalContext.current

    WindowDialog(
        show = true,
        title = if (file.isTextMessage) "文本内容预览" else file.name,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            if (file.isTextMessage) {
                val text = file.textContent ?: ""
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = text.ifEmpty { "(无文本内容)" },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "长度: ${text.length} 字符 • 大小: ${file.formattedSize}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else if (ThumbnailHelper.isImage(file)) {
                val previewBitmap by produceState<Bitmap?>(initialValue = null, file.id) {
                    value = withContext(Dispatchers.IO) {
                        ThumbnailHelper.loadPreviewImage(context, file, maxDimension = 900)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        FileThumbnail(file = file, size = 64.dp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "文件大小: ${file.formattedSize} • 类型: ${file.mimeType}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // 通用文件详情
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileThumbnail(file = file, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = file.formattedSize,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "MIME 类型: ${file.mimeType}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    if (file.path != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "路径: ${file.path}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                if (file.isTextMessage && !file.textContent.isNullOrEmpty()) {
                    TextButton(
                        text = "复制内容",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend", file.textContent))
                            Toast.makeText(context, "已复制文本到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    text = "关闭",
                    onClick = onDismissRequest,
                    colors = if (file.isTextMessage && !file.textContent.isNullOrEmpty()) ButtonDefaults.textButtonColors() else ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
