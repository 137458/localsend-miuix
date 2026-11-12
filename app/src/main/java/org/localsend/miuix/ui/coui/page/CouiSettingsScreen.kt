package org.localsend.miuix.ui.coui.page

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.layout.Group
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.R
import org.localsend.miuix.core.ExternalLinks
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.ui.MainActivity
import org.localsend.miuix.ui.component.CertFingerprintDialog
import org.localsend.miuix.ui.component.ConfirmDialog
import org.localsend.miuix.ui.component.PinDialog
import org.localsend.miuix.ui.component.TextInputDialog
import org.localsend.miuix.ui.coui.component.CouiArrowRow
import org.localsend.miuix.ui.coui.component.CouiChoiceRow
import org.localsend.miuix.ui.coui.component.CouiSwitchRow
import org.localsend.miuix.util.AppLanguage
import org.localsend.miuix.util.SettingsValidators

/** 高级网络选项中正在编辑的字段，null 表示无编辑弹窗。 */
private enum class AdvancedNetworkField {
    WHITELIST,
    BLACKLIST,
    MULTICAST_GROUP,
    DISCOVERY_TIMEOUT,
}

/**
 * ColorOS 皮肤的设置页。
 *
 * 与 Miuix 版逐项对应：同样的设置项、同样的写回路径、同样的校验规则。差异只在呈现层，
 * 卡片、行、开关与枚举选择换成 coUI-Kit 组件。全局弹窗（重命名、端口）仍由 App.kt 的
 * Miuix 宿主承担，本轮不搬。
 *
 * 顶栏仍是固定高度的标题带，滚动内容独立：本页挂在 App.kt 的 Miuix 宿主里，不在 coUI
 * Scaffold 的作用域内。coUI 的 Scaffold 自己建采样源（rememberCouiBackdrop）并用
 * ProvideCouiBackdrop 包住 topBar 槽，TopAppBar 缺省从 LocalCouiBackdrop 取值，
 * 所以「顶栏必须由宿主提供 CouiBackdrop」并不成立；骨架换成 Scaffold + TopAppBar 在下一轮。
 */
@Composable
fun CouiSettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val scheme = CouiTheme.colorScheme

    var showPinDialog by remember { mutableStateOf(false) }
    var showCertDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var editingAdvancedField by remember { mutableStateOf<AdvancedNetworkField?>(null) }

    val themeOptions =
        listOf(
            stringResource(R.string.theme_system),
            stringResource(R.string.theme_light),
            stringResource(R.string.theme_dark),
            stringResource(R.string.theme_monet_system),
            stringResource(R.string.theme_monet_light),
            stringResource(R.string.theme_monet_dark),
        )

    val uiSkinOptions =
        listOf(
            stringResource(R.string.ui_skin_hyperos),
            stringResource(R.string.ui_skin_coloros),
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text = stringResource(R.string.settings_title), kind = CouiTextKind.TopBar)
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .couiVerticalScroll(rememberScrollState())
                        .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                Group(title = stringResource(R.string.settings_section_general)) {
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_alias_title),
                        summary = settings.alias,
                        onClick = onOpenRenameDialog,
                    )
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_language_title),
                        value = languageOptions.getOrElse(selectedLanguageIndex(languageSettings, settings.language)) { languageOptions[0] },
                        options = languageSettings.mapIndexed { index, language -> language to languageOptions[index] },
                        current = settings.language,
                        onSelect = { language ->
                            manager.updateSettings { it.copy(language = language) }
                            // 语言偏好已持久化，重建后 attachBaseContext 会重新读取并包装 Context
                            (context as? MainActivity)?.recreate()
                        },
                    )
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_device_type_title),
                        value = deviceTypeOptions.getOrElse(currentDeviceTypeIndex) { deviceTypeOptions[0] },
                        options = supportedDeviceTypes.mapIndexed { index, type -> type to deviceTypeOptions[index] },
                        current = settings.deviceType,
                        onSelect = { type -> manager.updateSettings { it.copy(deviceType = type) } },
                    )
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_theme_title),
                        value = themeOptions.getOrElse(settings.themeModeIndex) { themeOptions[0] },
                        options = themeOptions.mapIndexed { index, label -> index to label },
                        current = settings.themeModeIndex,
                        onSelect = { index -> manager.updateSettings { it.copy(themeModeIndex = index) } },
                    )
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_ui_skin_title),
                        value = uiSkinOptions.getOrElse(settings.uiSkinIndex) { uiSkinOptions[0] },
                        options = uiSkinOptions.mapIndexed { index, label -> index to label },
                        current = settings.uiSkinIndex,
                        onSelect = { index -> manager.updateSettings { it.copy(uiSkinIndex = index) } },
                    )
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_vibrate_title),
                        checked = settings.vibrateOnComplete,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(vibrateOnComplete = checked) }
                        },
                    )
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_auto_finish_title),
                        checked = settings.autoFinish,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoFinish = checked) }
                        },
                    )
                }

                Group(title = stringResource(R.string.settings_section_receive)) {
                    CouiSwitchRow(
                        title = stringResource(R.string.receive_pref_quick_save_title),
                        summary = stringResource(R.string.settings_pref_quick_save_desc),
                        checked = settings.quickSave,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(quickSave = checked) }
                        },
                    )
                    if (settings.quickSave) {
                        CouiSwitchRow(
                            title = stringResource(R.string.receive_pref_quick_save_from_favorites_title),
                            checked = settings.quickSaveFromFavorites,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(quickSaveFromFavorites = checked) }
                            },
                        )
                    }
                    CouiSwitchRow(
                        title = stringResource(R.string.receive_pref_auto_copy_title),
                        summary = stringResource(R.string.settings_pref_auto_copy_desc),
                        checked = settings.autoCopyText,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCopyText = checked) }
                        },
                    )
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_save_text_as_file_title),
                        summary = stringResource(R.string.settings_pref_save_text_as_file_desc),
                        checked = settings.saveTextAsFile,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(saveTextAsFile = checked) }
                        },
                    )
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_auto_categorize_title),
                        summary = stringResource(R.string.settings_pref_auto_categorize_desc),
                        checked = settings.autoCategorizeMedia,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCategorizeMedia = checked) }
                        },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_download_path_title),
                        summary = settings.downloadDisplay ?: settings.downloadPath,
                        onClick = onPickDirectory,
                    )
                }

                Group(title = stringResource(R.string.settings_section_network_security)) {
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_port_title),
                        summary = settings.port.toString(),
                        onClick = onOpenPortDialog,
                    )
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_https_title),
                        summary = stringResource(R.string.settings_pref_https_summary),
                        checked = settings.useHttps,
                        onCheckedChange = { checked -> manager.applyUseHttpsChange(checked) },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_pin_title),
                        summary =
                            if (settings.pin.isNullOrEmpty()) {
                                stringResource(R.string.settings_pref_pin_disabled)
                            } else {
                                stringResource(R.string.settings_pref_pin_enabled)
                            },
                        onClick = { showPinDialog = true },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_cert_fp_title),
                        summary =
                            if (settings.useHttps) {
                                stringResource(R.string.settings_pref_cert_fp_enabled)
                            } else {
                                stringResource(R.string.settings_pref_cert_fp_disabled)
                            },
                        enabled = settings.useHttps,
                        onClick = { if (settings.useHttps) showCertDialog = true },
                    )
                }

                Group(title = stringResource(R.string.settings_section_advanced_network)) {
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_network_whitelist_title),
                        summary = settings.networkWhitelist.ifEmpty { "—" },
                        onClick = { editingAdvancedField = AdvancedNetworkField.WHITELIST },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_network_blacklist_title),
                        summary = settings.networkBlacklist.ifEmpty { "—" },
                        onClick = { editingAdvancedField = AdvancedNetworkField.BLACKLIST },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_multicast_group_title),
                        summary = settings.multicastGroup,
                        onClick = { editingAdvancedField = AdvancedNetworkField.MULTICAST_GROUP },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_discovery_timeout_title),
                        summary = settings.discoveryTimeout.toString(),
                        onClick = { editingAdvancedField = AdvancedNetworkField.DISCOVERY_TIMEOUT },
                    )
                }

                Group(title = stringResource(R.string.settings_section_about)) {
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_check_update_title),
                        summary = stringResource(R.string.settings_pref_check_update_summary, BuildConfig.VERSION_NAME),
                        onClick = onNavigateToUpdate,
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_github_title),
                        summary = ExternalLinks.GITHUB_REPO,
                        onClick = { UpdateManager.openInBrowser(context, ExternalLinks.GITHUB_REPO) },
                    )
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_license_title),
                        summary = stringResource(R.string.settings_pref_license_summary),
                        onClick = { UpdateManager.openInBrowser(context, ExternalLinks.APACHE_LICENSE) },
                    )
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
        ConfirmDialog(
            show = true,
            title = stringResource(R.string.dialog_cert_regen_confirm_title),
            message = stringResource(R.string.dialog_cert_regen_confirm_msg),
            confirmText = stringResource(R.string.btn_confirm),
            onConfirm = {
                showRegenerateConfirm = false
                showCertDialog = false
                manager.regenerateCertificate()
            },
            onDismissRequest = { showRegenerateConfirm = false },
            dismissText = stringResource(R.string.btn_cancel),
            danger = true,
        )
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

/** 语言枚举在选项表中的下标，找不到时回落到系统语言。 */
private fun selectedLanguageIndex(
    settings: List<String>,
    language: String,
): Int = settings.indexOf(language).coerceAtLeast(0)

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

    TextInputDialog(
        show = show,
        title = title,
        value = value,
        onValueChange = { value = it },
        label = title,
        keyboardType = keyboardType,
        maxLength = 200,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            if (validate(value)) {
                onSave(value)
                true
            } else {
                if (invalidToastRes != null) {
                    Toast.makeText(context, invalidToastRes, Toast.LENGTH_SHORT).show()
                }
                false
            }
        },
    )
}
