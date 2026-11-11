// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.NetworkUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun WebShareDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager,
) {
    val shares by manager.shares.collectAsStateWithLifecycle()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val selectedFiles by manager.selectedFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isSharing = shares.isNotEmpty()
    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    var selectedIp by remember(localIps) {
        mutableStateOf(localIps.firstOrNull() ?: "127.0.0.1")
    }
    val scheme = if (settings.useHttps) "https" else "http"
    // 端口被占用时服务端会自动顺延，必须用真实监听端口生成链接，否则二维码/网址指向未监听的端口
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val port = boundPort.takeIf { it > 0 } ?: settings.port
    val shareUrl = "$scheme://$selectedIp:$port"
    val copiedToast = stringResource(R.string.webshare_copied_toast)
    val copyUrl = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LocalSend Web URL", shareUrl))
        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
    }

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.webshare_dialog_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            // 二维码需要纯白底与静区，白色背景属于扫码必需，不随主题变化
            QrCodeImage(
                content = shareUrl,
                size = 196.dp,
                darkColor = Color.Black,
                lightColor = Color.White,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(DialogTokens.CardCorner))
                        .background(Color.White)
                        .padding(DialogTokens.CardPadding),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shareUrl,
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = copyUrl),
                )
                IconButton(onClick = copyUrl) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = stringResource(R.string.webshare_btn_copy_url),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (localIps.size > 1) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                ) {
                    localIps.forEach { ip ->
                        val label =
                            when {
                                ip.startsWith("192.168.43.") -> stringResource(R.string.webshare_ip_tag_hotspot, ip)
                                ip == localIps.first() -> stringResource(R.string.webshare_ip_tag_recommended, ip)
                                else -> ip
                            }
                        DialogChip(
                            text = label,
                            onClick = { selectedIp = ip },
                            selected = ip == selectedIp,
                        )
                    }
                }
            }

            if (settings.useHttps) {
                Text(
                    text = stringResource(R.string.webshare_https_warning),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isSharing) {
                val currentShare = shares.first()
                DialogCard {
                    Text(
                        text = stringResource(R.string.webshare_current_shared_count, currentShare.files.size),
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = DialogTokens.BlockGap),
                        verticalArrangement = Arrangement.spacedBy(DialogTokens.BlockGap),
                    ) {
                        currentShare.files.take(5).forEach { file ->
                            DialogInfoRow(
                                label =
                                    if (file.isTextMessage) {
                                        file.textContent?.take(40) ?: stringResource(R.string.send_quick_action_text)
                                    } else {
                                        file.name
                                    },
                                value = file.formattedSize,
                            )
                        }
                        if (currentShare.files.size > 5) {
                            Text(
                                text = stringResource(R.string.webshare_more_items, currentShare.files.size),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            when {
                isSharing -> {
                    DialogButtonRow(
                        secondaryText = stringResource(R.string.btn_close),
                        onSecondary = onDismissRequest,
                        primaryText = stringResource(R.string.webshare_btn_stop_share),
                        onPrimary = { manager.stopShare() },
                        primaryColors = dialogDangerButtonColors(),
                    )
                }
                selectedFiles.isNotEmpty() -> {
                    DialogButtonRow(
                        secondaryText = stringResource(R.string.btn_close),
                        onSecondary = onDismissRequest,
                        primaryText = stringResource(R.string.webshare_btn_share_selected, selectedFiles.size),
                        onPrimary = { manager.startShare(selectedFiles) },
                        primaryColors = ButtonDefaults.buttonColorsPrimary(),
                    )
                }
                else -> {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.btn_close))
                    }
                }
            }
        }
    }
}
