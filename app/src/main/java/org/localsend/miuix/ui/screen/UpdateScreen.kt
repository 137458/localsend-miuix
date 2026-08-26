package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateDownloadState
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.HyperOSFlowingGlowBackground
import org.localsend.miuix.ui.component.MiuixMarkdown
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS 澎湃风格沉浸式系统更新页面
 *
 * 核心特性：
 * 1. 顶部全景动态流光弥散背景（HyperOSFlowingGlowBackground），自适应深色/浅色模式
 * 2. 澎湃风格大版本号展示与呼吸发光徽章
 * 3. 完整的 Markdown 富文本更新日志渲染（MiuixMarkdown）
 * 4. 底部常驻悬浮操作栏（Sticky Bottom Action Bar），下载/安装/重试状态无缝流转
 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val isDark = isSystemInDarkTheme()

    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }

    fun doCheckUpdate() {
        if (isChecking) return
        isChecking = true
        errorMessage = null
        scope.launch {
            val result = updateManager.checkForUpdate()
            isChecking = false
            result.onSuccess { info ->
                checkResult = info
            }.onFailure { error ->
                errorMessage = error.localizedMessage ?: "检查更新失败"
                Toast.makeText(context, "检查更新失败: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        doCheckUpdate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 澎湃风格全景流光弥散背景
        HyperOSFlowingGlowBackground(
            modifier = Modifier.fillMaxSize(),
            isDark = isDark
        )

        // 2. 页面主体内容（可滚动）
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 80.dp,
                bottom = 120.dp // 为底部常驻操作栏预留空间
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // (1) 顶部大图标与版本信息展示区
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // HyperOS 风格发光应用图标徽章
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(
                                elevation = if (isDark) 16.dp else 8.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(
                                width = 1.dp,
                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Text(
                        text = "LocalSend Miuix",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    // 状态提示与版本 Badge
                    AnimatedContent(
                        targetState = Triple(isChecking, checkResult, errorMessage),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "UpdateStatusAnim"
                    ) { (checking, result, error) ->
                        when {
                            checking -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LinearProgressIndicator(modifier = Modifier.width(140.dp))
                                    Text(
                                        text = "正在检查最新版本...",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            result != null && result.hasUpdate -> {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
                                        .border(
                                            width = 1.dp,
                                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MiuixTheme.colorScheme.primary)
                                        )
                                        Text(
                                            text = "发现新版本 ${result.latestVersion}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            result != null && !result.hasUpdate -> {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFF4CAF50).copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "已是最新版本 (v${BuildConfig.VERSION_NAME})",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                            error != null -> {
                                Text(
                                    text = error,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.error
                                )
                            }
                            else -> {
                                Text(
                                    text = "当前版本: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    fontSize = 12.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            // (2) 新版本详情与更新日志（若有新版本）
            if (checkResult != null && checkResult!!.hasUpdate) {
                val info = checkResult!!

                // 基本信息卡片
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (info.releaseTitle.isNotBlank()) {
                                Text(
                                    text = info.releaseTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (info.publishedAt.isNotBlank()) {
                                    Text(
                                        text = "发布日期: ${info.publishedAt}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                if (info.apkSize > 0) {
                                    Text(
                                        text = "安装包: ${FileItem.formatFileSize(info.apkSize)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Markdown 更新日志卡片
                if (info.changelog.isNotBlank()) {
                    item {
                        SmallTitle(text = "更新日志")
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                MiuixMarkdown(
                                    markdown = info.changelog.trim(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else if (checkResult != null && !checkResult!!.hasUpdate) {
                // (3) 已是最新版本时的信息卡片
                item {
                    SmallTitle(text = "当前版本特性")
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FeatureRow(
                                icon = Icons.Default.Speed,
                                title = "局域网全速传输",
                                desc = "基于原生 Ktor 与 Okio 高性能传输引擎，极速互传"
                            )
                            FeatureRow(
                                icon = Icons.Default.Security,
                                title = "端到端安全加密",
                                desc = "TLS/HTTPS 证书安全加密，保护传输私密性"
                            )
                            FeatureRow(
                                icon = Icons.Default.RocketLaunch,
                                title = "HyperOS 澎湃沉浸设计",
                                desc = "液态玻璃悬浮底栏与 Miuix 超椭圆无缝融合"
                            )
                        }
                    }
                }
            }
        }

        // 3. 顶部透明沉浸式导航栏
        TopBar(
            onBack = onBack,
            onRefresh = { doCheckUpdate() },
            isChecking = isChecking
        )

        // 4. 底部常驻悬浮操作栏（Sticky Bottom Action Bar）
        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            checkResult = checkResult,
            isChecking = isChecking,
            downloadState = downloadState,
            onCheckUpdate = { doCheckUpdate() },
            onDownload = { url, size ->
                downloadState = UpdateDownloadState.Downloading(0f, 0L, size)
                scope.launch {
                    val dlResult = updateManager.downloadApk(url) { progress, current, total ->
                        downloadState = UpdateDownloadState.Downloading(progress, current, total)
                    }
                    dlResult.onSuccess { apkFile ->
                        downloadState = UpdateDownloadState.Completed(apkFile)
                        updateManager.installApk(context, apkFile)
                    }.onFailure { error ->
                        downloadState = UpdateDownloadState.Error(error.localizedMessage ?: "下载失败")
                        Toast.makeText(context, "下载失败: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onInstall = { file -> updateManager.installApk(context, file) },
            onRetryDownload = { downloadState = UpdateDownloadState.Idle },
            onOpenBrowser = { url -> updateManager.openInBrowser(context, url) }
        )
    }
}

/**
 * 顶部透明沉浸式导航栏
 */
@Composable
private fun TopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    isChecking: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "软件更新",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        IconButton(
            onClick = onRefresh,
            enabled = !isChecking
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "重新检查",
                tint = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 底部常驻操作栏（Sticky Bottom Action Bar）
 */
@Composable
private fun BottomActionBar(
    modifier: Modifier = Modifier,
    checkResult: UpdateCheckResult?,
    isChecking: Boolean,
    downloadState: UpdateDownloadState,
    onCheckUpdate: () -> Unit,
    onDownload: (url: String, size: Long) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onRetryDownload: () -> Unit,
    onOpenBrowser: (url: String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) {
        Color(0xFF1B1D22).copy(alpha = 0.92f)
    } else {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.95f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(bgColor)
            .border(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.25f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                // 1. 下载中状态
                downloadState is UpdateDownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在下载更新包...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(downloadState.progress * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = downloadState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${FileItem.formatFileSize(downloadState.downloadedBytes)} / ${FileItem.formatFileSize(downloadState.totalBytes)}",
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                // 2. 下载完成状态
                downloadState is UpdateDownloadState.Completed -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = { onInstall(downloadState.file) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "立即安装更新")
                    }
                }

                // 3. 下载出错状态
                downloadState is UpdateDownloadState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "下载失败: ${downloadState.message}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            onClick = onRetryDownload
                        ) {
                            Text(text = "重试")
                        }
                    }
                }

                // 4. 有新版本且处于空闲状态
                checkResult != null && checkResult.hasUpdate -> {
                    val info = checkResult
                    val downloadUrl = info.downloadUrl

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = {
                            if (downloadUrl != null) {
                                onDownload(downloadUrl, info.apkSize)
                            } else {
                                onOpenBrowser(info.releaseUrl)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (downloadUrl != null) Icons.Default.Download else Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (downloadUrl != null) {
                                if (info.apkSize > 0) "立即下载更新 (${FileItem.formatFileSize(info.apkSize)})" else "立即下载更新"
                            } else {
                                "前往 GitHub 下载"
                            }
                        )
                    }

                    TextButton(
                        text = "在 GitHub 中查看 Release 页面",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenBrowser(info.releaseUrl) }
                    )
                }

                // 5. 已是最新版本或检查中
                else -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                        onClick = onCheckUpdate,
                        enabled = !isChecking
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isChecking) "正在检查更新..." else "重新检查更新")
                    }
                }
            }
        }
    }
}

/**
 * 特性条目展示行
 */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}
