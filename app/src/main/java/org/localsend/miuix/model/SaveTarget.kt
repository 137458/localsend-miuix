package org.localsend.miuix.model

import android.net.Uri

/**
 * 接收文件的保存目标。
 * - [MediaStoreTarget]：默认保存到公共 Download 目录（通过 MediaStore 写入，Android 10+ 免存储权限）。
 * - [UriTarget]：写入用户通过 SAF 选择的自定义目录树。
 */
sealed class SaveTarget {
    data object MediaStoreTarget : SaveTarget()

    data class UriTarget(val treeUri: Uri) : SaveTarget()
}