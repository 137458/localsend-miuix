package org.localsend.miuix.ui.coui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import org.localsend.miuix.R

/**
 * TLS 证书指纹弹窗，正文用库 Dialog 的 content 槽位：标签 + 可长按复制的指纹。
 *
 * 动作走库的 confirm/dismiss/neutral 三槽：复制是主操作（弹窗不关闭，库的动作是受控回调），
 * 破坏性的「重新生成」放在 neutral 并统一走 [couiPrimaryActionColors]（`danger = true`），
 * 与同页 [CouiAlertDialog] 的破坏性主操作视觉一致。
 * 三个动作时库横排转竖排、顺序为 neutral/confirm/dismiss（Dialog.kt:105-106、:123）。
 */
@Composable
internal fun CouiCertFingerprintDialog(
    show: Boolean,
    fingerprint: String,
    onCopyFingerprint: () -> Unit,
    onDismissRequest: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme

    Dialog(
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = stringResource(R.string.dialog_cert_btn_copy),
                onClick = onCopyFingerprint,
                style = CouiDialogActionStyle.Primary,
                colors = couiPrimaryActionColors(),
            ),
        title = stringResource(R.string.dialog_cert_title),
        message = stringResource(R.string.dialog_cert_desc),
        neutralAction =
            CouiDialogAction(
                text = stringResource(R.string.dialog_cert_btn_regenerate),
                onClick = onRegenerate,
                style = CouiDialogActionStyle.Primary,
                colors = couiPrimaryActionColors(danger = true),
            ),
        dismissAction =
            CouiDialogAction(
                text = stringResource(R.string.btn_close),
                onClick = onDismissRequest,
            ),
        placement = rememberCouiDialogPlacement(),
        material = rememberCouiDialogMaterial(),
        visible = show,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiDialogDefaults.contentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.dialog_cert_label),
                kind = CouiTextKind.ItemSummary,
                color = scheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    text = fingerprint.ifEmpty { stringResource(R.string.dialog_cert_empty) },
                    kind = CouiTextKind.ItemValue,
                    color = scheme.accent,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
