package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val notes: String,
        val apkUrl: String
    ) : UpdateCheckResult()

    data class UpToDate(
        val currentVersion: String
    ) : UpdateCheckResult()

    data class Error(
        val message: String
    ) : UpdateCheckResult()
}

object UpdateChecker {

    const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/kubukubukuku-max/Anti_mager_coach/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_RESPONSE_CHARS = 512 * 1024

    fun getInstalledVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    suspend fun checkLatestRelease(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = getInstalledVersionName(context)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Anti-Mager-Coach-Android/$currentVersion")
            }

            when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val responseBody = readBoundedStream(connection)
                    parseReleaseJson(responseBody, currentVersion)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    UpdateCheckResult.Error("Belum ada rilis di GitHub")
                }
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> {
                    UpdateCheckResult.Error("Batas akses GitHub sementara tercapai. Coba lagi beberapa menit lagi.")
                }
                else -> {
                    UpdateCheckResult.Error("Gagal memeriksa pembaruan (kode $statusCode). Coba lagi nanti.")
                }
            }
        } catch (_: Exception) {
            UpdateCheckResult.Error("Gagal terhubung ke GitHub. Periksa koneksi internetmu lalu coba lagi.")
        } finally {
            connection?.disconnect()
        }
    }

    internal data class ReleaseAsset(
        val name: String,
        val downloadUrl: String
    )

    internal fun parseReleaseJson(rawJson: String, currentVersion: String): UpdateCheckResult {
        return try {
            val json = JSONObject(rawJson)
            val rawTag = if (json.isNull("tag_name")) "" else json.optString("tag_name", "")
            val rawBody = if (json.isNull("body")) "" else json.optString("body", "")
            val htmlUrl = if (json.isNull("html_url")) "" else json.optString("html_url", "")

            val assetList = mutableListOf<ReleaseAsset>()
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = if (asset.isNull("name")) "" else asset.optString("name", "")
                    val downloadUrl = if (asset.isNull("browser_download_url")) {
                        ""
                    } else {
                        asset.optString("browser_download_url", "")
                    }
                    assetList.add(ReleaseAsset(name = name, downloadUrl = downloadUrl))
                }
            }

            evaluateRelease(
                rawTag = rawTag,
                rawBody = rawBody,
                assets = assetList,
                htmlUrl = htmlUrl,
                currentVersion = currentVersion
            )
        } catch (_: Exception) {
            UpdateCheckResult.Error("Gagal membaca informasi pembaruan dari GitHub.")
        }
    }

    internal fun evaluateRelease(
        rawTag: String,
        rawBody: String,
        assets: List<ReleaseAsset>,
        htmlUrl: String,
        currentVersion: String
    ): UpdateCheckResult {
        val trimmedTag = rawTag.trim()
        if (trimmedTag.isEmpty()) {
            return UpdateCheckResult.Error("Data rilis GitHub tidak memiliki nomor versi.")
        }

        val latestVersion = normalizeVersion(trimmedTag)
        val normalizedCurrent = normalizeVersion(currentVersion)

        if (!isNewerVersion(latest = latestVersion, current = normalizedCurrent)) {
            return UpdateCheckResult.UpToDate(currentVersion = normalizedCurrent)
        }

        val notes = rawBody
            .trim()
            .ifEmpty { "Peningkatan stabilitas, performa, dan perbaikan ringan pada Anti-Mager Coach." }

        val apkDownloadUrl = assets.firstOrNull { asset ->
            val url = asset.downloadUrl.trim()
            val isApk = asset.name.endsWith(".apk", ignoreCase = true) ||
                url.endsWith(".apk", ignoreCase = true)
            isApk && isTrustedGitHubUrl(url)
        }?.downloadUrl?.trim()
            ?: htmlUrl.trim().takeIf { isTrustedGitHubUrl(it) }
            ?: return UpdateCheckResult.Error(
                "Versi $latestVersion tersedia, tetapi tautan file APK belum dilampirkan di rilis GitHub."
            )

        return UpdateCheckResult.UpdateAvailable(
            latestVersion = latestVersion,
            notes = notes,
            apkUrl = apkDownloadUrl
        )
    }

    internal fun normalizeVersion(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1).trim()
        } else {
            trimmed
        }
    }

    /**
     * Compares dot-separated semantic versions numerically (e.g., "1.10" > "1.2", "1.1" > "1.0").
     */
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = parseVersionNumbers(latest)
        val currentParts = parseVersionNumbers(current)
        if (latestParts.isEmpty() || currentParts.isEmpty()) {
            return latest != current && latest.isNotEmpty()
        }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    internal fun isTrustedGitHubUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            scheme == "https" && (
                host == "github.com" ||
                    host.endsWith(".github.com") ||
                    host == "githubusercontent.com" ||
                    host.endsWith(".githubusercontent.com")
                )
        } catch (_: Exception) {
            false
        }
    }

    private fun parseVersionNumbers(version: String): List<Int> {
        val core = normalizeVersion(version).substringBefore('-').substringBefore('+')
        return core.split('.').mapNotNull { part ->
            part.trim().toIntOrNull()
        }
    }

    private fun readBoundedStream(connection: HttpURLConnection): String {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
            val buffer = CharArray(4096)
            var totalRead = 0
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > MAX_RESPONSE_CHARS) break
                sb.append(buffer, 0, read)
            }
        }
        return sb.toString()
    }
}
