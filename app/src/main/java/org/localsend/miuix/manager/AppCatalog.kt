package org.localsend.miuix.manager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.localsend.miuix.model.FileItem
import java.io.File

/** 本机已安装应用 (APK) 目录查询，带内存缓存。 */
internal class AppCatalog(
    private val context: Context,
) {
    private var cachedApps: List<AppInfoItem>? = null
    private val loadMutex = Mutex()

    /**
     * 提取本机已安装的应用 (APK) 列表。
     * - 支持二级内存缓存（forceRefresh = false 时 0ms 秒开）
     * - 单次 IPC 批量获取 PackageInfo，消除数百次跨进程 Binder 往返
     * - 结合 CPU 核心数进行协程分块并发解析 (loadLabel 与 apkSize)
     */
    suspend fun get(forceRefresh: Boolean = false): List<AppInfoItem> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                cachedApps?.let { return@withContext it }
            }
            loadMutex.withLock {
                if (!forceRefresh) {
                    cachedApps?.let { return@withLock it }
                }
                val loaded = load()
                cachedApps = loaded
                loaded
            }
        }

    private suspend fun load(): List<AppInfoItem> =
        coroutineScope {
            val pm = context.packageManager
            val packages =
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                    } else {
                        pm.getInstalledPackages(0)
                    }
                } catch (e: Exception) {
                    emptyList()
                }

            if (packages.isEmpty()) return@coroutineScope emptyList()

            val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val chunkSize = (packages.size / availableCores).coerceAtLeast(16)

            packages
                .chunked(chunkSize)
                .map { chunk ->
                    async(Dispatchers.IO) {
                        chunk.mapNotNull { pkgInfo ->
                            try {
                                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                                val file = File(sourceDir)
                                val size = file.length()
                                if (size <= 0L) return@mapNotNull null

                                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                val label =
                                    try {
                                        appInfo.loadLabel(pm).toString()
                                    } catch (_: Exception) {
                                        pkgInfo.packageName
                                    }.ifBlank { pkgInfo.packageName }

                                AppInfoItem(
                                    label = label,
                                    packageName = pkgInfo.packageName,
                                    versionName = pkgInfo.versionName ?: "1.0",
                                    sourceDir = sourceDir,
                                    apkSize = size,
                                    isSystemApp = isSystem,
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll()
                .flatten()
                .sortedWith(compareBy({ it.isSystemApp }, { it.label.lowercase() }))
        }
}

/** 将选中的已安装应用作为 APK 文件项映射到待发送列表。 */
internal fun appInfoToFileItems(apps: List<AppInfoItem>): List<FileItem> =
    apps.map { app ->
        val cleanName = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        FileItem(
            name = "$cleanName.apk",
            size = app.apkSize,
            path = app.sourceDir,
            mimeType = "application/vnd.android.package-archive",
        )
    }
