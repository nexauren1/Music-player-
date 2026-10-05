package com.musicplayer.app

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val sha256: String = "",
    val sizeBytes: Long = 0L
) {
    fun isNewer(): Boolean {
        if (versionCode > 0L) {
            return versionCode > BuildConfig.VERSION_CODE.toLong() ||
                (versionCode == BuildConfig.VERSION_CODE.toLong() && versionName != BuildConfig.VERSION_NAME)
        }

        fun parse(value: String) = value
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
            .let { listOf(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }, it.getOrElse(2) { 0 }) }

        val remote = parse(versionName)
        val local = parse(BuildConfig.VERSION_NAME)
        return when {
            remote[0] != local[0] -> remote[0] > local[0]
            remote[1] != local[1] -> remote[1] > local[1]
            else -> remote[2] > local[2]
        }
    }
}

object NotificationHelper {
    const val UPDATE_CHANNEL = "updates"
    const val PLAYBACK_CHANNEL = "playback"

    fun areNotificationsEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    private const val UPDATE_NOTIFICATION_ID = 2001
    private const val INSTALL_NOTIFICATION_ID = 2002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PLAYBACK_CHANNEL,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun showUpdateAvailable(context: Context, info: UpdateInfo) {
        I18n.language = AppearanceStore.load(context).language
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(I18n.t("Music Player ") + info.versionName + " " + I18n.t("is available"))
            .setContentText(I18n.t("Open Music Player to download the update."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun showInstallSuccess(context: Context, message: String) {
        I18n.language = AppearanceStore.load(context).language
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(I18n.t("Music Player update installed"))
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        manager(context).notify(INSTALL_NOTIFICATION_ID, notification)
    }

    fun showInstallFailure(context: Context, message: String) {
        I18n.language = AppearanceStore.load(context).language
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(I18n.t("Music Player update failed"))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager(context).notify(INSTALL_NOTIFICATION_ID, notification)
    }

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}

object UpdateManager {
    suspend fun check(context: Context, notify: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
                    .ifBlank {
                        "https://raw.githubusercontent.com/nexauren1/Music-player-/main/update.json"
                    }
                val separator = if (manifestUrl.contains("?")) "&" else "?"
                val connection =
                    URL(manifestUrl + separator + "t=" + System.currentTimeMillis())
                        .openConnection() as HttpURLConnection

                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-cache")

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IllegalStateException("Update manifest returned HTTP $responseCode.")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val manifestInfo = UpdateInfo(
                    versionCode = json.optLong("versionCode"),
                    versionName = json.optString("versionName"),
                    apkUrl = json.optString("apkUrl"),
                    changelog = json.optString(
                        "changelog",
                        "Performance and stability improvements."
                    ),
                    sha256 = json.optString("sha256", ""),
                    sizeBytes = json.optLong("sizeBytes", 0L)
                )

                val info = if (
                    manifestInfo.versionCode > 0L &&
                    manifestInfo.versionName.isNotBlank() &&
                    manifestInfo.apkUrl.isNotBlank()
                ) {
                    manifestInfo
                } else {
                    fetchLatestGithubRelease()
                }

                if (info?.isNewer() == true) {
                    if (notify) NotificationHelper.showUpdateAvailable(context, info)
                    info
                } else {
                    null
                }
            }.getOrElse {
                fetchLatestGithubRelease()?.takeIf { it.isNewer() }
            }
        }

    private suspend fun fetchLatestGithubRelease(): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(
                    "https://api.github.com/repos/nexauren1/Music-player-/releases/latest"
                ).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MusicPlayer/" + BuildConfig.VERSION_NAME)
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) error("GitHub release lookup returned HTTP $code.")

                val release = JSONObject(responseBody)
                val version = release.optString("tag_name").removePrefix("v")
                val assets = release.optJSONArray("assets")
                val apk = assets?.let { array ->
                    (0 until array.length())
                        .map { array.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "app-release.apk" }
                } ?: error("Release APK was not found.")

                UpdateInfo(
                    versionCode = 0L,
                    versionName = version,
                    apkUrl = apk.optString("browser_download_url"),
                    changelog = release.optString(
                        "body",
                        "Performance and stability improvements."
                    ),
                    sha256 = "",
                    sizeBytes = apk.optLong("size", 0L)
                )
            }.getOrNull()
        }

}

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        UpdateManager.check(applicationContext, notify = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "nexa_music_update_check"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
