package org.localsend.miuix.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.AddContentBottomSheet
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.AppPickerBottomSheet
import org.localsend.miuix.ui.component.IncomingTransferDialog
import org.localsend.miuix.ui.component.LiquidGlassBottomBar
import org.localsend.miuix.ui.component.PortDialog
import org.localsend.miuix.ui.component.RenameDeviceDialog
import org.localsend.miuix.ui.component.SendTextDialog
import org.localsend.miuix.ui.navigation.AppRoute
import org.localsend.miuix.ui.screen.HistoryScreen
import org.localsend.miuix.ui.screen.ReceiveScreen
import org.localsend.miuix.ui.screen.SendScreen
import org.localsend.miuix.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(manager: LocalSendManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by manager.settings.collectAsState()
    val pendingIncomingSession by manager.pendingIncomingSession.collectAsState()
    val sessionMessage by manager.sessionMessage.collectAsState()

    // 传输提示（如"对方拒绝接收"）以 Toast 呈现，显示后即消费
    LaunchedEffect(sessionMessage) {
        val msg = sessionMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        manager.consumeSessionMessage()
    }

    // 1. Theme Configuration
    val colorSchemeMode = remember(settings.themeModeIndex) {
        when (settings.themeModeIndex) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            3 -> ColorSchemeMode.MonetSystem
            4 -> ColorSchemeMode.MonetLight
            5 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.System
        }
    }
    val themeController = remember(colorSchemeMode) { ThemeController(colorSchemeMode) }

    // 2. Horizontal Pager State
    val pagerState = rememberPagerState(pageCount = { 3 })

    // 3. Navigation Items（接收在第 0 页，发送在第 1 页）
    val navigationItems = remember {
        listOf(
            NavigationItem("接收", AppIcons.Receive),
            NavigationItem("发送", AppIcons.Send),
            NavigationItem("设置", AppIcons.Settings)
        )
    }

    // 4. Dialog Visibility States
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSendTextDialog by remember { mutableStateOf(false) }
    var showAddContentSheet by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showWebShareDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }

    // 5. Activity Result Launchers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            val items = uris.map { uri ->
                var name = "file_${System.currentTimeMillis()}"
                var size = 0L
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (ignored: Exception) {}

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                                name = cursor.getString(nameIndex) ?: name
                            }
                            if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                size = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    name = uri.lastPathSegment ?: name
                }
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                FileItem(name = name, size = size, uri = uri, mimeType = mime)
            }
            manager.addFiles(items)
            Toast.makeText(context, "已添加 ${items.size} 个文件", Toast.LENGTH_SHORT).show()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                manager.addFolder(uri)
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            val items = uris.map { uri ->
                var name = "media_${System.currentTimeMillis()}.jpg"
                var size = 0L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                                name = cursor.getString(nameIndex) ?: name
                            }
                            if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                size = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    name = uri.lastPathSegment ?: name
                }
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                FileItem(name = name, size = size, uri = uri, mimeType = mime)
            }
            manager.addFiles(items)
            Toast.makeText(context, "已添加 ${items.size} 个媒体文件", Toast.LENGTH_SHORT).show()
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val display = try {
                DocumentFile.fromTreeUri(context, uri)?.name
                    ?: uri.lastPathSegment
                    ?: "自定义目录"
            } catch (e: Exception) {
                uri.lastPathSegment ?: "自定义目录"
            }
            manager.setDownloadTree(uri, display)
            Toast.makeText(context, "保存目录已设置为: $display", Toast.LENGTH_SHORT).show()
        }
    }

    val pickClipboard = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    val bytes = clipText.toByteArray(Charsets.UTF_8)
                    val item = FileItem(
                        name = "clipboard_${System.currentTimeMillis()}.txt",
                        size = bytes.size.toLong(),
                        textContent = clipText,
                        mimeType = "text/plain"
                    )
                    manager.addFiles(listOf(item))
                    Toast.makeText(context, "已提取剪贴板文本并添加", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "剪贴板中无内容", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "无法访问剪贴板: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    MiuixTheme(controller = themeController) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomBarTotalPadding = 80.dp + navBarBottomPadding

        // 6. miuix-nav 根导航栈管理
        val backStack = rememberNavBackStack<AppRoute>(AppRoute.Main)

        // 全局拦截系统返回键与返回手势，当处于二级页面时优先返回主界面
        BackHandler(enabled = backStack.size > 1) {
            backStack.removeLastOrNull()
        }

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transition = NavTransitions.MiuixDefault
        ) {
            entry<AppRoute.Main> {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp + navBarBottomPadding, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            LiquidGlassBottomBar(
                                items = navigationItems,
                                selectedIndex = { pagerState.currentPage },
                                onSelected = { index ->
                                    scope.launch {
                                        if (kotlin.math.abs(pagerState.currentPage - index) > 1) {
                                            pagerState.scrollToPage(index)
                                        } else {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                backdrop = backdrop,
                                badge = { index ->
                                    if (index == 0 && pendingIncomingSession != null) {
                                        { Badge { Text("1") } }
                                    } else null
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 2,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val pagePadding = PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = bottomBarTotalPadding
                            )
                            when (page) {
                                0 -> ReceiveScreen(
                                    manager = manager,
                                    contentPadding = pagePadding,
                                    onOpenRenameDialog = { showRenameDialog = true },
                                    onOpenHistory = { backStack.add(AppRoute.History) }
                                )
                                1 -> SendScreen(
                                    manager = manager,
                                    contentPadding = pagePadding,
                                    onOpenAddSheet = { showAddContentSheet = true },
                                    onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    onPickFolder = { folderPickerLauncher.launch(null) },
                                    onPickMedia = {
                                        mediaPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    onPickApps = { showAppPickerSheet = true },
                                    onSendText = { showSendTextDialog = true },
                                    onPasteClipboard = pickClipboard,
                                    onOpenWebShare = { showWebShareDialog = true },
                                    onManualIp = { showManualIpDialog = true }
                                )
                                2 -> SettingsScreen(
                                    manager = manager,
                                    contentPadding = pagePadding,
                                    onOpenRenameDialog = { showRenameDialog = true },
                                    onOpenPortDialog = { showPortDialog = true },
                                    onPickDirectory = { directoryPickerLauncher.launch(null) }
                                )
                            }
                        }
                    }
                }
            }

            // 独立传输历史页（通过 miuix-nav 连续深度推进展示，支持左滑边缘返回手势）
            entry<AppRoute.History>(
                swipeDismiss = NavSwipeDirection.LeftToRight
            ) {
                HistoryScreen(
                    manager = manager,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }

        // Global Overlay Dialogs & BottomSheets
        IncomingTransferDialog(
            session = pendingIncomingSession,
            onAccept = {
                pendingIncomingSession?.let { manager.acceptIncomingTransfer(it.sessionId) }
            },
            onAcceptAndCopy = {
                pendingIncomingSession?.let { session ->
                    val text = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent
                    if (!text.isNullOrEmpty()) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LocalSend Text", text))
                        Toast.makeText(context, "已复制文本到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                    manager.acceptIncomingTransfer(session.sessionId)
                }
            },
            onDecline = {
                pendingIncomingSession?.let { manager.declineIncomingTransfer(it.sessionId) }
            }
        )

        RenameDeviceDialog(
            show = showRenameDialog,
            initialName = settings.alias,
            onDismissRequest = { showRenameDialog = false },
            onConfirm = { newAlias ->
                manager.updateSettings { it.copy(alias = newAlias) }
                Toast.makeText(context, "设备名称已更新为: $newAlias", Toast.LENGTH_SHORT).show()
            }
        )

        PortDialog(
            show = showPortDialog,
            initialPort = settings.port,
            onDismissRequest = { showPortDialog = false },
            onConfirm = { newPort ->
                manager.applyPortChange(newPort)
                Toast.makeText(context, "服务端口已更新为: $newPort", Toast.LENGTH_SHORT).show()
            }
        )

        SendTextDialog(
            show = showSendTextDialog,
            onDismissRequest = { showSendTextDialog = false },
            onConfirm = { text ->
                val bytes = text.toByteArray(Charsets.UTF_8)
                val item = FileItem(
                    name = "text_${System.currentTimeMillis()}.txt",
                    size = bytes.size.toLong(),
                    textContent = text,
                    mimeType = "text/plain"
                )
                manager.addFiles(listOf(item))
                Toast.makeText(context, "已添加纯文本内容", Toast.LENGTH_SHORT).show()
            }
        )

        AddContentBottomSheet(
            show = showAddContentSheet,
            onDismissRequest = { showAddContentSheet = false },
            onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
            onPickFolder = { folderPickerLauncher.launch(null) },
            onPickMedia = {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onPickApps = { showAppPickerSheet = true },
            onSendText = { showSendTextDialog = true },
            onPasteClipboard = pickClipboard
        )

        AppPickerBottomSheet(
            show = showAppPickerSheet,
            onDismissRequest = { showAppPickerSheet = false },
            manager = manager
        )

        org.localsend.miuix.ui.component.WebShareDialog(
            show = showWebShareDialog,
            onDismissRequest = { showWebShareDialog = false },
            manager = manager
        )

        org.localsend.miuix.ui.component.ManualIpDialog(
            show = showManualIpDialog,
            onDismissRequest = { showManualIpDialog = false },
            onSend = { ip, port ->
                manager.sendToIp(ip, port)
                Toast.makeText(context, "正在连接 $ip:$port 发送内容...", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
