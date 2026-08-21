package org.localsend.miuix.ui.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.component.AppIcons
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiveScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit
) {
    val settings by manager.settings.collectAsState()
    val activeSessions by manager.activeSessions.collectAsState()
    val transferHistory by manager.transferHistory.collectAsState()
    val localDevice = manager.getLocalDevice()
    val scrollBehavior = MiuixScrollBehavior()

    val runningSessions = activeSessions.filter { it.status == TransferStatus.InProgress || it.status == TransferStatus.WaitingApproval }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "接收",
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section 1: Device Status
            item {
                SmallTitle(text = "本机状态")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "设备名称",
                        summary = settings.alias,
                        onClick = onOpenRenameDialog
                    )
                    ArrowPreference(
                        title = "网络地址与端口",
                        summary = "${localDevice.ip}:${settings.port}",
                        onClick = {}
                    )
                    SwitchPreference(
                        title = "快速保存",
                        summary = "自动接受来自局域网设备的文件传输",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                }
            }

            // Section 2: Active Transfers
            if (runningSessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SmallTitle(text = "活动传输 (${runningSessions.size})")
                }

                items(runningSessions, key = { it.sessionId }) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (session.isIncoming) Icons.Default.Download else Icons.Default.Upload,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${if (session.isIncoming) "接收来自" else "发送至"} ${session.device.alias}",
                                        style = MiuixTheme.textStyles.headline1
                                    )
                                }
                                Button(
                                    onClick = { manager.cancelTransfer(session.sessionId) },
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text("取消")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = session.progress,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${session.formattedTransferredSize} / ${session.formattedTotalSize} (${(session.progress * 100).toInt()}%)",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Text(
                                    text = session.formattedSpeed,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Transfer History
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = "传输历史 (${transferHistory.size})")
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (transferHistory.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无传输记录",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "历史记录列表",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Button(
                                    onClick = { manager.clearHistory() },
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text("清空")
                                }
                            }

                            val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

                            transferHistory.forEach { item ->
                                val statusIcon = when (item.status) {
                                    TransferStatus.Completed -> Icons.Default.CheckCircle
                                    TransferStatus.Failed -> Icons.Default.Error
                                    else -> Icons.Default.Close
                                }
                                val statusColor = when (item.status) {
                                    TransferStatus.Completed -> MiuixTheme.colorScheme.primary
                                    TransferStatus.Failed -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                }

                                ArrowPreference(
                                    title = "${if (item.isIncoming) "接收: " else "发送: "}${item.fileNames.firstOrNull() ?: "文件"}${if (item.fileCount > 1) " 等 ${item.fileCount} 个" else ""}",
                                    summary = "${item.deviceAlias} • ${item.formattedSize} • ${dateFormat.format(Date(item.timestamp))}",
                                    startAction = {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
