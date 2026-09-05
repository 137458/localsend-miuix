package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.ui.component.CertFingerprintDialog
import org.localsend.miuix.ui.component.PinDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

import androidx.lifecycle.compose.LifecycleResumeEffect
import org.localsend.miuix.notification.TransferNotifier

@Composable
fun SettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()

    var showPinDialog by remember { mutableStateOf(false) }
    var showCertDialog by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(TransferNotifier.isNotificationsEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        isNotificationEnabled = TransferNotifier.isNotificationsEnabled(context)
        onPauseOrDispose {}
    }

    val themeOptions = remember {
        listOf(
            "跟随系统",
            "浅色模式",
            "深色模式",
            "莫奈跟随系统",
            "莫奈浅色",
            "莫奈深色"
        )
    }

    val deviceTypeOptions = remember {
        listOf("手机 (Mobile)", "平板 (Tablet)", "电脑 (Desktop)", "服务器 (Server)")
    }
    val currentDeviceTypeIndex = remember(settings.deviceType) {
        when (settings.deviceType) {
            DeviceType.mobile -> 0
            DeviceType.tablet -> 1
            DeviceType.desktop -> 2
            DeviceType.server -> 3
            else -> 0
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = "设置",
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
            // Section 1: General Settings
            item {
                SmallTitle(text = "通用设置")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "设备别名",
                        summary = settings.alias,
                        onClick = onOpenRenameDialog
                    )
                    WindowDropdownPreference(
                        title = "设备类型",
                        items = deviceTypeOptions,
                        selectedIndex = currentDeviceTypeIndex,
                        onSelectedIndexChange = { index ->
                            val newType = when (index) {
                                0 -> DeviceType.mobile
                                1 -> DeviceType.tablet
                                2 -> DeviceType.desktop
                                3 -> DeviceType.server
                                else -> DeviceType.mobile
                            }
                            manager.updateSettings { it.copy(deviceType = newType) }
                        }
                    )
                    WindowDropdownPreference(
                        title = "应用主题",
                        items = themeOptions,
                        selectedIndex = settings.themeModeIndex,
                        onSelectedIndexChange = { index ->
                            manager.updateSettings { it.copy(themeModeIndex = index) }
                        }
                    )
                    SwitchPreference(
                        title = "传输完成震动反馈",
                        summary = "发送或接收完成时触发触感震动",
                        checked = settings.vibrateOnComplete,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(vibrateOnComplete = checked) }
                        }
                    )
                    ArrowPreference(
                        title = "系统通知与流体云权限",
                        summary = if (isNotificationEnabled) "已开启 (传输进度与流体云胶囊提示正常)" else "未开启 (点击授权或前往系统设置开启通知)",
                        onClick = {
                            val activity = context as? org.localsend.miuix.ui.MainActivity
                            if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                activity.requestNecessaryPermissions()
                            } else {
                                org.localsend.miuix.notification.TransferNotifier.openNotificationSettings(context)
                            }
                        }
                    )
                }
            }

            // Section 2: Receive Settings
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "接收设置")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "快速保存",
                        summary = "自动接收所有传入的发送请求",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                    SwitchPreference(
                        title = "自动复制文本",
                        summary = "收到纯文本消息时自动复制到剪贴板",
                        checked = settings.autoCopyText,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCopyText = checked) }
                        }
                    )
                    SwitchPreference(
                        title = "保存传输历史",
                        summary = "将完成和取消的传输记录存入历史页面",
                        checked = settings.saveToHistory,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(saveToHistory = checked) }
                        }
                    )
                    ArrowPreference(
                        title = "文件保存目录",
                        summary = settings.downloadDisplay ?: settings.downloadPath,
                        onClick = onPickDirectory
                    )
                }
            }

            // Section 3: Network & Security Settings
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "网络与安全")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "服务端口",
                        summary = settings.port.toString(),
                        onClick = onOpenPortDialog
                    )
                    SwitchPreference(
                        title = "启用 HTTPS (TLS 加密)",
                        summary = "使用端到端自签名证书加密局域网通信",
                        checked = settings.useHttps,
                        onCheckedChange = { checked ->
                            manager.applyUseHttpsChange(checked)
                        }
                    )
                    ArrowPreference(
                        title = "传输 PIN 码保护",
                        summary = if (settings.pin.isNullOrEmpty()) "未启用 (点击设置)" else "已设置: ••••",
                        onClick = { showPinDialog = true }
                    )
                    ArrowPreference(
                        title = "TLS 证书指纹",
                        summary = if (settings.useHttps) "查看与重新生成 SHA-256 指纹" else "HTTPS 开启后可用",
                        onClick = { showCertDialog = true }
                    )
                }
            }

            // Section 4: About
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "检查更新",
                        summary = "当前版本 v${org.localsend.miuix.BuildConfig.VERSION_NAME} (点击检查新版本)",
                        onClick = onNavigateToUpdate
                    )
                    ArrowPreference(
                        title = "GitHub 开源主页",
                        summary = "https://github.com/137458/localsend-miuix",
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/137458/localsend-miuix")
                                ).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                    ArrowPreference(
                        title = "开源协议与标准",
                        summary = "LocalSend Protocol v2.1 • Apache 2.0 License",
                        onClick = {}
                    )
                }
            }
        }
    }

    PinDialog(
        show = showPinDialog,
        initialPin = settings.pin,
        onDismissRequest = { showPinDialog = false },
        onConfirm = { newPin ->
            manager.updateSettings { it.copy(pin = newPin) }
        }
    )

    CertFingerprintDialog(
        show = showCertDialog,
        fingerprint = if (settings.useHttps) manager.getLocalDevice().fingerprint else "",
        onDismissRequest = { showCertDialog = false },
        onRegenerate = {
            manager.regenerateCertificate()
        }
    )
}

