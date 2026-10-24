package org.localsend.miuix.ui.screen

import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.localsend.miuix.R
import org.localsend.miuix.core.ExternalLinks
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.notification.TransferNotifier
import org.localsend.miuix.ui.MainActivity
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.CertFingerprintDialog
import org.localsend.miuix.ui.component.DialogButtonRow
import org.localsend.miuix.ui.component.PinDialog
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import org.localsend.miuix.util.AppLanguage
import org.localsend.miuix.util.SettingsValidators
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 高级网络选项中正在编辑的字段，null 表示无编辑弹窗。 */
private enum class AdvancedNetworkField {
    WHITELIST,
    BLACKLIST,
    MULTICAST_GROUP,
    DISCOVERY_TIMEOUT,
}

@Composable
fun SettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val colorScheme = MiuixTheme.colorScheme

    var showPinDialog by remember { mutableStateOf(false) }
    var showCertDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var editingAdvancedField by remember { mutableStateOf<AdvancedNetworkField?>(null) }
    var isNotificationEnabled by remember { mutableStateOf(TransferNotifier.isNotificationsEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        isNotificationEnabled = TransferNotifier.isNotificationsEnabled(context)
        onPauseOrDispose {}
    }

    val themeOptions =
        listOf(
            stringResource(R.string.theme_system),
            stringResource(R.string.theme_light),
            stringResource(R.string.theme_dark),
            stringResource(R.string.theme_monet_system),
            stringResource(R.string.theme_monet_light),
            stringResource(R.string.theme_monet_dark),
        )

    val languageSettings =
        remember {
            listOf(AppLanguage.SETTING_SYSTEM, AppLanguage.SETTING_ZH, AppLanguage.SETTING_EN)
        }
    val languageOptions =
        listOf(
            stringResource(R.string.language_system),
            stringResource(R.string.language_zh),
            stringResource(R.string.language_en),
        )
    val currentLanguageIndex = languageSettings.indexOf(settings.language).coerceAtLeast(0)

    val supportedDeviceTypes =
        remember {
            listOf(
                DeviceType.mobile,
                DeviceType.tablet,
                DeviceType.desktop,
                DeviceType.server,
            )
        }
    val deviceTypeOptions =
        listOf(
            stringResource(R.string.device_type_mobile),
            stringResource(R.string.device_type_tablet),
            stringResource(R.string.device_type_desktop),
            stringResource(R.string.device_type_server),
        )
    val currentDeviceTypeIndex =
        remember(settings.deviceType, supportedDeviceTypes) {
            supportedDeviceTypes.indexOf(settings.deviceType).coerceAtLeast(0)
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
                    .blurBackdropSource(backdrop),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        start = 12.dp,
                        end = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Section 1: General Settings
                item {
                    SmallTitle(text = stringResource(R.string.settings_section_general))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_alias_title),
                            summary = settings.alias,
                            onClick = onOpenRenameDialog,
                        )
                        WindowDropdownPreference(
                            title = stringResource(R.string.settings_pref_language_title),
                            items = languageOptions,
                            selectedIndex = currentLanguageIndex,
                            onSelectedIndexChange = { index ->
                                val newLanguage = languageSettings.getOrElse(index) { AppLanguage.SETTING_SYSTEM }
                                manager.updateSettings { it.copy(language = newLanguage) }
                                // 语言偏好已持久化，重建后 attachBaseContext 会重新读取并包装 Context
                                (context as? MainActivity)?.recreate()
                            },
                        )
                        WindowDropdownPreference(
                            title = stringResource(R.string.settings_pref_device_type_title),
                            items = deviceTypeOptions,
                            selectedIndex = currentDeviceTypeIndex,
                            onSelectedIndexChange = { index ->
                                val newType = supportedDeviceTypes.getOrElse(index) { DeviceType.mobile }
                                manager.updateSettings { it.copy(deviceType = newType) }
                            },
                        )
                        WindowDropdownPreference(
                            title = stringResource(R.string.settings_pref_theme_title),
                            items = themeOptions,
                            selectedIndex = settings.themeModeIndex,
                            onSelectedIndexChange = { index ->
                                manager.updateSettings { it.copy(themeModeIndex = index) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_vibrate_title),
                            summary = stringResource(R.string.settings_pref_vibrate_summary),
                            checked = settings.vibrateOnComplete,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(vibrateOnComplete = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_auto_finish_title),
                            summary = stringResource(R.string.settings_pref_auto_finish_summary),
                            checked = settings.autoFinish,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(autoFinish = checked) }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_notif_perm_title),
                            summary = if (isNotificationEnabled) stringResource(R.string.settings_pref_notif_perm_enabled) else stringResource(R.string.settings_pref_notif_perm_disabled),
                            onClick = {
                                val activity = context as? org.localsend.miuix.ui.MainActivity
                                if (activity != null &&
                                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                    activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    activity.requestNecessaryPermissions()
                                } else {
                                    org.localsend.miuix.notification.TransferNotifier
                                        .openNotificationSettings(context)
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_wide_nav_rail_title),
                            summary = stringResource(R.string.settings_pref_wide_nav_rail_summary),
                            checked = settings.wideScreenNavigationRail,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(wideScreenNavigationRail = checked) }
                            },
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
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.receive_pref_quick_save_from_favorites_title),
                            summary = stringResource(R.string.receive_pref_quick_save_from_favorites_summary),
                            checked = settings.quickSaveFromFavorites,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(quickSaveFromFavorites = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.receive_pref_auto_copy_title),
                            summary = stringResource(R.string.settings_pref_auto_copy_desc),
                            checked = settings.autoCopyText,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(autoCopyText = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_save_text_as_file_title),
                            summary = stringResource(R.string.settings_pref_save_text_as_file_desc),
                            checked = settings.saveTextAsFile,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(saveTextAsFile = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_auto_categorize_title),
                            summary = stringResource(R.string.settings_pref_auto_categorize_desc),
                            checked = settings.autoCategorizeMedia,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(autoCategorizeMedia = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_web_share_auto_accept_title),
                            summary = stringResource(R.string.settings_pref_web_share_auto_accept_summary),
                            checked = settings.webShareAutoAccept,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(webShareAutoAccept = checked) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_save_history_title),
                            summary = stringResource(R.string.settings_pref_save_history_desc),
                            checked = settings.saveToHistory,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(saveToHistory = checked) }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_download_path_title),
                            summary = settings.downloadDisplay ?: settings.downloadPath,
                            onClick = onPickDirectory,
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
                            onClick = onOpenPortDialog,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_pref_https_title),
                            summary = stringResource(R.string.settings_pref_https_summary),
                            checked = settings.useHttps,
                            onCheckedChange = { checked ->
                                manager.applyUseHttpsChange(checked)
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_pin_title),
                            summary = if (settings.pin.isNullOrEmpty()) stringResource(R.string.settings_pref_pin_disabled) else stringResource(R.string.settings_pref_pin_enabled),
                            onClick = { showPinDialog = true },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_cert_fp_title),
                            summary = if (settings.useHttps) stringResource(R.string.settings_pref_cert_fp_enabled) else stringResource(R.string.settings_pref_cert_fp_disabled),
                            enabled = settings.useHttps,
                            onClick = { if (settings.useHttps) showCertDialog = true },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SmallTitle(text = stringResource(R.string.settings_section_advanced_network))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_network_whitelist_title),
                            summary = settings.networkWhitelist.ifEmpty { "—" },
                            onClick = { editingAdvancedField = AdvancedNetworkField.WHITELIST },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_network_blacklist_title),
                            summary = settings.networkBlacklist.ifEmpty { "—" },
                            onClick = { editingAdvancedField = AdvancedNetworkField.BLACKLIST },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_multicast_group_title),
                            summary = settings.multicastGroup,
                            onClick = { editingAdvancedField = AdvancedNetworkField.MULTICAST_GROUP },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_discovery_timeout_title),
                            summary = settings.discoveryTimeout.toString(),
                            onClick = { editingAdvancedField = AdvancedNetworkField.DISCOVERY_TIMEOUT },
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
                            onClick = onNavigateToUpdate,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_github_title),
                            summary = ExternalLinks.GITHUB_REPO,
                            onClick = {
                                UpdateManager(context).openInBrowser(context, ExternalLinks.GITHUB_REPO)
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_pref_license_title),
                            summary = stringResource(R.string.settings_pref_license_summary),
                            onClick = {
                                UpdateManager(context).openInBrowser(context, ExternalLinks.APACHE_LICENSE)
                            },
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
        },
    )

    CertFingerprintDialog(
        show = showCertDialog,
        fingerprint = if (settings.useHttps) manager.getLocalDevice().fingerprint else "",
        onDismissRequest = { showCertDialog = false },
        // 先收起证书弹窗再弹二次确认，避免两个弹窗叠层同时为 true
        onRegenerate = {
            showCertDialog = false
            showRegenerateConfirm = true
        },
    )

    // 重新生成证书会让所有已记录该指纹的设备下次校验失败，属于破坏性操作，需二次确认
    if (showRegenerateConfirm) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.dialog_cert_regen_confirm_title),
            onDismissRequest = { showRegenerateConfirm = false },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.dialog_cert_regen_confirm_msg),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(20.dp))
                DialogButtonRow(
                    secondaryText = stringResource(R.string.btn_cancel),
                    onSecondary = { showRegenerateConfirm = false },
                    primaryText = stringResource(R.string.btn_confirm),
                    onPrimary = {
                        showRegenerateConfirm = false
                        showCertDialog = false
                        manager.regenerateCertificate()
                    },
                    primaryColors =
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError,
                        ),
                )
            }
        }
    }

    when (editingAdvancedField) {
        AdvancedNetworkField.WHITELIST ->
            AdvancedNetworkEditDialog(
                show = true,
                title = stringResource(R.string.settings_pref_network_whitelist_title),
                initialValue = settings.networkWhitelist,
                keyboardType = KeyboardType.Ascii,
                invalidToastRes = null,
                validate = { true },
                onDismissRequest = { editingAdvancedField = null },
                onSave = { value ->
                    manager.updateSettings { it.copy(networkWhitelist = value) }
                },
            )
        AdvancedNetworkField.BLACKLIST ->
            AdvancedNetworkEditDialog(
                show = true,
                title = stringResource(R.string.settings_pref_network_blacklist_title),
                initialValue = settings.networkBlacklist,
                keyboardType = KeyboardType.Ascii,
                invalidToastRes = null,
                validate = { true },
                onDismissRequest = { editingAdvancedField = null },
                onSave = { value ->
                    manager.updateSettings { it.copy(networkBlacklist = value) }
                },
            )
        AdvancedNetworkField.MULTICAST_GROUP ->
            AdvancedNetworkEditDialog(
                show = true,
                title = stringResource(R.string.settings_pref_multicast_group_title),
                initialValue = settings.multicastGroup,
                keyboardType = KeyboardType.Ascii,
                invalidToastRes = R.string.toast_invalid_multicast_group,
                validate = { SettingsValidators.isValidMulticastGroup(it) },
                onDismissRequest = { editingAdvancedField = null },
                onSave = { value ->
                    manager.updateSettings { it.copy(multicastGroup = value) }
                },
            )
        AdvancedNetworkField.DISCOVERY_TIMEOUT ->
            AdvancedNetworkEditDialog(
                show = true,
                title = stringResource(R.string.settings_pref_discovery_timeout_title),
                initialValue = settings.discoveryTimeout.toString(),
                keyboardType = KeyboardType.Number,
                invalidToastRes = R.string.toast_invalid_discovery_timeout,
                validate = { SettingsValidators.parseDiscoveryTimeout(it) != null },
                onDismissRequest = { editingAdvancedField = null },
                onSave = { value ->
                    val timeout = SettingsValidators.parseDiscoveryTimeout(value)
                    if (timeout != null) {
                        manager.updateSettings { it.copy(discoveryTimeout = timeout) }
                    }
                },
            )
        null -> {}
    }
}

/** 高级网络选项的文本编辑弹窗，复用 PortDialog 的 WindowDialog+TextField+双按钮模式。 */
@Composable
private fun AdvancedNetworkEditDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    keyboardType: KeyboardType,
    @StringRes invalidToastRes: Int?,
    validate: (String) -> Boolean,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }

    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { newValue -> if (newValue.length <= 200) value = newValue },
                label = title,
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        if (validate(value)) {
                            onSave(value)
                            onDismissRequest()
                        } else if (invalidToastRes != null) {
                            Toast.makeText(context, invalidToastRes, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        }
    }
}
