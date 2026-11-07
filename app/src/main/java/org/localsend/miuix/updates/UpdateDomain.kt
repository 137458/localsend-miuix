package org.localsend.miuix.updates

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * 进程级更新域：downloader 常驻，弹窗与更新页共用；
 * promptedVersion 只活在内存里，「稍后」不会变成永久静默。
 */
class UpdateDomain(
    context: Context,
    appScope: CoroutineScope,
) {
    @Volatile
    var promptedVersion: String? = null

    val downloader = UpdateDownloader(context.applicationContext, appScope)
}
