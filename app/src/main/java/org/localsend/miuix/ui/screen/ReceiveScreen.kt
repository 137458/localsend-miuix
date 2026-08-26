package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.TransferSessionCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 接收页（第 0 页）：本机信息 + Web Share 链接共享 + 正在接收的传输进度。
 * 传输历史通过右上角图标进入独立历史页。
 */
@Composable
fun ReceiveScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsState()
    val activeSessions by manager.activeSessions.collectAsState()
    val incomingSessions = remember(activeSessions) { activeSessions.filter { it.isIncoming } }

    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val scrollBehavior = MiuixScrollBehavior()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = "接收",
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(onClick = onOpenHistory) {
                    Icon(imageVector = AppIcons.History, contentDescription = "传输历史")
                }
            }
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
                SmallTitle(text = "本机设备")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = settings.alias,
                        summary = "设备别名 (点击修改)",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.getDeviceIcon(settings.deviceType),
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onOpenRenameDialog
                    )
                    ArrowPreference(
                        title = "$primaryIp:${settings.port}",
                        summary = if (localIps.size > 1) "所有网卡 IP: ${localIps.joinToString(", ")}" else "局域网 IPv4 地址与服务端口",
                        onClick = {}
                    )
                }
            }

            // Section 2: Quick Receive Preferences
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "接收选项")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "快速保存",
                        summary = "自动接受同局域网所有设备的传输请求，无需每次手动确认",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                    SwitchPreference(
                        title = "自动复制文本",
                        summary = "收到纯文本消息时自动写入系统剪贴板",
                        checked = settings.autoCopyText,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCopyText = checked) }
                        }
                    )
                }
            }

            // Section 3: Incoming Transfer Progress
            if (incomingSessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = "正在接收 (${incomingSessions.count { it.status == TransferStatus.InProgress }})")
                }
                items(incomingSessions, key = { it.sessionId }) { session ->
                    TransferSessionCard(
                        session = session,
                        onCancel = { manager.cancelTransfer(session.sessionId) }
                    )
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                tint = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "等待接收中...",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "确保发送端连接在同一 Wi-Fi 或局域网",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}