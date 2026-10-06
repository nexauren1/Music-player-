package com.nexauren.musicplayer2

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.clickable
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp

class MusicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("now_playing_widget", Context.MODE_PRIVATE)
            val title = prefs.getString("title", "Music Player") ?: "Music Player"
            val artist = prefs.getString("artist", "Tap to open") ?: "Tap to open"
            val playing = prefs.getBoolean("playing", false)

            Column(
                modifier = GlanceModifier
                    .padding(12.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            ) {
                Text(title)
                Text(artist)
                Text(if (playing) "PLAYING" else "PAUSED")
            }
        }
    }

    companion object {
        suspend fun update(context: Context) {
            MusicWidget().updateAll(context)
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
