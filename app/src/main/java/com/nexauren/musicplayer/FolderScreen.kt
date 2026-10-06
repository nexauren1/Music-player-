package com.musicplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FolderScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    songs: List<Song>
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val folders = remember(songs) {
        songs.groupBy { it.folder.ifBlank { "Unknown folder" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    if (selected != null) {
        val folderSongs = folders[selected].orEmpty()
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        selected!!,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clickable { selected = null }
                    )
                }
            }
            items(folderSongs, key = { it.id }) { song ->
                SongRow(song, vm, showPlays = false)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(folders.entries.toList(), key = { it.key }) { entry ->
                Card(
                    Modifier.fillMaxWidth().clickable { selected = entry.key },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(entry.key, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(entry.value.size.toString() + " tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
