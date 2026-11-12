// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.coui.dialog

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.Divider
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.localsend.miuix.R
import org.localsend.miuix.manager.AppInfoItem
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.FileThumbnailContent
import org.localsend.miuix.ui.component.MarkdownText
import org.localsend.miuix.ui.component.QrCodeImage
import org.localsend.miuix.ui.component.UPDATE_NOTES_FADE_DP
import org.localsend.miuix.ui.component.UpdatePrimaryAction
import org.localsend.miuix.ui.component.buildUpdateDialogPlan
import org.localsend.miuix.ui.component.detectWebUrlInText
import org.localsend.miuix.ui.component.rememberDialogContentMaxHeight
import org.localsend.miuix.ui.component.updateBrowserUrl
import org.localsend.miuix.ui.component.updateDialogNotesFold
import org.localsend.miuix.ui.component.verticalEdgeFade
import org.localsend.miuix.ui.coui.component.CouiArrowRow
import org.localsend.miuix.ui.coui.component.couiMinTouchTarget
import org.localsend.miuix.ui.coui.theme.couiDangerColor
import org.localsend.miuix.ui.coui.theme.couiSuccessColor
import org.localsend.miuix.ui.util.rememberUiTimeText
import org.localsend.miuix.updates.UpdateDownloadState
import org.localsend.miuix.updates.UpdatePrompt
import org.localsend.miuix.util.InstallHelper
import org.localsend.miuix.util.ThumbnailHelper
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Play
import java.io.File

/** coUI 添加内容弹窗（替代 WindowBottomSheet）。 */
@Composable
internal fun CouiAddContentDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onPickApps: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    CouiContentDialog(
        show = show,
        title = stringResource(R.string.sheet_add_content_title),
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = stringResource(R.string.btn_close),
                onClick = onDismissRequest,
            ),
    ) {
        CouiDialogCard(contentPadding = 0.dp) {
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_file_title),
                onClick = {
                    onDismissRequest()
                    onPickFiles()
                },
            )
            Divider()
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_folder_title),
                onClick = {
                    onDismissRequest()
                    onPickFolder()
                },
            )
            Divider()
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_media_title),
                onClick = {
                    onDismissRequest()
                    onPickMedia()
                },
            )
            Divider()
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_apps_title),
                onClick = {
                    onDismissRequest()
                    onPickApps()
                },
            )
            Divider()
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_text_title),
                onClick = {
                    onDismissRequest()
                    onSendText()
                },
            )
            Divider()
            CouiArrowRow(
                title = stringResource(R.string.sheet_add_clipboard_title),
                onClick = {
                    onDismissRequest()
                    onPasteClipboard()
                },
            )
        }
    }
}

/** coUI 传输历史详情弹窗（保留退场动画，替代 WindowBottomSheet）。 */
@Composable
internal fun CouiHistoryDetailDialog(
    item: TransferHistoryItem?,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: (String) -> Unit,
    onResend: ((TransferHistoryItem) -> Unit)? = null,
) {
    var lastItem by remember { mutableStateOf(item) }
    if (item != null) {
        lastItem = item
    }
    val renderItem = item ?: lastItem
    val context = LocalContext.current
    val primaryActionColors = couiPrimaryActionColors()
    val formattedTime =
        if (renderItem != null) {
            rememberUiTimeText("yyyy-MM-dd HH:mm:ss", renderItem.timestamp)
        } else {
            ""
        }
    val fileListMaxHeight = rememberDialogContentMaxHeight()
    val canResend = onResend != null && renderItem != null

    val confirmAction =
        if (onResend != null && renderItem != null) {
            CouiDialogAction(
                text = stringResource(R.string.history_action_resend),
                onClick = { onResend.invoke(renderItem) },
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            )
        } else {
            CouiDialogAction(
                text = stringResource(R.string.btn_close),
                onClick = onDismissRequest,
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            )
        }

    CouiContentDialog(
        show = show && renderItem != null,
        title =
            if (renderItem?.isTextMessage == true) {
                stringResource(R.string.history_detail_text_title)
            } else {
                stringResource(R.string.history_detail_file_title)
            },
        onDismissRequest = onDismissRequest,
        confirmAction = confirmAction,
        dismissAction =
            if (canResend) {
                CouiDialogAction(
                    text = stringResource(R.string.btn_close),
                    onClick = onDismissRequest,
                )
            } else {
                null
            },
        neutralAction =
            renderItem?.let { current ->
                CouiDialogAction(
                    text = stringResource(R.string.history_detail_btn_delete_record),
                    onClick = {
                        onDelete(current.id)
                        onDismissRequest()
                        Toast.makeText(context, context.getString(R.string.toast_history_item_deleted), Toast.LENGTH_SHORT).show()
                    },
                )
            },
    ) {
        if (renderItem == null) return@CouiContentDialog
        val scheme = CouiTheme.colorScheme
        CouiDialogCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text =
                            if (renderItem.isIncoming) {
                                stringResource(R.string.history_detail_sender, renderItem.deviceAlias)
                            } else {
                                stringResource(R.string.history_detail_receiver, renderItem.deviceAlias)
                            },
                        style =
                            rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
                                color = scheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            when (renderItem.status) {
                                TransferStatus.Completed -> stringResource(R.string.status_completed)
                                TransferStatus.Failed -> stringResource(R.string.status_failed)
                                TransferStatus.Canceled -> stringResource(R.string.status_canceled)
                                else -> stringResource(R.string.status_processing)
                            },
                        kind = CouiTextKind.ItemSummary,
                        color =
                            when (renderItem.status) {
                                TransferStatus.Completed -> couiSuccessColor()
                                TransferStatus.Failed -> couiDangerColor()
                                else -> scheme.onSurfaceVariant
                            },
                        maxLines = 1,
                    )
                }
                CouiDialogInfoRow(label = stringResource(R.string.history_detail_device_ip), value = renderItem.deviceIp)
                CouiDialogInfoRow(label = stringResource(R.string.history_detail_time), value = formattedTime)
                if (!renderItem.isTextMessage) {
                    CouiDialogInfoRow(
                        label = stringResource(R.string.history_detail_total),
                        value = stringResource(R.string.history_detail_total_summary, renderItem.fileCount, renderItem.formattedSize),
                    )
                }
            }
        }

        if (renderItem.isTextMessage && !renderItem.textContent.isNullOrEmpty()) {
            val textContent = renderItem.textContent
            val detectedUrl = remember(textContent) { detectWebUrlInText(textContent) }
            val copyText: () -> Unit = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Text", textContent))
                Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            CouiDialogCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = rememberDialogContentMaxHeight(0.3f, 96.dp, 220.dp))
                                .verticalScroll(rememberScrollState()),
                    ) {
                        SelectionContainer {
                            Text(
                                text = textContent,
                                kind = CouiTextKind.ItemTitle,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        CouiDialogPillButton(
                            text = stringResource(R.string.history_detail_btn_copy_text),
                            onClick = copyText,
                            primary = detectedUrl == null,
                        )
                        if (detectedUrl != null) {
                            CouiDialogPillButton(
                                text = stringResource(R.string.btn_open_link),
                                primary = true,
                                onClick = {
                                    try {
                                        val intent =
                                            Intent(Intent.ACTION_VIEW, Uri.parse(detectedUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } else if (renderItem.fileEntries.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_detail_included_files),
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = fileListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(renderItem.fileEntries) { file ->
                        val interactionSource = remember { MutableInteractionSource() }
                        CouiDialogCard(
                            modifier =
                                Modifier
                                    .couiButtonFeedback(CouiTheme.isDark, interactionSource, list = true)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Button,
                                    ) {
                                        file.uri?.let { uri ->
                                            try {
                                                val intent =
                                                    Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, file.mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.toast_cannot_open_file), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                            contentPadding = 12.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileThumbnailContent(
                                    name = file.name,
                                    mimeType = file.mimeType,
                                    uri = file.uri,
                                    path = file.path,
                                    isTextMessage = false,
                                    stateKey = file.uriString ?: file.name,
                                    size = 36.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        kind = CouiTextKind.ItemTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = FileItem.formatFileSize(file.size),
                                        kind = CouiTextKind.ItemSummary,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                if (InstallHelper.isApk(file.name)) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CouiDialogPillButton(
                                        text = stringResource(R.string.btn_install_apk),
                                        primary = true,
                                        onClick = { launchCouiApkInstall(context, file.path, file.uriString) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchCouiApkInstall(
    context: Context,
    path: String?,
    uriString: String?,
) {
    val intent =
        InstallHelper.buildInstallIntent(context, path, uriString) ?: run {
            Toast.makeText(context, context.getString(R.string.toast_install_source_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
    try {
        context.startActivity(intent)
    } catch (e: SecurityException) {
        Toast.makeText(context, context.getString(R.string.toast_install_permission_needed), Toast.LENGTH_SHORT).show()
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.toast_install_permission_needed), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_cannot_open_file), Toast.LENGTH_SHORT).show()
    }
}

/** coUI 网页快传弹窗（二维码 + 局域网地址选择 + 共享状态）。 */
@Composable
internal fun CouiWebShareDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager,
) {
    val context = LocalContext.current
    val shares by manager.shares.collectAsStateWithLifecycle()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val selectedFiles by manager.selectedFiles.collectAsStateWithLifecycle()

    val isSharing = shares.isNotEmpty()
    val localIps = remember(show) { NetworkUtils.getLocalIpAddresses() }
    var selectedIp by remember(localIps) {
        mutableStateOf(localIps.firstOrNull() ?: "127.0.0.1")
    }
    val urlScheme = if (settings.useHttps) "https" else "http"
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val port = boundPort.takeIf { it > 0 } ?: settings.port
    val shareUrl = "$urlScheme://$selectedIp:$port"
    val copiedToast = stringResource(R.string.webshare_copied_toast)
    val copyUrl = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Web URL", shareUrl))
        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
    }

    val scheme = CouiTheme.colorScheme
    val dangerActionColors = couiPrimaryActionColors(danger = true)
    val primaryActionColors = couiPrimaryActionColors()

    val confirmAction =
        when {
            isSharing ->
                CouiDialogAction(
                    text = stringResource(R.string.webshare_btn_stop_share),
                    onClick = { manager.stopShare() },
                    style = CouiDialogActionStyle.Primary,
                    colors = dangerActionColors,
                )
            selectedFiles.isNotEmpty() ->
                CouiDialogAction(
                    text = stringResource(R.string.webshare_btn_share_selected, selectedFiles.size),
                    onClick = { manager.startShare(selectedFiles) },
                    style = CouiDialogActionStyle.Primary,
                    colors = primaryActionColors,
                )
            else ->
                CouiDialogAction(
                    text = stringResource(R.string.btn_close),
                    onClick = onDismissRequest,
                )
        }

    val dismissAction =
        if (isSharing || selectedFiles.isNotEmpty()) {
            CouiDialogAction(
                text = stringResource(R.string.btn_close),
                onClick = onDismissRequest,
            )
        } else {
            null
        }

    CouiContentDialog(
        show = show,
        title = stringResource(R.string.webshare_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmAction = confirmAction,
        dismissAction = dismissAction,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 二维码需纯白底与静区以保证相机识读率，不随深浅主题翻转
            QrCodeImage(
                content = shareUrl,
                size = 188.dp,
                darkColor = Color.Black,
                lightColor = Color.White,
                modifier =
                    Modifier
                        .clip(CouiShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = shareUrl,
                    style =
                        rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
                            color = scheme.accentOnSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(CouiShape(8.dp))
                            .clickable(role = Role.Button, onClick = copyUrl)
                            .padding(vertical = 8.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = copyUrl)
                            .couiMinTouchTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = stringResource(R.string.webshare_btn_copy_url),
                        tint = scheme.accentOnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (localIps.size > 1) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    localIps.forEach { ip ->
                        val label =
                            when {
                                ip.startsWith("192.168.43.") -> stringResource(R.string.webshare_ip_tag_hotspot, ip)
                                ip == localIps.first() -> stringResource(R.string.webshare_ip_tag_recommended, ip)
                                else -> ip
                            }
                        CouiDialogChip(
                            text = label,
                            selected = ip == selectedIp,
                            onClick = { selectedIp = ip },
                        )
                    }
                }
            }

            if (settings.useHttps) {
                Text(
                    text = stringResource(R.string.webshare_https_warning),
                    kind = CouiTextKind.ItemSummary,
                    color = couiDangerColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isSharing) {
                val currentShare = shares.first()
                CouiDialogCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicText(
                            text = stringResource(R.string.webshare_current_shared_count, currentShare.files.size),
                            style =
                                rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
                                    color = scheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                        currentShare.files.take(5).forEach { file ->
                            CouiDialogInfoRow(
                                label =
                                    if (file.isTextMessage) {
                                        file.textContent?.take(40) ?: stringResource(R.string.send_quick_action_text)
                                    } else {
                                        file.name
                                    },
                                value = file.formattedSize,
                            )
                        }
                        if (currentShare.files.size > 5) {
                            Text(
                                text = stringResource(R.string.webshare_more_items, currentShare.files.size),
                                kind = CouiTextKind.ItemSummary,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object CouiAppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(256) {}

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(
        packageName: String,
        bitmap: ImageBitmap,
    ) {
        cache.put(packageName, bitmap)
    }
}

@Composable
private fun CouiAppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(CouiAppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val cached = CouiAppIconCache.get(packageName)
            if (cached != null) {
                iconBitmap = cached
            } else {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        try {
                            val pm = context.packageManager
                            val drawable = pm.getApplicationIcon(packageName)
                            val bmp = drawable.toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888)
                            bmp.asImageBitmap().also { CouiAppIconCache.put(packageName, it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                iconBitmap = bitmap
            }
        }
    }

    val corner = CouiShape(10.dp)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier.clip(corner),
        )
    } else {
        Box(
            modifier =
                modifier
                    .clip(corner)
                    .background(CouiTheme.colorScheme.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.App,
                contentDescription = null,
                tint = CouiTheme.colorScheme.accentOnSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CouiIndeterminatePulseBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "app_picker_loading")
    val fraction by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "app_picker_loading_offset",
    )
    val scheme = CouiTheme.colorScheme
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CouiCapsuleShape)
                .background(scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.12f else 0.08f)),
    ) {
        val barWidth = maxWidth * 0.35f
        Box(
            modifier =
                Modifier
                    .offset(x = maxWidth * fraction)
                    .width(barWidth)
                    .height(6.dp)
                    .clip(CouiCapsuleShape)
                    .background(scheme.accent),
        )
    }
}

/** coUI 已安装应用多选弹窗（替代 WindowBottomSheet）。 */
@Composable
internal fun CouiAppPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager,
) {
    val coroutineScope = rememberCoroutineScope()
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(show) {
        if (show) {
            if (allApps.isEmpty()) {
                isLoading = true
            }
            allApps = manager.getInstalledApps(forceRefresh = false)
            selectedPackages = emptySet()
            isLoading = false
        }
    }

    val filteredApps =
        remember(allApps, searchQuery, showSystemApps) {
            allApps.filter { app ->
                (showSystemApps || !app.isSystemApp) &&
                    (
                        searchQuery.isEmpty() ||
                            app.label.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                    )
            }
        }

    val isAllFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { selectedPackages.contains(it.packageName) }
    val scheme = CouiTheme.colorScheme
    val primaryActionColors = couiPrimaryActionColors()

    CouiContentDialog(
        show = show,
        title = stringResource(R.string.app_picker_title),
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = stringResource(R.string.app_picker_btn_add_selected, selectedPackages.size),
                onClick = {
                    val selectedApps = allApps.filter { selectedPackages.contains(it.packageName) }
                    manager.addAppsAsFiles(selectedApps)
                    onDismissRequest()
                },
                enabled = selectedPackages.isNotEmpty(),
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            ),
        dismissAction =
            CouiDialogAction(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismissRequest,
            ),
    ) {
        CouiDialogTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(R.string.app_picker_search_label),
            singleLine = true,
            clearContentDescription = stringResource(R.string.btn_clear),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(CouiShape(8.dp))
                            .couiMinTouchTarget()
                            .toggleable(
                                value = showSystemApps,
                                role = Role.Checkbox,
                                onValueChange = { showSystemApps = it },
                            ).padding(end = 6.dp),
                ) {
                    Checkbox(checked = showSystemApps)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.app_picker_show_system_apps),
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_picker_selected_count, selectedPackages.size, filteredApps.size),
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicText(
                        text = if (isRefreshing) stringResource(R.string.app_picker_refreshing) else stringResource(R.string.btn_refresh),
                        style =
                            rememberCouiTextStyle(CouiTextKind.ItemSummary).copy(
                                color = if (isRefreshing) scheme.onSurfaceVariant else scheme.accentOnSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                        modifier =
                            Modifier
                                .clip(CouiShape(8.dp))
                                .clickable(role = Role.Button, enabled = !isRefreshing) {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        allApps = manager.getInstalledApps(forceRefresh = true)
                                        isRefreshing = false
                                    }
                                }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }

            if (filteredApps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val selectAllLabel =
                        when {
                            searchQuery.isNotBlank() && isAllFilteredSelected -> stringResource(R.string.app_picker_deselect_all_filtered, filteredApps.size)
                            searchQuery.isNotBlank() && !isAllFilteredSelected -> stringResource(R.string.app_picker_select_all_filtered, filteredApps.size)
                            isAllFilteredSelected -> stringResource(R.string.app_picker_deselect_all)
                            else -> stringResource(R.string.app_picker_select_all)
                        }
                    BasicText(
                        text = selectAllLabel,
                        style =
                            rememberCouiTextStyle(CouiTextKind.ItemSummary).copy(
                                color = scheme.accentOnSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                        modifier =
                            Modifier
                                .clip(CouiShape(8.dp))
                                .clickable(role = Role.Button) {
                                    selectedPackages =
                                        if (isAllFilteredSelected) {
                                            selectedPackages - filteredApps.map { it.packageName }.toSet()
                                        } else {
                                            selectedPackages + filteredApps.map { it.packageName }
                                        }
                                }.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val listMaxHeight = rememberDialogContentMaxHeight(0.45f, 180.dp, 420.dp)
        val placeholderHeight = listMaxHeight * 0.55f

        if (show && (isLoading || isRefreshing)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(placeholderHeight)
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                CouiIndeterminatePulseBar()
                Text(
                    text = stringResource(R.string.app_picker_refreshing),
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(placeholderHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_picker_empty),
                    kind = CouiTextKind.ItemTitle,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { "app_info_card" },
                ) { app ->
                    val isSelected = selectedPackages.contains(app.packageName)
                    val interactionSource = remember { MutableInteractionSource() }
                    CouiDialogCard(
                        modifier =
                            Modifier
                                .couiButtonFeedback(CouiTheme.isDark, interactionSource, list = true)
                                .toggleable(
                                    value = isSelected,
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        selectedPackages =
                                            if (isSelected) {
                                                selectedPackages - app.packageName
                                            } else {
                                                selectedPackages + app.packageName
                                            }
                                    },
                                ),
                        contentPadding = 12.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isSelected)
                            Spacer(modifier = Modifier.width(10.dp))
                            CouiAppIconImage(
                                packageName = app.packageName,
                                modifier = Modifier.size(38.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(
                                        text = app.label,
                                        style =
                                            rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
                                                color = scheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (app.isSystemApp) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.app_picker_system_tag),
                                            kind = CouiTextKind.ItemSummary,
                                            color = scheme.accentOnSurface,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${app.packageName} · v${app.versionName} · ${FileItem.formatFileSize(app.apkSize)}",
                                    kind = CouiTextKind.ItemSummary,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** coUI 更新弹窗统一接线（保留退场动画）。 */
@Composable
internal fun CouiUpdateDialogHost(
    show: Boolean,
    manager: LocalSendManager,
    releaseInfo: UpdateCheckResult?,
    onDismiss: () -> Unit,
) {
    var lastRelease by remember { mutableStateOf(releaseInfo) }
    if (releaseInfo != null) {
        lastRelease = releaseInfo
    }
    val renderRelease = releaseInfo ?: lastRelease ?: return
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val downloaderState = manager.updates.downloader.state
    val downloadState by downloaderState.collectAsStateWithLifecycle()

    CouiUpdateDialog(
        show = show && releaseInfo != null,
        releaseInfo = renderRelease,
        downloadState = downloadState,
        onDismiss = onDismiss,
        onDownload = {
            val apk = renderRelease.apk
            if (apk != null) {
                manager.updates.downloader.start(apk)
            } else {
                UpdateManager.openInBrowser(context, renderRelease.releaseUrl)
            }
        },
        onInstall = { file -> UpdateManager.installApk(context, file) },
        onCancelDownload = { manager.updates.downloader.cancel() },
        onOpenBrowser = { url -> UpdateManager.openInBrowser(context, url) },
        onIgnore = { version ->
            val next = UpdatePrompt.toggleIgnored(settings.ignoredVersion, version)
            manager.updateSettings { it.copy(ignoredVersion = next) }
            onDismiss()
        },
    )
}

@Composable
private fun CouiUpdateDialog(
    show: Boolean,
    releaseInfo: UpdateCheckResult,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onIgnore: ((version: String) -> Unit)? = null,
) {
    var expanded by remember(releaseInfo.latestVersion) { mutableStateOf(false) }
    val plan =
        remember(releaseInfo, downloadState, onIgnore) {
            buildUpdateDialogPlan(releaseInfo, downloadState, canIgnore = onIgnore != null)
        }
    val notes =
        remember(releaseInfo.changelog, expanded) {
            updateDialogNotesFold(releaseInfo.changelog, expanded)
        }
    val scrollState = rememberScrollState()
    val notesMaxHeight = rememberDialogContentMaxHeight(fraction = 0.4f, min = 150.dp, max = 320.dp)
    val scheme = CouiTheme.colorScheme
    val primaryActionColors = couiPrimaryActionColors()

    fun runPrimary() {
        when (plan.primaryAction) {
            UpdatePrimaryAction.DOWNLOAD, UpdatePrimaryAction.RETRY -> onDownload()
            UpdatePrimaryAction.INSTALL -> (downloadState as? UpdateDownloadState.Done)?.let { onInstall(it.file) }
            UpdatePrimaryAction.OPEN_RELEASE -> onOpenBrowser(releaseInfo.releaseUrl)
        }
    }

    CouiContentDialog(
        show = show,
        title = stringResource(R.string.update_dialog_title),
        message = plan.summary,
        onDismissRequest = onDismiss,
        confirmAction =
            CouiDialogAction(
                text = plan.primaryText,
                onClick = { runPrimary() },
                enabled = plan.primaryEnabled,
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            ),
        dismissAction =
            CouiDialogAction(
                text = plan.dismissText,
                onClick = onDismiss,
            ),
    ) {
        CouiDialogCard(contentPadding = 0.dp) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = notesMaxHeight)
                        .verticalEdgeFade(
                            top = scrollState.canScrollBackward,
                            bottom = scrollState.canScrollForward,
                            fade = UPDATE_NOTES_FADE_DP.dp,
                        ).verticalScroll(scrollState)
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (plan.showProgress) {
                    plan.progressDetail?.let {
                        Text(
                            text = it,
                            kind = CouiTextKind.ItemSummary,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    CouiDialogProgressBar(progress = plan.progressFraction ?: 0f)
                }

                if (downloadState is UpdateDownloadState.Failed) {
                    BasicText(
                        text = downloadState.message,
                        style =
                            rememberCouiTextStyle(CouiTextKind.ItemSummary).copy(
                                color = couiDangerColor(),
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                }

                if (plan.hasNotes) {
                    MarkdownText(
                        markdown = notes.visible,
                        modifier = Modifier.fillMaxWidth(),
                        baseFontSize = 13,
                    )
                    if (notes.hasMore) {
                        BasicText(
                            text =
                                if (expanded) {
                                    stringResource(R.string.update_dialog_notes_collapse)
                                } else {
                                    stringResource(R.string.update_dialog_notes_expand, notes.hiddenLines)
                                },
                            style =
                                rememberCouiTextStyle(CouiTextKind.ItemSummary).copy(
                                    color = scheme.accentOnSurface,
                                    fontWeight = FontWeight.Medium,
                                ),
                            modifier =
                                Modifier
                                    .clip(CouiShape(8.dp))
                                    .clickable(role = Role.Button) { expanded = !expanded }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.update_dialog_no_changelog),
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (plan.showBrowserFallback || plan.showCancelDownload || (plan.showIgnore && onIgnore != null)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (plan.showBrowserFallback) {
                    CouiDialogChip(
                        text = stringResource(R.string.update_dialog_btn_browser),
                        selected = false,
                        onClick = { onOpenBrowser(updateBrowserUrl(releaseInfo)) },
                    )
                }
                if (plan.showCancelDownload) {
                    CouiDialogChip(
                        text = stringResource(R.string.update_dialog_btn_cancel_download),
                        selected = false,
                        onClick = onCancelDownload,
                    )
                }
                if (plan.showIgnore && onIgnore != null) {
                    CouiDialogChip(
                        text = stringResource(R.string.update_dialog_ignore_version),
                        selected = false,
                        onClick = { onIgnore(releaseInfo.latestVersion) },
                    )
                }
            }
        }
    }
}

/** coUI 待发送文件预览弹窗（保留退场动画，支持多文件切换、复制文本与移出队列）。 */
@Composable
internal fun CouiFilePreviewDialog(
    files: List<FileItem>,
    show: Boolean,
    initialIndex: Int = 0,
    onRemoveFile: ((FileItem) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    var lastFiles by remember { mutableStateOf(files) }
    if (show && files.isNotEmpty()) {
        lastFiles = files
    }
    val renderFiles = if (files.isNotEmpty()) files else lastFiles
    if (renderFiles.isEmpty()) return

    val context = LocalContext.current
    var currentIndex by remember(initialIndex, renderFiles.size) {
        mutableIntStateOf(initialIndex.coerceIn(0, renderFiles.size - 1))
    }
    val safeIndex = currentIndex.coerceIn(0, renderFiles.size - 1)
    val file = renderFiles[safeIndex]
    val previewMaxHeight = rememberDialogContentMaxHeight(fraction = 0.4f, min = 120.dp, max = 280.dp)
    val scheme = CouiTheme.colorScheme
    val primaryActionColors = couiPrimaryActionColors()

    CouiContentDialog(
        show = show && files.isNotEmpty(),
        title = if (file.isTextMessage) stringResource(R.string.preview_text_title) else file.name,
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = stringResource(R.string.btn_close),
                onClick = onDismissRequest,
                style = CouiDialogActionStyle.Primary,
                colors = primaryActionColors,
            ),
        dismissAction =
            if (file.isTextMessage && !file.textContent.isNullOrEmpty()) {
                CouiDialogAction(
                    text = stringResource(R.string.preview_btn_copy_content),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend", file.textContent))
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    },
                )
            } else {
                null
            },
        neutralAction =
            if (onRemoveFile != null) {
                CouiDialogAction(
                    text = stringResource(R.string.preview_btn_remove_item),
                    onClick = {
                        val fileToRemove = file
                        if (renderFiles.size <= 1) {
                            onDismissRequest()
                        } else if (safeIndex >= renderFiles.size - 1) {
                            currentIndex = safeIndex - 1
                        }
                        onRemoveFile(fileToRemove)
                    },
                )
            } else {
                null
            },
    ) {
        if (renderFiles.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable(enabled = safeIndex > 0, role = Role.Button) {
                                    if (safeIndex > 0) currentIndex = safeIndex - 1
                                }.couiMinTouchTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.preview_action_prev),
                            tint = if (safeIndex > 0) scheme.accentOnSurface else scheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "${safeIndex + 1} / ${renderFiles.size}",
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable(enabled = safeIndex < renderFiles.size - 1, role = Role.Button) {
                                    if (safeIndex < renderFiles.size - 1) currentIndex = safeIndex + 1
                                }.couiMinTouchTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Forward,
                            contentDescription = stringResource(R.string.preview_action_next),
                            tint = if (safeIndex < renderFiles.size - 1) scheme.accentOnSurface else scheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = file.formattedSize,
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        val isImage = ThumbnailHelper.isImage(file)
        val isVideo = ThumbnailHelper.isVideo(file)

        if (file.isTextMessage) {
            val text = file.textContent ?: ""
            CouiDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = rememberDialogContentMaxHeight(0.35f, 100.dp, 260.dp))
                            .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = text.ifEmpty { stringResource(R.string.preview_empty_text) },
                            kind = CouiTextKind.ItemTitle,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.preview_text_stats, text.length, file.formattedSize),
                kind = CouiTextKind.ItemSummary,
                color = scheme.onSurfaceVariant,
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
                        .clip(CouiShape(16.dp))
                        .background(scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.08f else 0.045f)),
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
                                .clip(CouiShape(16.dp)),
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
                        // 播放键叠于半透黑遮罩上方，白前景与主题无关
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
                kind = CouiTextKind.ItemSummary,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            CouiDialogCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileThumbnail(file = file, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                kind = CouiTextKind.ItemTitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = file.formattedSize,
                                kind = CouiTextKind.ItemSummary,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview_mime_type, file.mimeType),
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurfaceVariant,
                    )
                    if (file.path != null) {
                        Text(
                            text = stringResource(R.string.preview_path, file.path),
                            kind = CouiTextKind.ItemSummary,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (renderFiles.size > 1) {
            val listState = rememberLazyListState()
            LaunchedEffect(safeIndex) {
                listState.animateScrollToItem(safeIndex)
            }
            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(CouiShape(12.dp))
                        .background(scheme.onSurface.copy(alpha = if (CouiTheme.isDark) 0.08f else 0.045f))
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(renderFiles, key = { _, item -> item.id }) { index, item ->
                    val isSelected = index == safeIndex
                    val borderColor = if (isSelected) scheme.accent else Color.Transparent
                    Box(
                        modifier =
                            Modifier
                                .clip(CouiShape(10.dp))
                                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = CouiShape(10.dp))
                                .clickable(role = Role.Button) { currentIndex = index }
                                .padding(3.dp),
                    ) {
                        FileThumbnail(file = item, size = 40.dp)
                    }
                }
            }
        }
    }
}
