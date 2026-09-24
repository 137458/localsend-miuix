package org.localsend.miuix.ui.component

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.localsend.miuix.R
import org.localsend.miuix.manager.AppInfoItem
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private object AppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(512) {}

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(
        packageName: String,
        bitmap: ImageBitmap,
    ) {
        cache.put(packageName, bitmap)
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val cached = AppIconCache.get(packageName)
            if (cached != null) {
                iconBitmap = cached
            } else {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        try {
                            val pm = context.packageManager
                            val drawable = pm.getApplicationIcon(packageName)
                            val bmp = drawable.toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888)
                            bmp.asImageBitmap().also { AppIconCache.put(packageName, it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                iconBitmap = bitmap
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun AppPickerBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    manager: LocalSendManager,
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(show) {
        if (show) {
            if (allApps.isEmpty()) {
                isLoading = true
            }
            allApps = manager.getInstalledApps(forceRefresh = false)
            selectedPackages = emptySet()
            isLoading = false
        }
    }

    val filteredApps =
        remember(allApps, searchQuery, showSystemApps) {
            allApps.filter { app ->
                (showSystemApps || !app.isSystemApp) &&
                    (
                        searchQuery.isEmpty() ||
                            app.label.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                    )
            }
        }

    val isAllFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { selectedPackages.contains(it.packageName) }

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.app_picker_title),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = stringResource(R.string.app_picker_search_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSystemApps = !showSystemApps },
                ) {
                    Checkbox(
                        state =
                            androidx.compose.ui.state
                                .ToggleableState(showSystemApps),
                        onClick = { showSystemApps = !showSystemApps },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.app_picker_show_system_apps),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_picker_selected_count, selectedPackages.size, filteredApps.size),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRefreshing) stringResource(R.string.app_picker_refreshing) else stringResource(R.string.btn_refresh),
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = if (isRefreshing) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clickable(enabled = !isRefreshing) {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        allApps = manager.getInstalledApps(forceRefresh = true)
                                        isRefreshing = false
                                    }
                                }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            val configuration = LocalConfiguration.current
            val maxListHeight = (configuration.screenHeightDp * 0.55f).dp.coerceIn(240.dp, 600.dp)

            if (filteredApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val selectAllLabel =
                        when {
                            searchQuery.isNotBlank() && isAllFilteredSelected -> stringResource(R.string.app_picker_deselect_all_filtered, filteredApps.size)
                            searchQuery.isNotBlank() && !isAllFilteredSelected -> stringResource(R.string.app_picker_select_all_filtered, filteredApps.size)
                            isAllFilteredSelected -> stringResource(R.string.app_picker_deselect_all)
                            else -> stringResource(R.string.app_picker_select_all)
                        }
                    Text(
                        text = selectAllLabel,
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clickable {
                                    selectedPackages =
                                        if (isAllFilteredSelected) {
                                            selectedPackages - filteredApps.map { it.packageName }.toSet()
                                        } else {
                                            selectedPackages + filteredApps.map { it.packageName }
                                        }
                                }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading || isRefreshing) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((maxListHeight * 0.7f).coerceIn(180.dp, 260.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.app_picker_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app_info_card" },
                    ) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPackages =
                                            if (isSelected) {
                                                selectedPackages - app.packageName
                                            } else {
                                                selectedPackages + app.packageName
                                            }
                                    },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    state =
                                        androidx.compose.ui.state
                                            .ToggleableState(isSelected),
                                    onClick = {
                                        selectedPackages =
                                            if (isSelected) {
                                                selectedPackages - app.packageName
                                            } else {
                                                selectedPackages + app.packageName
                                            }
                                    },
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                AppIconImage(
                                    packageName = app.packageName,
                                    modifier = Modifier.size(38.dp),
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (app.isSystemApp) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.app_picker_system_tag),
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${app.packageName} · v${app.versionName} · ${FileItem.formatFileSize(app.apkSize)}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val selectedApps = allApps.filter { selectedPackages.contains(it.packageName) }
                        manager.addAppsAsFiles(selectedApps)
                        onDismissRequest()
                    },
                    enabled = selectedPackages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.app_picker_btn_add_selected, selectedPackages.size))
                }
            }
        }
    }
}
