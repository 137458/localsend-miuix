package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.FileItem
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

/**
 * 官方 Miuix / HyperOS 规范版本更新弹窗：支持应用内流式下载安装包与一键自动调起安装。
 */
@Composable
fun UpdateDialog(
    show: Boolean,
    releaseInfo: UpdateCheckResult,
    onDismiss: () -> Unit,
    onUpdate: (url: String) -> Unit,
    onIgnore: ((version: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun startDownload() {
        val downloadUrl = releaseInfo.downloadUrl ?: releaseInfo.releaseUrl

        isDownloading = true
        downloadError = null
        downloadProgress = 0f

        coroutineScope.launch {
            val result = updateManager.downloadApk(
                downloadUrl = downloadUrl,
                onProgress = { progress, downloaded, total ->
                    downloadProgress = progress
                    downloadedBytes = downloaded
                    totalBytes = total
                },
            )
            isDownloading = false
            result
                .onSuccess { file ->
                    downloadedFile = file
                    updateManager.installApk(context, file)
                }
                .onFailure { error ->
                    downloadError = error.localizedMessage ?: "下载失败"
                }
        }
    }

    WindowDialog(
        show = show,
        title = "发现新版本",
        summary = "v${BuildConfig.VERSION_NAME} → ${releaseInfo.latestVersion}",
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 更新日志卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (releaseInfo.changelog.isNotBlank()) {
                    MarkdownText(
                        markdown = releaseInfo.changelog,
                        modifier = Modifier.fillMaxWidth(),
                        baseFontSize = 13,
                    )
                } else {
                    Text(
                        text = "暂无更新日志",
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // 下载进度状态条
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
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
            }

            // 错误提示
            if (downloadError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadError ?: "",
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                    color = MiuixTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部操作按钮
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onIgnore != null && !isDownloading && downloadedFile == null) {
                    TextButton(
                        text = "忽略此版本",
                        onClick = { onIgnore(releaseInfo.latestVersion) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (!isDownloading) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (downloadedFile != null) {
                    // 已下载完成，提供重新安装 / 打开安装包
                    TextButton(
                        text = "立即安装",
                        onClick = {
                            updateManager.installApk(context, downloadedFile!!)
                        },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                } else if (!isDownloading) {
                    TextButton(
                        text = if (downloadError != null) "重试" else "立即更新",
                        onClick = {
                            startDownload()
                        },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
