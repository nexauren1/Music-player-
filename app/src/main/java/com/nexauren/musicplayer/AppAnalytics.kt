package com.nexauren.musicplayer

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralized production analytics.
 * Only app/product events are logged here; no personally identifying data is sent.
 */
object AppAnalytics {
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        runCatching {
            analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics?.setAnalyticsCollectionEnabled(true)
            log("app_open")
        }
    }

    fun log(name: String, vararg params: Pair<String, String>) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) -> bundle.putString(key, value) }
            analytics?.logEvent(name, bundle)
        }
    }
}
