// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.countCategories
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.EmptyStateCard
import org.localsend.miuix.ui.component.FilePreviewDialog
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.InlineTransferProgress
import org.localsend.miuix.ui.component.SectionCard
import org.localsend.miuix.ui.component.TransferSessionCard
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 首页所有交互操作动作回调集合，收拢零散参数并消除 Data Clumps。 */
data class HomeActions(
    val onOpenRenameDialog: () -> Unit,
    val onOpenAddSheet: () -> Unit,
    val onPickFiles: () -> Unit,
    val onPickFolder: () -> Unit,
    val onPickMedia: () -> Unit,
    val onPickApps: () -> Unit,
    val onSendText: () -> Unit,
    val onPasteClipboard: () -> Unit,
    val onOpenWebShare: () -> Unit,
    val onManualIp: () -> Unit,
)

/**
 * 首页（第 0 页）：本机状态、正在接收的传输、待发送队列、快速选择内容与附近设备。
 * 发送与接收合并为一页：接收是被动能力，只在有任务时出现卡片，接收策略开关统一放在设置页。
 */
@Composable
fun HomeScreen(
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
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    val activeSessions by manager.activeSessions.collectAsStateWithLifecycle()
    val shares by manager.shares.collectAsStateWithLifecycle()
    val incomingSessions = remember(activeSessions) { activeSessions.filter { it.isIncoming } }
    val outgoingSessions = remember(activeSessions) { activeSessions.filter { !it.isIncoming } }
    val nonNearbySessions =
        remember(outgoingSessions, nearbyDevices, favoriteDeviceList) {
            val allKnown = favoriteDeviceList + nearbyDevices
            outgoingSessions.filter { session ->
                allKnown.none { it.matches(session.device) }
            }
        }

    // 本机监听地址：端口取自实际绑定值，改端口后立即跟随
    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val displayPort = if (boundPort > 0) boundPort else settings.port

    val totalSelectedSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    // 只在这里算计数；文案用 stringResource 在组合期读取，切换系统语言后标签才会跟着刷新
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
    // 每次下拉自增，允许扫描进行中再次下拉时重启一轮刷新
    var refreshRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) return@LaunchedEffect
        // 由扫描完成信号驱动收起：正常结束、异常与「无可用网卡」早退都会回调完成态，
        // 不再用固定 8 秒计时器兜底（那会掩盖扫描状态未复位的问题）
        manager.refreshDevicesAwait()
        isRefreshing = false
    }

    var previewingIndex by remember { mutableIntStateOf(-1) }
    var isFileListExpanded by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()

    val refreshPull = stringResource(R.string.send_pull_refresh_pull)
    val refreshRelease = stringResource(R.string.send_pull_refresh_release)
    val refreshRefreshing = stringResource(R.string.send_pull_refresh_refreshing)
    val refreshComplete = stringResource(R.string.send_pull_refresh_complete)

    val backdrop = rememberBlurBackdrop()
    val colorScheme = MiuixTheme.colorScheme

    val sendTo: (Device) -> Unit = { device ->
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
        } else {
            manager.sendFilesTo(device)
            Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, device.alias), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(R.string.app_name),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                    actions = {
                        IconButton(onClick = actions.onManualIp) {
                            Icon(imageVector = AppIcons.Edit, contentDescription = stringResource(R.string.action_input_ip))
                        }
                        IconButton(
                            onClick = {
                                manager.refreshDevices()
                                Toast.makeText(context, context.getString(R.string.toast_multicast_sent), Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(imageVector = AppIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
                    .blurBackdropSource(backdrop),
        ) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    refreshRequest++
                },
                pullToRefreshState = pullToRefreshState,
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                topAppBarScrollBehavior = scrollBehavior,
                refreshTexts = listOf(refreshPull, refreshRelease, refreshRefreshing, refreshComplete),
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Section 1: 本机状态（可被发现的地址 + 接收状态）
                    item {
                        SectionCard(title = stringResource(R.string.receive_section_local_device)) {
                            ArrowPreference(
                                title = settings.alias,
                                startAction = {
                                    Icon(
                                        imageVector = AppIcons.localDeviceIcon(),
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = actions.onOpenRenameDialog,
                            )
                            ArrowPreference(
                                title = "$primaryIp:$displayPort",
                                summary = if (localIps.size > 1) localIps.joinToString(", ") else null,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("LocalSend IP", "$primaryIp:$displayPort")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                },
                            )
                            LocalStatusRow(
                                receivingCount = incomingSessions.size,
                                quickSave = settings.quickSave,
                            )
                        }
                    }

                    // Section 2: 正在接收的传输（仅有任务时出现）
                    if (incomingSessions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SmallTitle(text = stringResource(R.string.receive_section_incoming_count, incomingSessions.size))
                        }
                        items(incomingSessions, key = { it.sessionId }) { session ->
                            TransferSessionCard(
                                session = session,
                                onCancel = { manager.cancelTransfer(session.sessionId) },
                                onAccept = { manager.acceptAllIncomingTransfer(session.sessionId) },
                            )
                        }
                    }

                    // Section 3: 待发送内容队列（仅在选择内容后显示）
                    if (selectedFiles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionCard(title = stringResource(R.string.send_section_selected_files)) {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(16.dp)
                                            .animateContentSize(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            // 条目数、体积与分类合成一行，避免头部堆叠三行重复报数
                                            val summaryHeader =
                                                stringResource(R.string.send_selected_summary_header, selectedFiles.size, FileItem.formatFileSize(totalSelectedSize))
                                            Text(
                                                text = if (categoryBreakdown.isEmpty()) summaryHeader else summaryHeader + " · " + categoryBreakdown.joinToString(" · "),
                                                style = MiuixTheme.textStyles.headline1,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { previewingIndex = 0 },
                                                colors = ButtonDefaults.buttonColors(),
                                                modifier = Modifier.defaultMinSize(minWidth = 56.dp),
                                            ) {
                                                Text(stringResource(R.string.btn_preview))
                                            }
                                            Button(
                                                onClick = actions.onOpenAddSheet,
                                                colors = ButtonDefaults.buttonColors(),
                                                modifier = Modifier.defaultMinSize(minWidth = 56.dp),
                                            ) {
                                                Text(stringResource(R.string.btn_add))
                                            }
                                            Button(
                                                onClick = { manager.clearFiles() },
                                                colors = ButtonDefaults.buttonColors(),
                                                modifier = Modifier.defaultMinSize(minWidth = 56.dp),
                                            ) {
                                                Text(stringResource(R.string.btn_clear))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val filesToDisplay =
                                        if (selectedFiles.size > 3 && !isFileListExpanded) {
                                            selectedFiles.take(3)
                                        } else {
                                            selectedFiles
                                        }

                                    filesToDisplay.forEach { file ->
                                        androidx.compose.runtime.key(file.id) {
                                            val actualIndex = selectedFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable(role = Role.Button) { previewingIndex = actualIndex }
                                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                FileThumbnail(
                                                    file = file,
                                                    size = 44.dp,
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (file.isTextMessage) stringResource(R.string.send_type_text_message) else file.name,
                                                        style = MiuixTheme.textStyles.body1,
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
                                                        style = MiuixTheme.textStyles.footnote1,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { manager.removeFile(file.id) },
                                                ) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Delete,
                                                        contentDescription = stringResource(R.string.btn_delete),
                                                        tint = MiuixTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (selectedFiles.size > 3) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(role = Role.Button) { isFileListExpanded = !isFileListExpanded }
                                                    .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = if (isFileListExpanded) stringResource(R.string.send_collapse_files) else stringResource(R.string.send_expand_files, selectedFiles.size - 3),
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = if (isFileListExpanded) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = if (isFileListExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                                                contentDescription = if (isFileListExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                                tint = if (isFileListExpanded) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: 快速选择内容（6 类入口）
                    item {
                        SectionCard(title = stringResource(R.string.send_section_quick_pick)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_files),
                                        icon = MiuixIcons.Folder,
                                        onClick = actions.onPickFiles,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_folder),
                                        icon = MiuixIcons.AddFolder,
                                        onClick = actions.onPickFolder,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_media),
                                        icon = MiuixIcons.Image,
                                        onClick = actions.onPickMedia,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_apps),
                                        icon = AppIcons.App,
                                        onClick = actions.onPickApps,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_text),
                                        icon = AppIcons.Text,
                                        onClick = actions.onSendText,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_clipboard),
                                        icon = AppIcons.Paste,
                                        onClick = actions.onPasteClipboard,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // Section 5: Web Share 入口
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            ArrowPreference(
                                title = if (shares.isNotEmpty()) stringResource(R.string.send_web_share_running_title, shares.first().files.size) else stringResource(R.string.send_web_share_idle_title),
                                startAction = {
                                    Icon(
                                        imageVector = AppIcons.Web,
                                        contentDescription = null,
                                        tint = if (shares.isNotEmpty()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = actions.onOpenWebShare,
                            )
                        }
                    }

                    // Section 6: 历史一键重发的目标设备
                    if (targetResendDevice != null) {
                        val resendDev = targetResendDevice!!
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionCard(title = stringResource(R.string.history_action_resend)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ArrowPreference(
                                        title = resendDev.alias,
                                        summary = resendDev.displaySummary,
                                        startAction = {
                                            Icon(
                                                imageVector = AppIcons.getDeviceIcon(resendDev.deviceType),
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        endActions = {
                                            IconButton(onClick = { manager.clearTargetResendDevice() }) {
                                                Icon(
                                                    imageVector = MiuixIcons.Close,
                                                    contentDescription = stringResource(R.string.btn_cancel),
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            manager.clearTargetResendDevice()
                                            sendTo(resendDev)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Section 7: 收藏设备
                    if (favoriteDeviceList.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SmallTitle(text = stringResource(R.string.send_favorites_title))
                        }
                        items(favoriteDeviceList, key = { "fav_" + it.identityKey }) { device ->
                            DeviceItemCard(
                                device = device,
                                isFavorite = true,
                                outgoingSessions = outgoingSessions,
                                onToggleFavorite = { manager.toggleFavorite(device) },
                                onSend = { sendTo(device) },
                                onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                            )
                        }
                    }

                    // Section 8: 附近设备
                    val totalDeviceCount = nonFavoriteNearbyDevices.size + nonNearbySessions.size
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        SmallTitle(text = stringResource(R.string.send_section_nearby_devices, totalDeviceCount))
                    }

                    if (isScanning) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.send_scanning_subnet_hint),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
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
                            EmptyStateCard(
                                icon = AppIcons.SearchDevice,
                                title = stringResource(R.string.send_searching_devices_title),
                                iconTint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                titleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        items(nonFavoriteNearbyDevices, key = { it.identityKey }) { device ->
                            DeviceItemCard(
                                device = device,
                                isFavorite = false,
                                outgoingSessions = outgoingSessions,
                                onToggleFavorite = { manager.toggleFavorite(device) },
                                onSend = { sendTo(device) },
                                onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                            )
                        }

                        // 针对手动输入 IP 发起、不在扫描列表中的目标设备，同样在同 Card 下内嵌进度
                        if (nonNearbySessions.isNotEmpty()) {
                            items(nonNearbySessions, key = { it.sessionId }) { session ->
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        BasicComponent(
                                            title = session.device.alias,
                                            summary = session.device.displaySummary,
                                            startAction = {
                                                Icon(
                                                    imageVector = AppIcons.getDeviceIcon(session.device.deviceType),
                                                    contentDescription = null,
                                                    tint = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            },
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
            }
        }
    }

    if (previewingIndex >= 0 && previewingIndex < selectedFiles.size) {
        FilePreviewDialog(
            files = selectedFiles,
            initialIndex = previewingIndex,
            onRemoveFile = { fileToRemove ->
                manager.removeFile(fileToRemove.id)
            },
            onDismissRequest = { previewingIndex = -1 },
        )
    }
}

/** 本机接收状态行：左侧状态点 + 接收中/待接收文案，右侧展示当前接收策略。 */
@Composable
private fun LocalStatusRow(
    receivingCount: Int,
    quickSave: Boolean,
) {
    val receiving = receivingCount > 0
    val statusColor = if (receiving) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            style = MiuixTheme.textStyles.footnote1,
            color = statusColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (quickSave) stringResource(R.string.home_receive_mode_auto) else stringResource(R.string.home_receive_mode_confirm),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeviceItemCard(
    device: Device,
    isFavorite: Boolean,
    outgoingSessions: List<TransferSession>,
    onToggleFavorite: () -> Unit,
    onSend: () -> Unit,
    onCancelTransfer: (String) -> Unit,
) {
    val deviceSessions =
        remember(outgoingSessions, device) {
            outgoingSessions.filter { it.device.matches(device) }
        }
    val networkLabel =
        if (device.alternateIps.isNotEmpty()) {
            stringResource(R.string.send_device_multi_subnet_tag, device.alternateIps.size)
        } else {
            ""
        }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = device.alias,
                summary = device.displaySummaryWithExtra(networkLabel),
                startAction = {
                    Icon(
                        imageVector = AppIcons.getDeviceIcon(device.deviceType),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                endActions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                            contentDescription = if (isFavorite) stringResource(R.string.send_action_unfavorite) else stringResource(R.string.send_action_favorite),
                            tint = if (isFavorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
                onClick = onSend,
            )
            if (deviceSessions.isNotEmpty()) {
                for (session in deviceSessions) {
                    InlineTransferProgress(
                        session = session,
                        onCancel = { onCancelTransfer(session.sessionId) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
