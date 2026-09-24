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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.MarkdownText
import org.localsend.miuix.ui.component.UpdateDialog
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 官方 Miuix / HyperOS 视觉规范系统与应用更新页。
 * 集成 HyperOS 动态流光背景、Markdown 更新日志与官方 UpdateDialog 弹窗体系。
 */
@Composable
fun UpdateScreen(
    manager: LocalSendManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val settings by manager.settings.collectAsState()

    var releaseInfo by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

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
            result
                .onSuccess { info ->
                    releaseInfo = info
                    if (info.hasUpdate) {
                        if (userInitiated || info.latestVersion != settings.ignoredVersion) {
                            showDialog = true
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

    LaunchedEffect(Unit) {
        if (settings.autoCheckUpdate) {
            doCheck(userInitiated = false)
        }
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
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            val barColor =
                if (backdrop != null) {
                    Color.Transparent
                } else if (scrollProgress == 1f) {
                    MiuixTheme.colorScheme.surface
                } else {
                    Color.Transparent
                }
            val titleColor =
                MiuixTheme.colorScheme.onSurface.copy(
                    alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                )
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = topAppBarScrollBehavior,
            ) {
                SmallTopAppBar(
                    title = stringResource(R.string.update_screen_title),
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = titleColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .blurBackdropSource(backdrop),
        ) {
            BgEffectBackground(
                dynamicBackground = isRuntimeShaderSupported(),
                isOs3Effect = settings.isOs3Effect,
                isFullSize = true,
                modifier = Modifier.fillMaxSize(),
                alpha = { 1f - scrollProgress },
            ) {
                // ── 顶部官方规范 Hero 视觉 ──
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = innerPadding.calculateTopPadding() + 24.dp)
                            .onSizeChanged { size ->
                                with(density) { logoHeightDp = size.height.toDp() }
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(88.dp)
                                .graphicsLayer {
                                    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                    clip = true
                                    shape = RoundedCornerShape(24.dp)
                                    alpha = 1 - iconProgress
                                    scaleX = 1 - (iconProgress * 0.05f)
                                    scaleY = 1 - (iconProgress * 0.05f)
                                }.background(MiuixTheme.colorScheme.surfaceVariant),
                    ) {
                        Image(
                            painter =
                                androidx.compose.ui.res
                                    .painterResource(id = org.localsend.miuix.R.drawable.ic_localsend_logo),
                            contentDescription = "LocalSend",
                            modifier = Modifier.size(52.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "LocalSend Miuix",
                        style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
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
                            text = stringResource(R.string.update_checking_hint),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                        alpha = 1 - verProgress
                                    },
                        )
                    } else if (hasNew) {
                        Text(
                            text = stringResource(R.string.update_found_header, releaseInfo?.latestVersion ?: "", BuildConfig.VERSION_NAME),
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                        alpha = 1 - verProgress
                                    },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.update_latest_header, BuildConfig.VERSION_NAME),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding() + 24.dp,
                        ),
                ) {
                    item(key = "logoSpacer") {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(logoHeightDp + 48.dp),
                        )
                    }

                    // ── 更新日志卡片（获取到版本信息后常驻展示，保证稳定可见） ──
                    if (releaseInfo != null) {
                        item(key = "changelog") {
                            val changelogTitle =
                                if (hasNew) {
                                    stringResource(R.string.update_changelog_title_new, releaseInfo?.latestVersion ?: "")
                                } else {
                                    stringResource(R.string.update_changelog_title_current, BuildConfig.VERSION_NAME)
                                }
                            val defaultReleaseTitle = stringResource(R.string.update_release_default_title)
                            SmallTitle(text = changelogTitle)
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = releaseInfo?.releaseTitle?.ifBlank { defaultReleaseTitle } ?: defaultReleaseTitle,
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
                                                text = stringResource(R.string.update_downloading_hint),
                                                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                            val percent = if (downloadProgress >= 0f) "${(downloadProgress * 100).toInt()}%" else ""
                                            val sizeText =
                                                if (totalBytes > 0) {
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
                                            text = stringResource(R.string.update_btn_install_now),
                                            onClick = {
                                                updateManager.installApk(context, downloadedFile!!)
                                            },
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else if (hasNew && !isDownloading) {
                                        TextButton(
                                            text = stringResource(R.string.update_btn_download_now),
                                            onClick = {
                                                val url = releaseInfo?.downloadUrl ?: releaseInfo?.releaseUrl
                                                if (url != null) {
                                                    isDownloading = true
                                                    downloadProgress = 0f
                                                    coroutineScope.launch {
                                                        val result =
                                                            updateManager.downloadApk(
                                                                downloadUrl = url,
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
                                                            }.onFailure { error ->
                                                                Toast.makeText(context, context.getString(R.string.toast_download_apk_failed, error.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
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
                        SmallTitle(text = stringResource(R.string.update_section_settings))
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                        ) {
                            SwitchPreference(
                                title = stringResource(R.string.update_pref_auto_check_title),
                                summary = stringResource(R.string.update_pref_auto_check_summary),
                                checked = settings.autoCheckUpdate,
                                onCheckedChange = { checked ->
                                    manager.updateSettings { it.copy(autoCheckUpdate = checked) }
                                },
                            )

                            SwitchPreference(
                                title = stringResource(R.string.update_pref_ignore_title),
                                summary =
                                    when {
                                        isChecking -> stringResource(R.string.update_pref_ignore_checking)
                                        !hasNew -> stringResource(R.string.update_pref_ignore_already_latest)
                                        settings.ignoredVersion == releaseInfo?.latestVersion -> stringResource(R.string.update_pref_ignore_ignored, releaseInfo?.latestVersion ?: "")
                                        else -> stringResource(R.string.update_pref_ignore_desc, releaseInfo?.latestVersion ?: "")
                                    },
                                checked = hasNew && settings.ignoredVersion == releaseInfo?.latestVersion,
                                onCheckedChange = { checked ->
                                    manager.updateSettings { it.copy(ignoredVersion = if (checked) releaseInfo?.latestVersion else null) }
                                },
                                enabled = hasNew,
                            )
                        }
                    }

                    // ── 版本通道与操作 ──
                    item(key = "channel") {
                        SmallTitle(text = stringResource(R.string.update_section_channel))
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                        ) {
                            BasicComponent(
                                title = stringResource(R.string.update_pref_manual_check_title),
                                summary = if (isChecking) stringResource(R.string.update_pref_manual_check_busy) else stringResource(R.string.update_pref_manual_check_idle),
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
                                summary = stringResource(R.string.update_pref_github_releases_summary),
                                onClick = {
                                    updateManager.openInBrowser(context, "https://github.com/137458/localsend-miuix/releases")
                                },
                            )

                            if (isRuntimeShaderSupported()) {
                                SwitchPreference(
                                    title = stringResource(R.string.update_pref_os3_title),
                                    summary = if (settings.isOs3Effect) stringResource(R.string.update_pref_os3_enabled) else stringResource(R.string.update_pref_os3_disabled),
                                    checked = settings.isOs3Effect,
                                    onCheckedChange = { checked ->
                                        manager.updateSettings { it.copy(isOs3Effect = checked) }
                                    },
                                )
                            }
                        }
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
                    manager.updateSettings { it.copy(ignoredVersion = ver) }
                    showDialog = false
                },
            )
        }
    }
}
