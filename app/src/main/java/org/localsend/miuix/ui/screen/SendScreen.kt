// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
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
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.EmptyStateCard
import org.localsend.miuix.ui.component.FilePreviewDialog
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.InlineTransferProgress
import org.localsend.miuix.ui.component.SectionCard
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import org.localsend.miuix.util.ThumbnailHelper
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

@Composable
fun SendScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenAddSheet: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onPickApps: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit,
    onOpenWebShare: () -> Unit,
    onManualIp: () -> Unit,
) {
    val context = LocalContext.current
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
    val outgoingSessions = remember(activeSessions) { activeSessions.filter { !it.isIncoming } }
    val nonNearbySessions =
        remember(outgoingSessions, nearbyDevices, favoriteDeviceList) {
            val allKnown = favoriteDeviceList + nearbyDevices
            outgoingSessions.filter { session ->
                allKnown.none { it.matches(session.device) }
            }
        }
    val totalSelectedSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    // 只在这里算计数；文案用 stringResource 在组合期读取，切换系统语言后标签才会跟着刷新
    val categoryCounts =
        remember(selectedFiles) {
            intArrayOf(
                selectedFiles.count { ThumbnailHelper.isImage(it) },
                selectedFiles.count { ThumbnailHelper.isVideo(it) },
                selectedFiles.count { it.mimeType.startsWith("audio/") },
                selectedFiles.count { it.isTextMessage },
                selectedFiles.count { ThumbnailHelper.isApk(it) },
            ).let { counts -> counts + (selectedFiles.size - counts.sum()) }
        }
    val imagesLabel = stringResource(R.string.send_cat_images_count, categoryCounts[0])
    val videosLabel = stringResource(R.string.send_cat_videos_count, categoryCounts[1])
    val audiosLabel = stringResource(R.string.send_cat_audios_count, categoryCounts[2])
    val textsLabel = stringResource(R.string.send_cat_texts_count, categoryCounts[3])
    val apksLabel = stringResource(R.string.send_cat_apks_count, categoryCounts[4])
    val othersLabel = stringResource(R.string.send_cat_others_count, categoryCounts[5])
    val categoryBreakdown =
        buildList {
            if (categoryCounts[0] > 0) add(imagesLabel)
            if (categoryCounts[1] > 0) add(videosLabel)
            if (categoryCounts[2] > 0) add(audiosLabel)
            if (categoryCounts[3] > 0) add(textsLabel)
            if (categoryCounts[4] > 0) add(apksLabel)
            if (categoryCounts[5] > 0) add(othersLabel)
        }

    var isRefreshing by remember { mutableStateOf(false) }
    // 每次下拉自增，允许扫描进行中再次下拉时重启一轮刷新
    var refreshRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) return@LaunchedEffect
        // 由扫描完成信号驱动收起：正常结束、异常与“无可用网卡”早退都会回调完成态，
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(R.string.send_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                    actions = {
                        IconButton(onClick = onManualIp) {
                            Icon(imageVector = AppIcons.Dialpad, contentDescription = stringResource(R.string.action_input_ip))
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
                    // Section 1: Quick Action Grid (6 types)
                    item {
                        SectionCard(title = stringResource(R.string.send_section_quick_pick)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    QuickActionItem(title = stringResource(R.string.send_quick_action_files), icon = MiuixIcons.Folder, onClick = onPickFiles)
                                    QuickActionItem(title = stringResource(R.string.send_quick_action_folder), icon = MiuixIcons.AddFolder, onClick = onPickFolder)
                                    QuickActionItem(title = stringResource(R.string.send_quick_action_media), icon = MiuixIcons.Image, onClick = onPickMedia)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    QuickActionItem(title = stringResource(R.string.send_quick_action_apps), icon = Icons.Default.Android, onClick = onPickApps)
                                    QuickActionItem(title = stringResource(R.string.send_quick_action_text), icon = Icons.Default.TextFields, onClick = onSendText)
                                    QuickActionItem(
                                        title = stringResource(R.string.send_quick_action_clipboard),
                                        icon = Icons.AutoMirrored.Filled.Assignment,
                                        onClick = onPasteClipboard,
                                    )
                                }
                            }
                        }
                    }

                    // Section 1.5: Web Share Trigger
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            ArrowPreference(
                                title = if (shares.isNotEmpty()) stringResource(R.string.send_web_share_running_title, shares.first().files.size) else stringResource(R.string.send_web_share_idle_title),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = if (shares.isNotEmpty()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = onOpenWebShare,
                            )
                        }
                    }

                    // Section 2: Selected Content Queue（仅在选择内容后显示）
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
                                                text = if (categoryBreakdown.isEmpty()) summaryHeader else "$summaryHeader · ${categoryBreakdown.joinToString(" · ")}",
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
                                                onClick = onOpenAddSheet,
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
                                                                "${file.textContent?.take(30)?.replace("\n", " ") ?: ""} • ${file.formattedSize}"
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

                    // Section 2.5: Resend Target Device
                    if (targetResendDevice != null) {
                        val resendDev = targetResendDevice!!
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionCard(title = stringResource(R.string.history_action_resend)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ArrowPreference(
                                        title = resendDev.alias,
                                        summary = "${resendDev.ip}:${resendDev.port} • " + (resendDev.deviceModel ?: resendDev.deviceType.value),
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
                                            if (selectedFiles.isEmpty()) {
                                                Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
                                            } else {
                                                manager.sendFilesTo(resendDev)
                                                Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, resendDev.alias), Toast.LENGTH_SHORT).show()
                                                manager.clearTargetResendDevice()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Favorite Devices
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
                                onSend = {
                                    if (selectedFiles.isEmpty()) {
                                        Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
                                    } else {
                                        manager.sendFilesTo(device)
                                        Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, device.alias), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                            )
                        }
                    }

                    // Section 4: Nearby Devices
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
                                icon = AppIcons.Wifi,
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
                                onSend = {
                                    if (selectedFiles.isEmpty()) {
                                        Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
                                    } else {
                                        manager.sendFilesTo(device)
                                        Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, device.alias), Toast.LENGTH_SHORT).show()
                                    }
                                },
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
                                            summary = "${session.device.ip}:${session.device.port} • ${session.device.deviceModel ?: session.device.deviceType.value}",
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

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
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
                summary = "${device.ip}:${device.port}$networkLabel • ${device.deviceModel ?: device.deviceType.value}",
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
                            imageVector = if (isFavorite) AppIcons.Star else AppIcons.StarBorder,
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
