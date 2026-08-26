package org.localsend.miuix.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.localsend.miuix.model.TransferSession
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun RenameDeviceDialog(
    show: Boolean,
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(show, initialName) { mutableStateOf(initialName) }

    WindowDialog(
        show = show,
        title = "修改设备名称",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "设备别名",
                useLabelAsPlaceholder = true,
                singleLine = true,
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
                    Text("取消")
                }
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onConfirm(trimmed)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
fun PortDialog(
    show: Boolean,
    initialPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var port by remember(show, initialPort) { mutableStateOf(initialPort.toString()) }

    WindowDialog(
        show = show,
        title = "修改服务端口",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = port,
                onValueChange = { newValue -> if (newValue.length <= 5) port = newValue },
                label = "端口号 (1 - 65535)",
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
                    Text("取消")
                }
                Button(
                    onClick = {
                        val newPort = port.trim().toIntOrNull()
                        if (newPort != null && newPort in 1..65535) {
                            onConfirm(newPort)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
fun ManualIpDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSend: (ip: String, port: Int) -> Unit
) {
    var ip by remember(show) { mutableStateOf("") }
    var port by remember(show) { mutableStateOf("53317") }

    WindowDialog(
        show = show,
        title = "手动输入 IP 发送",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = ip,
                onValueChange = { ip = it },
                label = "目标 IP 地址 (例如 192.168.1.100)",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = port,
                onValueChange = { port = it },
                label = "端口 (默认 53317)",
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
                    Text("取消")
                }
                Button(
                    onClick = {
                        val targetIp = ip.trim()
                        val targetPort = port.trim().toIntOrNull() ?: 53317
                        if (targetIp.isNotEmpty()) {
                            onSend(targetIp, targetPort)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("连接并发送")
                }
            }
        }
    }
}

@Composable
fun SendTextDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(show) { mutableStateOf("") }

    WindowDialog(
        show = show,
        title = "发送纯文本",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = "输入或粘贴要发送的文本内容",
                useLabelAsPlaceholder = true,
                minLines = 3,
                maxLines = 6,
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
                    Text("取消")
                }
                Button(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            onConfirm(trimmed)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("发送文本")
                }
            }
        }
    }
}

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
        title = "设置传输 PIN 码",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = "设置 PIN 码后，其它设备向您发送内容时必须输入相同的 PIN 码才能完成握手。留空则表示不启用 PIN 保护。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = pin,
                onValueChange = { pin = it },
                label = "PIN 码（留空关闭）",
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
                    Text("取消")
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
                    Text("保存")
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
        title = "TLS 安全证书指纹",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = "LocalSend 在 HTTPS 模式下使用本设备生成的自签名 X.509 证书进行端到端加密。对方设备可通过比对此 SHA-256 指纹确认未遭受中间人攻击。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SHA-256 指纹",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fingerprint.ifEmpty { "未生成或 HTTPS 未启用" },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Certificate Fingerprint", fingerprint))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制指纹")
                }

                Button(
                    onClick = {
                        onRegenerate()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error, contentColor = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重新生成")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭")
            }
        }
    }
}

@Composable
fun IncomingTransferDialog(
    session: TransferSession?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    WindowDialog(
        show = session != null,
        title = if (session?.isTextMessage == true) "收到纯文本消息" else "收到传输请求",
        onDismissRequest = onDecline
    ) {
        if (session != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "来自设备: ${session.device.alias} (${session.device.ip})",
                    style = MiuixTheme.textStyles.body1
                )

                if (session.isTextMessage) {
                    val previewText = session.singleTextMessageContent ?: "纯文本消息"
                    Text(
                        text = "文本内容 (${previewText.length} 字符)",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = previewText,
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 6
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LocalSend Text", previewText))
                        },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 复制到剪贴板")
                    }
                } else {
                    Text(
                        text = "共 ${session.files.size} 个文件，大小 ${session.formattedTotalSize}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(session.files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.getFileIcon(file.mimeType, file.name),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MiuixTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = file.name,
                                        style = MiuixTheme.textStyles.body1,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = file.formattedSize,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("拒绝")
                    }
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("接收")
                    }
                }
            }
        }
    }
}

@Composable
fun AddContentBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onPickApps: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    top.yukonga.miuix.kmp.window.WindowBottomSheet(
        show = show,
        title = "添加发送内容",
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "选择文件",
                    summary = "从设备存储选择任意文件",
                    onClick = {
                        onDismissRequest()
                        onPickFiles()
                    }
                )
                ArrowPreference(
                    title = "选择文件夹",
                    summary = "递归添加整个文件夹内的所有文件",
                    onClick = {
                        onDismissRequest()
                        onPickFolder()
                    }
                )
                ArrowPreference(
                    title = "选择媒体",
                    summary = "从相册选择照片与视频",
                    onClick = {
                        onDismissRequest()
                        onPickMedia()
                    }
                )
                ArrowPreference(
                    title = "选择应用 (APK)",
                    summary = "提取本机已安装应用的 APK 文件",
                    onClick = {
                        onDismissRequest()
                        onPickApps()
                    }
                )
                ArrowPreference(
                    title = "输入纯文本",
                    summary = "输入需要发送的文字或链接",
                    onClick = {
                        onDismissRequest()
                        onSendText()
                    }
                )
                ArrowPreference(
                    title = "从剪贴板粘贴",
                    summary = "快速提取当前剪贴板文本或链接",
                    onClick = {
                        onDismissRequest()
                        onPasteClipboard()
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
