package com.nexauren.musicplayer

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun TrimDialog(
    song: Song,
    vm: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentDuration by vm.duration.collectAsState()
    val durationMs = currentDuration.coerceAtLeast(song.duration).coerceAtLeast(2_000L)
    val maxSeconds = (durationMs / 1000L).toFloat()
    var start by remember { mutableFloatStateOf(0f) }
    var end by remember { mutableFloatStateOf(maxSeconds) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    fun export() {
        if (exporting || end <= start + 0.5f) return
        exporting = true
        progress = 0
        error = ""

        val output = File(context.cacheDir, "musicplayer_clip_" + System.currentTimeMillis() + ".mp4")
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((start * 1000f).toLong())
            .setEndPositionMs((end * 1000f).toLong())
            .build()
        val input = MediaItem.Builder()
            .setUri(song.uri)
            .setClippingConfiguration(clip)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                exporting = false
                progress = 100
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    output
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, I18n.t("Share trimmed clip")))
                }.onFailure {
                    error = it.message ?: I18n.t("The clip was exported, but it could not be shared.")
                }
            }

            override fun onError(
                composition: Composition,
                result: ExportResult,
                exception: ExportException
            ) {
                exporting = false
                error = exception.message ?: I18n.t("Could not trim this file.")
            }
        }

        runCatching {
            Transformer.Builder(context)
                .addListener(listener)
                .build()
                .start(EditedMediaItem.Builder(input).build(), output.absolutePath)
        }.onFailure {
            exporting = false
            error = it.message ?: I18n.t("Could not start the trim operation.")
        }
    }

    LaunchedEffect(exporting) {
        if (!exporting) return@LaunchedEffect
        while (exporting) {
            kotlinx.coroutines.delay(250)
            if (progress < 95) progress += 2
        }
    }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        title = { Text(I18n.t("Trim") + " " + song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(I18n.t("Start") + ": " + formatTrimDuration((start * 1000).toLong()))
                Slider(
                    enabled = !exporting,
                    value = start,
                    onValueChange = { start = it.coerceAtMost(end - 0.5f) },
                    valueRange = 0f..maxSeconds
                )
                Text(I18n.t("End") + ": " + formatTrimDuration((end * 1000).toLong()))
                Slider(
                    enabled = !exporting,
                    value = end,
                    onValueChange = { end = it.coerceAtLeast(start + 0.5f) },
                    valueRange = 0f..maxSeconds
                )
                Text(
                    I18n.t("Export the selected section as a shareable MP4 clip."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exporting) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = !exporting, onClick = { export() }) {
                Text(I18n.t("Export clip"))
            }
        },
        dismissButton = {
            TextButton(enabled = !exporting, onClick = onDismiss) {
                Text(I18n.t("Close"))
            }
        }
    )
}


private fun formatTrimDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
