package com.musicplayer.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class TagValues(val title: String, val artist: String, val album: String)

private object MediaTagEditor {
    fun requestWrite(context: Context, song: Song): android.content.IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)).intentSender
        } else null

    fun apply(context: Context, song: Song, values: TagValues): Boolean {
        val content = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, values.title.trim())
            put(MediaStore.Audio.Media.ARTIST, values.artist.trim())
            put(MediaStore.Audio.Media.ALBUM, values.album.trim())
        }
        return runCatching { context.contentResolver.update(song.uri, content, null, null) > 0 }.getOrDefault(false)
    }
}

@Composable
fun TagEditorDialog(song: Song, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var pending by remember { mutableStateOf<TagValues?>(null) }
    var message by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
        val values = pending
        if (result.resultCode == Activity.RESULT_OK && values != null) {
            if (MediaTagEditor.apply(context, song, values)) onSaved()
            else message = I18n.t("The tags could not be saved.")
        } else {
            message = I18n.t("Permission to edit this file was not granted.")
        }
        pending = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("Edit tags")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.t("Title")) })
                OutlinedTextField(artist, { artist = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.t("Artist")) })
                OutlinedTextField(album, { album = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.t("Album")) })
                if (message.isNotBlank()) Text(message)
            }
        },
        confirmButton = {
            Button(onClick = {
                val values = TagValues(title, artist, album)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pending = values
                    val sender = MediaTagEditor.requestWrite(context, song)
                    if (sender != null) launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                    else message = I18n.t("Unable to request file permission.")
                } else if (MediaTagEditor.apply(context, song, values)) onSaved()
                else message = I18n.t("The tags could not be saved.")
            }) { Text(I18n.t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(I18n.t("Cancel")) } }
    )
}