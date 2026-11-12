package org.localsend.miuix.ui.coui.page

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlinx.coroutines.launch
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.HistoryFilterTab
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.model.filterByTab
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.ConfirmDialog
import org.localsend.miuix.ui.component.HistoryDetailDialog
import org.localsend.miuix.ui.util.openDownloadFolder
import org.localsend.miuix.ui.util.rememberUiTimeText
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.itos.coui.theme.CouiTheme as CouiKitTheme

/**
 * 传输历史页（ColorOS 皮肤）：筛选、卡片、详情与删除确认的行为与 HyperOS 皮肤一致，
 * 视觉改用 coUI-Kit 的配色、形状与字阶；coUI-Kit 没有卡片与图标组件，
 * 卡片由 Row 自绘，图标沿用项目既有 AppIcons（tint 由本页指定）。
 */
@Composable
fun CouiHistoryScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenHome: () -> Unit,
) {
    val context = LocalContext.current
    val history by manager.transferHistory.collectAsStateWithLifecycle()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val scheme = CouiKitTheme.colorScheme
    val errorColor = MiuixTheme.colorScheme.error
    val coroutineScope = rememberCoroutineScope()

    var selectedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedItem by remember { mutableStateOf<TransferHistoryItem?>(null) }
    var itemToDelete by remember { mutableStateOf<TransferHistoryItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filterTabs =
        listOf(
            stringResource(R.string.history_tab_all),
            stringResource(R.string.history_tab_receive),
            stringResource(R.string.history_tab_send),
        )
    val currentTab =
        when (selectedFilterIndex) {
            1 -> HistoryFilterTab.RECEIVE
            2 -> HistoryFilterTab.SEND
            else -> HistoryFilterTab.ALL
        }
    val filteredHistory =
        remember(history, currentTab) {
            history.filterByTab(currentTab)
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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    kind = CouiTextKind.TopBar,
                )
                Spacer(modifier = Modifier.weight(1f))
                CouiHistoryAction(
                    icon = AppIcons.Folder,
                    contentDescription = stringResource(R.string.history_action_open_folder),
                    tint = scheme.onSurface,
                    onClick = {
                        openDownloadFolder(
                            context = context,
                            downloadTreeUri = settings.downloadTreeUri,
                        )
                    },
                )
                if (history.isNotEmpty()) {
                    CouiHistoryAction(
                        icon = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.history_dialog_clear_title),
                        tint = errorColor,
                        onClick = { showClearConfirmDialog = true },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filterTabs.forEachIndexed { index, label ->
                    val selected = index == selectedFilterIndex
                    val tapIndex by rememberUpdatedState(index)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(remember { CouiShape(radius = 17.dp) })
                                .background(if (selected) scheme.surface else scheme.container)
                                .pointerInput(Unit) {
                                    detectTapGestures { selectedFilterIndex = tapIndex }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            kind = CouiTextKind.ItemSummary,
                            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                if (filteredHistory.isEmpty()) {
                    CouiHistoryEmptyState(
                        text =
                            when (selectedFilterIndex) {
                                1 -> stringResource(R.string.history_empty_receive)
                                2 -> stringResource(R.string.history_empty_send)
                                else -> stringResource(R.string.history_empty_title)
                            },
                    )
                } else {
                    // 回弹效果按滚动容器独占：在 item 作用域外记住，列表重组时不会反复重建
                    val historyOverscroll = rememberCouiOverscrollEffect()
                    // 回弹节点由 LazyColumn 内部的 scrollableArea 挂载，不能再叠 Modifier.overscroll
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        overscrollEffect = historyOverscroll,
                        contentPadding =
                            PaddingValues(
                                top = 4.dp,
                                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredHistory, key = { it.id }) { item ->
                            CouiHistoryCard(
                                item = item,
                                onOpenDetail = { selectedItem = item },
                                onLongPress = { itemToDelete = item },
                                onCopyText = { text ->
                                    manager.copyTextToClipboard(text)
                                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    HistoryDetailDialog(
        item = selectedItem,
        show = selectedItem != null,
        onDismissRequest = { selectedItem = null },
        onDelete = { id -> manager.deleteHistoryItem(id) },
        onResend = { item ->
            coroutineScope.launch {
                val count = manager.resendHistoryItem(item)
                if (count > 0) {
                    selectedItem = null
                    onOpenHome()
                } else {
                    Toast.makeText(context, context.getString(R.string.history_resend_empty_hint), Toast.LENGTH_SHORT).show()
                }
            }
        },
    )

    if (itemToDelete != null) {
        val target = itemToDelete!!
        ConfirmDialog(
            show = true,
            title = stringResource(R.string.history_dialog_delete_title),
            message = stringResource(R.string.history_dialog_delete_msg, target.deviceAlias),
            confirmText = stringResource(R.string.history_detail_btn_delete_record),
            onConfirm = {
                manager.deleteHistoryItem(target.id)
                itemToDelete = null
                Toast.makeText(context, context.getString(R.string.toast_history_item_deleted), Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { itemToDelete = null },
            dismissText = stringResource(R.string.btn_cancel),
            danger = true,
        )
    }

    if (showClearConfirmDialog) {
        ConfirmDialog(
            show = true,
            title = stringResource(R.string.history_dialog_clear_title),
            message = stringResource(R.string.history_dialog_clear_msg, history.size),
            confirmText = stringResource(R.string.btn_clear),
            onConfirm = {
                manager.clearHistory()
                showClearConfirmDialog = false
            },
            onDismissRequest = { showClearConfirmDialog = false },
            dismissText = stringResource(R.string.btn_cancel),
            danger = true,
        )
    }
}

/** 顶栏动作：coUI-Kit 没有 IconButton，用圆形可点区域包一个 Miuix 图标，配色由调用方给定，按压反馈走 couiButtonFeedback。 */
@Composable
private fun CouiHistoryAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CouiShape(20.dp))
                .couiButtonFeedback(CouiKitTheme.isDark, source)
                .clickable(source, null, enabled = true, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}

/** 空态：coUI-Kit 未提供空列表组件，居中图标加一行说明。 */
@Composable
private fun CouiHistoryEmptyState(text: String) {
    val scheme = CouiKitTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = AppIcons.History,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            kind = CouiTextKind.ItemSummary,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/**
 * 单条记录卡片：coUI-Kit 无卡片组件，按 ColorOS 分组卡样式自绘，整卡点击看详情、长按删除。
 * 不用库 `layout.Item`：它不支持长按，会丢掉长按删除；卡片本身是单行富内容，也没有可拆的行与分隔线。
 */
@Composable
private fun CouiHistoryCard(
    item: TransferHistoryItem,
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    onCopyText: (String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val errorColor = MiuixTheme.colorScheme.error
    val shape = remember { CouiShape(radius = 18.dp) }
    val latestOpenDetail by rememberUpdatedState(onOpenDetail)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val cardSource = remember { MutableInteractionSource() }

    val iconVector =
        when {
            item.isTextMessage -> AppIcons.Text
            item.isIncoming -> AppIcons.Receive
            else -> AppIcons.Send
        }
    val iconTint =
        when (item.status) {
            TransferStatus.Failed -> errorColor
            TransferStatus.Canceled -> scheme.onSurfaceVariant
            else -> scheme.accent
        }
    val statusLabel =
        when (item.status) {
            TransferStatus.Completed -> stringResource(R.string.status_completed)
            TransferStatus.Failed -> stringResource(R.string.status_failed)
            TransferStatus.Canceled -> stringResource(R.string.status_canceled)
            else -> stringResource(R.string.status_processing)
        }
    val statusColor =
        when (item.status) {
            TransferStatus.Failed -> errorColor
            TransferStatus.Canceled -> scheme.onSurfaceVariant
            else -> scheme.accent
        }
    val titleText =
        if (item.isIncoming) {
            stringResource(R.string.history_item_from, item.deviceAlias)
        } else {
            stringResource(R.string.history_item_to, item.deviceAlias)
        }
    val detailText =
        if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
            item.textContent ?: ""
        } else if (item.fileNames.isNotEmpty()) {
            item.fileNames.joinToString(stringResource(R.string.enumeration_separator))
        } else {
            stringResource(R.string.history_files_count, item.fileCount)
        }
    val formattedTime = rememberUiTimeText("MM-dd HH:mm", item.timestamp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(shape)
                .background(scheme.card)
                .couiButtonFeedback(CouiKitTheme.isDark, cardSource)
                .combinedClickable(
                    interactionSource = cardSource,
                    indication = null,
                    role = Role.Button,
                    onLongClick = { latestLongPress() },
                    onClick = { latestOpenDetail() },
                ).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(scheme.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titleText,
                    kind = CouiTextKind.ItemTitle,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    kind = CouiTextKind.ItemSummary,
                    color = statusColor,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detailText,
                kind = CouiTextKind.ItemSummary,
                color = scheme.onSurfaceVariant,
                maxLines = if (item.isTextMessage) 2 else 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${item.deviceIp} · ${item.formattedSize} · $formattedTime",
                kind = CouiTextKind.GroupFooter,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
            val textContent = item.textContent
            Spacer(modifier = Modifier.width(8.dp))
            val copyShape = remember { CouiShape(radius = 12.dp) }
            val copySource = remember { MutableInteractionSource() }
            Box(
                modifier =
                    Modifier
                        .clip(copyShape)
                        .background(scheme.container)
                        .couiButtonFeedback(CouiKitTheme.isDark, copySource)
                        .clickable(copySource, null, enabled = true, role = Role.Button) {
                            onCopyText(textContent)
                        }.padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.btn_copy),
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.accent,
                    maxLines = 1,
                )
            }
        }
    }
}
