// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.util.ThumbnailHelper
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun FileThumbnail(
    file: FileItem,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    FileThumbnailContent(
        name = file.name,
        mimeType = file.mimeType,
        uri = file.uri,
        path = file.path,
        isTextMessage = file.isTextMessage,
        stateKey = file.id,
        modifier = modifier,
        size = size,
    )
}

/**
 * 缩略图渲染的共享实现：供传输中的 [FileItem] 与历史记录条目（字段结构不同）复用。
 * [stateKey] 变化时重新加载缩略图。
 */
@Composable
internal fun FileThumbnailContent(
    name: String,
    mimeType: String,
    uri: Uri?,
    path: String?,
    isTextMessage: Boolean,
    stateKey: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val context = LocalContext.current
    val isImageOrVideoOrApk =
        ThumbnailHelper.isImage(name, mimeType) ||
            ThumbnailHelper.isVideo(name, mimeType) ||
            ThumbnailHelper.isApk(name, mimeType)

    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, stateKey) {
        if (isImageOrVideoOrApk) {
            value =
                withContext(Dispatchers.IO) {
                    ThumbnailHelper.loadThumbnail(
                        context = context,
                        name = name,
                        mimeType = mimeType,
                        uri = uri,
                        path = path,
                        targetSize = (size.value * 2.5f).toInt().coerceAtLeast(96),
                    )
                }
        }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = name,
                modifier =
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(DialogTokens.ChipCorner)),
                contentScale = ContentScale.Crop,
            )
            // 视频类型额外显示半透明播放图标标记
            if (ThumbnailHelper.isVideo(name, mimeType)) {
                Box(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // 播放角标恒定压在 55% 黑色蒙层上，白色是与主题无关的对比色，属蒙层特效而非主题色
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = stringResource(R.string.preview_video_tag),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            val icon = if (isTextMessage) AppIcons.Text else AppIcons.getFileIcon(mimeType, name)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
fun FilePreviewDialog(
    files: List<FileItem>,
    initialIndex: Int = 0,
    onRemoveFile: ((FileItem) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    var currentIndex by remember(files.size) {
        mutableIntStateOf(initialIndex.coerceIn(0, files.size - 1))
    }
    val safeIndex = currentIndex.coerceIn(0, files.size - 1)
    val file = files[safeIndex]

    // 图片/视频预览的高度上限：小屏不压塌，大屏不拉满，避免写死 280.dp
    val previewMaxHeight = rememberDialogContentMaxHeight(fraction = 0.4f, min = 120.dp, max = 280.dp)

    WindowDialog(
        show = true,
        title = if (file.isTextMessage) stringResource(R.string.preview_text_title) else file.name,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            // 1. 预览区：多文件导航条与主体内容同属一个区块，内部只用 BlockGap 分隔
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
            ) {
                // 多文件顶部导航指示条（仅多文件时显示）
                if (files.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = { if (safeIndex > 0) currentIndex = safeIndex - 1 },
                                enabled = safeIndex > 0,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.preview_action_prev),
                                    tint = if (safeIndex > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
                                )
                            }
                            Text(
                                text = "${safeIndex + 1} / ${files.size}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            IconButton(
                                onClick = { if (safeIndex < files.size - 1) currentIndex = safeIndex + 1 },
                                enabled = safeIndex < files.size - 1,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Forward,
                                    contentDescription = stringResource(R.string.preview_action_next),
                                    tint = if (safeIndex < files.size - 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
                                )
                            }
                        }

                        Text(
                            text = file.formattedSize,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 2. 主体预览内容（文本 / 图片 / 视频 / 通用文件）
                val isImage = ThumbnailHelper.isImage(file)
                val isVideo = ThumbnailHelper.isVideo(file)

                if (file.isTextMessage) {
                    val text = file.textContent ?: ""
                    // 文本预览的圆角与内边距交给 DialogCard，超长内容由 DialogBody 限高并内部滚动
                    DialogCard {
                        DialogBody {
                            SelectionContainer {
                                Text(
                                    text = text.ifEmpty { stringResource(R.string.preview_empty_text) },
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview_text_stats, text.length, file.formattedSize),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else if (isImage || isVideo) {
                    val previewBitmap by produceState<Bitmap?>(initialValue = null, file.id) {
                        value =
                            withContext(Dispatchers.IO) {
                                ThumbnailHelper.loadPreviewImage(context, file, maxDimension = 900)
                            }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = previewMaxHeight)
                                .clip(RoundedCornerShape(DialogTokens.CardCorner))
                                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = file.name,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = previewMaxHeight)
                                        .clip(RoundedCornerShape(DialogTokens.CardCorner)),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            FileThumbnail(file = file, size = 64.dp)
                        }

                        if (isVideo) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 播放键恒定压在 55% 黑色蒙层上，白色是与主题无关的对比色，属蒙层特效而非主题色
                                Icon(
                                    imageVector = MiuixIcons.Play,
                                    contentDescription = stringResource(R.string.preview_video_content_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview_file_stats, file.formattedSize, file.mimeType),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // 通用文件详情：统一卡片承载，文件名+体积为一行主信息，MIME 与路径为辅助信息
                    DialogCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileThumbnail(file = file, size = 48.dp)
                            Spacer(modifier = Modifier.width(DialogTokens.BlockGap))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MiuixTheme.textStyles.body1,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = file.formattedSize,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(DialogTokens.BlockGap))
                        Text(
                            text = stringResource(R.string.preview_mime_type, file.mimeType),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (file.path != null) {
                            Spacer(modifier = Modifier.height(DialogTokens.BlockGap))
                            Text(
                                text = stringResource(R.string.preview_path, file.path),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 3. 多文件底部缩略图轮播横条（快速跳转与可视定位）
            if (files.size > 1) {
                val listState = rememberLazyListState()
                LaunchedEffect(safeIndex) {
                    listState.animateScrollToItem(safeIndex)
                }
                LazyRow(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                            .padding(DialogTokens.BlockGap),
                    horizontalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(files, key = { _, item -> item.id }) { index, item ->
                        val isSelected = index == safeIndex
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(DialogTokens.ChipCorner))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(DialogTokens.ChipCorner))
                                        } else {
                                            Modifier.border(1.dp, Color.Transparent, RoundedCornerShape(DialogTokens.ChipCorner))
                                        },
                                    ).clickable(role = Role.Button) { currentIndex = index }
                                    .padding(2.dp),
                        ) {
                            FileThumbnail(file = item, size = 40.dp)
                        }
                    }
                }
            }

            // 4. 动作区：关闭作为主操作整行按钮，复制与移除按条件降级为文字按钮
            DialogActionColumn {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.btn_close))
                }
                if (file.isTextMessage && !file.textContent.isNullOrEmpty()) {
                    DialogTextAction(
                        text = stringResource(R.string.preview_btn_copy_content),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend", file.textContent))
                            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                if (onRemoveFile != null) {
                    DialogTextAction(
                        text = stringResource(R.string.preview_btn_remove_item),
                        onClick = {
                            val fileToRemove = file
                            if (files.size <= 1) {
                                onDismissRequest()
                            } else if (safeIndex >= files.size - 1) {
                                currentIndex = safeIndex - 1
                            }
                            onRemoveFile(fileToRemove)
                        },
                        colors = dialogDangerTextColors(),
                    )
                }
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    file: FileItem?,
    onDismissRequest: () -> Unit,
) {
    if (file == null) return
    FilePreviewDialog(
        files = listOf(file),
        initialIndex = 0,
        onRemoveFile = null,
        onDismissRequest = onDismissRequest,
    )
}
