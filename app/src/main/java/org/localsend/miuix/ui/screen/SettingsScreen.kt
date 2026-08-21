package org.localsend.miuix.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.NetworkUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit
) {
    val settings by manager.settings.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val localIps = NetworkUtils.getLocalIpAddresses()

    val themeOptions = listOf(
        "跟随系统 (System)",
        "浅色模式 (Light)",
        "深色模式 (Dark)",
        "莫奈跟随系统 (Monet System)",
        "莫奈浅色 (Monet Light)",
        "莫奈深色 (Monet Dark)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
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
            // Section 1: General
            item {
                SmallTitle(text = "常规")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "设备名称",
                        summary = settings.alias,
                        onClick = onOpenRenameDialog
                    )
                    ArrowPreference(
                        title = "保存目录",
                        summary = settings.downloadPath.ifEmpty { "应用专属下载目录" },
                        onClick = {}
                    )
                    SwitchPreference(
                        title = "快速保存",
                        summary = "自动接收所有传入的文件传输请求",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                    SwitchPreference(
                        title = "自动完成",
                        summary = "传输成功后自动归档任务",
                        checked = settings.autoFinish,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoFinish = checked) }
                        }
                    )
                }
            }

            // Section 2: Network
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = "网络与传输")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "服务端口",
                        summary = "${settings.port} (标准端口 53317)",
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "多播广播地址",
                        summary = "${settings.multicastGroup}:53317",
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "本机网络接口",
                        summary = localIps.joinToString(", "),
                        onClick = {}
                    )
                    SwitchPreference(
                        title = "HTTPS 加密传输",
                        summary = "使用 TLS 加密通道进行安全文件传输",
                        checked = settings.useHttps,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(useHttps = checked) }
                        }
                    )
                }
            }

            // Section 3: Appearance
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = "外观与主题")
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = "色彩主题模式",
                        summary = themeOptions.getOrElse(settings.themeModeIndex) { "跟随系统" },
                        items = themeOptions,
                        selectedIndex = settings.themeModeIndex,
                        onSelectedIndexChange = { index ->
                            manager.updateSettings { it.copy(themeModeIndex = index) }
                        }
                    )
                }
            }

            // Section 4: About
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "LocalSend Miuix",
                        summary = "v1.0.0 (基于 Xiaomi HyperOS 设计重塑)",
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "协议兼容性",
                        summary = "LocalSend Protocol v2 (兼容全平台官方客户端)",
                        onClick = {}
                    )
                    ArrowPreference(
                        title = "Miuix 组件库",
                        summary = "top.yukonga.miuix.kmp: 0.9.4-rc01",
                        onClick = {}
                    )
                }
            }
        }
    }
}
