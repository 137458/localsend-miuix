package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.NetworkUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun WebShareDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager
) {
    val shares by manager.shares.collectAsState()
    val settings by manager.settings.collectAsState()
    val selectedFiles by manager.selectedFiles.collectAsState()
    val context = LocalContext.current

    val isSharing = shares.isNotEmpty()
    val localIps = NetworkUtils.getLocalIpAddresses()
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val scheme = if (settings.useHttps) "https" else "http"
    val shareUrl = "$scheme://$primaryIp:${settings.port}"

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "通过浏览器链接分享 (Web Share)"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSharing) {
                val currentShare = shares.first()

                Text(
                    text = "同局域网设备扫描二维码或在浏览器访问下方地址即可接收",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // QR Code Container with clean white background for scanning
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QrCodeImage(
                        content = shareUrl,
                        size = 196.dp,
                        darkColor = Color.Black,
                        lightColor = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "网页访问地址",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = shareUrl,
                                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Web URL", shareUrl))
                            },
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text("复制")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "当前共享内容 (${currentShare.files.size} 项)",
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        currentShare.files.take(4).forEach { file ->
                            Text(
                                text = if (file.isTextMessage) "💬 ${file.textContent?.take(40) ?: "纯文本"}" else "📄 ${file.name} (${file.formattedSize})",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        if (currentShare.files.size > 4) {
                            Text(
                                text = "... 等共 ${currentShare.files.size} 项",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭窗口")
                    }

                    Button(
                        onClick = {
                            manager.stopShare()
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("停止共享")
                    }
                }
            } else {
                Text(
                    text = "通过网页分享可以让任何支持浏览器的设备（如 iOS、Mac、Linux、无客户端电脑）直接下载文件或复制文本。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                if (selectedFiles.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "当前待发送列表中暂无内容，请先在发送页选择文件或输入文本。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "将以当前已选的 ${selectedFiles.size} 项内容启动 Web 共享服务。",
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            manager.startShare(selectedFiles)
                        },
                        enabled = selectedFiles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text("开始 Web 共享")
                    }
                }
            }
        }
    }
}

