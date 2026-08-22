package org.localsend.miuix.model

import android.net.Uri
import java.io.File

/**
 * 接收文件的保存目标。
 * - [FileTarget]：直接写入本地文件路径（默认目录，无需存储权限）。
 * - [UriTarget]：写入用户通过 SAF 选择的自定义目录树。
 */
sealed class SaveTarget {
    data class FileTarget(val file: File) : SaveTarget()

    data class UriTarget(val treeUri: Uri) : SaveTarget()
}