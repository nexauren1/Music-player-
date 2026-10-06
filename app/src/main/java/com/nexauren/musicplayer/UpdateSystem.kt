package com.musicplayer.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
            val current = BuildConfig.VERSION_CODE.toLong()
            if (versionCode != current) return versionCode > current
        }

        fun parse(value: String) = value
            .removePrefix("v")
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
    private const val UPDATE_NOTIFICATION_ID = 2001
    private const val DOWNLOAD_NOTIFICATION_ID = 2003
    private const val INSTALL_NOTIFICATION_ID = 2002
    private const val PREFS = "update_notifications"

    fun areNotificationsEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notificationKey = info.versionName + ":" + info.versionCode
        if (prefs.getString("last_notified", "") == notificationKey) return
        prefs.edit().putString("last_notified", notificationKey).apply()

        val intent = Intent(context, UpdateDownloadReceiver::class.java).apply {
            putExtra(UpdateDownloadReceiver.EXTRA_URL, info.apkUrl)
            putExtra(UpdateDownloadReceiver.EXTRA_VERSION, info.versionName)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = I18n.t("Music Player ") + info.versionName + " " + I18n.t("is available")
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(I18n.t("Tap to download the APK directly."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(UPDATE_NOTIFICATION_ID, notification)
    }


    fun showDownloadProgress(context: Context, version: String, percent: Int) {
        I18n.language = AppearanceStore.load(context).language
        context.getSystemService(NotificationManager::class.java).notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(I18n.t("Downloading Music Player update"))
                .setContentText("$version • $percent%")
                .setProgress(100, percent.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    fun showDownloadReady(context: Context, version: String, uri: android.net.Uri) {
        I18n.language = AppearanceStore.load(context).language
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context, DOWNLOAD_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(NotificationManager::class.java).notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(I18n.t("Update downloaded"))
                .setContentText(I18n.t("Tap to install") + " • " + version)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }

    fun showDownloadFailure(context: Context, message: String) {
        I18n.language = AppearanceStore.load(context).language
        context.getSystemService(NotificationManager::class.java).notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(I18n.t("Update download failed"))
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        )
    }

    fun clearUpdateNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(UPDATE_NOTIFICATION_ID)
    }

    fun showInstallSuccess(context: Context, message: String) {
        I18n.language = AppearanceStore.load(context).language
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(I18n.t("Music Player update installed"))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(INSTALL_NOTIFICATION_ID, notification)
    }

    fun showInstallFailure(context: Context, message: String) {
        I18n.language = AppearanceStore.load(context).language
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(I18n.t("Music Player update failed"))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(INSTALL_NOTIFICATION_ID, notification)
    }
}

object UpdateManager {
    fun enqueueDownload(context: Context, info: UpdateInfo): Long =
        enqueueDownload(context, info.apkUrl, info.versionName)

    fun enqueueDownload(context: Context, url: String, versionName: String): Long {
        require(url.isNotBlank()) { "APK download URL is empty." }
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "latest" }
        val request = DownloadManager.Request(url.toUri())
            .setTitle("Music Player $safeVersion")
            .setDescription("Downloading APK update")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "music-player-$safeVersion.apk")
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    private const val MANIFEST_FALLBACK =
        "https://raw.githubusercontent.com/nexauren1/Music-player-/main/update.json"

    suspend fun check(context: Context, notify: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val manifest = fetchManifest()
            val release = fetchLatestGithubRelease()
            val candidates = listOfNotNull(manifest, release)
            val info = candidates.firstOrNull { it.isNewer() }
            if (info != null && notify) {
                NotificationHelper.showUpdateAvailable(context, info)
            }
            info
        }

    private fun fetchManifest(): UpdateInfo? =
        runCatching {
            val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim().ifBlank { MANIFEST_FALLBACK }
            val separator = if (manifestUrl.contains("?")) "&" else "?"
            val connection = (URL(manifestUrl + separator + "t=" + System.currentTimeMillis())
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }

            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            connection.disconnect()
            if (code !in 200..299) error("Update manifest returned HTTP $code.")

            val json = JSONObject(body)
            UpdateInfo(
                versionCode = json.optLong("versionCode"),
                versionName = json.optString("versionName"),
                apkUrl = json.optString("apkUrl"),
                changelog = json.optString("changelog", "Performance and stability improvements."),
                sha256 = json.optString("sha256", ""),
                sizeBytes = json.optLong("sizeBytes", 0L)
            ).takeIf {
                it.versionName.isNotBlank() && it.apkUrl.isNotBlank()
            }
        }.getOrNull()

    private fun fetchLatestGithubRelease(): UpdateInfo? =
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
                setRequestProperty("Cache-Control", "no-cache")
            }

            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            connection.disconnect()
            if (code !in 200..299) error("GitHub release lookup returned HTTP $code.")

            val release = JSONObject(body)
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
                changelog = release.optString("body", "Performance and stability improvements."),
                sizeBytes = apk.optLong("size", 0L)
            )
        }.getOrNull()
}

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            UpdateManager.check(applicationContext, notify = true)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "nexa_music_update_check_v2"
        private const val IMMEDIATE_NAME = "nexa_music_update_check_now"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
