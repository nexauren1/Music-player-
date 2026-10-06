package com.musicplayer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
fun VideoScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    onEnterPip: () -> Unit
) {
    val videos by vm.videos.collectAsState()
    val current by vm.currentVideo.collectAsState()
    val controller = vm.playerController()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Videos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(videos.size.toString() + " videos on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (current != null) {
                        Spacer(Modifier.height(10.dp))
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            factory = { PlayerView(it) },
                            update = { it.player = controller }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(current!!.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = onEnterPip) {
                                Text("Floating")
                            }
                        }
                    }
                }
            }
        }
        if (videos.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
                    Text(
                        "No videos found. Grant media permission and scan again.",
                        Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(video.title, maxLines = 2, fontWeight = FontWeight.Bold)
                            Text(video.folder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { vm.playVideo(video) }) {
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}
