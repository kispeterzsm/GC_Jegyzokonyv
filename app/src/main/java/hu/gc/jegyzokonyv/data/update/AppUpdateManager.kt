package hu.gc.jegyzokonyv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.gc.jegyzokonyv.BuildConfig
import hu.gc.jegyzokonyv.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(ioDispatcher) {
        val release = fetchLatestRelease()
        val latestVersion = release.versionName
        if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    suspend fun downloadUpdate(release: GithubRelease): File = withContext(ioDispatcher) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "app-debug-${release.versionName}.apk")
        downloadFile(release.apkDownloadUrl, apkFile)
        apkFile
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun createInstallPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchLatestRelease(): GithubRelease {
        val connection = (URL(GITHUB_LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GC-Jegyzokonyv/${BuildConfig.VERSION_NAME}")
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        return connection.useJsonResponse { json ->
            val tagName = json.getString("tag_name")
            val versionName = tagName.removePrefix("v").removeSuffix("-debug")
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == APK_ASSET_NAME) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            GithubRelease(
                tagName = tagName,
                versionName = versionName,
                apkDownloadUrl = apkUrl ?: error("No $APK_ASSET_NAME asset found on the latest GitHub release."),
            )
        }
    }

    private fun downloadFile(url: String, output: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GC-Jegyzokonyv/${BuildConfig.VERSION_NAME}")
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        if (connection.responseCode !in 200..299) {
            error("GitHub download failed with HTTP ${connection.responseCode}.")
        }
        connection.inputStream.use { input ->
            output.outputStream().use { outputStream -> input.copyTo(outputStream) }
        }
    }

    private fun HttpURLConnection.useJsonResponse(block: (JSONObject) -> GithubRelease): GithubRelease {
        try {
            if (responseCode !in 200..299) {
                error("GitHub update check failed with HTTP $responseCode.")
            }
            val body = inputStream.bufferedReader().use { it.readText() }
            return block(JSONObject(body))
        } finally {
            disconnect()
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxSize) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private companion object {
        const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/kispeterzsm/GC_Jegyzokonyv/releases/latest"
        const val APK_ASSET_NAME = "app-debug.apk"
    }
}

data class GithubRelease(
    val tagName: String,
    val versionName: String,
    val apkDownloadUrl: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val release: GithubRelease) : UpdateCheckResult
}
