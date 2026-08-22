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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.NetworkUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ReceiveScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit
) {
    val settings by manager.settings.collectAsState()
    val activeSessions by manager.activeSessions.collectAsState()
    val transferHistory by manager.transferHistory.collectAsState()

    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val scrollBehavior = MiuixScrollBehavior()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = "接收",
            scrollBehavior = scrollBehavior
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section 1: Device Info Card
            item {
                SmallTitle(text = "本机状态")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = settings.alias,
                        summary = "本机名称 (点击可修改)",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onOpenRenameDialog
                    )
                    ArrowPreference(
                        title = "$primaryIp:${settings.port}",
                        summary = if (localIps.size > 1) "所有 IP: ${localIps.joinToString(", ")}" else "局域网 IP 与服务端口",
                        onClick = {}
                    )
                    SwitchPreference(
                        title = "快速保存",
                        summary = "自动接收所有传入的传输请求",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                }
            }

            // Section 2: Active Ongoing Sessions
            if (activeSessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "正在传输 (${activeSessions.count { it.status == TransferStatus.InProgress }})")
                }
                items(activeSessions, key = { it.sessionId }) { session ->
                    org.localsend.miuix.ui.component.TransferSessionCard(
                        session = session,
                        onCancel = { manager.cancelTransfer(session.sessionId) }
                    )
                }
            }

            // Section 3: History List
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle(text = "传输历史 (${transferHistory.size})")
                    if (transferHistory.isNotEmpty()) {
                        Button(
                            onClick = { manager.clearHistory() },
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text("清空")
                        }
                    }
                }
            }

            if (transferHistory.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无传输历史记录",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            } else {
                items(transferHistory, key = { it.id }) { item ->
                    HistoryItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(item: TransferHistoryItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (item.status) {
                    TransferStatus.Completed -> Icons.Default.CheckCircle
                    TransferStatus.Failed -> Icons.Default.Error
                    else -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = when (item.status) {
                    TransferStatus.Completed -> Color(0xFF4CAF50)
                    TransferStatus.Failed -> MiuixTheme.colorScheme.error
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${if (item.isIncoming) "接收自" else "发送至"}: ${item.deviceAlias}",
                    style = MiuixTheme.textStyles.body1
                )
                Text(
                    text = "${item.fileCount} 个文件 (${FileItem.formatFileSize(item.totalSize)}) • ${item.fileNames.firstOrNull() ?: ""}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
        }
    }
}
