package com.musicplayer.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long,
    val dateAddedMillis: Long = 0L
)

class MusicRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )

        context.contentResolver.query(
            collection,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                result += Song(
                    id = id,
                    title = cursor.getString(titleIndex).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistIndex).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumIndex).orEmpty().ifBlank { "Unknown album" },
                    duration = cursor.getLong(durationIndex),
                    uri = ContentUris.withAppendedId(collection, id),
                    albumId = cursor.getLong(albumIdIndex),
                    dateAddedMillis = cursor.getLong(dateAddedIndex) * 1000L
                )
            }
        }

        result
    }

    fun favoriteIds(): Set<Long> =
        prefs.getStringSet("favorites", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun isFavorite(id: Long): Boolean = id in favoriteIds()

    fun toggleFavorite(id: Long): Boolean {
        val set = favoriteIds().toMutableSet()
        val newValue = if (set.remove(id)) {
            false
        } else {
            set.add(id)
            true
        }
        prefs.edit()
            .putStringSet("favorites", set.map(Long::toString).toSet())
            .apply()
        return newValue
    }

    fun playCounts(): Map<Long, Int> {
        val raw = prefs.getString("plays", "").orEmpty()
        if (raw.isBlank()) return emptyMap()

        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val count = parts[1].toIntOrNull() ?: return@mapNotNull null
            id to count
        }.toMap()
    }

    fun recordPlay(id: Long) {
        val counts = playCounts().toMutableMap()
        counts[id] = (counts[id] ?: 0) + 1
        prefs.edit().putString(
            "plays",
            counts.entries.joinToString(";") {
                it.key.toString() + ":" + it.value.toString()
            }
        ).apply()
    }

    suspend fun loadArtwork(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        }.getOrNull()
    }
}
