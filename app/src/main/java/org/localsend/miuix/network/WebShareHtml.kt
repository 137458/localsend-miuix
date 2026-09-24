package org.localsend.miuix.network

import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.model.ShareSession
import org.localsend.miuix.webshare.WebShareCopy

/**
 * Web Share 根页渲染：文件图标选择、HTML 转义与整页 HTML 构造。
 * 纯字符串逻辑，所需数据一律通过函数参数显式传入，不依赖 LocalSendServer 的实例状态。
 */
internal object WebShareHtmlRenderer {

    /** 简单的 HTML 转义，用于根页展示文件名，避免注入。 */
    private fun escapeHtml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun getWebFileSvgIcon(mimeType: String, fileName: String): String {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime == "application/vnd.android.package-archive" || lowerName.endsWith(".apk") || lowerName.endsWith(".xapk") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="8" width="16" height="12" rx="2"></rect><path d="M9 4v4"></path><path d="M15 4v4"></path><circle cx="9" cy="13" r="1" fill="currentColor"></circle><circle cx="15" cy="13" r="1" fill="currentColor"></circle></svg>"""

            lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") || lowerName.endsWith(".svg") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>"""

            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") || lowerName.endsWith(".avi") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"></rect><line x1="7" y1="2" x2="7" y2="22"></line><line x1="17" y1="2" x2="17" y2="22"></line><line x1="2" y1="12" x2="22" y2="12"></line><line x1="2" y1="7" x2="7" y2="7"></line><line x1="2" y1="17" x2="7" y2="17"></line><line x1="17" y1="17" x2="22" y2="17"></line><line x1="17" y1="7" x2="22" y2="7"></line></svg>"""

            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>"""

            lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("compressed") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path><line x1="12" y1="11" x2="12" y2="17"></line><line x1="9" y1="14" x2="15" y2="14"></line></svg>"""

            lowerMime.startsWith("text/") || lowerMime == "application/json" || lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") || lowerName.endsWith(".kt") || lowerName.endsWith(".java") || lowerName.endsWith(".py") || lowerName.endsWith(".js") ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>"""

            else ->
                """<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>"""
        }
    }

    internal fun isSameIp(ip1: String, ip2: String): Boolean {
        if (ip1 == ip2) return true
        val clean1 = ip1.removePrefix("::ffff:").removePrefix("/").trim()
        val clean2 = ip2.removePrefix("::ffff:").removePrefix("/").trim()
        if (clean1 == clean2) return true
        val loopbacks = setOf("127.0.0.1", "::1", "localhost", "0:0:0:0:0:0:0:1")
        if (clean1 in loopbacks && clean2 in loopbacks) return true
        return false
    }

    internal fun buildWebShareHtml(alias: String, session: ShareSession?, copy: WebShareCopy): String {
        val hasSessionFiles = session != null && session.files.isNotEmpty()
        val textItems = session?.files?.filter { it.isTextMessage && !it.textContent.isNullOrEmpty() } ?: emptyList()
        val binaryFiles = session?.files?.filterNot { it.isTextMessage && !it.textContent.isNullOrEmpty() } ?: emptyList()

        val textSectionHtml = if (textItems.isNotEmpty()) {
            val textCards = textItems.joinToString("") { textItem ->
                val escapedText = escapeHtml(textItem.textContent ?: "")
                """
                <div class="text-card">
                    <pre class="text-content" id="text-${textItem.id}">$escapedText</pre>
                    <button class="btn btn-sec" onclick="copyText('text-${textItem.id}')">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                        ${copy.copyText}
                    </button>
                </div>
                """.trimIndent()
            }
            """
            <div class="section-card">
                <div class="section-header">
                    <span class="section-title">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                        ${copy.sharedText}
                    </span>
                    <span class="section-tag">${copy.textCountLabel(textItems.size)}</span>
                </div>
                $textCards
            </div>
            """.trimIndent()
        } else ""

        val fileListHtml = if (binaryFiles.isNotEmpty()) {
            val rows = binaryFiles.joinToString("") { file ->
                val downloadUrl = "${LocalSendRoutes.DOWNLOAD}?sessionId=${session?.sessionId}&fileId=${file.id}"
                val iconSvg = getWebFileSvgIcon(file.mimeType, file.name)
                """
                <div class="file-item">
                    <div style="display:flex; align-items:center; gap:12px; max-width:70%;">
                        <div style="flex-shrink:0; display:flex; align-items:center;">
                            $iconSvg
                        </div>
                        <div class="file-details">
                            <span class="file-name">${escapeHtml(file.name)}</span>
                            <span class="file-meta">${file.formattedSize}</span>
                        </div>
                    </div>
                    <a class="btn btn-primary" href="$downloadUrl" download="${escapeHtml(file.name)}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                        ${copy.download}
                    </a>
                </div>
                """.trimIndent()
            }
            """
            <div class="section-card">
                <div class="section-header">
                    <span class="section-title">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
                        ${copy.sharedFiles}
                    </span>
                    <div style="display:inline-flex;align-items:center;gap:8px;">
                        ${if (binaryFiles.size > 1 && session != null) """<a href="${LocalSendRoutes.DOWNLOAD_ZIP}?sessionId=${session.sessionId}" class="btn-zip"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg> ${copy.zipAll}</a>""" else ""}
                        <span class="section-tag">${binaryFiles.size} ${copy.fileCountLabel}</span>
                    </div>
                </div>
                <div class="file-list">$rows</div>
            </div>
            """.trimIndent()
        } else ""

        val noShareHint = if (!hasSessionFiles) {
            """
            <div class="empty-hint">
                <p>${copy.emptyHint}</p>
            </div>
            """.trimIndent()
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="${copy.htmlLang}">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>${escapeHtml(copy.pageTitle(alias))}</title>
                <style>
                    :root {
                        --bg: #f4f5f8;
                        --card-bg: #ffffff;
                        --text-main: #111827;
                        --text-sub: #6b7280;
                        --primary: #007aff;
                        --primary-hover: #0062cc;
                        --primary-light: rgba(0, 122, 255, 0.08);
                        --border: #e5e7eb;
                        --card-border: rgba(0, 0, 0, 0.06);
                        --success: #34c759;
                        --danger: #ff3b30;
                        --radius-lg: 20px;
                        --radius-md: 12px;
                        --radius-sm: 8px;
                        --shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg: #0e0f12;
                            --card-bg: #18191e;
                            --text-main: #f3f4f6;
                            --text-sub: #9ca3af;
                            --primary: #0a84ff;
                            --primary-hover: #0071e3;
                            --primary-light: rgba(10, 132, 255, 0.15);
                            --border: #262833;
                            --card-border: rgba(255, 255, 255, 0.08);
                            --shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
                        }
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
                        background: var(--bg);
                        color: var(--text-main);
                        min-height: 100vh;
                        padding: 24px 16px 48px 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container { max-width: 580px; width: 100%; display: flex; flex-direction: column; gap: 16px; }
                    .header-card {
                        background: var(--card-bg);
                        border-radius: var(--radius-lg);
                        padding: 24px 20px;
                        text-align: center;
                        border: 1px solid var(--card-border);
                        box-shadow: var(--shadow);
                    }
                    .device-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        padding: 4px 12px;
                        background: var(--primary-light);
                        color: var(--primary);
                        font-size: 13px;
                        font-weight: 600;
                        border-radius: 20px;
                        margin-bottom: 8px;
                    }
                    .header-title { font-size: 20px; font-weight: 700; color: var(--text-main); margin-bottom: 4px; }
                    .header-sub { font-size: 13px; color: var(--text-sub); }
                    .empty-hint { text-align: center; padding: 12px; font-size: 13px; color: var(--text-sub); }
                    .section-card {
                        background: var(--card-bg);
                        border-radius: var(--radius-lg);
                        padding: 18px;
                        border: 1px solid var(--card-border);
                        box-shadow: var(--shadow);
                    }
                    .section-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 12px;
                        padding: 0 4px;
                    }
                    .section-title {
                        font-size: 15px;
                        font-weight: 600;
                        color: var(--text-main);
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .section-tag {
                        font-size: 12px;
                        background: var(--primary-light);
                        color: var(--primary);
                        padding: 2px 8px;
                        border-radius: 10px;
                        font-weight: 500;
                    }
                    .btn-zip {
                        font-size: 12px;
                        background: var(--primary);
                        color: #ffffff !important;
                        padding: 2px 10px;
                        border-radius: 12px;
                        font-weight: 600;
                        text-decoration: none;
                        display: inline-flex;
                        align-items: center;
                        gap: 4px;
                        transition: opacity 0.2s;
                    }
                    .btn-zip:hover { opacity: 0.88; }
                    .text-card { margin-bottom: 10px; }
                    .text-card:last-child { margin-bottom: 0; }
                    .text-content {
                        background: var(--bg);
                        border: 1px solid var(--border);
                        border-radius: var(--radius-md);
                        padding: 12px;
                        font-family: inherit;
                        font-size: 14px;
                        line-height: 1.5;
                        white-space: pre-wrap;
                        word-break: break-word;
                        max-height: 180px;
                        overflow-y: auto;
                        margin-bottom: 8px;
                    }
                    .file-list { display: flex; flex-direction: column; gap: 8px; }
                    .file-item {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 12px 14px;
                        background: var(--bg);
                        border-radius: var(--radius-md);
                        border: 1px solid var(--border);
                    }
                    .file-details { display: flex; flex-direction: column; max-width: 70%; }
                    .file-name { font-size: 14px; font-weight: 500; word-break: break-all; color: var(--text-main); }
                    .file-meta { font-size: 12px; color: var(--text-sub); margin-top: 2px; }
                    .btn {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        padding: 8px 16px;
                        border-radius: var(--radius-sm);
                        font-size: 13px;
                        font-weight: 600;
                        text-decoration: none;
                        border: none;
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }
                    .btn-primary { background: var(--primary); color: #fff; }
                    .btn-primary:hover { background: var(--primary-hover); transform: translateY(-1px); }
                    .btn-sec { background: var(--bg); color: var(--text-main); border: 1px solid var(--border); width: 100%; padding: 8px; }
                    .btn-sec:hover { background: var(--border); }
                    .upload-dropzone {
                        border: 2px dashed var(--primary);
                        background: var(--primary-light);
                        border-radius: var(--radius-md);
                        padding: 26px 16px;
                        text-align: center;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        user-select: none;
                    }
                    .upload-dropzone:hover {
                        border-color: var(--primary-hover);
                        background: rgba(0, 122, 255, 0.14);
                    }
                    .dropzone-icon { display: flex; justify-content: center; margin-bottom: 8px; }
                    .dropzone-text { font-size: 14px; font-weight: 600; color: var(--primary); margin-bottom: 4px; }
                    .dropzone-hint { font-size: 12px; color: var(--text-sub); }
                    .upload-status-box {
                        display: none;
                        margin-top: 12px;
                        padding: 12px;
                        background: var(--bg);
                        border-radius: var(--radius-md);
                        border: 1px solid var(--border);
                    }
                    .progress-bar-wrap {
                        width: 100%;
                        height: 6px;
                        background: var(--border);
                        border-radius: 3px;
                        overflow: hidden;
                        margin: 8px 0;
                    }
                    .progress-bar {
                        height: 100%;
                        width: 0%;
                        background: var(--primary);
                        border-radius: 3px;
                        transition: width 0.2s ease;
                    }
                    .status-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 12px;
                        color: var(--text-sub);
                    }
                    .drag-overlay {
                        position: fixed;
                        top: 0; left: 0; right: 0; bottom: 0;
                        background: rgba(0, 122, 255, 0.92);
                        backdrop-filter: blur(12px);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        color: #fff;
                        z-index: 9999;
                        opacity: 0;
                        pointer-events: none;
                        transition: opacity 0.2s ease;
                    }
                    .drag-overlay.active { opacity: 1; pointer-events: all; }
                    .drag-overlay-icon { display: flex; justify-content: center; margin-bottom: 16px; }
                    .drag-overlay-title { font-size: 20px; font-weight: 700; }
                    .toast {
                        position: fixed;
                        bottom: 28px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: rgba(20, 20, 24, 0.92);
                        backdrop-filter: blur(12px);
                        color: #fff;
                        padding: 10px 20px;
                        border-radius: 30px;
                        font-size: 13px;
                        font-weight: 500;
                        box-shadow: 0 6px 20px rgba(0,0,0,0.25);
                        opacity: 0;
                        transition: opacity 0.25s ease;
                        pointer-events: none;
                        z-index: 10000;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header-card">
                        <div class="device-badge">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.55a11 11 0 0 1 14.08 0"></path><path d="M1.42 9a16 16 0 0 1 21.16 0"></path><path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path><line x1="12" y1="20" x2="12.01" y2="20"></line></svg>
                            ${copy.lanOnline}
                        </div>
                        <div class="header-title">${escapeHtml(alias)}</div>
                        <div class="header-sub">${copy.headerSub}</div>
                    </div>
                    $noShareHint
                    $textSectionHtml
                    $fileListHtml
                    <div class="section-card">
                        <div class="section-header">
                            <span class="section-title">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                                ${copy.uploadToPhone}
                            </span>
                            <span class="section-tag">${copy.bidirectional}</span>
                        </div>
                        <div class="upload-dropzone" id="uploadDropzone">
                            <div class="dropzone-icon">
                                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                            </div>
                            <div class="dropzone-text">${copy.dropzoneText}</div>
                            <div class="dropzone-hint">${copy.dropzoneHint}</div>
                        </div>
                        <input type="file" id="fileInput" multiple style="display:none">
                        <div class="upload-status-box" id="uploadStatusBox">
                            <div class="status-row">
                                <span id="uploadStatusTitle" style="font-weight:600; color:var(--text-main)">${copy.preparingUpload}</span>
                                <span id="uploadStatusPercent">0%</span>
                            </div>
                            <div class="progress-bar-wrap">
                                <div class="progress-bar" id="uploadProgressBar"></div>
                            </div>
                            <div class="status-row">
                                <span id="uploadDetail">${copy.waiting}</span>
                                <span id="uploadCount">0/0</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="drag-overlay" id="dragOverlay">
                    <div class="drag-overlay-icon">
                        <svg width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                    </div>
                    <div class="drag-overlay-title">${copy.dropOverlay}</div>
                </div>

                <div id="toast" class="toast">${copy.copied}</div>

                <script>
                    var I18N = ${copy.toJsObject()};
                    function showToast(msg) {
                        var t = document.getElementById('toast');
                        t.innerText = msg;
                        t.style.opacity = '1';
                        setTimeout(function() { t.style.opacity = '0'; }, 2000);
                    }

                    function copyText(id) {
                        var el = document.getElementById(id);
                        if (!el) return;
                        navigator.clipboard.writeText(el.innerText).then(function() {
                            showToast(I18N.copied);
                        }).catch(function() {
                            showToast(I18N.copyFailed);
                        });
                    }

                    function formatSize(bytes) {
                        if (bytes < 1024) return bytes + ' B';
                        var i = Math.floor(Math.log(bytes) / Math.log(1024));
                        var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
                        return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
                    }

                    var isUploading = false;
                    var dragDepth = 0;
                    var currentPin = null;

                    function getFingerprint() {
                        var fp = sessionStorage.getItem('localsend_web_fp');
                        if (!fp) {
                            fp = 'web-' + Math.random().toString(36).substring(2) + Date.now().toString(36);
                            sessionStorage.setItem('localsend_web_fp', fp);
                        }
                        return fp;
                    }

                    var dropzone = document.getElementById('uploadDropzone');
                    var fileInput = document.getElementById('fileInput');
                    var statusBox = document.getElementById('uploadStatusBox');
                    var statusTitle = document.getElementById('uploadStatusTitle');
                    var statusPercent = document.getElementById('uploadStatusPercent');
                    var progressBar = document.getElementById('uploadProgressBar');
                    var detailText = document.getElementById('uploadDetail');
                    var countText = document.getElementById('uploadCount');
                    var overlay = document.getElementById('dragOverlay');

                    dropzone.onclick = function() {
                        if (!isUploading) fileInput.click();
                    };

                    fileInput.onchange = function() {
                        if (fileInput.files && fileInput.files.length > 0) {
                            startUpload(fileInput.files);
                            fileInput.value = '';
                        }
                    };

                    window.addEventListener('dragenter', function(e) {
                        if (!e.dataTransfer || !e.dataTransfer.types || e.dataTransfer.types.indexOf('Files') === -1) return;
                        e.preventDefault();
                        dragDepth++;
                        if (!isUploading) overlay.classList.add('active');
                    });

                    window.addEventListener('dragover', function(e) {
                        if (!e.dataTransfer || !e.dataTransfer.types || e.dataTransfer.types.indexOf('Files') === -1) return;
                        e.preventDefault();
                        if (isUploading) e.dataTransfer.dropEffect = 'none';
                    });

                    window.addEventListener('dragleave', function(e) {
                        dragDepth--;
                        if (dragDepth <= 0) {
                            dragDepth = 0;
                            overlay.classList.remove('active');
                        }
                    });

                    window.addEventListener('drop', function(e) {
                        e.preventDefault();
                        dragDepth = 0;
                        overlay.classList.remove('active');
                        if (isUploading) return;
                        var dt = e.dataTransfer;
                        var files = [];
                        if (dt && dt.items) {
                            for (var i = 0; i < dt.items.length; i++) {
                                var item = dt.items[i];
                                if (item.webkitGetAsEntry && item.webkitGetAsEntry().isDirectory) continue;
                                var f = item.getAsFile();
                                if (f) files.push(f);
                            }
                        } else if (dt && dt.files) {
                            for (var j = 0; j < dt.files.length; j++) files.push(dt.files[j]);
                        }
                        if (files.length > 0) startUpload(files);
                    });

                    function startUpload(fileList) {
                        isUploading = true;
                        statusBox.style.display = 'block';
                        statusTitle.innerText = I18N.waitingConfirm;
                        statusTitle.style.color = 'var(--text-main)';
                        statusPercent.innerText = '0%';
                        progressBar.style.width = '0%';
                        detailText.innerText = I18N.tapAccept;
                        countText.innerText = '0/' + fileList.length;

                        var filesMap = {};
                        var fileBlobs = {};
                        for (var i = 0; i < fileList.length; i++) {
                            var f = fileList[i];
                            var id = 'web-f-' + i + '-' + Date.now();
                            filesMap[id] = {
                                id: id,
                                fileName: f.name,
                                size: f.size,
                                fileType: f.type || 'application/octet-stream'
                            };
                            fileBlobs[id] = f;
                        }

                        var requestBody = {
                            info: {
                                alias: I18N.webAlias,
                                version: '2.1',
                                deviceModel: navigator.userAgent.indexOf('Mac') !== -1 ? 'Mac Browser' : (navigator.userAgent.indexOf('Windows') !== -1 ? 'PC Browser' : 'Web Client'),
                                deviceType: 'web',
                                fingerprint: getFingerprint(),
                                port: 0,
                                protocol: location.protocol.replace(':', ''),
                                download: false
                            },
                            files: filesMap
                        };

                        executePrepare(requestBody, fileBlobs, fileList.length, true);
                    }

                    function executePrepare(reqBody, fileBlobs, totalCount, isFirst) {
                        var url = '/api/localsend/v2/prepare-upload';
                        if (currentPin) url += '?pin=' + encodeURIComponent(currentPin);

                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', url, true);
                        xhr.setRequestHeader('Content-Type', 'application/json');
                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                try {
                                    var res = JSON.parse(xhr.responseText);
                                    uploadAllFiles(res.sessionId, res.files, fileBlobs, totalCount);
                                } catch(e) {
                                    finishError(I18N.parseFailed);
                                }
                            } else if (xhr.status === 204) {
                                finishSuccess();
                            } else if (xhr.status === 401) {
                                var pinPrompt = prompt(isFirst ? I18N.pinPrompt : I18N.pinRetry);
                                if (!pinPrompt) {
                                    finishError(I18N.pinMissing);
                                    return;
                                }
                                currentPin = pinPrompt;
                                executePrepare(reqBody, fileBlobs, totalCount, false);
                            } else if (xhr.status === 403) {
                                finishError(I18N.rejected);
                            } else if (xhr.status === 409) {
                                finishError(I18N.busy);
                            } else if (xhr.status === 429) {
                                finishError(I18N.tooMany);
                            } else {
                                finishError(I18N.handshakeFailed + ' (HTTP ' + xhr.status + ')');
                            }
                        };
                        xhr.onerror = function() {
                            finishError(I18N.networkFailed);
                        };
                        xhr.send(JSON.stringify(reqBody));
                    }

                    function uploadAllFiles(sessionId, tokens, fileBlobs, totalCount) {
                        var fileIds = Object.keys(tokens);
                        var completed = 0;

                        function uploadNext(index) {
                            if (index >= fileIds.length) {
                                statusTitle.innerText = I18N.uploadComplete;
                                statusTitle.style.color = 'var(--success)';
                                statusPercent.innerText = '100%';
                                progressBar.style.width = '100%';
                                detailText.innerText = I18N.allSaved;
                                countText.innerText = totalCount + '/' + totalCount;
                                showToast(I18N.allTransferred);
                                setTimeout(function() {
                                    isUploading = false;
                                    statusBox.style.display = 'none';
                                }, 3500);
                                return;
                            }

                            var fId = fileIds[index];
                            var blob = fileBlobs[fId];
                            var token = tokens[fId];
                            statusTitle.innerText = I18N.uploadingPrefix + blob.name;
                            countText.innerText = (index + 1) + '/' + totalCount;

                            var uploadUrl = '/api/localsend/v2/upload?sessionId=' + encodeURIComponent(sessionId) +
                                '&fileId=' + encodeURIComponent(fId) +
                                '&token=' + encodeURIComponent(token);

                            var xhr = new XMLHttpRequest();
                            xhr.open('POST', uploadUrl, true);
                            xhr.setRequestHeader('Content-Type', 'application/octet-stream');

                            xhr.upload.onprogress = function(e) {
                                if (e.lengthComputable) {
                                    var percent = Math.round((e.loaded / e.total) * 100);
                                    statusPercent.innerText = percent + '%';
                                    progressBar.style.width = percent + '%';
                                    detailText.innerText = formatSize(e.loaded) + ' / ' + formatSize(e.total);
                                }
                            };

                            xhr.onload = function() {
                                if (xhr.status === 200 || xhr.status === 204) {
                                    completed++;
                                    uploadNext(index + 1);
                                } else {
                                    finishError(I18N.fileFailed + ' ' + blob.name + ' (HTTP ' + xhr.status + ')');
                                }
                            };

                            xhr.onerror = function() {
                                finishError(I18N.fileInterrupted + ' ' + blob.name);
                            };

                            xhr.send(blob);
                        }

                        uploadNext(0);
                    }

                    function finishError(msg) {
                        isUploading = false;
                        statusTitle.innerText = I18N.uploadFailed;
                        statusTitle.style.color = 'var(--danger)';
                        detailText.innerText = msg;
                        showToast(msg);
                    }

                    function registerBrowserDevice() {
                        var fp = getFingerprint();
                        var model = I18N.modelWeb;
                        var ua = navigator.userAgent;
                        if (ua.indexOf('Mac') !== -1) model = I18N.modelMac;
                        else if (ua.indexOf('Windows') !== -1) model = I18N.modelPc;
                        else if (ua.indexOf('iPhone') !== -1) model = I18N.modelIphone;
                        else if (ua.indexOf('iPad') !== -1) model = I18N.modelIpad;
                        else if (ua.indexOf('Android') !== -1) model = I18N.modelAndroid;

                        var reqBody = {
                            alias: I18N.webAlias,
                            version: '2.1',
                            deviceModel: model,
                            deviceType: 'web',
                            fingerprint: fp,
                            port: 0,
                            protocol: location.protocol.replace(':', ''),
                            download: false
                        };
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/localsend/v2/register', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');
                        xhr.send(JSON.stringify(reqBody));
                    }
                    try { registerBrowserDevice(); } catch(e) {}
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}