package com.musicplayer.app

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object MediaLibraryActions {
    fun buildDeleteRequest(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }

    fun deleteImmediately(context: Context, uris: List<Uri>): Int {
        var deleted = 0
        uris.forEach { uri ->
            deleted += runCatching {
                context.contentResolver.delete(uri, null, null)
            }.getOrDefault(0)
        }
        return deleted
    }

    fun shareUris(context: Context, uris: List<Uri>, mimeType: String) {
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share selected media"))
        }
    }
}

object DuplicateDetector {
    private fun clean(value: String): String =
        value.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun audio(songs: List<Song>): List<List<Song>> =
        songs.groupBy {
            listOf(clean(it.title), clean(it.artist), it.duration / 1000L).joinToString("|")
        }.values.filter { it.size > 1 }.map { it.sortedBy { song -> song.id } }

    fun video(videos: List<VideoItem>): List<List<VideoItem>> =
        videos.groupBy {
            listOf(clean(it.title), it.duration / 1000L).joinToString("|")
        }.values.filter { it.size > 1 }.map { it.sortedBy { video -> video.id } }
}
