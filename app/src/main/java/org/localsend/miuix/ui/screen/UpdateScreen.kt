package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.MarkdownText
import org.localsend.miuix.ui.component.UpdateDialog
import org.localsend.miuix.ui.effect.BgEffectBackground
import org.localsend.miuix.ui.effect.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 官方 Miuix / HyperOS 视觉规范系统与应用更新页。
 * 对齐 pixez-flutter-MIUIX 架构与全套动效实现。
 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    var releaseInfo by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var isOs3Effect by remember { mutableStateOf(true) }

    var ignoredVersion by remember { mutableStateOf<String?>(null) }
    var autoCheckUpdate by remember { mutableStateOf(true) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val hasNew = releaseInfo?.hasUpdate == true

    fun doCheck(userInitiated: Boolean = false) {
        if (isChecking) return
        isChecking = true
        coroutineScope.launch {
            val result = updateManager.checkForUpdate()
            isChecking = false
            result.onSuccess { info ->
                releaseInfo = info
                if (info.hasUpdate) {
                    showDialog = true
                } else if (userInitiated) {
                    Toast.makeText(context, "已是最新版本 (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                val message = error.localizedMessage ?: "检查更新失败"
                if (userInitiated) {
                    Toast.makeText(context, "检查更新失败: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        doCheck(userInitiated = false)
    }

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(240.dp) }

    Scaffold(
        topBar = {
            val barColor = if (scrollProgress == 1f) MiuixTheme.colorScheme.surface else Color.Transparent
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            SmallTopAppBar(
                title = "软件更新",
                scrollBehavior = topAppBarScrollBehavior,
                color = barColor,
                titleColor = titleColor,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BgEffectBackground(
            dynamicBackground = isRuntimeShaderSupported(),
            isOs3Effect = isOs3Effect,
            isFullSize = true,
            modifier = Modifier.fillMaxSize(),
            alpha = { 1f - scrollProgress },
        ) {
            // ── 顶部官方规范 Hero 视觉 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = innerPadding.calculateTopPadding() + 24.dp)
                    .onSizeChanged { size ->
                        with(density) { logoHeightDp = size.height.toDp() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                            clip = true
                            shape = RoundedCornerShape(24.dp)
                            alpha = 1 - iconProgress
                            scaleX = 1 - (iconProgress * 0.05f)
                            scaleY = 1 - (iconProgress * 0.05f)
                        }
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = org.localsend.miuix.R.drawable.ic_localsend_logo),
                        contentDescription = "LocalSend",
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "LocalSend Miuix",
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .graphicsLayer {
                            val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - nameProgress
                            scaleX = 1 - (nameProgress * 0.05f)
                            scaleY = 1 - (nameProgress * 0.05f)
                        },
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (isChecking) {
                    Text(
                        text = "正在检查更新...",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - verProgress
                            },
                    )
                } else if (hasNew) {
                    Text(
                        text = "发现新版本 ${releaseInfo?.latestVersion} (当前 v${BuildConfig.VERSION_NAME})",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - verProgress
                            },
                    )
                } else {
                    Text(
                        text = "已是最新版本 (v${BuildConfig.VERSION_NAME})",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - verProgress
                            },
                    )
                }
            }

            // ── 滚动内容列表 ──
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "logoSpacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(logoHeightDp + 48.dp),
                    )
                }

                // ── 更新日志卡片（获取到版本信息后常驻展示，保证稳定可见） ──
                if (releaseInfo != null) {
                    item(key = "changelog") {
                        val changelogTitle = if (hasNew) {
                            "新版本更新日志 (${releaseInfo?.latestVersion})"
                        } else {
                            "当前版本说明 (v${BuildConfig.VERSION_NAME})"
                        }
                        SmallTitle(text = changelogTitle)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = releaseInfo?.releaseTitle?.ifBlank { "版本特性说明" } ?: "版本特性说明",
                                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                MarkdownText(
                                    markdown = releaseInfo?.changelog ?: "",
                                    modifier = Modifier.fillMaxWidth(),
                                    baseFontSize = 14,
                                )

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "正在下载更新...",
                                            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                        val percent = if (downloadProgress >= 0f) "${(downloadProgress * 100).toInt()}%" else ""
                                        val sizeText = if (totalBytes > 0) {
                                            "${FileItem.formatFileSize(downloadedBytes)} / ${FileItem.formatFileSize(totalBytes)}"
                                        } else {
                                            FileItem.formatFileSize(downloadedBytes)
                                        }
                                        Text(
                                            text = if (percent.isNotEmpty()) "$sizeText ($percent)" else sizeText,
                                            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = downloadProgress,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (downloadedFile != null) {
                                    TextButton(
                                        text = "立即安装更新",
                                        onClick = {
                                            updateManager.installApk(context, downloadedFile!!)
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else if (hasNew && !isDownloading) {
                                    TextButton(
                                        text = "立即下载更新",
                                        onClick = {
                                            val url = releaseInfo?.downloadUrl ?: releaseInfo?.releaseUrl
                                            if (url != null) {
                                                isDownloading = true
                                                downloadProgress = 0f
                                                coroutineScope.launch {
                                                    val result = updateManager.downloadApk(
                                                        downloadUrl = url,
                                                        onProgress = { progress, downloaded, total ->
                                                            downloadProgress = progress
                                                            downloadedBytes = downloaded
                                                            totalBytes = total
                                                        },
                                                    )
                                                    isDownloading = false
                                                    result.onSuccess { file ->
                                                        downloadedFile = file
                                                        updateManager.installApk(context, file)
                                                    }.onFailure { error ->
                                                        Toast.makeText(context, "下载失败: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                releaseInfo?.releaseUrl?.let { updateManager.openInBrowser(context, it) }
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ── 更新设置 ──
                item(key = "settings") {
                    SmallTitle(text = "更新设置")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        BasicComponent(
                            title = "自动检查更新",
                            summary = "每次启动应用时自动检查最新版本",
                            endActions = {
                                Switch(
                                    checked = autoCheckUpdate,
                                    onCheckedChange = { checked ->
                                        autoCheckUpdate = checked
                                    },
                                )
                            },
                        )

                        BasicComponent(
                            title = "忽略此版本",
                            summary = when {
                                isChecking -> "正在检查..."
                                !hasNew -> "当前已是最新版本"
                                ignoredVersion == releaseInfo?.latestVersion -> "已忽略版本 ${releaseInfo?.latestVersion}"
                                else -> "忽略新版本 ${releaseInfo?.latestVersion} 的更新提示"
                            },
                            endActions = {
                                Switch(
                                    checked = hasNew && ignoredVersion == releaseInfo?.latestVersion,
                                    onCheckedChange = { checked ->
                                        ignoredVersion = if (checked) releaseInfo?.latestVersion else null
                                    },
                                    enabled = hasNew,
                                )
                            },
                        )
                    }
                }

                // ── 版本通道与操作 ──
                item(key = "channel") {
                    SmallTitle(text = "版本通道与操作")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        BasicComponent(
                            title = "手动检查更新",
                            summary = if (isChecking) "正在检索远程版本信息..." else "点击从 GitHub Releases 检索最新版本",
                            onClick = {
                                doCheck(userInitiated = true)
                            },
                            endActions = {
                                if (isChecking) {
                                    InfiniteProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )

                        BasicComponent(
                            title = "GitHub Releases",
                            summary = "前往项目官方发布页面查看历史版本",
                            onClick = {
                                updateManager.openInBrowser(context, "https://github.com/137458/localsend-miuix/releases")
                            },
                        )

                        BasicComponent(
                            title = "HyperOS 3 流光特效",
                            summary = if (isOs3Effect) "已开启 HyperOS 3 增强流光着色器" else "当前使用 HyperOS 2 经典流光着色器",
                            endActions = {
                                Switch(
                                    checked = isOs3Effect,
                                    onCheckedChange = { isOs3Effect = it },
                                )
                            },
                        )
                    }
                }
            }
        }

        // 官方 Miuix 风格更新弹窗
        if (showDialog && releaseInfo != null) {
            UpdateDialog(
                show = showDialog,
                releaseInfo = releaseInfo!!,
                onDismiss = { showDialog = false },
                onUpdate = { url ->
                    showDialog = false
                    updateManager.openInBrowser(context, url)
                },
                onIgnore = { ver ->
                    ignoredVersion = ver
                    showDialog = false
                },
            )
        }
    }
}
