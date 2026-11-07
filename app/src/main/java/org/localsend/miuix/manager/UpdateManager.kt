package org.localsend.miuix.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.localsend.miuix.BuildConfig
import org.localsend.miuix.R
import org.localsend.miuix.updates.UpdateAsset
import org.localsend.miuix.updates.UpdatePrompt
import java.io.File

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String = "",
    // GitHub Release API 在 2025 年后为资产提供 "sha256:<hex>" 摘要；缺失时跳过校验
    val digest: String? = null,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val releaseUrl: String,
    val apk: UpdateAsset?,
    val hasUpdate: Boolean,
)

class UpdateManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "UpdateManager"
        const val GITHUB_OWNER = "137458"
        const val GITHUB_REPO = "localsend-miuix"
        const val API_LATEST_RELEASE = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }

    suspend fun checkForUpdate(): Result<UpdateCheckResult> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get(API_LATEST_RELEASE) {
                        header("Accept", "application/vnd.github+json")
                        header("User-Agent", "LocalSend-Miuix-App")
                    }

                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception(context.getString(org.localsend.miuix.R.string.msg_github_api_failed, response.status.value)),
                    )
                }

                val bodyText = response.bodyAsText()
                val release = json.decodeFromString<GithubRelease>(bodyText)

                val remoteVersion = UpdatePrompt.normalized(release.tagName)
                val currentVersion = UpdatePrompt.normalized(BuildConfig.VERSION_NAME)

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val apk =
                    apkAsset?.let {
                        UpdateAsset(
                            name = it.name,
                            size = it.size,
                            downloadUrl = it.browserDownloadUrl,
                            digest = it.digest,
                        )
                    }

                val hasUpdate = UpdatePrompt.compare(remoteVersion, currentVersion) > 0

                Result.success(
                    UpdateCheckResult(
                        currentVersion = currentVersion,
                        latestVersion = remoteVersion,
                        releaseTitle = release.name ?: release.tagName,
                        changelog = release.body.orEmpty(),
                        publishedAt = release.publishedAt?.take(10).orEmpty(),
                        releaseUrl = release.htmlUrl ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases",
                        apk = apk,
                        hasUpdate = hasUpdate,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun installApk(
        context: Context,
        apkFile: File,
    ) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                context.startActivity(settingsIntent)
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "installApk: failed to launch package installer: ${e.message}", e)
            try {
                android.widget.Toast
                    .makeText(context, context.getString(R.string.update_install_failed), android.widget.Toast.LENGTH_SHORT)
                    .show()
            } catch (_: Exception) {
            }
        }
    }

    fun openInBrowser(
        context: Context,
        url: String,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show()
        }
    }
}
