package com.musicplayer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        if (url.isBlank()) return
        runCatching {
            UpdateManager.enqueueDownload(context, url, version.ifBlank { "latest" })
        }
    }

    companion object {
        const val EXTRA_URL = "apk_url"
        const val EXTRA_VERSION = "version_name"
    }
}
