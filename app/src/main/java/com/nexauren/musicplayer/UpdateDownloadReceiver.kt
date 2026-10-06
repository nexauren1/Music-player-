package com.nexauren.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateDownloadReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_URL = "apk_url"
        const val EXTRA_VERSION = "version_name"
        const val EXTRA_SHA256 = "sha256"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        val sha256 = intent.getStringExtra(EXTRA_SHA256).orEmpty()

        if (url.isBlank() || version.isBlank()) {
            NotificationHelper.showDownloadFailure(
                context,
                "Invalid update download information."
            )
            return
        }

        UpdateManager.enqueueDownload(context, url, version, sha256)
        NotificationHelper.clearUpdateNotification(context)
    }
}
