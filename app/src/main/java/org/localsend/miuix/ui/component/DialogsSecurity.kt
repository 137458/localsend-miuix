package org.localsend.miuix.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import org.localsend.miuix.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun PinDialog(
    show: Boolean,
    initialPin: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var pin by remember(show, initialPin) { mutableStateOf(initialPin ?: "") }

    TextInputDialog(
        show = show,
        title = stringResource(R.string.dialog_pin_title),
        value = pin,
        onValueChange = { pin = it },
        label = stringResource(R.string.dialog_pin_label),
        confirmText = stringResource(R.string.btn_save),
        keyboardType = KeyboardType.Number,
        description = stringResource(R.string.dialog_pin_desc),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            onConfirm(pin.trim().ifEmpty { null })
            true
        },
    )
}

@Composable
fun TargetDevicePinDialog(
    show: Boolean,
    targetAlias: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    if (show) {
        TextInputDialog(
            show = true,
            title = stringResource(R.string.dialog_target_pin_title),
            value = pin,
            onValueChange = { pin = it },
            label = stringResource(R.string.dialog_target_pin_label),
            keyboardType = KeyboardType.Ascii,
            description = stringResource(R.string.dialog_target_pin_desc, targetAlias),
            onDismissRequest = onDismissRequest,
            onConfirm = {
                onConfirm(pin.trim())
                true
            },
        )
    }
}

/**
 * 证书指纹弹窗：说明走 WindowDialog 的 summary 槽位，指纹放在 [DialogCard] 里，
 * 动作区按「主操作按钮 + 次级文字动作」纵向排列。
 */
@Composable
fun CertFingerprintDialog(
    show: Boolean,
    fingerprint: String,
    onDismissRequest: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val context = LocalContext.current

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_cert_title),
        summary = stringResource(R.string.dialog_cert_desc),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogTokens.SectionGap),
        ) {
            DialogCard {
                Text(
                    text = stringResource(R.string.dialog_cert_label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = fingerprint.ifEmpty { stringResource(R.string.dialog_cert_empty) },
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = DialogTokens.BlockGap),
                )
            }

            DialogActionColumn {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Certificate Fingerprint", fingerprint))
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dialog_cert_btn_copy))
                }
                DialogTextAction(
                    text = stringResource(R.string.dialog_cert_btn_regenerate),
                    onClick = onRegenerate,
                    colors = dialogDangerTextColors(),
                )
                DialogTextAction(
                    text = stringResource(R.string.btn_close),
                    onClick = onDismissRequest,
                )
            }
        }
    }
}
