package org.localsend.miuix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.localsend.miuix.model.TransferSession
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 传输会话卡片：
 * - 纯文本消息：展示文本预览、对端别名与确认状态，不展示冗余的字节进度条与速率；
 * - 文件传输：展示正在收发文件的全景进度（当前文件、总体进度条、已传/总量、百分比、速率、剩余时间与可折叠文件清单）。
 */
@Composable
fun TransferSessionCard(
    session: TransferSession,
    onCancel: () -> Unit,
    onAccept: (() -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (session.isTextMessage) {
                TextMessageCardContent(session = session, onCancel = onCancel, onAccept = onAccept)
            } else {
                FileTransferCardContent(
                    session = session,
                    isExpanded = isExpanded,
                    onToggleExpanded = { isExpanded = !isExpanded },
                    onCancel = onCancel,
                    onAccept = onAccept,
                )
            }
        }
    }
}

/**
 * 内嵌在设备 Card 下方的传输进度组件（直接在同一个设备 Card 内部渲染）。
 */
@Composable
fun InlineTransferProgress(
    session: TransferSession,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onAccept: (() -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
    ) {
        // 分割线
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (session.isTextMessage) {
            InlineTextMessageProgress(session = session, onCancel = onCancel)
        } else {
            InlineFileTransferProgress(
                session = session,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded },
                onCancel = onCancel,
                onAccept = onAccept,
            )
        }
    }
}
