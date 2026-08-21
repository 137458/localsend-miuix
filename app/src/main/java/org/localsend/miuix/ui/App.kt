package org.localsend.miuix.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
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
import kotlinx.coroutines.launch
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.AddContentBottomSheet
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.IncomingTransferDialog
import org.localsend.miuix.ui.component.LiquidGlassBottomBar
import org.localsend.miuix.ui.component.ManualIpDialog
import org.localsend.miuix.ui.component.RenameDeviceDialog
import org.localsend.miuix.ui.component.SendTextDialog
import org.localsend.miuix.ui.screen.ReceiveScreen
import org.localsend.miuix.ui.screen.SendScreen
import org.localsend.miuix.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(manager: LocalSendManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by manager.settings.collectAsState()
    val pendingIncomingSession by manager.pendingIncomingSession.collectAsState()

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

    // 3. Navigation Items
    val navigationItems = remember {
        listOf(
            NavigationItem("发送", AppIcons.Send),
            NavigationItem("接收", AppIcons.Receive),
            NavigationItem("设置", AppIcons.Settings)
        )
    }

    // 4. Dialog Visibility States
    var showRenameDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showSendTextDialog by remember { mutableStateOf(false) }
    var showAddContentSheet by remember { mutableStateOf(false) }

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
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = backdrop,
                        badge = { index ->
                            if (index == 1 && pendingIncomingSession != null) {
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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pagePadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottomBarTotalPadding
                    )
                    when (page) {
                        0 -> SendScreen(
                            manager = manager,
                            contentPadding = pagePadding,
                            onOpenAddSheet = { showAddContentSheet = true },
                            onOpenManualIp = { showManualIpDialog = true },
                            onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onPickMedia = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onSendText = { showSendTextDialog = true },
                            onPasteClipboard = pickClipboard
                        )
                        1 -> ReceiveScreen(
                            manager = manager,
                            contentPadding = pagePadding,
                            onOpenRenameDialog = { showRenameDialog = true }
                        )
                        2 -> SettingsScreen(
                            manager = manager,
                            contentPadding = pagePadding,
                            onOpenRenameDialog = { showRenameDialog = true }
                        )
                    }
                }
            }

            // Global Overlay Dialogs & BottomSheets
            IncomingTransferDialog(
                session = pendingIncomingSession,
                onAccept = {
                    pendingIncomingSession?.let { manager.acceptIncomingTransfer(it.sessionId) }
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

            ManualIpDialog(
                show = showManualIpDialog,
                onDismissRequest = { showManualIpDialog = false },
                onSend = { targetIp, targetPort ->
                    val manualDevice = Device(
                        alias = "指定设备 ($targetIp)",
                        fingerprint = "$targetIp:$targetPort",
                        port = targetPort,
                        ip = targetIp
                    )
                    manager.sendFilesTo(manualDevice)
                    Toast.makeText(context, "正在向 $targetIp 发起连接与传输", Toast.LENGTH_SHORT).show()
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
                onPickMedia = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onSendText = { showSendTextDialog = true },
                onPasteClipboard = pickClipboard
            )
        }
    }
}
