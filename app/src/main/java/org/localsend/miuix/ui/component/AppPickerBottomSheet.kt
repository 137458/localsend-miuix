package org.localsend.miuix.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.miuix.manager.AppInfoItem
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun AppPickerBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    val selectedPackages = remember { mutableStateListOf<String>() }

    LaunchedEffect(show) {
        if (show) {
            isLoading = true
            allApps = manager.getInstalledApps()
            selectedPackages.clear()
            isLoading = false
        }
    }

    val filteredApps = remember(allApps, searchQuery, showSystemApps) {
        allApps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                (searchQuery.isEmpty() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "选择应用 (APK)"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "搜索应用名称或包名...",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSystemApps = !showSystemApps }
                ) {
                    Checkbox(
                        state = androidx.compose.ui.state.ToggleableState(showSystemApps),
                        onClick = { showSystemApps = !showSystemApps }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "显示系统应用",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                Text(
                    text = "已选 ${selectedPackages.size} 项 / 共 ${filteredApps.size} 个",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有找到符合条件的应用",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        selectedPackages.remove(app.packageName)
                                    } else {
                                        selectedPackages.add(app.packageName)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    state = androidx.compose.ui.state.ToggleableState(isSelected),
                                    onClick = {
                                        if (isSelected) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (app.isSystemApp) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "系统",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${app.packageName} · v${app.versionName} · ${FileItem.formatFileSize(app.apkSize)}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        val selectedApps = allApps.filter { selectedPackages.contains(it.packageName) }
                        manager.addAppsAsFiles(selectedApps)
                        onDismissRequest()
                    },
                    enabled = selectedPackages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("添加所选 (${selectedPackages.size})")
                }
            }
        }
    }
}

