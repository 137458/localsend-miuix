package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SendScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenAddSheet: () -> Unit,
    onOpenManualIp: () -> Unit
) {
    val context = LocalContext.current
    val selectedFiles by manager.selectedFiles.collectAsState()
    val nearbyDevices by manager.nearbyDevices.collectAsState()
    val isScanning by manager.isScanning.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
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
        }
    ) { innerPadding ->
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
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Selected Files
                item {
                    SmallTitle(text = "待发送内容")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedFiles.isEmpty()) "尚未添加待发送内容" else "已选择 ${selectedFiles.size} 个项目",
                                    style = MiuixTheme.textStyles.headline1
                                )
                                if (selectedFiles.isNotEmpty()) {
                                    Button(
                                        onClick = { manager.clearFiles() },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("清空")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenAddSheet,
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("添加文件/内容")
                                }
                            }

                            if (selectedFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
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

                // Section 2: Nearby Devices
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SmallTitle(text = "附近设备 (${nearbyDevices.size})")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (nearbyDevices.isEmpty()) {
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
                                    text = if (isScanning) "正在全网段扫描设备..." else "正在搜索同一局域网下的 LocalSend 设备...",
                                    style = MiuixTheme.textStyles.body2,
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

                // Section 3: Quick Tools
                item {
                    Spacer(modifier = Modifier.height(8.dp))
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
