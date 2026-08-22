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
fun IncomingTransferDialog(
    session: TransferSession?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    WindowDialog(
        show = session != null,
        title = "收到传输请求",
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
    onPickMedia: () -> Unit,
    onSendText: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    WindowBottomSheet(
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
                    title = "选择媒体",
                    summary = "从相册选择照片与视频",
                    onClick = {
                        onDismissRequest()
                        onPickMedia()
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
                    summary = "快速提取当前剪贴板内容",
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
