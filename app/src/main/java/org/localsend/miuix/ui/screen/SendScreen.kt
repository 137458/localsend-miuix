package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.util.ThumbnailHelper
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.FilePreviewDialog
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.InlineTransferProgress
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
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
    onManualIp: () -> Unit
) {
    val context = LocalContext.current
    val selectedFiles by manager.selectedFiles.collectAsState()
    val nearbyDevices by manager.nearbyDevices.collectAsState()
    val isScanning by manager.isScanning.collectAsState()
    val activeSessions by manager.activeSessions.collectAsState()
    val shares by manager.shares.collectAsState()
    val outgoingSessions = remember(activeSessions) { activeSessions.filter { !it.isIncoming } }
    val nonNearbySessions = remember(outgoingSessions, nearbyDevices) {
        outgoingSessions.filter { session ->
            nearbyDevices.none {
                (it.fingerprint.isNotEmpty() && it.fingerprint == session.device.fingerprint) || it.ip == session.device.ip
            }
        }
    }
    val totalSelectedSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val categoryBreakdown = remember(selectedFiles) {
        val images = selectedFiles.count { ThumbnailHelper.isImage(it) }
        val videos = selectedFiles.count { ThumbnailHelper.isVideo(it) }
        val audios = selectedFiles.count { it.mimeType.startsWith("audio/") }
        val texts = selectedFiles.count { it.isTextMessage }
        val apks = selectedFiles.count { ThumbnailHelper.isApk(it) }
        val others = selectedFiles.size - (images + videos + audios + texts + apks)
        buildList {
            if (images > 0) add("$images 张图片")
            if (videos > 0) add("$videos 个视频")
            if (audios > 0) add("$audios 首音频")
            if (texts > 0) add("$texts 条文本")
            if (apks > 0) add("$apks 个应用")
            if (others > 0) add("$others 个文件")
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var previewingIndex by remember { mutableIntStateOf(-1) }
    var isFileListExpanded by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = "发送",
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(onClick = onManualIp) {
                    Icon(imageVector = AppIcons.Send, contentDescription = "输入IP")
                }
                IconButton(
                    onClick = {
                        manager.refreshDevices()
                        Toast.makeText(context, "已发送局域网发现广播", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = AppIcons.Refresh, contentDescription = "刷新")
                }
            }
        )

        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                manager.refreshDevices()
                manager.scanSubnet()
                isRefreshing = false
            },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "释放立即刷新", "正在刷新...", "刷新完成"),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Quick Action Grid (6 types)
                item {
                    SmallTitle(text = "快速选择内容")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionItem(title = "文件", icon = Icons.Default.Folder, onClick = onPickFiles)
                                QuickActionItem(title = "文件夹", icon = Icons.Default.FolderOpen, onClick = onPickFolder)
                                QuickActionItem(title = "媒体", icon = Icons.Default.Image, onClick = onPickMedia)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionItem(title = "应用", icon = Icons.Default.Android, onClick = onPickApps)
                                QuickActionItem(title = "纯文本", icon = Icons.Default.TextFields, onClick = onSendText)
                                QuickActionItem(
                                    title = "剪贴板",
                                    icon = Icons.AutoMirrored.Filled.Assignment,
                                    onClick = onPasteClipboard
                                )
                            }
                        }
                    }
                }

                // Section 1.5: Web Share Trigger
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = if (shares.isNotEmpty()) "Web 共享正在运行中" else "通过浏览器链接分享 (Web Share)",
                            summary = if (shares.isNotEmpty()) "已有 ${shares.first().files.size} 项正在局域网共享，点击查看链接与二维码" else "无需客户端，任何浏览器扫描二维码或访问链接即可接收",
                            startAction = {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (shares.isNotEmpty()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = onOpenWebShare
                        )
                    }
                }

                // Section 2: Selected Content Queue（仅在选择内容后显示）
                if (selectedFiles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        SmallTitle(text = "待发送内容 (${selectedFiles.size})")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .animateContentSize()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "共 ${selectedFiles.size} 项 (${FileItem.formatFileSize(totalSelectedSize)})",
                                            style = MiuixTheme.textStyles.headline1
                                        )
                                        if (categoryBreakdown.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = categoryBreakdown.joinToString(" · "),
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { previewingIndex = 0 },
                                            colors = ButtonDefaults.buttonColors(),
                                            modifier = Modifier.defaultMinSize(minWidth = 56.dp)
                                        ) {
                                            Text("预览")
                                        }
                                        Button(
                                            onClick = onOpenAddSheet,
                                            colors = ButtonDefaults.buttonColors(),
                                            modifier = Modifier.defaultMinSize(minWidth = 56.dp)
                                        ) {
                                            Text("添加")
                                        }
                                        Button(
                                            onClick = { manager.clearFiles() },
                                            colors = ButtonDefaults.buttonColors(),
                                            modifier = Modifier.defaultMinSize(minWidth = 56.dp)
                                        ) {
                                            Text("清空")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val filesToDisplay = if (selectedFiles.size > 3 && !isFileListExpanded) {
                                    selectedFiles.take(3)
                                } else {
                                    selectedFiles
                                }

                                filesToDisplay.forEach { file ->
                                    androidx.compose.runtime.key(file.id) {
                                        val actualIndex = selectedFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { previewingIndex = actualIndex }
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FileThumbnail(
                                                file = file,
                                                size = 44.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (file.isTextMessage) "纯文本消息" else file.name,
                                                    style = MiuixTheme.textStyles.body1,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (file.isTextMessage) {
                                                        "${file.textContent?.take(30)?.replace("\n", " ") ?: ""} • ${file.formattedSize}"
                                                    } else {
                                                        file.formattedSize
                                                    },
                                                    style = MiuixTheme.textStyles.footnote1,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { manager.removeFile(file.id) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MiuixTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                if (selectedFiles.size > 3) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isFileListExpanded = !isFileListExpanded }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isFileListExpanded) "收起待发送列表" else "展开其余 ${selectedFiles.size - 3} 项文件",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = if (isFileListExpanded) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isFileListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isFileListExpanded) "收起" else "展开",
                                            tint = if (isFileListExpanded) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Nearby Devices
                val totalDeviceCount = nearbyDevices.size + nonNearbySessions.size
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "附近设备 ($totalDeviceCount)")
                }

                if (isScanning) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "正在全网段并发探测设备中...",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }

                if (nearbyDevices.isEmpty() && nonNearbySessions.isEmpty() && !isScanning) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = AppIcons.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "正在搜索同一局域网下的 LocalSend 设备...",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "下拉即可刷新或点击右上角广播",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                } else {
                    items(nearbyDevices, key = { if (it.fingerprint.isNotEmpty()) it.fingerprint else "${it.ip}:${it.port}" }) { device ->
                        val deviceSessions = outgoingSessions.filter {
                            (it.device.fingerprint.isNotEmpty() && it.device.fingerprint == device.fingerprint) || it.device.ip == device.ip
                        }
                        val networkLabel = if (device.alternateIps.isNotEmpty()) {
                            " (+${device.alternateIps.size}个网段)"
                        } else {
                            ""
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = device.alias,
                                    summary = "${device.ip}:${device.port}$networkLabel • ${device.deviceModel ?: device.deviceType.value}",
                                    startAction = {
                                        Icon(
                                            imageVector = AppIcons.getDeviceIcon(device.deviceType),
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    onClick = {
                                        if (selectedFiles.isEmpty()) {
                                            Toast.makeText(context, "请先添加要发送的文件或内容", Toast.LENGTH_SHORT).show()
                                        } else {
                                            manager.sendFilesTo(device)
                                            Toast.makeText(context, "正在向 ${device.alias} 发起传输...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                if (deviceSessions.isNotEmpty()) {
                                    deviceSessions.forEach { session ->
                                        InlineTransferProgress(
                                            session = session,
                                            onCancel = { manager.cancelTransfer(session.sessionId) },
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 针对手动输入 IP 发起、不在扫描列表中的目标设备，同样在同 Card 下内嵌进度
                    if (nonNearbySessions.isNotEmpty()) {
                        items(nonNearbySessions, key = { it.sessionId }) { session ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ArrowPreference(
                                        title = session.device.alias,
                                        summary = "${session.device.ip}:${session.device.port} • ${session.device.deviceModel ?: session.device.deviceType.value}",
                                        startAction = {
                                            Icon(
                                                imageVector = AppIcons.getDeviceIcon(session.device.deviceType),
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        onClick = {}
                                    )
                                    InlineTransferProgress(
                                        session = session,
                                        onCancel = { manager.cancelTransfer(session.sessionId) },
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                    )
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
            onDismissRequest = { previewingIndex = -1 }
        )
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}