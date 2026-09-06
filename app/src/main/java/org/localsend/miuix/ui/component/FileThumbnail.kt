package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
    files: List<FileItem>,
    initialIndex: Int = 0,
    onRemoveFile: ((FileItem) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    var currentIndex by remember(files.size) {
        mutableIntStateOf(initialIndex.coerceIn(0, files.size - 1))
    }
    val safeIndex = currentIndex.coerceIn(0, files.size - 1)
    val file = files[safeIndex]

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
            // 1. 多文件顶部导航指示条（仅多文件时显示）
            if (files.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (safeIndex > 0) currentIndex = safeIndex - 1 },
                            enabled = safeIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一个",
                                tint = if (safeIndex > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
                            )
                        }
                        Text(
                            text = "${safeIndex + 1} / ${files.size}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (safeIndex < files.size - 1) currentIndex = safeIndex + 1 },
                            enabled = safeIndex < files.size - 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "下一个",
                                tint = if (safeIndex < files.size - 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
                            )
                        }
                    }

                    Text(
                        text = file.formattedSize,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // 2. 主体预览内容（文本 / 图片 / 视频 / 通用文件）
            val isImage = ThumbnailHelper.isImage(file)
            val isVideo = ThumbnailHelper.isVideo(file)

            if (file.isTextMessage) {
                val text = file.textContent ?: ""
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
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
            } else if (isImage || isVideo) {
                val previewBitmap by produceState<Bitmap?>(initialValue = null, file.id) {
                    value = withContext(Dispatchers.IO) {
                        ThumbnailHelper.loadPreviewImage(context, file, maxDimension = 900)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp)
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
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        FileThumbnail(file = file, size = 64.dp)
                    }

                    if (isVideo) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "视频预览",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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

            // 3. 多文件底部缩略图轮播横条（快速跳转与可视定位）
            if (files.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                val listState = rememberLazyListState()
                LaunchedEffect(safeIndex) {
                    listState.animateScrollToItem(safeIndex)
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(files, key = { _, item -> item.id }) { index, item ->
                        val isSelected = index == safeIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier.border(1.dp, Color.Transparent, RoundedCornerShape(8.dp))
                                    }
                                )
                                .clickable { currentIndex = index }
                                .padding(2.dp)
                        ) {
                            FileThumbnail(file = item, size = 40.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                }
                if (onRemoveFile != null) {
                    TextButton(
                        text = "移除此项",
                        onClick = {
                            val fileToRemove = file
                            if (files.size <= 1) {
                                onDismissRequest()
                            } else if (safeIndex >= files.size - 1) {
                                currentIndex = safeIndex - 1
                            }
                            onRemoveFile(fileToRemove)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(
                    text = "关闭",
                    onClick = onDismissRequest,
                    colors = if (file.isTextMessage && !file.textContent.isNullOrEmpty() && onRemoveFile == null) {
                        ButtonDefaults.textButtonColors()
                    } else {
                        ButtonDefaults.textButtonColorsPrimary()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    file: FileItem?,
    onDismissRequest: () -> Unit
) {
    if (file == null) return
    FilePreviewDialog(
        files = listOf(file),
        initialIndex = 0,
        onRemoveFile = null,
        onDismissRequest = onDismissRequest
    )
}
