package com.nexauren.musicplayer

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    private val _libraryVersion = MutableStateFlow(0)
    val libraryVersion = _libraryVersion.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null

    init {
        viewModelScope.launch {
            connect()
            scan()
            startPositionTicker()
        }
    }

    private suspend fun connect() {
        withContext(Dispatchers.IO) {
            runCatching {
                val token = SessionToken(
                    getApplication(),
                    ComponentName(getApplication(), PlaybackService::class.java)
                )
                val connected =
                    MediaController.Builder(getApplication(), token).buildAsync().get()
                controller = connected

                connected.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncCurrent(mediaItem)
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                        ) {
                            mediaItem?.mediaId?.toLongOrNull()?.let(repository::recordPlay)
                            _libraryVersion.value += 1
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncPosition()
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        _libraryVersion.value += 1
                    }
                })

                withContext(Dispatchers.Main) {
                    syncCurrent(connected.currentMediaItem)
                    syncPosition()
                    syncVolume()
                }
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _songs.value = repository.scan()
            _libraryVersion.value += 1
            syncCurrent(controller?.currentMediaItem)
        }
    }

    fun play(song: Song) {
        val c = controller ?: return
        val list = _songs.value
        if (list.isEmpty()) return

        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        repository.recordPlay(song.id)
        val items = list.map { it.toMediaItem() }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
        controller?.play()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
        controller?.play()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _position.value = position
    }

    fun setVolume(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        controller?.volume = normalized
        _volume.value = normalized
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun isShuffleEnabled(): Boolean = controller?.shuffleModeEnabled == true

    fun repeatMode(): Int = controller?.repeatMode ?: Player.REPEAT_MODE_OFF

    fun audioSessionId(): Int = controller?.audioSessionId ?: 0

    fun favorite(song: Song) {
        repository.toggleFavorite(song.id)
        _libraryVersion.value += 1
    }

    fun isFavorite(song: Song): Boolean = repository.isFavorite(song.id)

    fun favorites(): List<Song> =
        _songs.value.filter { repository.isFavorite(it.id) }

    fun mostPlayed(): List<Song> {
        val counts = repository.playCounts()
        return _songs.value
            .sortedByDescending { counts[it.id] ?: 0 }
            .filter { (counts[it.id] ?: 0) > 0 }
    }

    fun playCount(song: Song): Int = repository.playCounts()[song.id] ?: 0

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
        }
    }

    fun refreshCurrent() { syncCurrent(controller?.currentMediaItem) }

    private fun syncCurrent(mediaItem: MediaItem?) {
        val id = mediaItem?.mediaId?.toLongOrNull() ?: return
        _currentSong.value = _songs.value.firstOrNull { it.id == id }
    }

    private fun syncPosition() {
        controller?.let {
            _position.value = it.currentPosition.coerceAtLeast(0L)
            _duration.value = it.duration.coerceAtLeast(0L)
            _isPlaying.value = it.isPlaying
        }
    }

    private fun syncVolume() {
        controller?.let {
            _volume.value = it.volume.coerceIn(0f, 1f)
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                syncPosition()
                syncVolume()
                delay(500)
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()

    override fun onCleared() {
        positionJob?.cancel()
        sleepJob?.cancel()
        controller?.release()
        super.onCleared()
    }
}
