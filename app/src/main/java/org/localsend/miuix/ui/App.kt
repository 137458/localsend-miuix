package org.localsend.miuix.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.ui.component.AddContentBottomSheet
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.IncomingTransferDialog
import org.localsend.miuix.ui.component.ManualIpDialog
import org.localsend.miuix.ui.component.RenameDeviceDialog
import org.localsend.miuix.ui.component.SendTextDialog
import org.localsend.miuix.ui.screen.ReceiveScreen
import org.localsend.miuix.ui.screen.SendScreen
import org.localsend.miuix.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(manager: LocalSendManager) {
    val context = LocalContext.current
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

    // 2. Navigation Tab State
    var selectedTab by remember { mutableIntStateOf(0) }

    // 3. Dialog Visibility States
    var showRenameDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showSendTextDialog by remember { mutableStateOf(false) }
    var showAddContentSheet by remember { mutableStateOf(false) }

    // 4. Activity Result Launchers for File/Media Picking
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                var name = "file_${System.currentTimeMillis()}"
                var size = 0L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex)
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                var name = "media_${System.currentTimeMillis()}.jpg"
                var size = 0L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex)
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                FileItem(name = name, size = size, uri = uri, mimeType = mime)
            }
            manager.addFiles(items)
            Toast.makeText(context, "已添加 ${items.size} 个媒体文件", Toast.LENGTH_SHORT).show()
        }
    }

    MiuixTheme(controller = themeController) {
        Scaffold(
            bottomBar = {
                FloatingNavigationBar {
                    FloatingNavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = AppIcons.Send,
                        label = "发送"
                    )
                    FloatingNavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = AppIcons.Receive,
                        label = "接收",
                        badge = if (pendingIncomingSession != null) ({ Badge { Text("1") } }) else null
                    )
                    FloatingNavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = AppIcons.Settings,
                        label = "设置"
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> SendScreen(
                        manager = manager,
                        contentPadding = innerPadding,
                        onOpenAddSheet = { showAddContentSheet = true },
                        onOpenManualIp = { showManualIpDialog = true }
                    )
                    1 -> ReceiveScreen(
                        manager = manager,
                        contentPadding = innerPadding,
                        onOpenRenameDialog = { showRenameDialog = true }
                    )
                    2 -> SettingsScreen(
                        manager = manager,
                        contentPadding = innerPadding,
                        onOpenRenameDialog = { showRenameDialog = true }
                    )
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
                onPickFiles = { filePickerLauncher.launch("*/*") },
                onPickMedia = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onSendText = { showSendTextDialog = true },
                onPasteClipboard = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
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
                        Toast.makeText(context, "剪贴板中无文本内容", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
