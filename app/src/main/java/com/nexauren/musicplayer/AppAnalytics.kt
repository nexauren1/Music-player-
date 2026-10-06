package com.nexauren.musicplayer2

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
        if (analytics != null) return
        runCatching {
            val instance = FirebaseAnalytics.getInstance(context.applicationContext)
            instance.setAnalyticsCollectionEnabled(true)
            analytics = instance
        }
    }

    fun onActivityStart() {
        if (activityStartedAt > 0L) return
        activityStartedAt = SystemClock.elapsedRealtime()
        log("app_session_start")
    }

    fun onActivityStop() {
        val now = SystemClock.elapsedRealtime()
        val activeMs = if (activityStartedAt > 0L) now - activityStartedAt else 0L
        if (activeMs > 0L) {
            log("app_session_end", "activity_time_ms" to activeMs.toString())
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

    fun login(method: String) {
        log(FirebaseAnalytics.Event.LOGIN, "method" to method)
    }

    fun signUp(method: String) {
        log(FirebaseAnalytics.Event.SIGN_UP, "method" to method)
    }

    fun log(name: String, vararg params: Pair<String, String>) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) -> bundle.putString(key, value) }
            analytics?.logEvent(name, bundle)
        }
    }
}
