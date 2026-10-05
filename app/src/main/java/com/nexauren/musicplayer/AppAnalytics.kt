package com.musicplayer.app

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import com.google.firebase.analytics.FirebaseAnalytics

object AppAnalytics {
    private var analytics: FirebaseAnalytics? = null
    private var activityStartedAt = 0L
    private var screenStartedAt = 0L
    private var currentScreen: String? = null

    fun initialize(context: Context) {
        runCatching {
            analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics?.setAnalyticsCollectionEnabled(true)
        }
    }

    fun onActivityStart() {
        activityStartedAt = SystemClock.elapsedRealtime()
        log("session_start")
    }

    fun onActivityStop() {
        val now = SystemClock.elapsedRealtime()
        val activeMs = if (activityStartedAt > 0L) now - activityStartedAt else 0L
        if (activeMs > 0L) {
            log("session_end", "activity_time_ms" to activeMs.toString())
        }
        endCurrentScreen(now)
        activityStartedAt = 0L
    }

    fun screenView(screenName: String) {
        val now = SystemClock.elapsedRealtime()
        endCurrentScreen(now)

        currentScreen = screenName
        screenStartedAt = now

        runCatching {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            }
            analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }
    }

    fun endCurrentScreen(now: Long = SystemClock.elapsedRealtime()) {
        val name = currentScreen ?: return
        val duration = if (screenStartedAt > 0L) now - screenStartedAt else 0L
        if (duration > 0L) {
            log("page_time", "page_name" to name, "duration_ms" to duration.toString())
        }
        currentScreen = null
        screenStartedAt = 0L
    }

    fun log(name: String, vararg params: Pair<String, String>) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) -> bundle.putString(key, value) }
            analytics?.logEvent(name, bundle)
        }
    }
}
