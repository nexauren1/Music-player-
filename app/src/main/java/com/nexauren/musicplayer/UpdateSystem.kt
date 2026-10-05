package com.nexauren.musicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
            .setContentTitle("Nexa Music " + info.versionName + " is available")
            .setContentText("Open Nexa Music to download the update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun showInstallFailure(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("Nexa Music update failed")
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
        val target = File(context.cacheDir, "nexa-music-" + info.versionCode + ".apk")
        val connection = URL(info.apkUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
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
    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(settingsIntent)
            return
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.use { packageSession ->
            FileInputStream(apk).use { input: FileInputStream ->
                packageSession.openWrite("base.apk", 0, apk.length()).use { output: java.io.OutputStream ->
                    input.copyTo(output)
                }
            }

            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            packageSession.commit(pending.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        if (status != PackageInstaller.STATUS_SUCCESS) {
            val message =
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Installation failed."

            NotificationHelper.createChannels(context)
            NotificationHelper.showInstallFailure(context, message)
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
                PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
