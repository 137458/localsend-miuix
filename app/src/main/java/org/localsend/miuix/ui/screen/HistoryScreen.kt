package org.localsend.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.localsend.miuix.R
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import org.localsend.miuix.ui.component.AppIcons
import org.localsend.miuix.ui.component.HistoryDetailDialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 传输历史页：支持文本消息与文件传输区分展示、一键复制文本、点击查看详情弹窗、单条记录删除。
 */
@Composable
fun HistoryScreen(
    manager: LocalSendManager,
    onBack: () -> Unit
) {
    val history by manager.transferHistory.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    var selectedItem by remember { mutableStateOf<TransferHistoryItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.history_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.history_dialog_clear_title),
                                tint = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (history.isEmpty()) {
                item {
                    SmallTitle(text = stringResource(R.string.history_empty_title))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.history_empty_summary),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { selectedItem = item },
                        onCopyText = { text ->
                            manager.copyTextToClipboard(text)
                        }
                    )
                }
            }
        }
    }

    HistoryDetailDialog(
        item = selectedItem,
        show = selectedItem != null,
        onDismissRequest = { selectedItem = null },
        onDelete = { id -> manager.deleteHistoryItem(id) }
    )

    if (showClearConfirmDialog) {
        top.yukonga.miuix.kmp.window.WindowDialog(
            show = true,
            title = stringResource(R.string.history_dialog_clear_title),
            onDismissRequest = { showClearConfirmDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_dialog_clear_msg, history.size),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showClearConfirmDialog = false },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Button(
                        onClick = {
                            manager.clearHistory()
                            showClearConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: TransferHistoryItem,
    onClick: () -> Unit,
    onCopyText: (String) -> Unit
) {
    val context = LocalContext.current
    val primary = MiuixTheme.colorScheme.primary
    val statusInfo = remember(primary, item.status, context) {
        statusInfo(context = context, status = item.status, primaryColor = primary)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isTextMessage) Icons.Default.ChatBubbleOutline else if (item.isIncoming) Icons.Default.Download else Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (item.isTextMessage) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isIncoming) stringResource(R.string.history_item_from, item.deviceAlias) else stringResource(R.string.history_item_to, item.deviceAlias),
                        style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusInfo.label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = statusInfo.color
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                    Text(
                        text = item.textContent,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (item.fileNames.isNotEmpty()) item.fileNames.joinToString("、") else stringResource(R.string.history_files_count, item.fileCount),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val formattedTime = remember(item.timestamp) {
                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                }

                Text(
                    text = "${item.deviceIp} · ${item.formattedSize} · $formattedTime",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            if (item.isTextMessage && !item.textContent.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onCopyText(item.textContent)
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(stringResource(R.string.btn_copy))
                }
            }
        }
    }
}

private data class StatusInfo(val label: String, val color: Color)

private fun statusInfo(context: Context, status: TransferStatus, primaryColor: Color): StatusInfo = when (status) {
    TransferStatus.Completed -> StatusInfo(context.getString(R.string.status_completed), Color(0xFF16A34A))
    TransferStatus.Failed -> StatusInfo(context.getString(R.string.status_failed), Color(0xFFDC2626))
    TransferStatus.Canceled -> StatusInfo(context.getString(R.string.status_canceled), Color(0xFFF59E0B))
    else -> StatusInfo(context.getString(R.string.status_processing), primaryColor)
}