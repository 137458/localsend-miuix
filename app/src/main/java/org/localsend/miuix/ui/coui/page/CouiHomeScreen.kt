package org.localsend.miuix.ui.coui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.countCategories
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.FilePreviewDialog
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.InlineTransferProgress
import org.localsend.miuix.ui.coui.component.CouiRowSummary
import org.localsend.miuix.ui.coui.component.CouiRowTitle
import org.localsend.miuix.ui.screen.HomeActions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** ColorOS 首页的圆角半径：卡片与队列容器用大圆角，胶囊按钮用半高圆角。 */
private val CouiHomeCardRadius = 20.dp

/** 卡片内行与行之间那条半分隔线的高度。 */
private val CouiHomeDividerHeight = 0.5.dp

/**
 * 首页（ColorOS 皮肤）：本机状态、正在接收的传输、待发送队列、快速选择与附近设备。
 *
 * 骨架、卡片容器与行式列表按 coUI 的材质自绘（coUI-Kit 没有 Card/Icon/IconButton 与
 * 进度条，只有材质与 layout 原语）；缩略图、进度与预览弹窗继续复用既有组件，
 * 它们不参与皮肤外观，重复实现会扩散维护面。
 */
@Composable
fun CouiHomeScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    actions: HomeActions,
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val selectedFiles by manager.selectedFiles.collectAsStateWithLifecycle()
    val nearbyDevices by manager.nearbyDevices.collectAsStateWithLifecycle()
    val favoriteDevices by manager.favoriteDevices.collectAsStateWithLifecycle()
    val targetResendDevice by manager.targetResendDevice.collectAsStateWithLifecycle()
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    val activeSessions by manager.activeSessions.collectAsStateWithLifecycle()
    val shares by manager.shares.collectAsStateWithLifecycle()

    // 收藏设备优先用扫描到的实时对象（其地址与端口才是当前可达的），扫描不到才回落持久化记录
    val favoriteDeviceList =
        remember(favoriteDevices, nearbyDevices, targetResendDevice) {
            val list =
                favoriteDevices.map { fav ->
                    nearbyDevices.firstOrNull { nearby -> fav.matches(nearby) } ?: fav.toDevice()
                }
            val resend = targetResendDevice
            if (resend != null) list.filterNot { it.matches(resend) } else list
        }
    val nonFavoriteNearbyDevices =
        remember(favoriteDevices, nearbyDevices, targetResendDevice) {
            val list =
                nearbyDevices.filterNot { nearby ->
                    favoriteDevices.any { fav -> fav.matches(nearby) }
                }
            val resend = targetResendDevice
            if (resend != null) list.filterNot { it.matches(resend) } else list
        }
    val incomingSessions = remember(activeSessions) { activeSessions.filter { it.isIncoming } }
    val outgoingSessions = remember(activeSessions) { activeSessions.filter { !it.isIncoming } }
    val nonNearbySessions =
        remember(outgoingSessions, nearbyDevices, favoriteDeviceList) {
            val allKnown = favoriteDeviceList + nearbyDevices
            outgoingSessions.filter { session ->
                allKnown.none { it.matches(session.device) }
            }
        }

    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val displayPort = if (boundPort > 0) boundPort else settings.port

    val totalSelectedSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val categoryCounts = remember(selectedFiles) { selectedFiles.countCategories() }
    val imagesLabel = stringResource(R.string.send_cat_images_count, categoryCounts.images)
    val videosLabel = stringResource(R.string.send_cat_videos_count, categoryCounts.videos)
    val audiosLabel = stringResource(R.string.send_cat_audios_count, categoryCounts.audios)
    val textsLabel = stringResource(R.string.send_cat_texts_count, categoryCounts.texts)
    val apksLabel = stringResource(R.string.send_cat_apks_count, categoryCounts.apks)
    val othersLabel = stringResource(R.string.send_cat_others_count, categoryCounts.others)
    val categoryBreakdown =
        remember(categoryCounts, imagesLabel, videosLabel, audiosLabel, textsLabel, apksLabel, othersLabel) {
            buildList {
                if (categoryCounts.images > 0) add(imagesLabel)
                if (categoryCounts.videos > 0) add(videosLabel)
                if (categoryCounts.audios > 0) add(audiosLabel)
                if (categoryCounts.texts > 0) add(textsLabel)
                if (categoryCounts.apks > 0) add(apksLabel)
                if (categoryCounts.others > 0) add(othersLabel)
            }
        }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var previewingIndex by remember { mutableIntStateOf(-1) }
    var isFileListExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) return@LaunchedEffect
        manager.refreshDevicesAwait()
        isRefreshing = false
    }

    val sendTo: (Device) -> Unit = { device ->
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
        } else {
            manager.sendFilesTo(device)
            Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, device.alias), Toast.LENGTH_SHORT).show()
        }
    }

    val maybeOnRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            refreshRequest++
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CouiTheme.colorScheme.background)
                .statusBarsPadding(),
    ) {
        CouiHomeTopBar(
            isRefreshing = isRefreshing,
            onOpenManualIp = actions.onManualIp,
            onRefresh = {
                maybeOnRefresh()
                manager.refreshDevices()
                Toast.makeText(context, context.getString(R.string.toast_multicast_sent), Toast.LENGTH_SHORT).show()
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 本机状态：可被发现的地址 + 接收策略
            item {
                CouiHomeSection(title = stringResource(R.string.receive_section_local_device)) {
                    CouiHomeRow(
                        icon = AppIcons.localDeviceIcon(),
                        title = settings.alias,
                        onClick = actions.onOpenRenameDialog,
                    )
                    CouiHomeDivider()
                    CouiHomeRow(
                        title = "$primaryIp:$displayPort",
                        summary = if (localIps.size > 1) localIps.joinToString(", ") else null,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("LocalSend IP", "$primaryIp:$displayPort")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        },
                    )
                    CouiHomeDivider()
                    CouiLocalStatusRow(
                        receivingCount = incomingSessions.size,
                        quickSave = settings.quickSave,
                    )
                }
            }

            // 2. 正在接收的传输：无任务时不渲染标题与卡片
            if (incomingSessions.isNotEmpty()) {
                items(incomingSessions, key = { "in_" + it.sessionId }) { session ->
                    CouiHomeSection(title = stringResource(R.string.receive_section_incoming_count, incomingSessions.size)) {
                        CouiHomeRow(
                            icon = AppIcons.getDeviceIcon(session.device.deviceType),
                            title = session.device.alias,
                            summary = session.device.displaySummary,
                        )
                        InlineTransferProgress(
                            session = session,
                            onCancel = { manager.cancelTransfer(session.sessionId) },
                            onAccept = { manager.acceptAllIncomingTransfer(session.sessionId) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            // 3. 待发送队列：头部三按钮 + 文件行，超过三条折叠
            if (selectedFiles.isNotEmpty()) {
                item {
                    CouiHomeSection(title = stringResource(R.string.send_section_selected_files)) {
                        Column(
                            modifier =
                                Modifier
                                    .padding(14.dp)
                                    .animateContentSize(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val summaryHeader =
                                        stringResource(
                                            R.string.send_selected_summary_header,
                                            selectedFiles.size,
                                            FileItem.formatFileSize(totalSelectedSize),
                                        )
                                    Text(
                                        text = if (categoryBreakdown.isEmpty()) summaryHeader else summaryHeader + " · " + categoryBreakdown.joinToString(" · "),
                                        kind = CouiTextKind.ItemTitle,
                                        color = CouiTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CouiPillButton(text = stringResource(R.string.btn_preview)) { previewingIndex = 0 }
                                    CouiPillButton(text = stringResource(R.string.btn_add), onClick = actions.onOpenAddSheet)
                                    CouiPillButton(text = stringResource(R.string.btn_clear)) { manager.clearFiles() }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val filesToDisplay =
                                if (selectedFiles.size > 3 && !isFileListExpanded) {
                                    selectedFiles.take(3)
                                } else {
                                    selectedFiles
                                }
                            filesToDisplay.forEach { file ->
                                key(file.id) {
                                    val actualIndex = selectedFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                    CouiSelectedFileRow(
                                        file = file,
                                        onOpenPreview = { previewingIndex = actualIndex },
                                        onRemove = { manager.removeFile(file.id) },
                                    )
                                }
                            }

                            if (selectedFiles.size > 3) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(CouiShape(12.dp, 3f))
                                            .pointerInput(Unit) { detectTapGestures(onTap = { isFileListExpanded = !isFileListExpanded }) }
                                            .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val expandTint =
                                        if (isFileListExpanded) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.accentOnSurface
                                    Text(
                                        text =
                                            if (isFileListExpanded) {
                                                stringResource(R.string.send_collapse_files)
                                            } else {
                                                stringResource(R.string.send_expand_files, selectedFiles.size - 3)
                                            },
                                        kind = CouiTextKind.GroupFooter,
                                        color = expandTint,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isFileListExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                                        contentDescription = null,
                                        tint = expandTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 快速选择内容：两行三列
            item {
                CouiHomeSection(title = stringResource(R.string.send_section_quick_pick)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_files),
                                icon = MiuixIcons.Folder,
                                onClick = actions.onPickFiles,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_folder),
                                icon = MiuixIcons.AddFolder,
                                onClick = actions.onPickFolder,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_media),
                                icon = MiuixIcons.Image,
                                onClick = actions.onPickMedia,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_apps),
                                icon = AppIcons.App,
                                onClick = actions.onPickApps,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_text),
                                icon = AppIcons.Text,
                                onClick = actions.onSendText,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_clipboard),
                                icon = AppIcons.Paste,
                                onClick = actions.onPasteClipboard,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 5. Web Share 入口
            item {
                CouiHomeSection {
                    CouiHomeRow(
                        icon = AppIcons.Web,
                        title =
                            if (shares.isNotEmpty()) {
                                stringResource(R.string.send_web_share_running_title, shares.first().files.size)
                            } else {
                                stringResource(R.string.send_web_share_idle_title)
                            },
                        iconTint = if (shares.isNotEmpty()) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant,
                        onClick = actions.onOpenWebShare,
                    )
                }
            }

            // 6. 历史一键重发的目标设备
            if (targetResendDevice != null) {
                val resendDev = targetResendDevice!!
                item {
                    CouiHomeSection(title = stringResource(R.string.history_action_resend)) {
                        CouiHomeRow(
                            icon = AppIcons.getDeviceIcon(resendDev.deviceType),
                            title = resendDev.alias,
                            summary = resendDev.displaySummary,
                            trailing = {
                                CouiIconAction(
                                    icon = MiuixIcons.Close,
                                    contentDescription = stringResource(R.string.btn_cancel),
                                    tint = CouiTheme.colorScheme.onSurfaceVariant,
                                    onClick = { manager.clearTargetResendDevice() },
                                )
                            },
                            onClick = {
                                manager.clearTargetResendDevice()
                                sendTo(resendDev)
                            },
                        )
                    }
                }
            }

            // 7. 收藏设备
            if (favoriteDeviceList.isNotEmpty()) {
                items(favoriteDeviceList, key = { "fav_" + it.identityKey }) { device ->
                    CouiHomeSection(title = stringResource(R.string.send_favorites_title)) {
                        CouiDeviceCardContent(
                            device = device,
                            isFavorite = true,
                            sessions = outgoingSessions,
                            onToggleFavorite = { manager.toggleFavorite(device) },
                            onSend = { sendTo(device) },
                            onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                        )
                    }
                }
            }

            // 8. 附近设备
            val totalDeviceCount = nonFavoriteNearbyDevices.size + nonNearbySessions.size
            item {
                CouiSectionTitle(text = stringResource(R.string.send_section_nearby_devices, totalDeviceCount))
            }

            if (isScanning) {
                item {
                    CouiHomeSection {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.send_scanning_subnet_hint),
                                kind = CouiTextKind.ItemSummary,
                                color = CouiTheme.colorScheme.accentOnSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }

            if (nonFavoriteNearbyDevices.isEmpty() && nonNearbySessions.isEmpty() && !isScanning) {
                item {
                    CouiHomeSection {
                        CouiEmptyState(
                            icon = AppIcons.SearchDevice,
                            title = stringResource(R.string.send_searching_devices_title),
                        )
                    }
                }
            } else {
                items(nonFavoriteNearbyDevices, key = { it.identityKey }) { device ->
                    CouiHomeSection {
                        CouiDeviceCardContent(
                            device = device,
                            isFavorite = false,
                            sessions = outgoingSessions,
                            onToggleFavorite = { manager.toggleFavorite(device) },
                            onSend = { sendTo(device) },
                            onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                        )
                    }
                }
                // 手动输入 IP 起的目标不在扫描列表里，同样在本卡内展示进度
                if (nonNearbySessions.isNotEmpty()) {
                    items(nonNearbySessions, key = { "manual_" + it.sessionId }) { session ->
                        CouiHomeSection {
                            CouiHomeRow(
                                icon = AppIcons.getDeviceIcon(session.device.deviceType),
                                title = session.device.alias,
                                summary = session.device.displaySummary,
                            )
                            InlineTransferProgress(
                                session = session,
                                onCancel = { manager.cancelTransfer(session.sessionId) },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (previewingIndex >= 0 && previewingIndex < selectedFiles.size) {
        FilePreviewDialog(
            files = selectedFiles,
            initialIndex = previewingIndex,
            onRemoveFile = { fileToRemove -> manager.removeFile(fileToRemove.id) },
            onDismissRequest = { previewingIndex = -1 },
        )
    }
}

/** 顶栏：应用名 + 手动输入 IP + 重新扫描。刷新期间图标侧不做进度态，文案由 Toast 兜底。 */
@Composable
private fun CouiHomeTopBar(
    isRefreshing: Boolean,
    onOpenManualIp: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            kind = CouiTextKind.TopBar,
            color = CouiTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        CouiIconAction(
            icon = AppIcons.Edit,
            contentDescription = stringResource(R.string.action_input_ip),
            tint = if (isRefreshing) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.onSurface,
            onClick = onOpenManualIp,
        )
        CouiIconAction(
            icon = AppIcons.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            tint = if (isRefreshing) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.onSurface,
            onClick = onRefresh,
        )
    }
}

/** 分组标题：卡片外的一行小字，与卡片内 16dp 文字缩进对齐。 */
@Composable
private fun CouiSectionTitle(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.GroupTitle,
        color = CouiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** 分组容器：可选标题 + 卡片表面。卡片的圆角、底色与分隔线统一在这里定义。 */
@Composable
private fun CouiHomeSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            CouiSectionTitle(text = title)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(CouiShape(CouiHomeCardRadius, 3f))
                    .background(CouiTheme.colorScheme.card),
            content = content,
        )
    }
}

/** 卡片内半分隔线，左侧留出与文字相同的缩进。 */
@Composable
private fun CouiHomeDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(CouiHomeDividerHeight)
                .background(CouiTheme.colorScheme.outlineVariant),
    )
}

/**
 * 行：可选左图标 + 标题 + 可选说明 + 可选行尾插槽。
 *
 * 整行可点，点击语义挂在行上而不是标题上，避免只有文字区域响应。
 */
@Composable
private fun CouiHomeRow(
    title: String,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    summary: String? = null,
    iconTint: Color = CouiTheme.colorScheme.accentOnSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tapHandler = onClick
    val rowModifier =
        if (tapHandler != null) {
            Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { tapHandler() }) }
        } else {
            Modifier.fillMaxWidth()
        }
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            CouiRowTitle(text = title)
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                CouiRowSummary(text = summary)
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 图标动作：40dp 触控区 + 22dp 图形，用于顶栏与行尾。 */
@Composable
private fun CouiIconAction(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CouiShape(20.dp, 3f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 本机接收状态：左状态点 + 接收中文案，右侧当前接收策略。 */
@Composable
private fun CouiLocalStatusRow(
    receivingCount: Int,
    quickSave: Boolean,
) {
    val receiving = receivingCount > 0
    val statusColor = if (receiving) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (receiving) stringResource(R.string.home_status_receiving, receivingCount) else stringResource(R.string.home_status_ready),
            kind = CouiTextKind.ItemSummary,
            color = statusColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (quickSave) stringResource(R.string.home_receive_mode_auto) else stringResource(R.string.home_receive_mode_confirm),
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内容胶囊按钮：coUI 没有通用按钮，这里按材质画一枚 34dp 高的胶囊。 */
@Composable
private fun CouiPillButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(CouiShape(17.dp, 3f))
                .background(CouiTheme.colorScheme.container)
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
                .defaultMinSize(minWidth = 56.dp, minHeight = 34.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.accentOnSurface,
            maxLines = 1,
        )
    }
}

/** 快速选择格：图标在上、文案在下，占满所属列宽。 */
@Composable
private fun CouiQuickAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(CouiShape(16.dp, 3f))
                .background(CouiTheme.colorScheme.container)
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
                .defaultMinSize(minHeight = 72.dp)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = CouiTheme.colorScheme.accentOnSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 待发送队列里的文件行：缩略图 + 名称/体积 + 删除。 */
@Composable
private fun CouiSelectedFileRow(
    file: FileItem,
    onOpenPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CouiShape(12.dp, 3f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenPreview() }) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumbnail(file = file, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (file.isTextMessage) stringResource(R.string.send_type_text_message) else file.name,
                kind = CouiTextKind.ItemTitle,
                color = CouiTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    if (file.isTextMessage) {
                        (file.textContent?.take(30)?.replace("\n", " ") ?: "") + " • " + file.formattedSize
                    } else {
                        file.formattedSize
                    },
                kind = CouiTextKind.ItemSummary,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CouiIconAction(
            icon = MiuixIcons.Delete,
            contentDescription = stringResource(R.string.btn_delete),
            tint = MiuixTheme.colorScheme.error,
            onClick = onRemove,
        )
    }
}

/** 设备卡内容：别名与摘要行 + 收藏按钮 + 该设备当前会话的内嵌进度。 */
@Composable
private fun CouiDeviceCardContent(
    device: Device,
    isFavorite: Boolean,
    sessions: List<TransferSession>,
    onToggleFavorite: () -> Unit,
    onSend: () -> Unit,
    onCancelTransfer: (String) -> Unit,
) {
    val deviceSessions =
        remember(sessions, device) {
            sessions.filter { it.device.matches(device) }
        }
    val networkLabel =
        if (device.alternateIps.isNotEmpty()) {
            stringResource(R.string.send_device_multi_subnet_tag, device.alternateIps.size)
        } else {
            ""
        }
    CouiHomeRow(
        icon = AppIcons.getDeviceIcon(device.deviceType),
        title = device.alias,
        summary = device.displaySummaryWithExtra(networkLabel),
        trailing = {
            CouiIconAction(
                icon = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = if (isFavorite) stringResource(R.string.send_action_unfavorite) else stringResource(R.string.send_action_favorite),
                tint = if (isFavorite) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleFavorite,
            )
        },
        onClick = onSend,
    )
    for (session in deviceSessions) {
        InlineTransferProgress(
            session = session,
            onCancel = { onCancelTransfer(session.sessionId) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/** 空态：一个居中图标加一行提示，不占满整屏。 */
@Composable
private fun CouiEmptyState(
    icon: ImageVector,
    title: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
        )
    }
}
