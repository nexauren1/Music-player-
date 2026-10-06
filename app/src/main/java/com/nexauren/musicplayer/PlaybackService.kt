package com.nexauren.musicplayer

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var libraryItems: List<MediaItem> = emptyList()

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (libraryItems.isEmpty()) refreshLibrary()
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(emptyList(), params)
                )
            }

            val from = (page * pageSize).coerceAtMost(libraryItems.size)
            val to = if (pageSize == Int.MAX_VALUE) {
                libraryItems.size
            } else {
                (from + pageSize).coerceAtMost(libraryItems.size)
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(libraryItems.subList(from, to), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = if (mediaId == ROOT_ID) {
                rootItem()
            } else {
                libraryItems.firstOrNull { it.mediaId == mediaId }
            }
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, null)
            )
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val filtered = libraryItems.filter { item ->
                val title = item.mediaMetadata.title?.toString().orEmpty()
                val artist = item.mediaMetadata.artist?.toString().orEmpty()
                title.contains(query, true) || artist.contains(query, true)
            }
            val from = (page * pageSize).coerceAtMost(filtered.size)
            val to = if (pageSize == Int.MAX_VALUE) filtered.size
            else (from + pageSize).coerceAtMost(filtered.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(filtered.subList(from, to), params))
        }
    }

    override fun onCreate() {
        super.onCreate()

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(attributes, true)
                repeatMode = Player.REPEAT_MODE_OFF
            }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, libraryCallback).build()
        refreshLibrary()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    private fun refreshLibrary() {
        serviceScope.launch {
            val scanned = runCatching { MusicRepository(this@PlaybackService).scan() }.getOrDefault(emptyList())
            libraryItems = scanned.map { it.toMediaItem() }
            mediaLibrarySession?.notifyChildrenChanged(ROOT_ID, libraryItems.size, null)
        }
    }

    private fun rootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Music Player")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) stopSelf() else super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.release()
        player?.release()
        mediaLibrarySession = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "root"
    }
}
