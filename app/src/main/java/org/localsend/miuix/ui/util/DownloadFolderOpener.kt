package org.localsend.miuix.ui.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import org.localsend.miuix.R

/**
 * 打开下载目录：优先直达用户在设置里选定的保存目录，失败时回落到系统下载管理器，
 * 两者都打不开才提示无法打开。
 */
fun openDownloadFolder(
    context: Context,
    downloadTreeUri: String?,
) {
    val targets =
        buildList {
            if (!downloadTreeUri.isNullOrEmpty()) {
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(downloadTreeUri), DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
            add(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    val opened =
        targets.any { intent ->
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.toast_cannot_open_folder), Toast.LENGTH_SHORT).show()
    }
}
