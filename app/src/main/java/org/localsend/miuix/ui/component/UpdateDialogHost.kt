package org.localsend.miuix.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.updates.UpdatePrompt

/**
 * 更新弹窗的统一接线：下载、安装、取消、浏览器回退与忽略版本都交给管理器处理，
 * 首页启动提示与更新页手动检查共用同一套回调。
 */
@Composable
fun UpdateDialogHost(
    show: Boolean,
    manager: LocalSendManager,
    releaseInfo: UpdateCheckResult?,
    onDismiss: () -> Unit,
) {
    if (releaseInfo == null) return
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val downloaderState = manager.updates.downloader.state
    val downloadState by downloaderState.collectAsStateWithLifecycle()

    UpdateDialog(
        show = show,
        releaseInfo = releaseInfo,
        downloadState = downloadState,
        onDismiss = onDismiss,
        onDownload = {
            val apk = releaseInfo.apk
            if (apk != null) {
                manager.updates.downloader.start(apk)
            } else {
                UpdateManager.openInBrowser(context, releaseInfo.releaseUrl)
            }
        },
        onInstall = { file -> UpdateManager.installApk(context, file) },
        onCancelDownload = { manager.updates.downloader.cancel() },
        onOpenBrowser = { url -> UpdateManager.openInBrowser(context, url) },
        onIgnore = { version ->
            val next = UpdatePrompt.toggleIgnored(settings.ignoredVersion, version)
            manager.updateSettings { it.copy(ignoredVersion = next) }
            onDismiss()
        },
    )
}
