package com.nexauren.musicplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var moreOpen by remember { mutableStateOf(false) }
    val selectedVideos = videos.filter { it.id in selectedIds }
    val context = LocalContext.current

    fun clearSelection() {
        selectionMode = false
        selectedIds = emptySet()
        moreOpen = false
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        clearSelection()
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
            clearSelection()
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
                    Text(
                        I18n.t("Videos"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        videos.size.toString() + " " + I18n.t("videos on this device"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (current != null) {
                        Spacer(Modifier.height(10.dp))
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            factory = { PlayerView(it) },
                            update = { it.player = controller }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                current!!.title,
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onEnterPip) {
                                Text(I18n.t("Floating"))
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                I18n.t("Selected") + ": " + selectedVideos.size,
                                Modifier.weight(1f),
                                fontWeight = FontWeight.Black
                            )
                            TextButton(onClick = { clearSelection() }) {
                                Text(I18n.t("Cancel selection"))
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AssistChip(
                                    onClick = {
                                        selectedIds = if (selectedVideos.size == videos.size) {
                                            emptySet()
                                        } else {
                                            videos.map { it.id }.toSet()
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (selectedVideos.size == videos.size) I18n.t("Clear all")
                                            else I18n.t("Select all")
                                        )
                                    }
                                )
                            }
                            item {
                                AssistChip(
                                    enabled = selectedVideos.isNotEmpty(),
                                    onClick = {
                                        vm.addVideosToQueue(selectedVideos)
                                        clearSelection()
                                    },
                                    label = { Text(I18n.t("Add to queue")) }
                                )
                            }
                            item {
                                AssistChip(
                                    enabled = selectedVideos.isNotEmpty(),
                                    onClick = {
                                        MediaLibraryActions.shareUris(context, selectedVideos.map { it.uri }, "video/*")
                                    },
                                    label = { Text(I18n.t("Share")) }
                                )
                            }
                            item {
                                AssistChip(
                                    enabled = selectedVideos.isNotEmpty(),
                                    onClick = { deleteSelected() },
                                    label = { Text(I18n.t("Delete")) }
                                )
                            }
                            item {
                                androidx.compose.foundation.layout.Box {
                                    AssistChip(
                                        onClick = { moreOpen = true },
                                        label = { Text(I18n.t("More")) }
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = moreOpen,
                                        onDismissRequest = { moreOpen = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(I18n.t("Play next")) },
                                            onClick = {
                                                selectedVideos.forEach { vm.addVideosToQueue(listOf(it)) }
                                                moreOpen = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(I18n.t("Sleep timer")) },
                                            onClick = {
                                                vm.startSleepTimer(15)
                                                moreOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (videos.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
                ) {
                    Text(
                        I18n.t("No videos found. Grant media permission and scan again."),
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
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (selected) selectedIds - video.id else selectedIds + video.id
                                } else {
                                    vm.playVideo(video)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = selectedIds + video.id
                                } else {
                                    selectedIds = if (selected) selectedIds - video.id else selectedIds + video.id
                                }
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(video.folder, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selectionMode) {
                            Text(
                                if (selected) "✓" else "○",
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Button(onClick = { vm.playVideo(video) }) {
                                Text(I18n.t("Play"))
                            }
                        }
                    }
                }
            }
        }
    }
}
