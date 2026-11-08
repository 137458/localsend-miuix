// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.EmptyStateCard
import org.localsend.miuix.ui.component.SectionCard
import org.localsend.miuix.ui.component.TransferSessionCard
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val activeSessions by manager.activeSessions.collectAsStateWithLifecycle()
    val incomingSessions = remember(activeSessions) { activeSessions.filter { it.isIncoming } }

    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val displayPort = if (boundPort > 0) boundPort else settings.port
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(R.string.receive_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                    actions = {
                        IconButton(onClick = onOpenHistory) {
                            Icon(imageVector = AppIcons.History, contentDescription = stringResource(R.string.action_history))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
                    .blurBackdropSource(backdrop),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Section 1: Device Info Card
                item {
                    SectionCard(title = stringResource(R.string.receive_section_local_device)) {
                        ArrowPreference(
                            title = settings.alias,
                            summary = stringResource(R.string.receive_pref_alias_summary),
                            startAction = {
                                Icon(
                                    imageVector = AppIcons.getDeviceIcon(settings.deviceType),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            onClick = onOpenRenameDialog,
                        )
                        ArrowPreference(
                            title = "$primaryIp:$displayPort",
                            summary = if (localIps.size > 1) stringResource(R.string.receive_pref_all_ips_summary, localIps.joinToString(", ")) else stringResource(R.string.receive_pref_ip_port_summary),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("LocalSend IP", "$primaryIp:$displayPort")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }

                // Section 2: Quick Receive Preferences
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionCard(title = stringResource(R.string.receive_section_options)) {
                        SwitchPreference(
                            title = stringResource(R.string.receive_pref_quick_save_title),
                            summary = stringResource(R.string.receive_pref_quick_save_summary),
                            checked = settings.quickSave,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(quickSave = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.receive_pref_quick_save_from_favorites_title),
                            summary = stringResource(R.string.receive_pref_quick_save_from_favorites_summary),
                            checked = settings.quickSaveFromFavorites,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(quickSaveFromFavorites = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.receive_pref_auto_copy_title),
                            summary = stringResource(R.string.receive_pref_auto_copy_summary),
                            checked = settings.autoCopyText,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(autoCopyText = checked) }
                            },
                        )
                    }
                }

                // Section 3: Incoming Transfer Progress
                if (incomingSessions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        SmallTitle(text = stringResource(R.string.receive_section_incoming_count, incomingSessions.size))
                    }
                    items(incomingSessions, key = { it.sessionId }) { session ->
                        TransferSessionCard(
                            session = session,
                            onCancel = { manager.cancelTransfer(session.sessionId) },
                            onAccept = { manager.acceptAllIncomingTransfer(session.sessionId) },
                        )
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        EmptyStateCard(
                            icon = AppIcons.Wifi,
                            title = stringResource(R.string.receive_waiting_idle),
                            hint = stringResource(R.string.receive_waiting_hint),
                            iconTint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
