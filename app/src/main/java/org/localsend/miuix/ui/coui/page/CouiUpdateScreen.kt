// ColorOS 皮肤软件更新页：coUI-Kit 卡片与设置分组，不使用 HyperOS 流光背景与模糊顶栏。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.coui.page

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.layout.Group
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.launch
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.ui.component.MarkdownText
import org.localsend.miuix.ui.component.UpdateDialogHost
import org.localsend.miuix.ui.component.updateBrowserUrl
import org.localsend.miuix.ui.component.updateProgressDetail
import org.localsend.miuix.ui.component.updateProgressFraction
import org.localsend.miuix.ui.coui.component.CouiArrowRow
import org.localsend.miuix.ui.coui.component.CouiSwitchRow
import org.localsend.miuix.updates.UpdateDownloadState
import org.localsend.miuix.updates.UpdatePrompt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.round

/**
 * ColorOS 皮肤的系统更新页。
 * 版式与 HyperOS 版对齐（版本信息、更新日志、更新设置、版本通道），视觉改用 coUI 卡片与分组。
 */
@Composable
fun CouiUpdateScreen(
    manager: LocalSendManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    val settings by manager.settings.collectAsStateWithLifecycle()

    var releaseInfo by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val downloaderState = manager.updates.downloader.state
    val downloadState by downloaderState.collectAsStateWithLifecycle()

    val hasNew = releaseInfo?.hasUpdate == true

    fun doCheck(userInitiated: Boolean = false) {
        if (isChecking) return
        isChecking = true
        coroutineScope.launch {
            val result = updateManager.checkForUpdate()
            isChecking = false
            result
                .onSuccess { info ->
                    releaseInfo = info
                    if (info.hasUpdate) {
                        val shouldNotify =
                            UpdatePrompt.shouldNotify(
                                latestVersion = info.latestVersion,
                                ignoredVersion = settings.ignoredVersion,
                                promptedVersion = manager.updates.promptedVersion,
                            )
                        if (userInitiated || shouldNotify) {
                            showDialog = true
                            manager.updates.promptedVersion = info.latestVersion
                        }
                    } else if (userInitiated) {
                        Toast.makeText(context, context.getString(R.string.toast_latest_version, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    val message = error.localizedMessage ?: ""
                    if (userInitiated) {
                        Toast.makeText(context, context.getString(R.string.toast_check_update_failed, message), Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    fun startDownload() {
        val release = releaseInfo ?: return
        val apk = release.apk
        if (apk != null) {
            manager.updates.downloader.start(apk)
        } else {
            UpdateManager.openInBrowser(context, updateBrowserUrl(release))
        }
    }

    LaunchedEffect(Unit) {
        if (settings.autoCheckUpdate) {
            doCheck(userInitiated = false)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CouiTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            CouiUpdateTopBar(onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") {
                    CouiUpdateHero(
                        isChecking = isChecking,
                        hasNew = hasNew,
                        latestVersion = releaseInfo?.latestVersion,
                    )
                }

                if (releaseInfo != null) {
                    item(key = "changelog") {
                        val changelogTitle =
                            if (hasNew) {
                                stringResource(R.string.update_changelog_title_new, releaseInfo?.latestVersion ?: "")
                            } else {
                                stringResource(R.string.update_changelog_title_current, BuildConfig.VERSION_NAME)
                            }
                        val defaultReleaseTitle = stringResource(R.string.update_release_default_title)
                        val releaseTitleText = releaseInfo?.releaseTitle?.ifBlank { defaultReleaseTitle } ?: defaultReleaseTitle
                        CouiSectionCard(title = changelogTitle) {
                            Text(
                                text = releaseTitleText,
                                kind = CouiTextKind.ItemTitle,
                                color = CouiTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            MarkdownText(
                                markdown = releaseInfo?.changelog ?: "",
                                modifier = Modifier.fillMaxWidth(),
                                baseFontSize = 14,
                            )
                            CouiDownloadStatus(state = downloadState)
                            CouiDownloadActions(
                                state = downloadState,
                                hasNew = hasNew,
                                apkAvailable = releaseInfo?.apk != null,
                                onCancel = { manager.updates.downloader.cancel() },
                                onInstall = { (downloadState as? UpdateDownloadState.Done)?.let { UpdateManager.installApk(context, it.file) } },
                                onRetry = { startDownload() },
                                onBrowser = { releaseInfo?.let { UpdateManager.openInBrowser(context, updateBrowserUrl(it)) } },
                                onDownload = { startDownload() },
                            )
                        }
                    }
                }

                item(key = "settings") {
                    Group(title = stringResource(R.string.update_section_settings)) {
                        CouiSwitchRow(
                            title = stringResource(R.string.update_pref_auto_check_title),
                            checked = settings.autoCheckUpdate,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(autoCheckUpdate = checked) }
                            },
                        )
                        CouiSwitchRow(
                            title = stringResource(R.string.update_pref_ignore_title),
                            summary =
                                when {
                                    isChecking -> stringResource(R.string.update_pref_ignore_checking)
                                    !hasNew -> stringResource(R.string.update_pref_ignore_already_latest)
                                    UpdatePrompt.isIgnored(settings.ignoredVersion, releaseInfo?.latestVersion) ->
                                        stringResource(R.string.update_pref_ignore_ignored, releaseInfo?.latestVersion ?: "")
                                    else -> stringResource(R.string.update_pref_ignore_desc, releaseInfo?.latestVersion ?: "")
                                },
                            checked = hasNew && UpdatePrompt.isIgnored(settings.ignoredVersion, releaseInfo?.latestVersion),
                            onCheckedChange = {
                                val next = UpdatePrompt.toggleIgnored(settings.ignoredVersion, releaseInfo?.latestVersion)
                                manager.updateSettings { it.copy(ignoredVersion = next) }
                            },
                            enabled = hasNew,
                        )
                    }
                }

                item(key = "channel") {
                    Group(title = stringResource(R.string.update_section_channel)) {
                        CouiArrowRow(
                            title = stringResource(R.string.update_pref_manual_check_title),
                            summary =
                                if (isChecking) {
                                    stringResource(R.string.update_pref_manual_check_busy)
                                } else {
                                    stringResource(R.string.update_pref_manual_check_idle)
                                },
                            onClick = { doCheck(userInitiated = true) },
                        )
                        CouiArrowRow(
                            title = "GitHub Releases",
                            onClick = {
                                UpdateManager.openInBrowser(context, "https://github.com/137458/localsend-miuix/releases")
                            },
                        )
                    }
                }
            }
        }

        UpdateDialogHost(
            show = showDialog,
            manager = manager,
            releaseInfo = releaseInfo,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun CouiUpdateTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CouiTopBarAction(
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.update_screen_title),
            kind = CouiTextKind.TopBar,
            color = CouiTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CouiTopBarAction(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CouiShape(radius = 20.dp, cornerWeight = 3f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnClick() })
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = contentDescription,
            tint = CouiTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 版本 Hero 区：应用图标、名称与当前检查结果状态行。
 */
@Composable
private fun CouiUpdateHero(
    isChecking: Boolean,
    hasNew: Boolean,
    latestVersion: String?,
) {
    val scheme = CouiTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.container),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_localsend_logo),
                contentDescription = "LocalSend",
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "LocalSend Miuix",
            kind = CouiTextKind.HomeTitle,
            color = scheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        val statusText =
            when {
                isChecking -> stringResource(R.string.update_checking_hint)
                hasNew -> stringResource(R.string.update_found_header, latestVersion ?: "", BuildConfig.VERSION_NAME)
                else -> stringResource(R.string.update_latest_header, BuildConfig.VERSION_NAME)
            }
        val statusColor = if (hasNew && !isChecking) scheme.accent else scheme.onSurfaceVariant
        Text(
            text = statusText,
            kind = CouiTextKind.ItemSummary,
            color = statusColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

/**
 * 通用卡片：标题使用分组字阶，内容放在 20dp 圆角卡片内。
 */
@Composable
private fun CouiSectionCard(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                kind = CouiTextKind.GroupTitle,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(CouiShape(radius = 20.dp, cornerWeight = 3f))
                    .background(CouiTheme.colorScheme.card)
                    .padding(14.dp),
            content = content,
        )
    }
}

/**
 * 下载进度与失败原因：与 HyperOS 版信息一致，进度条沿用 Miuix 组件（coUI 不提供进度控件）。
 */
@Composable
private fun CouiDownloadStatus(state: UpdateDownloadState) {
    val scheme = CouiTheme.colorScheme
    when (state) {
        is UpdateDownloadState.Downloading -> {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.update_downloading_hint),
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.accent,
                )
                Text(
                    text = "${round(updateProgressFraction(state) * 100).toInt()}%",
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = updateProgressFraction(state),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = updateProgressDetail(state),
                kind = CouiTextKind.GroupFooter,
                color = scheme.onSurfaceVariant,
            )
        }
        is UpdateDownloadState.Failed -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.message,
                kind = CouiTextKind.GroupFooter,
                color = MiuixTheme.colorScheme.error,
            )
        }
        else -> Unit
    }
}

/**
 * 下载相关操作按钮：下载 / 取消 / 安装 / 重试 / 浏览器打开。
 */
@Composable
private fun CouiDownloadActions(
    state: UpdateDownloadState,
    hasNew: Boolean,
    apkAvailable: Boolean,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onBrowser: () -> Unit,
    onDownload: () -> Unit,
) {
    if (state !is UpdateDownloadState.Downloading) {
        Spacer(modifier = Modifier.height(14.dp))
    }
    when (state) {
        is UpdateDownloadState.Downloading -> {
            CouiActionButton(
                text = stringResource(R.string.update_dialog_btn_cancel_download),
                primary = false,
                onClick = onCancel,
            )
        }
        is UpdateDownloadState.Done -> {
            CouiActionButton(
                text = stringResource(R.string.update_btn_install_now),
                onClick = onInstall,
            )
        }
        is UpdateDownloadState.Failed -> {
            CouiActionButton(
                text = stringResource(R.string.btn_retry),
                onClick = onRetry,
            )
            Spacer(modifier = Modifier.height(8.dp))
            CouiActionButton(
                text = stringResource(R.string.update_dialog_btn_browser),
                primary = false,
                onClick = onBrowser,
            )
        }
        UpdateDownloadState.Idle -> {
            if (hasNew) {
                CouiActionButton(
                    text =
                        if (apkAvailable) {
                            stringResource(R.string.update_btn_download_now)
                        } else {
                            "前往 Releases 页面"
                        },
                    onClick = onDownload,
                )
            }
        }
    }
}

/**
 * 全宽胶囊按钮：coUI 没有通用 Button，主/次操作用 accent 与 container 两种底色区分。
 */
@Composable
private fun CouiActionButton(
    text: String,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CouiShape(radius = 22.dp, cornerWeight = 3f))
                .background(if (primary) scheme.accent else scheme.container)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnClick() })
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemTitle,
            color = if (primary) scheme.onAccent else scheme.onSurface,
        )
    }
}
