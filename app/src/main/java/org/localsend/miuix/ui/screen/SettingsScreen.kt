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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.LocalSendManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
fun SettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()

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
                        title = "应用主题",
                        items = themeOptions,
                        selectedIndex = settings.themeModeIndex,
                        onSelectedIndexChange = { index ->
                            manager.updateSettings { it.copy(themeModeIndex = index) }
                        }
                    )
                    SwitchPreference(
                        title = "自动保存",
                        summary = "自动接收所有传入的发送请求",
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                }
            }

            // Section 2: Network Settings
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "网络与传输")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "端口号",
                        summary = settings.port.toString(),
                        onClick = onOpenPortDialog
                    )
                    SwitchPreference(
                        title = "启用 HTTPS 加密",
                        summary = "使用 TLS 进行局域网传输加密",
                        checked = settings.useHttps,
                        onCheckedChange = { checked ->
                            manager.applyUseHttpsChange(checked)
                        }
                    )
                    ArrowPreference(
                        title = "保存目录",
                        summary = settings.downloadDisplay ?: settings.downloadPath,
                        onClick = onPickDirectory
                    )
                }
            }

            // Section 3: About
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "LocalSend Miuix",
                        summary = "版本 1.0.0 (基于 Miuix 0.9.4 & HyperOS 视觉规范)",
                        onClick = {
                            Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ArrowPreference(
                        title = "开源协议",
                        summary = "LocalSend 协议标准 v2.1 • Apache 2.0",
                        onClick = {}
                    )
                }
            }
        }
    }
}
