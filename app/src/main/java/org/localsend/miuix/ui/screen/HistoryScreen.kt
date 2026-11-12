// Compose 1.7.6 未提供 LocalResources；本文件命中点均为 Toast / 回调等一次性提示。
@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.screen

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.HistoryFilterTab
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.model.filterByTab
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.BlurredBar
import org.localsend.miuix.ui.component.ConfirmDialog
import org.localsend.miuix.ui.component.EmptyStateCard
import org.localsend.miuix.ui.component.HistoryDetailDialog
import org.localsend.miuix.ui.component.LiquidGlassSegmentedBar
import org.localsend.miuix.ui.component.blurBackdropSource
import org.localsend.miuix.ui.component.rememberBlurBackdrop
import org.localsend.miuix.ui.util.openDownloadFolder
import org.localsend.miuix.ui.util.rememberUiTimeText
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 传输历史页：支持分类标签（全部/接收/发送）筛选、直达保存目录、文本消息快速复制、
 * 点击查看详情、长按快速删除单条记录、一键清空全部历史。
 */
@Composable
fun HistoryScreen(
    manager: LocalSendManager,
    contentPadding: PaddingValues,
    onOpenHome: () -> Unit,
) {
    val context = LocalContext.current
    val history by manager.transferHistory.collectAsStateWithLifecycle()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val colorScheme = MiuixTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    var selectedFilterIndex by remember { mutableIntStateOf(0) }
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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BlurredBar(
                    backdrop = backdrop,
                    scrollBehavior = scrollBehavior,
                ) {
                    TopAppBar(
                        title = stringResource(R.string.history_title),
                        scrollBehavior = scrollBehavior,
                        color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                        actions = {
                            IconButton(
                                onClick = {
                                    openDownloadFolder(
                                        context = context,
                                        downloadTreeUri = settings.downloadTreeUri,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = AppIcons.Folder,
                                    contentDescription = stringResource(R.string.history_action_open_folder),
                                    tint = colorScheme.onSurface,
                                )
                            }
                            if (history.isNotEmpty()) {
                                IconButton(onClick = { showClearConfirmDialog = true }) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = stringResource(R.string.history_dialog_clear_title),
                                        tint = colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
                // 分段胶囊独立悬浮于顶栏下方，外层无背景底色，滚动内容直接穿过胶囊并参与液态玻璃折射采样
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 4.dp,
                                bottom = 8.dp,
                                start = 16.dp,
                                end = 16.dp,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidGlassSegmentedBar(
                        tabs = filterTabs,
                        selectedIndex = selectedFilterIndex,
                        onSelected = { selectedFilterIndex = it },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                        top = innerPadding.calculateTopPadding() + 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = AppIcons.History,
                            title =
                                when (selectedFilterIndex) {
                                    1 -> stringResource(R.string.history_empty_receive)
                                    2 -> stringResource(R.string.history_empty_send)
                                    else -> stringResource(R.string.history_empty_title)
                                },
                        )
                    }
                } else {
                    items(filteredHistory, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            modifier = Modifier.animateItem(),
                            onClick = { selectedItem = item },
                            onLongClick = { itemToDelete = item },
                            onCopyText = { text ->
                                manager.copyTextToClipboard(text)
                            },
                        )
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

    // 删除单条记录不可撤销，走统一确认弹窗：说明进 summary，主操作使用错误色
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

    // 清空历史同样不可撤销，与删除记录共用同一个确认弹窗骨架
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

@Composable
private fun HistoryItemCard(
    item: TransferHistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = MiuixTheme.colorScheme.primary
    val error = MiuixTheme.colorScheme.error
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val statusInfo =
        remember(primary, error, summary, item.status, context) {
            statusInfo(
                context = context,
                status = item.status,
                primaryColor = primary,
                errorColor = error,
                summaryColor = summary,
            )
        }

    val iconVector =
        if (item.isTextMessage) {
            AppIcons.Text
        } else if (item.isIncoming) {
            AppIcons.Receive
        } else {
            AppIcons.Send
        }

    val iconTint =
        when (item.status) {
            TransferStatus.Failed -> error
            TransferStatus.Canceled -> summary
            else -> primary
        }

    Card(
        onClick = onClick,
        onLongPress = onLongClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            if (item.isIncoming) {
                                stringResource(R.string.history_item_from, item.deviceAlias)
                            } else {
                                stringResource(R.string.history_item_to, item.deviceAlias)
                            },
                        style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusInfo.label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = statusInfo.color,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                    Text(
                        text = item.textContent,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    val separator = stringResource(R.string.enumeration_separator)
                    Text(
                        text =
                            if (item.fileNames.isNotEmpty()) {
                                item.fileNames.joinToString(separator)
                            } else {
                                stringResource(R.string.history_files_count, item.fileCount)
                            },
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val formattedTime = rememberUiTimeText("MM-dd HH:mm", item.timestamp)

                Text(
                    text = "${item.deviceIp} · ${item.formattedSize} · $formattedTime",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onCopyText(item.textContent)
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.btn_copy))
                }
            }
        }
    }
}

private data class StatusInfo(
    val label: String,
    val color: Color,
)

private fun statusInfo(
    context: Context,
    status: TransferStatus,
    primaryColor: Color,
    errorColor: Color,
    summaryColor: Color,
): StatusInfo =
    when (status) {
        TransferStatus.Completed -> StatusInfo(context.getString(R.string.status_completed), primaryColor)
        TransferStatus.Failed -> StatusInfo(context.getString(R.string.status_failed), errorColor)
        TransferStatus.Canceled -> StatusInfo(context.getString(R.string.status_canceled), summaryColor)
        else -> StatusInfo(context.getString(R.string.status_processing), primaryColor)
    }
