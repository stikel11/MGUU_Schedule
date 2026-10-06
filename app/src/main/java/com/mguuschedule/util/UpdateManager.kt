package com.mguuschedule.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class GitHubRelease(
    val versionName: String,
    val downloadUrl: String,
    val body: String
)

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/stikel11/MGUU_Schedule/releases/latest"

    suspend fun checkUpdate(): Result<GitHubRelease?> = withContext(Dispatchers.IO) {
        try {
            val response = Jsoup.connect(GITHUB_API_URL)
                .ignoreContentType(true)
                .timeout(10000)
                .get()
                .body()
                .text()

            val json = JsonParser.parseString(response).asJsonObject
            val tagName = json.get("tag_name")?.asString ?: ""
            val body = json.get("body")?.asString ?: ""
            val assets = json.getAsJsonArray("assets")

            val version = tagName.replace(Regex("[^0-9.]"), "")
            
            var downloadUrl = ""
            for (asset in assets) {
                val assetObj = asset.asJsonObject
                val name = assetObj.get("name")?.asString ?: ""
                if (name.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                    break
                }
            }

            if (version.isNotBlank() && downloadUrl.isNotBlank()) {
                Result.success(GitHubRelease(version, downloadUrl, body))
            } else {
                Result.success(null) // No valid release found
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstall(context: Context, url: String, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "update.apk")
            if (file.exists()) file.delete()

            val connection = URL(url).openConnection()
            connection.connect()
            val fileLength = connection.contentLength

            val input = connection.getInputStream()
            val output = FileOutputStream(file)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            var lastProgress = 0

            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    if (progress > lastProgress) {
                        withContext(Dispatchers.Main) { onProgress(progress) }
                        lastProgress = progress
                    }
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("UPDATE", "Ошибка установки APK: ${e.message}", e)
        }
    }

    fun isNewerVersion(current: String, fetched: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val fetchedParts = fetched.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, fetchedParts.size)
        for (i in 0 until length) {
            val c = currentParts.getOrElse(i) { 0 }
            val f = fetchedParts.getOrElse(i) { 0 }
            if (f > c) return true
            if (f < c) return false
        }
        return false
    }
}
