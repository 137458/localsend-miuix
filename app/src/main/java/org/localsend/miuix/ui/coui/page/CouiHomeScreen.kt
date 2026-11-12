@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.coui.page

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import kotlinx.coroutines.launch
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.model.countCategories
import org.localsend.miuix.network.NetworkUtils
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.FilePreviewDialog
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.InlineTransferProgress
import org.localsend.miuix.ui.coui.component.CouiIconAction
import org.localsend.miuix.ui.coui.component.CouiIncomingBadgeAction
import org.localsend.miuix.ui.coui.component.CouiRowSummary
import org.localsend.miuix.ui.coui.component.CouiRowTitle
import org.localsend.miuix.ui.coui.component.couiMinTouchTarget
import org.localsend.miuix.ui.coui.theme.couiDangerColor
import org.localsend.miuix.ui.screen.HomeActions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image

/**
 * 首页（ColorOS 皮肤）：本机状态、正在接收的传输、待发送队列、快速选择与附近设备。
 *
 * 页面骨架走库 `layout.Scaffold` + `topappbar.LargeTopAppBar`：横向安全区由外层宿主的 Miuix Scaffold
 * 与列表内容留白承担，内层显式关闭重复系统栏消费并保留顶栏高度经 `state.contentPadding` 注入；大标题
 * 随列表滚动收成紧凑标题，折叠位移与栏高经 `state.contentPadding` 注入列表内侧，滚动内容从栏下
 * 穿过时由库的渐变遮罩与渐进模糊收边；行、分隔线与行内按压/点击反馈走库 `layout.Item`、
 * `layout.Divider` 与 `couiButtonFeedback`。
 *
 * 顶栏的动作按钮使用共享的 `CouiIconAction`：库的 `TopAppBarButton`/`TopAppBarButtonGroup`
 * 标注 @RequiresApi(34)，本模块 minSdk 31 且不使用 Android 14 专属 API，故没有可替换的库按钮。
 * 卡片外壳、图标按钮与进度仍按 coUI 的材质自绘（coUI-Kit 没有 Card/Icon/IconButton 与进度条，
 * 只有材质与 layout 原语）；缩略图、进度与预览弹窗继续复用既有组件，它们不参与皮肤外观，
 * 重复实现会扩散维护面。
 */
@Composable
fun CouiHomeScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    actions: HomeActions,
    incomingTaskCount: Int = 0,
    onOpenIncoming: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by manager.settings.collectAsStateWithLifecycle()
    val selectedFiles by manager.selectedFiles.collectAsStateWithLifecycle()
    val nearbyDevices by manager.nearbyDevices.collectAsStateWithLifecycle()
    val favoriteDevices by manager.favoriteDevices.collectAsStateWithLifecycle()
    val targetResendDevice by manager.targetResendDevice.collectAsStateWithLifecycle()
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    val activeSessions by manager.activeSessions.collectAsStateWithLifecycle()
    val shares by manager.shares.collectAsStateWithLifecycle()

    // 收藏设备优先用扫描到的实时对象（其地址与端口才是当前可达的），扫描不到才回落持久化记录
    val favoriteDeviceList =
        remember(favoriteDevices, nearbyDevices, targetResendDevice) {
            val list =
                favoriteDevices.map { fav ->
                    nearbyDevices.firstOrNull { nearby -> fav.matches(nearby) } ?: fav.toDevice()
                }
            val resend = targetResendDevice
            if (resend != null) list.filterNot { it.matches(resend) } else list
        }
    val nonFavoriteNearbyDevices =
        remember(favoriteDevices, nearbyDevices, targetResendDevice) {
            val list =
                nearbyDevices.filterNot { nearby ->
                    favoriteDevices.any { fav -> fav.matches(nearby) }
                }
            val resend = targetResendDevice
            if (resend != null) list.filterNot { it.matches(resend) } else list
        }
    val incomingSessions = remember(activeSessions) { activeSessions.filter { it.isIncoming } }
    val outgoingSessions = remember(activeSessions) { activeSessions.filter { !it.isIncoming } }
    val nonNearbySessions =
        remember(outgoingSessions, nearbyDevices, favoriteDeviceList) {
            val allKnown = favoriteDeviceList + nearbyDevices
            outgoingSessions.filter { session ->
                allKnown.none { it.matches(session.device) }
            }
        }

    val localIps = remember { NetworkUtils.getLocalIpAddresses() }
    val primaryIp = localIps.firstOrNull() ?: "127.0.0.1"
    val boundPort by manager.serverPort.collectAsStateWithLifecycle()
    val displayPort = if (boundPort > 0) boundPort else settings.port

    val totalSelectedSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val categoryCounts = remember(selectedFiles) { selectedFiles.countCategories() }
    val imagesLabel = stringResource(R.string.send_cat_images_count, categoryCounts.images)
    val videosLabel = stringResource(R.string.send_cat_videos_count, categoryCounts.videos)
    val audiosLabel = stringResource(R.string.send_cat_audios_count, categoryCounts.audios)
    val textsLabel = stringResource(R.string.send_cat_texts_count, categoryCounts.texts)
    val apksLabel = stringResource(R.string.send_cat_apks_count, categoryCounts.apks)
    val othersLabel = stringResource(R.string.send_cat_others_count, categoryCounts.others)
    val categoryBreakdown =
        remember(categoryCounts, imagesLabel, videosLabel, audiosLabel, textsLabel, apksLabel, othersLabel) {
            buildList {
                if (categoryCounts.images > 0) add(imagesLabel)
                if (categoryCounts.videos > 0) add(videosLabel)
                if (categoryCounts.audios > 0) add(audiosLabel)
                if (categoryCounts.texts > 0) add(textsLabel)
                if (categoryCounts.apks > 0) add(apksLabel)
                if (categoryCounts.others > 0) add(othersLabel)
            }
        }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var previewingIndex by remember { mutableIntStateOf(-1) }
    var isFileListExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) return@LaunchedEffect
        manager.refreshDevicesAwait()
        isRefreshing = false
    }

    val sendTo: (Device) -> Unit = { device ->
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_empty_selection_warn), Toast.LENGTH_SHORT).show()
        } else {
            manager.sendFilesTo(device)
            Toast.makeText(context, context.getString(R.string.toast_initiating_transfer, device.alias), Toast.LENGTH_SHORT).show()
        }
    }

    val maybeOnRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            refreshRequest++
        }
    }

    // 大标题折叠：canScrollBackward 决定正文到顶后才展开标题，回弹期间不提前弹开。
    // derivedStateOf 把滚动状态读取收进派生快照，避免每帧重组整页。
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val canScrollBackward = remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val scrollBehavior =
        CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = { canScrollBackward.value })
    val density = LocalDensity.current

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CouiTheme.colorScheme.background)
                // 折叠连接只挂到滚动内容的祖先一次；LazyColumn 上不再叠 nestedScroll。
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 外层 Miuix Scaffold 已包一层主导航窗口容器；此处显式清零外围 insets，避免嵌套重复消费，
        // 顶部状态栏由 LargeTopAppBar 的默认 insets 单独处理并经 collapsedPadding 注入列表。
        windowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 大标题折叠、标题排版与栏下渐变模糊由库 LargeTopAppBar 承担；动作按钮使用共享的
            // CouiIconAction，因为库 TopAppBarButton 标注 @RequiresApi(34)，minSdk 31 上没有可用实现。
            LargeTopAppBar(
                title = {
                    // 库 Text 的 kind 必填，且其 BasicText 直接吃 kind 样式、不读组件注入的
                    // LocalCouiTextStyle，所以槽内不能用无 kind 的文本。HomeTitle 就是库
                    // expandedTitleTextStyle 的默认来源（40sp），折叠缩放 24/40 才落在库的
                    // titleOnlyCollapsedSize 上，几何与库默认一致。
                    Text(
                        text = stringResource(R.string.app_name),
                        kind = CouiTextKind.HomeTitle,
                        color = CouiTheme.colorScheme.onSurface,
                        // 库 Text 非列表项 kind 默认居中，大标题须与库的起点动画对齐，故显式起始对齐；
                        // 单行省略保证收起后标题高度不随文案换行跳动。
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    CouiIncomingBadgeAction(
                        incomingTaskCount = incomingTaskCount,
                        onClick = {
                            onOpenIncoming()
                            coroutineScope.launch {
                                listState.animateScrollToItem(if (incomingSessions.isNotEmpty()) 1 else 0)
                            }
                        },
                    )
                    CouiIconAction(
                        icon = AppIcons.Edit,
                        contentDescription = stringResource(R.string.action_input_ip),
                        tint = if (isRefreshing) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.onSurface,
                        onClick = actions.onManualIp,
                    )
                    CouiIconAction(
                        icon = AppIcons.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = if (isRefreshing) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.onSurface,
                        onClick = {
                            maybeOnRefresh()
                            manager.refreshDevices()
                            Toast.makeText(context, context.getString(R.string.toast_multicast_sent), Toast.LENGTH_SHORT).show()
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        // 折叠位移由库 state.contentPadding 注入：它消费 Scaffold 原始 padding 并叠加顶栏收起的位移，
        // 只平移正文顶部留白，不移动滚动视口（库 KDoc 明确不可再整体平移视口）。
        val collapsedPadding = scrollBehavior.state.contentPadding(scaffoldPadding, density)
        // 回弹效果按滚动容器独占：在 item 作用域外记住，列表重组时不会反复重建
        val homeOverscroll = rememberCouiOverscrollEffect()
        // LazyColumn 的 overscrollEffect 实参由内部 scrollableArea 挂载回弹节点；
        // 再叠 Modifier.overscroll 会把同一个 EdgeNode 二次 attach。
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            overscrollEffect = homeOverscroll,
            // 顶栏高度由折叠状态的 contentPadding 注入：首项被推到栏下，滚动时内容从栏下穿过，
            // 栏底过渡由库 TopAppBarSurface 的渐变遮罩与渐进模糊负责。
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = collapsedPadding.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 本机状态：可被发现的地址 + 接收策略
            item {
                CouiHomeSection(title = stringResource(R.string.receive_section_local_device)) {
                    CouiHomeRow(
                        icon = AppIcons.localDeviceIcon(),
                        title = settings.alias,
                        onClick = actions.onOpenRenameDialog,
                    )
                    Divider()
                    CouiHomeRow(
                        title = "$primaryIp:$displayPort",
                        summary = if (localIps.size > 1) localIps.joinToString(", ") else null,
                        onClick = {
                            manager.copyTextToClipboard("$primaryIp:$displayPort")
                            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        },
                    )
                    Divider()
                    CouiLocalStatusRow(
                        receivingCount = incomingSessions.size,
                        quickSave = settings.quickSave,
                    )
                }
            }

            // 2. 正在接收的传输：无任务时不渲染标题与卡片
            if (incomingSessions.isNotEmpty()) {
                items(incomingSessions, key = { "in_" + it.sessionId }) { session ->
                    CouiHomeSection(title = stringResource(R.string.receive_section_incoming_count, incomingSessions.size)) {
                        CouiHomeRow(
                            icon = AppIcons.getDeviceIcon(session.device.deviceType),
                            title = session.device.alias,
                            summary = session.device.displaySummary,
                        )
                        InlineTransferProgress(
                            session = session,
                            onCancel = { manager.cancelTransfer(session.sessionId) },
                            onAccept = { manager.acceptAllIncomingTransfer(session.sessionId) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            // 3. 待发送队列：头部三按钮 + 文件行，超过三条折叠
            if (selectedFiles.isNotEmpty()) {
                item {
                    CouiHomeSection(title = stringResource(R.string.send_section_selected_files)) {
                        Column(
                            modifier =
                                Modifier
                                    .padding(14.dp)
                                    .animateContentSize(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val summaryHeader =
                                        stringResource(
                                            R.string.send_selected_summary_header,
                                            selectedFiles.size,
                                            FileItem.formatFileSize(totalSelectedSize),
                                        )
                                    Text(
                                        text = if (categoryBreakdown.isEmpty()) summaryHeader else summaryHeader + " · " + categoryBreakdown.joinToString(" · "),
                                        kind = CouiTextKind.ItemTitle,
                                        color = CouiTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CouiPillButton(text = stringResource(R.string.btn_preview)) { previewingIndex = 0 }
                                    CouiPillButton(text = stringResource(R.string.btn_add), onClick = actions.onOpenAddSheet)
                                    CouiPillButton(text = stringResource(R.string.btn_clear)) { manager.clearFiles() }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val filesToDisplay =
                                if (selectedFiles.size > 3 && !isFileListExpanded) {
                                    selectedFiles.take(3)
                                } else {
                                    selectedFiles
                                }
                            filesToDisplay.forEach { file ->
                                key(file.id) {
                                    val actualIndex = selectedFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                    CouiSelectedFileRow(
                                        file = file,
                                        onOpenPreview = { previewingIndex = actualIndex },
                                        onRemove = { manager.removeFile(file.id) },
                                    )
                                }
                            }

                            if (selectedFiles.size > 3) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val expandSource = remember { MutableInteractionSource() }
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(CouiShape(12.dp))
                                            .couiButtonFeedback(CouiTheme.isDark, expandSource)
                                            .clickable(expandSource, null, enabled = true, role = Role.Button) {
                                                isFileListExpanded = !isFileListExpanded
                                            }.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val expandTint =
                                        if (isFileListExpanded) CouiTheme.colorScheme.onSurfaceVariant else CouiTheme.colorScheme.accentOnSurface
                                    Text(
                                        text =
                                            if (isFileListExpanded) {
                                                stringResource(R.string.send_collapse_files)
                                            } else {
                                                stringResource(R.string.send_expand_files, selectedFiles.size - 3)
                                            },
                                        kind = CouiTextKind.ItemSummary,
                                        color = expandTint,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isFileListExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                                        contentDescription = null,
                                        tint = expandTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 快速选择内容：两行三列
            item {
                CouiHomeSection(title = stringResource(R.string.send_section_quick_pick)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_files),
                                icon = MiuixIcons.Folder,
                                onClick = actions.onPickFiles,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_folder),
                                icon = MiuixIcons.AddFolder,
                                onClick = actions.onPickFolder,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_media),
                                icon = MiuixIcons.Image,
                                onClick = actions.onPickMedia,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_apps),
                                icon = AppIcons.App,
                                onClick = actions.onPickApps,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_text),
                                icon = AppIcons.Text,
                                onClick = actions.onSendText,
                                modifier = Modifier.weight(1f),
                            )
                            CouiQuickAction(
                                title = stringResource(R.string.send_quick_action_clipboard),
                                icon = AppIcons.Paste,
                                onClick = actions.onPasteClipboard,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 5. Web Share 入口
            item {
                CouiHomeSection {
                    CouiHomeRow(
                        icon = AppIcons.Web,
                        title =
                            if (shares.isNotEmpty()) {
                                stringResource(R.string.send_web_share_running_title, shares.first().files.size)
                            } else {
                                stringResource(R.string.send_web_share_idle_title)
                            },
                        iconTint = if (shares.isNotEmpty()) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant,
                        onClick = actions.onOpenWebShare,
                    )
                }
            }

            // 6. 历史一键重发的目标设备
            if (targetResendDevice != null) {
                val resendDev = targetResendDevice!!
                item {
                    CouiHomeSection(title = stringResource(R.string.history_action_resend)) {
                        CouiHomeRow(
                            icon = AppIcons.getDeviceIcon(resendDev.deviceType),
                            title = resendDev.alias,
                            summary = resendDev.displaySummary,
                            trailing = {
                                CouiIconAction(
                                    icon = MiuixIcons.Close,
                                    contentDescription = stringResource(R.string.btn_cancel),
                                    tint = CouiTheme.colorScheme.onSurfaceVariant,
                                    onClick = { manager.clearTargetResendDevice() },
                                )
                            },
                            onClick = {
                                manager.clearTargetResendDevice()
                                sendTo(resendDev)
                            },
                        )
                    }
                }
            }

            // 7. 收藏设备
            if (favoriteDeviceList.isNotEmpty()) {
                items(favoriteDeviceList, key = { "fav_" + it.identityKey }) { device ->
                    CouiHomeSection(title = stringResource(R.string.send_favorites_title)) {
                        CouiDeviceCardContent(
                            device = device,
                            isFavorite = true,
                            sessions = outgoingSessions,
                            onToggleFavorite = { manager.toggleFavorite(device) },
                            onSend = { sendTo(device) },
                            onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                        )
                    }
                }
            }

            // 8. 附近设备
            val totalDeviceCount = nonFavoriteNearbyDevices.size + nonNearbySessions.size
            item {
                CouiSectionTitle(text = stringResource(R.string.send_section_nearby_devices, totalDeviceCount))
            }

            if (isScanning) {
                item {
                    CouiHomeSection {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.send_scanning_subnet_hint),
                                kind = CouiTextKind.ItemSummary,
                                color = CouiTheme.colorScheme.accentOnSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }

            if (nonFavoriteNearbyDevices.isEmpty() && nonNearbySessions.isEmpty() && !isScanning) {
                item {
                    CouiHomeSection {
                        CouiEmptyState(
                            icon = AppIcons.SearchDevice,
                            title = stringResource(R.string.send_searching_devices_title),
                        )
                    }
                }
            } else {
                items(nonFavoriteNearbyDevices, key = { it.identityKey }) { device ->
                    CouiHomeSection {
                        CouiDeviceCardContent(
                            device = device,
                            isFavorite = false,
                            sessions = outgoingSessions,
                            onToggleFavorite = { manager.toggleFavorite(device) },
                            onSend = { sendTo(device) },
                            onCancelTransfer = { sessionId -> manager.cancelTransfer(sessionId) },
                        )
                    }
                }
                // 手动输入 IP 起的目标不在扫描列表里，同样在本卡内展示进度
                if (nonNearbySessions.isNotEmpty()) {
                    items(nonNearbySessions, key = { "manual_" + it.sessionId }) { session ->
                        CouiHomeSection {
                            CouiHomeRow(
                                icon = AppIcons.getDeviceIcon(session.device.deviceType),
                                title = session.device.alias,
                                summary = session.device.displaySummary,
                            )
                            InlineTransferProgress(
                                session = session,
                                onCancel = { manager.cancelTransfer(session.sessionId) },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (previewingIndex >= 0 && previewingIndex < selectedFiles.size) {
        FilePreviewDialog(
            files = selectedFiles,
            initialIndex = previewingIndex,
            onRemoveFile = { fileToRemove -> manager.removeFile(fileToRemove.id) },
            onDismissRequest = { previewingIndex = -1 },
        )
    }
}

/** 分组标题：卡片外的一行小字，与卡片内 16dp 文字缩进对齐。 */
@Composable
private fun CouiSectionTitle(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.GroupTitle,
        color = CouiTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                .semantics { heading() },
    )
}

/**
 * 分组容器：可选标题 + 卡片表面。
 *
 * 没有换成库 `layout.Group`：Group 的外边距固定 16dp（本页 LazyColumn gutter 是 12dp）、
 * 标题行自带 32dp 缩进与 12/16dp 顶部间距，直接替换会与卡片外那行 `CouiSectionTitle`（16dp 缩进）错位。
 * 卡片形状统一对齐 `CouiPageDefaults.shape`（24dp, 3f 连续曲率）；卡内分隔线统一用库 `layout.Divider`。
 */
@Composable
private fun CouiHomeSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            CouiSectionTitle(text = title)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(CouiPageDefaults.shape)
                    .background(CouiTheme.colorScheme.card),
            content = content,
        )
    }
}

/**
 * 行：可选左图标 + 标题 + 可选说明 + 可选行尾插槽。
 *
 * 交给库 `layout.Item`：`onClick` 非空时它自带唯一的 `couiButtonFeedback(list = true)`
 * 与 `clickable(role = Role.Button)`，行内留白也进点击区，行高按库默认最小 48dp。
 * 点击语义仍挂在整行上而不是标题上，避免只有文字区域响应。
 */
@Composable
private fun CouiHomeRow(
    title: String,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    summary: String? = null,
    iconTint: Color = CouiTheme.colorScheme.accentOnSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    // 先落成带类型的局部 val，空值直接给 null，行内不留占位
    val iconSlot: (@Composable () -> Unit)? =
        if (icon == null) {
            null
        } else {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    val summarySlot: (@Composable () -> Unit)? =
        if (summary == null) {
            null
        } else {
            { CouiRowSummary(text = summary) }
        }
    Item(
        icon = iconSlot,
        summary = summarySlot,
        trailing = {
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        },
        onClick = onClick,
        dark = CouiTheme.isDark,
        title = { CouiRowTitle(text = title) },
    )
}

/** 本机接收状态：左状态点 + 接收中文案，右侧当前接收策略。 */
@Composable
private fun CouiLocalStatusRow(
    receivingCount: Int,
    quickSave: Boolean,
) {
    val receiving = receivingCount > 0
    val statusColor = if (receiving) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (receiving) stringResource(R.string.home_status_receiving, receivingCount) else stringResource(R.string.home_status_ready),
            kind = CouiTextKind.ItemSummary,
            color = statusColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (quickSave) stringResource(R.string.home_receive_mode_auto) else stringResource(R.string.home_receive_mode_confirm),
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内容胶囊按钮：coUI 没有通用按钮，这里按材质画一枚 34dp 高的胶囊。 */
@Composable
private fun CouiPillButton(
    text: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .clickable(source, null, enabled = true, role = Role.Button, onClick = onClick)
                .couiMinTouchTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(CouiCapsuleShape)
                    .background(CouiTheme.colorScheme.container)
                    .couiButtonFeedback(CouiTheme.isDark, source)
                    .defaultMinSize(minWidth = 56.dp, minHeight = 34.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                kind = CouiTextKind.ItemSummary,
                color = CouiTheme.colorScheme.accentOnSurface,
                maxLines = 1,
            )
        }
    }
}

/** 快速选择格：图标在上、文案在下，占满所属列宽。 */
@Composable
private fun CouiQuickAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .clip(CouiShape(16.dp))
                .background(CouiTheme.colorScheme.container)
                .couiButtonFeedback(CouiTheme.isDark, source)
                .clickable(source, null, enabled = true, role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 72.dp)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = CouiTheme.colorScheme.accentOnSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 待发送队列里的文件行：缩略图 + 名称/体积 + 删除。 */
@Composable
private fun CouiSelectedFileRow(
    file: FileItem,
    onOpenPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CouiShape(12.dp))
                .couiButtonFeedback(CouiTheme.isDark, source)
                .clickable(source, null, enabled = true, role = Role.Button, onClick = onOpenPreview)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumbnail(file = file, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (file.isTextMessage) stringResource(R.string.send_type_text_message) else file.name,
                kind = CouiTextKind.ItemTitle,
                color = CouiTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    if (file.isTextMessage) {
                        (file.textContent?.take(30)?.replace("\n", " ") ?: "") + " • " + file.formattedSize
                    } else {
                        file.formattedSize
                    },
                kind = CouiTextKind.ItemSummary,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CouiIconAction(
            icon = MiuixIcons.Delete,
            contentDescription = stringResource(R.string.btn_delete),
            tint = couiDangerColor(),
            onClick = onRemove,
        )
    }
}

/** 设备卡内容：别名与摘要行 + 收藏按钮 + 该设备当前会话的内嵌进度。 */
@Composable
private fun CouiDeviceCardContent(
    device: Device,
    isFavorite: Boolean,
    sessions: List<TransferSession>,
    onToggleFavorite: () -> Unit,
    onSend: () -> Unit,
    onCancelTransfer: (String) -> Unit,
) {
    val deviceSessions =
        remember(sessions, device) {
            sessions.filter { it.device.matches(device) }
        }
    val networkLabel =
        if (device.alternateIps.isNotEmpty()) {
            stringResource(R.string.send_device_multi_subnet_tag, device.alternateIps.size)
        } else {
            ""
        }
    CouiHomeRow(
        icon = AppIcons.getDeviceIcon(device.deviceType),
        title = device.alias,
        summary = device.displaySummaryWithExtra(networkLabel),
        trailing = {
            CouiIconAction(
                icon = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = if (isFavorite) stringResource(R.string.send_action_unfavorite) else stringResource(R.string.send_action_favorite),
                tint = if (isFavorite) CouiTheme.colorScheme.accentOnSurface else CouiTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleFavorite,
            )
        },
        onClick = onSend,
    )
    for (session in deviceSessions) {
        InlineTransferProgress(
            session = session,
            onCancel = { onCancelTransfer(session.sessionId) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/** 空态：一个居中图标加一行提示，不占满整屏。 */
@Composable
private fun CouiEmptyState(
    icon: ImageVector,
    title: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
        )
    }
}
