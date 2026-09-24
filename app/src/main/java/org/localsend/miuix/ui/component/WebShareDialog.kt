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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.NetworkUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
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
    // 端口被占用时服务端会自动顺延，必须用真实监听端口生成链接，否则二维码/网址指向未监听的端口
    val port = manager.getServerPort().takeIf { it > 0 } ?: settings.port
    val shareUrl = "$scheme://$primaryIp:$port"

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.webshare_dialog_title)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.webshare_dialog_hint),
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

            if (settings.useHttps) {
                Text(
                    text = stringResource(R.string.webshare_https_warning),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

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
                            text = stringResource(R.string.webshare_url_label),
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
                        Text(stringResource(R.string.btn_copy))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSharing) {
                val currentShare = shares.first()
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.webshare_current_shared_count, currentShare.files.size),
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        currentShare.files.take(5).forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (file.isTextMessage) AppIcons.Text else AppIcons.getFileIcon(file.mimeType, file.name),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (file.isTextMessage) (file.textContent?.take(40) ?: stringResource(R.string.send_quick_action_text)) else "${file.name} (${file.formattedSize})",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (currentShare.files.size > 5) {
                            Text(
                                text = stringResource(R.string.webshare_more_items, currentShare.files.size),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp, start = 24.dp)
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.webshare_ready_title),
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedFiles.isNotEmpty()) {
                                stringResource(R.string.webshare_ready_desc_with_files, selectedFiles.size)
                            } else {
                                stringResource(R.string.webshare_ready_desc_empty)
                            },
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
                    Text(stringResource(R.string.btn_close))
                }

                if (isSharing) {
                    Button(
                        onClick = {
                            manager.stopShare()
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.webshare_btn_stop_share))
                    }
                } else if (selectedFiles.isNotEmpty()) {
                    Button(
                        onClick = {
                            manager.startShare(selectedFiles)
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.webshare_btn_share_selected, selectedFiles.size))
                    }
                }
            }
        }
    }
}

