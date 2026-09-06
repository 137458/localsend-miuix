package org.localsend.miuix.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.localsend.miuix.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GithubAsset> = emptyList()
)

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val apkSize: Long,
    val hasUpdate: Boolean
)

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState
    data class Completed(val file: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

class UpdateManager(private val context: Context) {

    companion object {
        const val GITHUB_OWNER = "137458"
        const val GITHUB_REPO = "localsend-miuix"
        const val API_LATEST_RELEASE = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun checkForUpdate(): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(API_LATEST_RELEASE) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "LocalSend-Miuix-App")
            }

            if (!response.status.isSuccess()) {
                return@withContext Result.failure(Exception("GitHub API 请求失败: HTTP ${response.status.value}"))
            }

            val bodyText = response.bodyAsText()
            val release = json.decodeFromString<GithubRelease>(bodyText)

            val remoteVersion = release.tagName.trim().removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val downloadUrl = apkAsset?.browserDownloadUrl
            val apkSize = apkAsset?.size ?: 0L

            val hasUpdate = compareVersions(remoteVersion, currentVersion) > 0

            Result.success(
                UpdateCheckResult(
                    currentVersion = "v$currentVersion",
                    latestVersion = "v$remoteVersion",
                    releaseTitle = release.name ?: release.tagName,
                    changelog = release.body.orEmpty(),
                    publishedAt = release.publishedAt?.take(10).orEmpty(),
                    releaseUrl = release.htmlUrl ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases",
                    downloadUrl = downloadUrl,
                    apkSize = apkSize,
                    hasUpdate = hasUpdate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 版本号比较：v1 > v2 返回 1，v1 < v2 返回 -1，相等返回 0
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var apkFile: File? = null
        try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val targetFile = File(cacheDir, "localsend-update.apk")
            apkFile = targetFile
            if (targetFile.exists()) targetFile.delete()

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "LocalSend-Miuix-App")
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong.takeIf { it > 0 } ?: 0L
            } else {
                connection.contentLength.toLong().takeIf { it > 0 } ?: 0L
            }
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)
        } catch (e: CancellationException) {
            apkFile?.delete()
            throw e
        } catch (e: Exception) {
            apkFile?.delete()
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openInBrowser(context, "file://${apkFile.absolutePath}")
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
