package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

/** 对话框底部双按钮行：左侧次要操作、右侧主操作（可为危险色），供确认类弹窗复用。 */
@Composable
internal fun DialogButtonRow(
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    primaryColors: ButtonColors = ButtonDefaults.buttonColors(),
    spacing: Dp = 12.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Button(
            onClick = onSecondary,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(1f),
        ) {
            Text(secondaryText)
        }
        Button(
            onClick = onPrimary,
            colors = primaryColors,
            modifier = Modifier.weight(1f),
        ) {
            Text(primaryText)
        }
    }
}
