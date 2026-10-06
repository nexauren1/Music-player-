package com.nexauren.musicplayer2

import android.app.Application

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppAnalytics.initialize(this)
        NotificationHelper.createChannels(this)
        UpdateWorker.schedule(this)
        UpdateWorker.checkNow(this)
    }
}
