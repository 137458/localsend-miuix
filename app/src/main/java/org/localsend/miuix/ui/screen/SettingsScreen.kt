package org.localsend.miuix.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import org.localsend.miuix.core.ExternalLinks
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.ui.component.CertFingerprintDialog
import org.localsend.miuix.ui.component.PinDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.lifecycle.compose.LifecycleResumeEffect
import org.localsend.miuix.notification.TransferNotifier

@Composable
fun SettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val colorScheme = MiuixTheme.colorScheme

    var showPinDialog by remember { mutableStateOf(false) }
    var showCertDialog by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(TransferNotifier.isNotificationsEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        isNotificationEnabled = TransferNotifier.isNotificationsEnabled(context)
        onPauseOrDispose {}
    }

    val themeOptions = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
        stringResource(R.string.theme_monet_system),
        stringResource(R.string.theme_monet_light),
        stringResource(R.string.theme_monet_dark)
    )

    val deviceTypeOptions = listOf(
        stringResource(R.string.device_type_mobile),
        stringResource(R.string.device_type_tablet),
        stringResource(R.string.device_type_desktop),
        stringResource(R.string.device_type_server)
    )
    val currentDeviceTypeIndex = remember(settings.deviceType) {
        when (settings.deviceType) {
            DeviceType.mobile -> 0
            DeviceType.tablet -> 1
            DeviceType.desktop -> 2
            DeviceType.server -> 3
            else -> 0
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        BlurredBar(
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
        ) {
            TopAppBar(
                title = stringResource(R.string.settings_title),
                scrollBehavior = scrollBehavior,
                color = if (backdrop != null) Color.Transparent else colorScheme.surface,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .blurBackdropSource(backdrop)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: General Settings
                item {
                    SmallTitle(text = stringResource(R.string.settings_section_general))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_alias_title),
                            summary = settings.alias,
                            onClick = onOpenRenameDialog
                        )
                    WindowDropdownPreference(
                        title = stringResource(R.string.settings_pref_device_type_title),
                        items = deviceTypeOptions,
                        selectedIndex = currentDeviceTypeIndex,
                        onSelectedIndexChange = { index ->
                            val newType = when (index) {
                                0 -> DeviceType.mobile
                                1 -> DeviceType.tablet
                                2 -> DeviceType.desktop
                                3 -> DeviceType.server
                                else -> DeviceType.mobile
                            }
                            manager.updateSettings { it.copy(deviceType = newType) }
                        }
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.settings_pref_theme_title),
                        items = themeOptions,
                        selectedIndex = settings.themeModeIndex,
                        onSelectedIndexChange = { index ->
                            manager.updateSettings { it.copy(themeModeIndex = index) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_vibrate_title),
                        summary = stringResource(R.string.settings_pref_vibrate_summary),
                        checked = settings.vibrateOnComplete,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(vibrateOnComplete = checked) }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_notif_perm_title),
                        summary = if (isNotificationEnabled) stringResource(R.string.settings_pref_notif_perm_enabled) else stringResource(R.string.settings_pref_notif_perm_disabled),
                        onClick = {
                            val activity = context as? org.localsend.miuix.ui.MainActivity
                            if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                activity.requestNecessaryPermissions()
                            } else {
                                org.localsend.miuix.notification.TransferNotifier.openNotificationSettings(context)
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_wide_nav_rail_title),
                        summary = stringResource(R.string.settings_pref_wide_nav_rail_summary),
                        checked = settings.wideScreenNavigationRail,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(wideScreenNavigationRail = checked) }
                        }
                    )
                }
            }

            // Section 2: Receive Settings
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = stringResource(R.string.settings_section_receive))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.receive_pref_quick_save_title),
                        summary = stringResource(R.string.settings_pref_quick_save_desc),
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.receive_pref_auto_copy_title),
                        summary = stringResource(R.string.settings_pref_auto_copy_desc),
                        checked = settings.autoCopyText,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCopyText = checked) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_save_text_as_file_title),
                        summary = stringResource(R.string.settings_pref_save_text_as_file_desc),
                        checked = settings.saveTextAsFile,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(saveTextAsFile = checked) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_auto_categorize_title),
                        summary = stringResource(R.string.settings_pref_auto_categorize_desc),
                        checked = settings.autoCategorizeMedia,
                        onCheckedChange = { checked ->
                            manager.setAutoCategorizeMedia(checked)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_save_history_title),
                        summary = stringResource(R.string.settings_pref_save_history_desc),
                        checked = settings.saveToHistory,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(saveToHistory = checked) }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_download_path_title),
                        summary = settings.downloadDisplay ?: settings.downloadPath,
                        onClick = onPickDirectory
                    )
                }
            }

            // Section 3: Network & Security Settings
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = stringResource(R.string.settings_section_network_security))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_port_title),
                        summary = settings.port.toString(),
                        onClick = onOpenPortDialog
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pref_https_title),
                        summary = stringResource(R.string.settings_pref_https_summary),
                        checked = settings.useHttps,
                        onCheckedChange = { checked ->
                            manager.applyUseHttpsChange(checked)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_pin_title),
                        summary = if (settings.pin.isNullOrEmpty()) stringResource(R.string.settings_pref_pin_disabled) else stringResource(R.string.settings_pref_pin_enabled),
                        onClick = { showPinDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_cert_fp_title),
                        summary = if (settings.useHttps) stringResource(R.string.settings_pref_cert_fp_enabled) else stringResource(R.string.settings_pref_cert_fp_disabled),
                        enabled = settings.useHttps,
                        onClick = { if (settings.useHttps) showCertDialog = true }
                    )
                }
            }

            // Section 4: About
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SmallTitle(text = stringResource(R.string.settings_section_about))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_check_update_title),
                        summary = stringResource(R.string.settings_pref_check_update_summary, org.localsend.miuix.BuildConfig.VERSION_NAME),
                        onClick = onNavigateToUpdate
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_github_title),
                        summary = ExternalLinks.GITHUB_REPO,
                        onClick = {
                            UpdateManager(context).openInBrowser(context, ExternalLinks.GITHUB_REPO)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_pref_license_title),
                        summary = stringResource(R.string.settings_pref_license_summary),
                        onClick = {
                            UpdateManager(context).openInBrowser(context, ExternalLinks.APACHE_LICENSE)
                        }
                    )
                }
            }
        }
    }
}

    PinDialog(
        show = showPinDialog,
        initialPin = settings.pin,
        onDismissRequest = { showPinDialog = false },
        onConfirm = { newPin ->
            manager.updateSettings { it.copy(pin = newPin) }
        }
    )

    CertFingerprintDialog(
        show = showCertDialog,
        fingerprint = if (settings.useHttps) manager.getLocalDevice().fingerprint else "",
        onDismissRequest = { showCertDialog = false },
        onRegenerate = {
            manager.regenerateCertificate()
        }
    )
}

