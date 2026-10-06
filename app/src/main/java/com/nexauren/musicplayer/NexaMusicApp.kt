package com.nexauren.musicplayer

import android.app.Application

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        UpdateWorker.schedule(this)
        UpdateWorker.checkNow(this)
    }
}
