package com.musicplayer.app

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectedVideos = videos.filter { it.id in selectedIds }
    val context = androidx.compose.ui.platform.LocalContext.current

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        selectionMode = false
        selectedIds = emptySet()
        vm.scanVideos()
    }

    fun deleteSelected() {
        val uris = selectedVideos.map { it.uri }
        if (uris.isEmpty()) return
        val sender = MediaLibraryActions.buildDeleteRequest(context, uris)
        if (sender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            MediaLibraryActions.deleteImmediately(context, uris)
            selectionMode = false
            selectedIds = emptySet()
            vm.scanVideos()
        }
    }

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
                    Text("Videos", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
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
                            Text(current!!.title, Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            TextButton(onClick = onEnterPip) { Text("Floating") }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedIds = emptySet()
                    },
                    label = { Text(if (selectionMode) "Cancel selection" else "Select videos") }
                )
                if (selectionMode) {
                    AssistChip(
                        onClick = {
                            selectedIds = if (selectedIds.size == videos.size) emptySet()
                            else videos.map { it.id }.toSet()
                        },
                        label = { Text(if (selectedIds.size == videos.size) "Clear all" else "Select all") }
                    )
                    AssistChip(
                        enabled = selectedVideos.isNotEmpty(),
                        onClick = {
                            MediaLibraryActions.shareUris(context, selectedVideos.map { it.uri }, "video/*")
                        },
                        label = { Text("Share") }
                    )
                    AssistChip(
                        enabled = selectedVideos.isNotEmpty(),
                        onClick = { deleteSelected() },
                        label = { Text("Delete") }
                    )
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
                val selected = video.id in selectedIds
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selectionMode) {
                                selectedIds = if (selected) selectedIds - video.id else selectedIds + video.id
                            } else {
                                vm.playVideo(video)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(video.title, maxLines = 2, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(video.folder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selectionMode) {
                            Text(if (selected) "✓" else "○", fontWeight = androidx.compose.ui.text.font.FontWeight.Black, modifier = Modifier.padding(10.dp))
                        } else {
                            Button(onClick = { vm.playVideo(video) }) { Text("Play") }
                        }
                    }
                }
            }
        }
    }
}
