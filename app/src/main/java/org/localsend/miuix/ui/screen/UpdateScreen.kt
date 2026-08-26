package org.localsend.miuix.ui.screen

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val scrollBehavior = MiuixScrollBehavior()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = "软件更新",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { doCheckUpdate() },
                        enabled = !isChecking
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. App Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "LocalSend Miuix",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "当前版本: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )

                        if (isChecking) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                                Text(
                                    text = "正在检查最新版本...",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        } else if (checkResult != null) {
                            val info = checkResult!!
                            if (info.hasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "发现新版本 ${info.latestVersion}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "已是最新版本",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        } else if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 2. New Version Details Card
            if (checkResult != null && checkResult!!.hasUpdate) {
                val info = checkResult!!
                item {
                    SmallTitle(text = "更新内容 (${info.latestVersion})")
                    Spacer(modifier = Modifier.height(6.dp))
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                        text = "包体大小: ${FileItem.formatFileSize(info.apkSize)}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }

                            if (info.changelog.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = info.changelog.trim(),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Download / Install Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (val state = downloadState) {
                            is UpdateDownloadState.Idle -> {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    onClick = {
                                        val url = info.downloadUrl
                                        if (url != null) {
                                            downloadState = UpdateDownloadState.Downloading(0f, 0L, info.apkSize)
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
                                        } else {
                                            updateManager.openInBrowser(context, info.releaseUrl)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = if (info.downloadUrl != null) "立即下载更新" else "前往 GitHub 下载")
                                }
                            }
                            is UpdateDownloadState.Downloading -> {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                text = "${(state.progress * 100).toInt()}%",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.primary
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = state.progress,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = "${FileItem.formatFileSize(state.downloadedBytes)} / ${FileItem.formatFileSize(state.totalBytes)}",
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                            is UpdateDownloadState.Completed -> {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    onClick = {
                                        updateManager.installApk(context, state.file)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "安装更新包")
                                }
                            }
                            is UpdateDownloadState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "下载出错: ${state.message}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.error
                                    )
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                        onClick = {
                                            downloadState = UpdateDownloadState.Idle
                                        }
                                    ) {
                                        Text(text = "重试下载")
                                    }
                                }
                            }
                        }

                        TextButton(
                            text = "在 GitHub 中查看 Release 页面",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                updateManager.openInBrowser(context, info.releaseUrl)
                            }
                        )
                    }
                }
            } else if (checkResult != null && !checkResult!!.hasUpdate) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                        onClick = { doCheckUpdate() },
                        enabled = !isChecking
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "重新检查更新")
                    }
                }
            }
        }
    }
}
