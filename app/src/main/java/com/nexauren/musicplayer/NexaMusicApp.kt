package com.musicplayer.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        NotificationHelper.createChannels(this)
        UpdateWorker.schedule(this)
    }
}
