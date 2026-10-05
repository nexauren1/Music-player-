package com.musicplayer.app

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val sha256: String = ""
) {
    fun isNewer(): Boolean = versionCode > BuildConfig.VERSION_CODE
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
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("Music Player " + info.versionName + " is available")
            .setContentText("Open Music Player to download the update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun showInstallFailure(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("Music Player update failed")
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
                val connection =
                    URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection

                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.optLong("versionCode"),
                    versionName = json.optString("versionName"),
                    apkUrl = json.optString("apkUrl"),
                    changelog = json.optString(
                        "changelog",
                        "Performance and stability improvements."
                    ),
                    sha256 = json.optString("sha256", "")
                )

                if (info.isNewer()) {
                    if (notify) NotificationHelper.showUpdateAvailable(context, info)
                    info
                } else {
                    null
                }
            }.getOrNull()
        }

    suspend fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "music-player-" + info.versionCode + ".apk")
        target.delete()
        val connection = URL(info.apkUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Update download failed: " + connection.responseCode)
        }

        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    if (total > 0) {
                        onProgress(((downloaded * 100) / total).toInt())
                    }
                }
            }
        }
        connection.disconnect()

        if (info.sha256.isNotBlank()) {
            val actual = sha256(target)
            check(actual.equals(info.sha256, ignoreCase = true)) {
                "Downloaded APK checksum does not match."
            }
        }

        onProgress(100)
        ApkInstaller.install(context, target)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

object ApkInstaller {
    private const val PREFS = "update_install"
    private const val PENDING_APK = "pending_apk"

    fun install(context: Context, apk: File) {
        if (!apk.exists() || apk.length() == 0L) {
            throw IllegalStateException("The downloaded APK is missing or empty.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PENDING_APK, apk.absolutePath)
                .apply()

            val settingsIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(settingsIntent)
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apk
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(installIntent)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PENDING_APK)
                .apply()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Android could not start the APK installer: " + (error.message ?: "unknown error")
            )
        }
    }

    fun resumeIfPending(context: Context) {
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PENDING_APK, null)
            ?: return

        val apk = File(path)
        if (apk.exists()) {
            install(context, apk)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PENDING_APK)
                .apply()
        }
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
