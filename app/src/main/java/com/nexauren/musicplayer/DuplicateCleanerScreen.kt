package com.nexauren.musicplayer

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DuplicateCleanerScreen(
    modifier: Modifier,
    vm: PlayerViewModel
) {
    val songs by vm.songs.collectAsState()
    val videos by vm.videos.collectAsState()
    var mode by remember { mutableStateOf("music") }
    var refreshToken by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val audioGroups = remember(songs, refreshToken) { DuplicateDetector.audio(songs) }
    val videoGroups = remember(videos, refreshToken) { DuplicateDetector.video(videos) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        vm.scan()
        vm.scanVideos()
        refreshToken++
    }

    val duplicateUris = remember(mode, audioGroups, videoGroups) {
        if (mode == "music") audioGroups.flatMap { it.drop(1).map { song -> song.uri } }
        else videoGroups.flatMap { it.drop(1).map { video -> video.uri } }
    }

    fun deleteDuplicates() {
        if (duplicateUris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaLibraryActions.buildDeleteRequest(context, duplicateUris)?.let {
                deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
        } else {
            MediaLibraryActions.deleteImmediately(context, duplicateUris)
            vm.scan()
            vm.scanVideos()
            refreshToken++
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Duplicate cleaner"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        I18n.t("Finds matching title/artist and duration, then keeps the first copy and marks the extras for deletion."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { mode = "music" }) { Text(I18n.t("Music") + " (" + audioGroups.size + ")") }
                        Button(onClick = { mode = "video" }) { Text(I18n.t("Videos") + " (" + videoGroups.size + ")") }
                    }
                    Spacer(Modifier.padding(4.dp))
                    Button(
                        enabled = duplicateUris.isNotEmpty(),
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.DeleteSweep, null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(I18n.t("Delete duplicates") + " (" + duplicateUris.size + ")")
                    }
                }
            }
        }

        if (mode == "music") {
            items(audioGroups) { group ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(group.first().title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(group.size.toString() + " " + I18n.t("matching copies"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        group.forEachIndexed { index, song ->
                            Text(
                                "${I18n.t(if (index == 0) "KEEP" else "DELETE")} • ${song.artist} • ${song.album}",
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            items(videoGroups) { group ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(group.first().title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${group.size} matching copies", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        group.forEachIndexed { index, video ->
                            Text(
                                "${I18n.t(if (index == 0) "KEEP" else "DELETE")} • ${video.folder}",
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(I18n.t("Delete duplicates?")) },
            text = { Text(I18n.t("This keeps the first copy in each group and deletes the remaining matching files.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleteDuplicates()
                }) { Text(I18n.t("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(I18n.t("Cancel")) }
            }
        )
    }
}
