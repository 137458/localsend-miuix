@file:Suppress("LocalContextGetResourceValueCall")

package org.localsend.miuix.ui.coui.dialog

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.R
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferSession
import org.localsend.miuix.ui.component.FileThumbnail
import org.localsend.miuix.ui.component.detectWebUrlInText
import org.localsend.miuix.ui.component.rememberDialogContentMaxHeight
import org.localsend.miuix.ui.coui.component.couiMinTouchTarget

/**
 * 传入传输请求弹窗的 coUI 版本：标题与来源设备走库的 title/message 槽，
 * 文本预览与文件多选放进 content 槽，动作区由库排版。
 *
 * 库的 Dialog 只把 title/message 当两个短文本槽（Dialog.kt:78-92），其余内容全部由
 * content 槽承载，所以文件多选、全选开关与文本预览这些都放在 content 里，而不是塞进 message。
 * [session] 用 visible 传递而不是在外面套 if：库只在 visible 非空时保留退场窗口（Dialog.kt:48-49），
 * 调用必须始终留在组合中，否则退场动画会退化成窗口直接消失。
 *
 * 动作语义与原 Miuix 实现逐项对齐：
 * - 文本消息：确认 = 复制并接收（onAcceptAndCopy），拒绝 = dismissAction，URL 另在 content 内给一行；
 * - 文件请求：确认 = 按选中集合接收（空集时禁用），拒绝 = dismissAction。
 * 拒绝用 Secondary 而不是 neutralAction：库的 neutralAction 恒渲染成破坏性色（Dialog.kt:167），
 * 而 Secondary 没有主题配色工厂，因此这里保持库的默认灰，不额外染红。
 */
@Composable
internal fun CouiIncomingTransferDialog(
    session: TransferSession?,
    onAccept: (selectedFileIds: Set<String>?) -> Unit,
    onAcceptAndCopy: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    var lastSession by remember { mutableStateOf(session) }
    if (session != null) {
        lastSession = session
    }
    val renderSession = session ?: lastSession
    var selectedFileIds by remember(renderSession?.sessionId) {
        mutableStateOf(renderSession?.files?.map { it.id }?.toSet() ?: emptySet())
    }
    val primaryActionColors = couiPrimaryActionColors()
    val placement = rememberCouiDialogPlacement()
    val material = rememberCouiDialogMaterial()
    val isTextMessage = renderSession?.isTextMessage == true

    Dialog(
        // 关闭弹窗（点击外部或返回键）只是收起提示，不等于拒绝：请求仍由系统保持待处理，
        // 接收页卡片上的“接收”按钮与通知栏操作都还能继续处理，只有超时或用户显式拒绝才回 403。
        onDismissRequest = onDismiss,
        confirmAction =
            if (isTextMessage) {
                CouiDialogAction(
                    text = stringResource(R.string.btn_copy),
                    onClick = onAcceptAndCopy,
                    style = CouiDialogActionStyle.Primary,
                    colors = primaryActionColors,
                )
            } else {
                CouiDialogAction(
                    text = stringResource(R.string.btn_accept),
                    onClick = { onAccept(selectedFileIds) },
                    enabled = selectedFileIds.isNotEmpty(),
                    style = CouiDialogActionStyle.Primary,
                    colors = primaryActionColors,
                )
            },
        title = if (isTextMessage) stringResource(R.string.dialog_incoming_text_title) else stringResource(R.string.dialog_incoming_files_title),
        message = renderSession?.let { stringResource(R.string.dialog_incoming_from_device, it.device.alias, it.device.ip) },
        dismissAction = CouiDialogAction(text = stringResource(R.string.btn_decline), onClick = onDecline),
        placement = placement,
        material = material,
        visible = session != null,
    ) {
        if (renderSession != null) {
            // content 槽位于库自带的纵向滚动区内，四周留白要与 title/message 的 contentPadding 对齐；
            // 因此这里只补水平内边距，纵向间距交给 Column 的 spacedBy。
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (renderSession.isTextMessage) {
                    CouiIncomingTextBody(
                        session = renderSession,
                        onOpenUrl = { onAccept(null) },
                    )
                } else {
                    CouiIncomingFileSelection(
                        session = renderSession,
                        selectedFileIds = selectedFileIds,
                        onSelectedFileIdsChange = { selectedFileIds = it },
                    )
                }
            }
        }
    }
}

/** 文本消息正文：字数提示 + 可滚动预览卡片 + 命中 URL 时的打开行。 */
@Composable
private fun CouiIncomingTextBody(
    session: TransferSession,
    onOpenUrl: () -> Unit,
) {
    val previewText = session.singleTextMessageContent ?: session.files.firstOrNull()?.textContent ?: stringResource(R.string.notif_plain_text_message)
    val detectedUrl = remember(previewText) { detectWebUrlInText(previewText) }

    Text(
        text = stringResource(R.string.dialog_incoming_text_length, previewText.length),
        kind = CouiTextKind.ItemSummary,
        color = CouiTheme.colorScheme.onSurfaceVariant,
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CouiShape(12.dp))
                .background(CouiTheme.colorScheme.container)
                .padding(14.dp),
    ) {
        // 长文本在卡片内自己滚动，弹窗主体与动作区高度保持稳定。
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = rememberDialogContentMaxHeight(0.3f, 100.dp, 240.dp))
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(text = previewText, kind = CouiTextKind.ItemTitle)
        }
    }

    if (detectedUrl != null) {
        CouiOpenUrlRow(url = detectedUrl, onClick = onOpenUrl)
    }
}

/**
 * 打开链接行。
 *
 * 逻辑与原 Miuix 实现一致：启动外部浏览器后按“全部接收”回执，
 * 让文本消息不会因为用户去看了链接而一直挂在待处理状态。
 */
@Composable
private fun CouiOpenUrlRow(
    url: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(CouiShape(12.dp))
                .couiButtonFeedback(dark = CouiTheme.isDark, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    try {
                        val intent =
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show()
                    }
                    onClick()
                }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.btn_open_link),
            kind = CouiTextKind.ItemTitle,
            color = CouiTheme.colorScheme.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 文件请求正文：计数/全选行 + 可滚动的多选文件卡片。 */
@Composable
private fun CouiIncomingFileSelection(
    session: TransferSession,
    selectedFileIds: Set<String>,
    onSelectedFileIdsChange: (Set<String>) -> Unit,
) {
    val totalFilesCount = session.files.size
    val isAllSelected = totalFilesCount > 0 && selectedFileIds.size == totalFilesCount

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (totalFilesCount > 1) {
                    stringResource(R.string.dialog_incoming_selective_count, selectedFileIds.size, totalFilesCount)
                } else {
                    stringResource(R.string.dialog_incoming_files_summary, session.files.size, session.formattedTotalSize)
                },
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (totalFilesCount > 1) {
            CouiSelectAllToggle(
                isAllSelected = isAllSelected,
                onToggle = { onSelectedFileIdsChange(if (isAllSelected) emptySet() else session.files.map { it.id }.toSet()) },
            )
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CouiShape(12.dp))
                .background(CouiTheme.colorScheme.container),
    ) {
        // 列表必须限高：content 槽所在的滚动容器会给出无界高度，LazyColumn 拿到无界约束会直接抛异常。
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = rememberDialogContentMaxHeight(0.4f, 140.dp, 320.dp)),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(session.files, key = { it.id }) { file ->
                CouiIncomingFileRow(
                    file = file,
                    checked = selectedFileIds.contains(file.id),
                    selectable = totalFilesCount > 1,
                    onToggle = {
                        onSelectedFileIdsChange(
                            if (selectedFileIds.contains(file.id)) {
                                selectedFileIds - file.id
                            } else {
                                selectedFileIds + file.id
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * 全选开关。
 *
 * 库的 Checkbox 只负责绘制，点击与无障碍语义必须由宿主提供（Checkbox.kt:46），
 * 所以这里把 toggleable 挂在整行上，checkbox 自己不接点击，避免一次点击被消费两次。
 */
@Composable
private fun CouiSelectAllToggle(
    isAllSelected: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .toggleable(
                    value = isAllSelected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).couiMinTouchTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(CouiShape(8.dp))
                    .couiButtonFeedback(dark = CouiTheme.isDark, interactionSource = interactionSource)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isAllSelected)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.dialog_incoming_select_all),
                kind = CouiTextKind.ItemSummary,
                color = CouiTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 单个文件行：仅多文件请求可切换；单文件请求隐藏勾选框，整行也不可点。 */
@Composable
private fun CouiIncomingFileRow(
    file: FileItem,
    checked: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(CouiShape(8.dp))
                .couiButtonFeedback(
                    dark = CouiTheme.isDark,
                    interactionSource = interactionSource,
                    enabled = selectable,
                    list = true,
                ).toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = selectable,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            Checkbox(checked = checked)
            Spacer(modifier = Modifier.width(8.dp))
        }
        FileThumbnail(file = file, size = 28.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = file.name,
            kind = CouiTextKind.ItemTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = file.formattedSize,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
