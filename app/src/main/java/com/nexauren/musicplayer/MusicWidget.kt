package com.musicplayer.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.dp
import androidx.glance.unit.sp

class MusicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("now_playing_widget", Context.MODE_PRIVATE)
            val title = prefs.getString("title", "Music Player") ?: "Music Player"
            val artist = prefs.getString("artist", "Tap to open") ?: "Tap to open"
            val playing = prefs.getBoolean("playing", false)
            Column(
                modifier = GlanceModifier.fillMaxSize().background(Color(0xFF11131A)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = TextStyle(color = Color.White, fontSize = 15.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(artist, style = TextStyle(color = Color(0xFFB7BCC8), fontSize = 12.sp))
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (playing) "PLAYING" else "PAUSED", style = TextStyle(color = Color(0xFFB88CFF), fontSize = 11.sp))
                    Spacer(GlanceModifier.height(1.dp))
                    Text("Music Player", style = TextStyle(color = Color(0xFF777D8C), fontSize = 10.sp))
                }
            }
        }
    }
    companion object {
        suspend fun update(context: Context) { MusicWidget().updateAll(context) }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}