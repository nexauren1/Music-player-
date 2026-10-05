package com.nexauren.musicplayer

import android.app.Application
import com.google.android.gms.ads.MobileAds

class NexaMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        NotificationHelper.createChannels(this)
        UpdateWorker.schedule(this)
    }
}
