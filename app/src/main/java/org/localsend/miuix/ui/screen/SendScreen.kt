package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.AppIcons
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
    onOpenManualIp: () -> Unit,
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    val context = LocalContext.current
    val selectedFiles by manager.selectedFiles.collectAsState()
    val nearbyDevices by manager.nearbyDevices.collectAsState()
    val isScanning by manager.isScanning.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()

    val totalSelectedSize = remember(selectedFiles) {
        selectedFiles.sumOf { it.size }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = "发送",
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(
                    onClick = {
                        manager.refreshDevices()
                        Toast.makeText(context, "已发送局域网发现广播", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = AppIcons.Refresh, contentDescription = "刷新")
                }
                IconButton(
                    onClick = onOpenManualIp
                ) {
                    Icon(imageVector = AppIcons.Scan, contentDescription = "手动 IP")
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
                // Section 1: Quick Action Grid
                item {
                    SmallTitle(text = "快速选择")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionItem(
                                title = "文件",
                                icon = Icons.Default.Folder,
                                onClick = onPickFiles
                            )
                            QuickActionItem(
                                title = "媒体",
                                icon = Icons.Default.Image,
                                onClick = onPickMedia
                            )
                            QuickActionItem(
                                title = "文本",
                                icon = Icons.Default.TextFields,
                                onClick = onSendText
                            )
                            QuickActionItem(
                                title = "剪贴板",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                onClick = onPasteClipboard
                            )
                        }
                    }
                }

                // Section 2: Selected Content Queue
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "待发送内容 (${selectedFiles.size})")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (selectedFiles.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "尚未选择任何内容",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onOpenAddSheet,
                                    colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("添加文件 / 媒体 / 文本")
                                }
                            }
                        } else {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "共 ${selectedFiles.size} 项 (${FileItem.formatFileSize(totalSelectedSize)})",
                                        style = MiuixTheme.textStyles.headline1
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = onOpenAddSheet,
                                            colors = ButtonDefaults.buttonColorsPrimary()
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("添加")
                                        }
                                        Button(
                                            onClick = { manager.clearFiles() },
                                            colors = ButtonDefaults.buttonColors()
                                        ) {
                                            Text("清空")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                selectedFiles.forEach { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.getFileIcon(file.mimeType, file.name),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MiuixTheme.textStyles.body1,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = file.formattedSize,
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                        }
                    }
                }

                // Section 3: Nearby Devices
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "附近设备 (${nearbyDevices.size})")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (isScanning) {
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
                                    progress = 0.5f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (nearbyDevices.isEmpty() && !isScanning) {
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
                        } else {
                            nearbyDevices.forEach { device ->
                                ArrowPreference(
                                    title = device.alias,
                                    summary = "${device.ip}:${device.port} • ${device.deviceModel ?: device.deviceType.value}",
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
                            }
                        }
                    }
                }

                // Section 4: Manual Tools
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "快捷工具")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "手动输入 IP 发送",
                            summary = "直接连接到指定 IP 地址与端口",
                            onClick = onOpenManualIp
                        )
                        ArrowPreference(
                            title = if (isScanning) "正在全网段扫描中..." else "扫描局域网子网段",
                            summary = "并发探测 192.168.x.1..254，解决多播受阻问题",
                            onClick = {
                                manager.scanSubnet()
                                Toast.makeText(context, "开始全网段异步扫描", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
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
