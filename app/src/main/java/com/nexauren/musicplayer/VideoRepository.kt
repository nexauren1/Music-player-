package com.nexauren.musicplayer2

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoItem(
    val id: Long,
    val title: String,
    val duration: Long,
    val uri: Uri,
    val folder: String
)

class VideoRepository(private val context: Context) {
    suspend fun scan(): List<VideoItem> = withContext(Dispatchers.IO) {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA
        )
        val result = mutableListOf<VideoItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val data = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                val folder = data.substringBeforeLast("/", "Videos")
                    .substringAfterLast("/", "Videos")
                    .ifBlank { "Videos" }
                result += VideoItem(
                    id = id,
                    title = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled video" },
                    duration = cursor.getLong(durationIndex),
                    uri = ContentUris.withAppendedId(collection, id),
                    folder = folder
                )
            }
        }
        result
    }
}
