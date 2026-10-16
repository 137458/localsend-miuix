package org.localsend.miuix.webshare

/**
 * User-visible copy for the Web Share HTML page (protocol §5.1).
 * Selected from the browser Accept-Language header; does not change REST routes.
 */
data class WebShareCopy(
    val htmlLang: String,
    val pageTitleSuffix: String,
    val lanOnline: String,
    val headerSub: String,
    val copyText: String,
    val sharedText: String,
    val textCount: String,
    val download: String,
    val sharedFiles: String,
    val zipAll: String,
    val fileCount: String,
    val emptyHint: String,
    val uploadToPhone: String,
    val bidirectional: String,
    val dropzoneText: String,
    val dropzoneHint: String,
    val preparingUpload: String,
    val waiting: String,
    val dropOverlay: String,
    val copied: String,
    val copyFailed: String,
    val waitingConfirm: String,
    val tapAccept: String,
    val parseFailed: String,
    val pinPrompt: String,
    val pinRetry: String,
    val pinMissing: String,
    val rejected: String,
    val busy: String,
    val tooMany: String,
    val handshakeFailed: String,
    val networkFailed: String,
    val uploadComplete: String,
    val allSaved: String,
    val allTransferred: String,
    val uploadingPrefix: String,
    val fileFailed: String,
    val fileInterrupted: String,
    val uploadFailed: String,
    val webAlias: String,
    val modelWeb: String,
    val modelMac: String,
    val modelPc: String,
    val modelIphone: String,
    val modelIpad: String,
    val modelAndroid: String,
    val modelLinux: String,
    val sendFileTab: String = "",
    val sendTextTab: String = "",
    val textInputPlaceholder: String = "",
    val sendTextBtn: String = "",
    val emptyTextWarning: String = "",
    val preview: String = "",
    val close: String = "",
    val cancel: String = "",
    val transferCanceled: String = "",
    val contentUpdated: String = "",
    val refresh: String = "",
    val pastedImageUploading: String = "",
) {
    fun pageTitle(alias: String): String = "$alias - $pageTitleSuffix"

    fun textCountLabel(count: Int): String = "$count $textCount"

    val fileCountLabel: String get() = fileCount

    fun webAliasFor(ip: String): String = "$webAlias ($ip)"

    fun toJsObject(): String =
        jsMap().entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"$key\":${jsonEscape(value)}"
        }

    fun jsMap(): Map<String, String> =
        mapOf(
            "copied" to copied,
            "copyFailed" to copyFailed,
            "waitingConfirm" to waitingConfirm,
            "tapAccept" to tapAccept,
            "parseFailed" to parseFailed,
            "pinPrompt" to pinPrompt,
            "pinRetry" to pinRetry,
            "pinMissing" to pinMissing,
            "rejected" to rejected,
            "busy" to busy,
            "tooMany" to tooMany,
            "handshakeFailed" to handshakeFailed,
            "networkFailed" to networkFailed,
            "uploadComplete" to uploadComplete,
            "allSaved" to allSaved,
            "allTransferred" to allTransferred,
            "uploadingPrefix" to uploadingPrefix,
            "fileFailed" to fileFailed,
            "fileInterrupted" to fileInterrupted,
            "uploadFailed" to uploadFailed,
            "webAlias" to webAlias,
            "modelWeb" to modelWeb,
            "modelMac" to modelMac,
            "modelPc" to modelPc,
            "modelIphone" to modelIphone,
            "modelIpad" to modelIpad,
            "modelAndroid" to modelAndroid,
            "sendFileTab" to sendFileTab,
            "sendTextTab" to sendTextTab,
            "textInputPlaceholder" to textInputPlaceholder,
            "sendTextBtn" to sendTextBtn,
            "emptyTextWarning" to emptyTextWarning,
            "preview" to preview,
            "close" to close,
            "cancel" to cancel,
            "transferCanceled" to transferCanceled,
            "contentUpdated" to contentUpdated,
            "refresh" to refresh,
            "pastedImageUploading" to pastedImageUploading,
        )

    companion object {
        val Chinese =
            WebShareCopy(
                htmlLang = "zh-CN",
                pageTitleSuffix = "LocalSend 局域网快传",
                lanOnline = "局域网在线",
                headerSub = "通过局域网高速安全传输，无需外网连接",
                copyText = "复制文本",
                sharedText = "共享文本",
                textCount = "条",
                download = "下载",
                sharedFiles = "共享文件",
                zipAll = "打包下载全部 (.zip)",
                fileCount = "个",
                emptyHint = "当前发送端未添加共享内容，但您可以直接向手机上传文件或发送文本",
                uploadToPhone = "传输到手机",
                bidirectional = "双向快传",
                dropzoneText = "点击选择文件 或 拖拽文件到此处",
                dropzoneHint = "支持任意格式文件与多文件同时上传（支持 Ctrl+V 粘贴截图）",
                preparingUpload = "准备传输...",
                waiting = "等待中...",
                dropOverlay = "松开鼠标即可上传至手机",
                copied = "已复制到剪贴板",
                copyFailed = "复制失败，请手动选择复制",
                waitingConfirm = "正在等待手机端确认...",
                tapAccept = "请在手机上点击同意接收",
                parseFailed = "解析响应失败",
                pinPrompt = "该设备启用了 PIN 码保护，请输入 PIN 码：",
                pinRetry = "PIN 码错误，请重新输入：",
                pinMissing = "未提供 PIN 码，上传已终止",
                rejected = "手机端拒绝了此次接收请求",
                busy = "手机端正在处理其他传输，请稍后再试",
                tooMany = "请求过于频繁，请稍后再试",
                handshakeFailed = "上传握手失败",
                networkFailed = "网络连接失败，请检查局域网连接",
                uploadComplete = "传输完成",
                allSaved = "所有内容已成功保存到手机",
                allTransferred = "所有内容已成功传输至手机",
                uploadingPrefix = "正在传输: ",
                fileFailed = "上传文件失败",
                fileInterrupted = "上传文件时网络中断",
                uploadFailed = "传输失败",
                webAlias = "浏览器 Web 端",
                modelWeb = "Web 浏览器",
                modelMac = "Mac 浏览器",
                modelPc = "PC 浏览器",
                modelIphone = "iPhone 浏览器",
                modelIpad = "iPad 浏览器",
                modelAndroid = "Android 浏览器",
                modelLinux = "Linux 浏览器",
                sendFileTab = "上传文件",
                sendTextTab = "发送文本",
                textInputPlaceholder = "在此输入或粘贴文本、链接，即可快速发送至手机...",
                sendTextBtn = "发送到手机",
                emptyTextWarning = "请输入要发送的内容",
                preview = "预览",
                close = "关闭",
                cancel = "取消",
                transferCanceled = "传输已取消",
                contentUpdated = "手机端共享内容已更新",
                refresh = "刷新",
                pastedImageUploading = "检测到剪贴板图片，正在上传...",
            )

        val English =
            WebShareCopy(
                htmlLang = "en",
                pageTitleSuffix = "LocalSend LAN Transfer",
                lanOnline = "Online on LAN",
                headerSub = "Fast, secure transfer over the local network. No internet required.",
                copyText = "Copy text",
                sharedText = "Shared text",
                textCount = "item(s)",
                download = "Download",
                sharedFiles = "Shared files",
                zipAll = "Download all as .zip",
                fileCount = "file(s)",
                emptyHint = "Nothing is shared yet. You can still send files or text to this phone.",
                uploadToPhone = "Send to Phone",
                bidirectional = "Two-way",
                dropzoneText = "Click to choose files or drop them here",
                dropzoneHint = "Any file type. Multiple files supported (Ctrl+V to paste screenshots).",
                preparingUpload = "Preparing transfer...",
                waiting = "Waiting...",
                dropOverlay = "Release to upload to this phone",
                copied = "Copied to clipboard",
                copyFailed = "Copy failed. Select the text and copy manually.",
                waitingConfirm = "Waiting for the phone to accept...",
                tapAccept = "Tap Accept on the phone",
                parseFailed = "Failed to parse the response",
                pinPrompt = "This device is PIN protected. Enter the PIN:",
                pinRetry = "Incorrect PIN. Try again:",
                pinMissing = "No PIN provided. Upload cancelled.",
                rejected = "The phone declined this transfer",
                busy = "The phone is busy with another transfer. Try again later.",
                tooMany = "Too many requests. Try again later.",
                handshakeFailed = "Upload handshake failed",
                networkFailed = "Network error. Check the LAN connection.",
                uploadComplete = "Transfer complete",
                allSaved = "All items were saved to the phone",
                allTransferred = "All items transferred to the phone",
                uploadingPrefix = "Transferring: ",
                fileFailed = "Failed to upload file",
                fileInterrupted = "Upload interrupted",
                uploadFailed = "Transfer failed",
                webAlias = "Browser",
                modelWeb = "Web browser",
                modelMac = "Mac browser",
                modelPc = "PC browser",
                modelIphone = "iPhone browser",
                modelIpad = "iPad browser",
                modelAndroid = "Android browser",
                modelLinux = "Linux browser",
                sendFileTab = "Upload Files",
                sendTextTab = "Send Text",
                textInputPlaceholder = "Type or paste text or links here to send to phone...",
                sendTextBtn = "Send to Phone",
                emptyTextWarning = "Please enter text to send",
                preview = "Preview",
                close = "Close",
                cancel = "Cancel",
                transferCanceled = "Transfer cancelled",
                contentUpdated = "Shared content updated on phone",
                refresh = "Refresh",
                pastedImageUploading = "Clipboard image detected, uploading...",
            )

        fun fromAcceptLanguage(header: String?): WebShareCopy = if (prefersEnglish(header)) English else Chinese

        fun prefersEnglish(header: String?): Boolean {
            if (header.isNullOrBlank()) return false
            var bestEn = 0.0
            var bestZh = 0.0
            for (part in header.split(',')) {
                val segs = part.trim().split(';')
                if (segs.isEmpty()) continue
                val tag = segs[0].trim().lowercase()
                val q =
                    segs
                        .drop(1)
                        .firstOrNull { it.trim().startsWith("q=") }
                        ?.substringAfter("q=")
                        ?.toDoubleOrNull()
                        ?: 1.0
                when {
                    tag.startsWith("zh") -> bestZh = maxOf(bestZh, q)
                    tag.startsWith("en") -> bestEn = maxOf(bestEn, q)
                }
            }
            return bestEn > bestZh
        }

        private fun jsonEscape(value: String): String =
            buildString {
                append('"')
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        else -> append(ch)
                    }
                }
                append('"')
            }

        fun browserModel(
            userAgent: String,
            copy: WebShareCopy = Chinese,
        ): String =
            when {
                userAgent.contains("Macintosh") || userAgent.contains("Mac OS") -> copy.modelMac
                userAgent.contains("Windows") -> copy.modelPc
                userAgent.contains("iPhone") -> copy.modelIphone
                userAgent.contains("iPad") -> copy.modelIpad
                userAgent.contains("Android") -> copy.modelAndroid
                userAgent.contains("Linux") -> copy.modelLinux
                else -> copy.modelWeb
            }
    }
}
