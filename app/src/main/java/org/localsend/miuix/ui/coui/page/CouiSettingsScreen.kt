package org.localsend.miuix.ui.coui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Scaffold
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.R
import org.localsend.miuix.core.ExternalLinks
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.manager.UpdateManager
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.ui.MainActivity
import org.localsend.miuix.ui.coui.component.CouiArrowRow
import org.localsend.miuix.ui.coui.component.CouiChoiceRow
import org.localsend.miuix.ui.coui.component.CouiIncomingBadgeAction
import org.localsend.miuix.ui.coui.component.CouiSwitchRow
import org.localsend.miuix.ui.coui.component.CouiTopBarTitle
import org.localsend.miuix.ui.coui.dialog.CouiAlertDialog
import org.localsend.miuix.ui.coui.dialog.CouiCertFingerprintDialog
import org.localsend.miuix.ui.coui.dialog.CouiInputDialog
import org.localsend.miuix.ui.coui.dialog.CouiPinDialog
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
 * Miuix 宿主承担；本页只接管 PIN、证书指纹、重新生成确认与高级网络字段。
 *
 * 骨架用库 layout.Scaffold + topappbar.TopAppBar：顶栏玻璃取 Scaffold 自建的采样源
 * （Scaffold.kt:61 rememberCouiBackdrop，:86 ProvideCouiBackdrop 包住 topBar 槽），
 * 纵向状态栏 inset 由 TopAppBar 的缺省 windowInsets 消费，栏高经 contentPadding 交给正文；
 * 横向安全区由 Scaffold 容器消费（Scaffold.kt:79），页面自己不再加 statusBarsPadding。
 *
 * 正文是 Column + couiVerticalScroll（不是 LazyColumn），顶栏高度放进滚动节点内侧：
 * 首屏内容排在栏下，滚动时内容从顶栏下穿过、被顶栏玻璃采样。
 */
@Composable
fun CouiSettingsScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenRenameDialog: () -> Unit,
    onOpenPortDialog: () -> Unit,
    onPickDirectory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    incomingTaskCount: Int = 0,
    onOpenHome: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()

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

    // 外层 Box 只负责给 Scaffold 有界约束：库在无界约束下直接 require 失败（Scaffold.kt:81）。
    Box(modifier = Modifier.fillMaxSize()) {
        // 顶栏玻璃与采样源由库 Scaffold 自建；外围 insets 清零以防和外层 Miuix Scaffold 重复消费。
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            windowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        CouiTopBarTitle(
                            text = stringResource(R.string.settings_title),
                            color = CouiTheme.colorScheme.onSurface,
                        )
                    },
                    actions = {
                        CouiIncomingBadgeAction(
                            incomingTaskCount = incomingTaskCount,
                            onClick = onOpenHome,
                        )
                    },
                )
            },
        ) { scaffoldPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // padding 写在 couiVerticalScroll 之后：它作用在滚动内容上而不是视口，
                        // 所以视口从页面顶端开始、首项排在栏下，滚动时内容从顶栏下穿过被玻璃采样。
                        .couiVerticalScroll(rememberScrollState())
                        .padding(
                            top = scaffoldPadding.calculateTopPadding(),
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        ),
            ) {
                Group(title = stringResource(R.string.settings_section_general)) {
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_alias_title),
                        summary = settings.alias,
                        onClick = onOpenRenameDialog,
                    )
                    Divider()
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
                    Divider()
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_device_type_title),
                        value = deviceTypeOptions.getOrElse(currentDeviceTypeIndex) { deviceTypeOptions[0] },
                        options = supportedDeviceTypes.mapIndexed { index, type -> type to deviceTypeOptions[index] },
                        current = settings.deviceType,
                        onSelect = { type -> manager.updateSettings { it.copy(deviceType = type) } },
                    )
                    Divider()
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_theme_title),
                        value = themeOptions.getOrElse(settings.themeModeIndex) { themeOptions[0] },
                        options = themeOptions.mapIndexed { index, label -> index to label },
                        current = settings.themeModeIndex,
                        onSelect = { index -> manager.updateSettings { it.copy(themeModeIndex = index) } },
                    )
                    Divider()
                    CouiChoiceRow(
                        title = stringResource(R.string.settings_pref_ui_skin_title),
                        value = uiSkinOptions.getOrElse(settings.uiSkinIndex) { uiSkinOptions[0] },
                        options = uiSkinOptions.mapIndexed { index, label -> index to label },
                        current = settings.uiSkinIndex,
                        onSelect = { index -> manager.updateSettings { it.copy(uiSkinIndex = index) } },
                    )
                    Divider()
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_vibrate_title),
                        checked = settings.vibrateOnComplete,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(vibrateOnComplete = checked) }
                        },
                    )
                    Divider()
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
                    Divider()
                    if (settings.quickSave) {
                        CouiSwitchRow(
                            title = stringResource(R.string.receive_pref_quick_save_from_favorites_title),
                            checked = settings.quickSaveFromFavorites,
                            onCheckedChange = { checked ->
                                manager.updateSettings { it.copy(quickSaveFromFavorites = checked) }
                            },
                        )
                        Divider()
                    }
                    CouiSwitchRow(
                        title = stringResource(R.string.receive_pref_auto_copy_title),
                        summary = stringResource(R.string.settings_pref_auto_copy_desc),
                        checked = settings.autoCopyText,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCopyText = checked) }
                        },
                    )
                    Divider()
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_save_text_as_file_title),
                        summary = stringResource(R.string.settings_pref_save_text_as_file_desc),
                        checked = settings.saveTextAsFile,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(saveTextAsFile = checked) }
                        },
                    )
                    Divider()
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_auto_categorize_title),
                        summary = stringResource(R.string.settings_pref_auto_categorize_desc),
                        checked = settings.autoCategorizeMedia,
                        onCheckedChange = { checked ->
                            manager.updateSettings { it.copy(autoCategorizeMedia = checked) }
                        },
                    )
                    Divider()
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
                    Divider()
                    CouiSwitchRow(
                        title = stringResource(R.string.settings_pref_https_title),
                        summary = stringResource(R.string.settings_pref_https_summary),
                        checked = settings.useHttps,
                        onCheckedChange = { checked -> manager.applyUseHttpsChange(checked) },
                    )
                    Divider()
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
                    Divider()
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
                    Divider()
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_network_blacklist_title),
                        summary = settings.networkBlacklist.ifEmpty { "—" },
                        onClick = { editingAdvancedField = AdvancedNetworkField.BLACKLIST },
                    )
                    Divider()
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_multicast_group_title),
                        summary = settings.multicastGroup,
                        onClick = { editingAdvancedField = AdvancedNetworkField.MULTICAST_GROUP },
                    )
                    Divider()
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
                    Divider()
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_github_title),
                        summary = ExternalLinks.GITHUB_REPO,
                        onClick = { UpdateManager.openInBrowser(context, ExternalLinks.GITHUB_REPO) },
                    )
                    Divider()
                    CouiArrowRow(
                        title = stringResource(R.string.settings_pref_license_title),
                        summary = stringResource(R.string.settings_pref_license_summary),
                        onClick = { UpdateManager.openInBrowser(context, ExternalLinks.APACHE_LICENSE) },
                    )
                }
            }
        }
    }

    CouiPinDialog(
        show = showPinDialog,
        initialPin = settings.pin,
        onDismissRequest = { showPinDialog = false },
        onConfirm = { newPin ->
            manager.updateSettings { it.copy(pin = newPin) }
        },
    )

    val certFingerprint = if (settings.useHttps) manager.getLocalDevice().fingerprint else ""
    CouiCertFingerprintDialog(
        show = showCertDialog,
        fingerprint = certFingerprint,
        onCopyFingerprint = { manager.copyTextToClipboard(certFingerprint) },
        onDismissRequest = { showCertDialog = false },
        // 先收起证书弹窗再弹二次确认，避免两个弹窗叠层同时为 true
        onRegenerate = {
            showCertDialog = false
            showRegenerateConfirm = true
        },
    )

    // 重新生成证书会让所有已记录该指纹的设备下次校验失败，属于破坏性操作，需二次确认。
    // 调用不套 if：库靠 visible 保留退场窗口（Dialog.kt:48-49），拆掉组合会丢退场动画。
    CouiAlertDialog(
        show = showRegenerateConfirm,
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

    // 弹窗同样常驻组合，只把 show 交给库的 visible；为 null 时不显示，字段值不参与渲染。
    val editingField = editingAdvancedField
    val editingTitle =
        when (editingField) {
            AdvancedNetworkField.WHITELIST -> stringResource(R.string.settings_pref_network_whitelist_title)
            AdvancedNetworkField.BLACKLIST -> stringResource(R.string.settings_pref_network_blacklist_title)
            AdvancedNetworkField.MULTICAST_GROUP -> stringResource(R.string.settings_pref_multicast_group_title)
            AdvancedNetworkField.DISCOVERY_TIMEOUT -> stringResource(R.string.settings_pref_discovery_timeout_title)
            null -> ""
        }
    val editingInitialValue =
        when (editingField) {
            AdvancedNetworkField.WHITELIST -> settings.networkWhitelist
            AdvancedNetworkField.BLACKLIST -> settings.networkBlacklist
            AdvancedNetworkField.MULTICAST_GROUP -> settings.multicastGroup
            AdvancedNetworkField.DISCOVERY_TIMEOUT -> settings.discoveryTimeout.toString()
            null -> ""
        }
    // 键值表没有格式约束，留空即清空；组播地址与超时沿用原校验器。
    val editingValidate: (String) -> Boolean =
        when (editingField) {
            AdvancedNetworkField.MULTICAST_GROUP -> { value -> SettingsValidators.isValidMulticastGroup(value) }
            AdvancedNetworkField.DISCOVERY_TIMEOUT -> { value -> SettingsValidators.parseDiscoveryTimeout(value) != null }
            else -> { _ -> true }
        }

    AdvancedNetworkEditDialog(
        show = editingField != null,
        title = editingTitle,
        initialValue = editingInitialValue,
        validate = editingValidate,
        onDismissRequest = { editingAdvancedField = null },
        onSave = { value ->
            when (editingField) {
                AdvancedNetworkField.WHITELIST -> manager.updateSettings { it.copy(networkWhitelist = value) }
                AdvancedNetworkField.BLACKLIST -> manager.updateSettings { it.copy(networkBlacklist = value) }
                AdvancedNetworkField.MULTICAST_GROUP -> manager.updateSettings { it.copy(multicastGroup = value) }
                AdvancedNetworkField.DISCOVERY_TIMEOUT -> {
                    val timeout = SettingsValidators.parseDiscoveryTimeout(value)
                    if (timeout != null) {
                        manager.updateSettings { it.copy(discoveryTimeout = timeout) }
                    }
                }
                null -> {}
            }
        },
    )
}

/** 语言枚举在选项表中的下标，找不到时回落到系统语言。 */
private fun selectedLanguageIndex(
    settings: List<String>,
    language: String,
): Int = settings.indexOf(language).coerceAtLeast(0)

/**
 * 高级网络选项的文本编辑弹窗。
 *
 * 校验不通过或超过 200 字符上限时通过字数计数器变红并禁用确认按钮反馈给用户。
 */
@Composable
private fun AdvancedNetworkEditDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    validate: (String) -> Boolean,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(show, initialValue) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    val overLimit = value.text.length > 200

    CouiInputDialog(
        show = show,
        title = title,
        value = value,
        onValueChange = { value = it },
        placeholder = title,
        confirmText = stringResource(R.string.btn_confirm),
        clearContentDescription = stringResource(R.string.btn_clear),
        dismissText = stringResource(R.string.btn_cancel),
        confirmEnabled = !overLimit && validate(value.text),
        counter = "${value.text.length}/200",
        counterIsError = overLimit,
        onConfirm = {
            onSave(value.text)
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
    )
}
