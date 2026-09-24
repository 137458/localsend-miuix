package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun PinDialog(
    show: Boolean,
    initialPin: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var pin by remember(show, initialPin) { mutableStateOf(initialPin ?: "") }

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_pin_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_pin_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = pin,
                onValueChange = { pin = it },
                label = stringResource(R.string.dialog_pin_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        val trimmed = pin.trim().ifEmpty { null }
                        onConfirm(trimmed)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}

@Composable
fun TargetDevicePinDialog(
    show: Boolean,
    targetAlias: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    if (show) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.dialog_target_pin_title),
            onDismissRequest = onDismissRequest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_target_pin_desc, targetAlias),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = stringResource(R.string.dialog_target_pin_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Button(
                        onClick = {
                            onConfirm(pin.trim())
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun CertFingerprintDialog(
    show: Boolean,
    fingerprint: String,
    onDismissRequest: () -> Unit,
    onRegenerate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    WindowDialog(
        show = show,
        title = stringResource(R.string.dialog_cert_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_cert_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_cert_label),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fingerprint.ifEmpty { stringResource(R.string.dialog_cert_empty) },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DialogButtonRow(
                secondaryText = stringResource(R.string.dialog_cert_btn_copy),
                onSecondary = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Certificate Fingerprint", fingerprint))
                },
                primaryText = stringResource(R.string.dialog_cert_btn_regenerate),
                onPrimary = { onRegenerate() },
                primaryColors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error, contentColor = MiuixTheme.colorScheme.onError),
                spacing = 8.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_close))
            }
        }
    }
}