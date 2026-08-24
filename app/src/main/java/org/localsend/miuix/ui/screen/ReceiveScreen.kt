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
    val incomingSessions = activeSessions.filter { it.isIncoming }
    val shares by manager.shares.collectAsState()
    val selectedFiles by manager.selectedFiles.collectAsState()

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

            // Section 1.5: Incoming Transfer Progress（使用统一的高质感 TransferSessionCard）
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
            }

            // Section 2: Web Share（通过链接共享给局域网浏览器）
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "通过链接分享")
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (shares.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "把选中的文件共享为可浏览/下载的链接，接收方无需安装应用",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (selectedFiles.isEmpty()) {
                                        Toast.makeText(context, "请先在发送页添加待分享的文件", Toast.LENGTH_SHORT).show()
                                    } else {
                                        manager.startShare(selectedFiles)
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(imageVector = AppIcons.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("开启链接共享")
                            }
                        }
                    } else {
                        // StateFlow 单例共享（empty↔single 原子切换），此处安全性对当前实现保证；
                        // 用 firstOrNull 防御，避免未来引入多共享时抛 NoSuchElementException
                        val session = shares.firstOrNull() ?: return@Card
                        val device = manager.getLocalDevice()
                        val link = session.downloadLink(device.protocol, device.ip, device.port)
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "收件人浏览器打开以下地址即可下载",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = link,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "共 ${session.files.size} 个文件",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE
                                        ) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(
                                            android.content.ClipData.newPlainText("LocalSend", link)
                                        )
                                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Icon(imageVector = AppIcons.Copy, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("复制链接")
                                }
                                Button(
                                    onClick = { manager.stopShare() },
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text("结束共享")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}