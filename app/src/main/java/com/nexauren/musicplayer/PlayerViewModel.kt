package com.musicplayer.app

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import android.content.Context
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
    private val audioEffects = EqualizerController(application)
    private val playbackPrefs = application.getSharedPreferences("playback_effects", Context.MODE_PRIVATE)
    private val _speed = MutableStateFlow(playbackPrefs.getFloat("speed", 1f).coerceIn(.5f, 2f))
    val speed = _speed.asStateFlow()
    private val _pitch = MutableStateFlow(playbackPrefs.getFloat("pitch", 1f).coerceIn(.5f, 1.5f))
    val pitch = _pitch.asStateFlow()
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
                        audioEffects.attach(audioSessionId)
                        _libraryVersion.value += 1
                    }
                })

                audioEffects.attach(connected.audioSessionId)
                applyPlaybackParameters()
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
        audioEffects.attach(c.audioSessionId)
        applyPlaybackParameters()
        AppAnalytics.log("play_start", "song_id" to song.id.toString())
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
                AppAnalytics.log("play_pause", "state" to "paused")
            } else {
                it.play()
                AppAnalytics.log("play_pause", "state" to "playing")
            }
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
        controller?.play()
        AppAnalytics.log("next_track")
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
        controller?.play()
        AppAnalytics.log("previous_track")
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

    fun equalizerController(): EqualizerController = audioEffects

    fun setSpeed(value: Float) {
        val v = value.coerceIn(.5f, 2f)
        _speed.value = v
        playbackPrefs.edit().putFloat("speed", v).apply()
        applyPlaybackParameters()
        AppAnalytics.log("playback_speed_change", "speed" to v.toString())
    }

    fun setPitch(value: Float) {
        val v = value.coerceIn(.5f, 1.5f)
        _pitch.value = v
        playbackPrefs.edit().putFloat("pitch", v).apply()
        applyPlaybackParameters()
        AppAnalytics.log("pitch_change", "pitch" to v.toString())
    }

    fun resetEffects() {
        setSpeed(1f)
        setPitch(1f)
        audioEffects.resetAll()
        AppAnalytics.log("effects_reset")
    }

    fun effectState(): DjEffectState = audioEffects.effectState()

    private fun applyPlaybackParameters() {
        controller?.playbackParameters = PlaybackParameters(_speed.value, _pitch.value)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        val item = song.toMediaItem()
        if (c.mediaItemCount == 0) {
            play(song)
            return
        }
        val currentIndex = c.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        val insertIndex = (currentIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(insertIndex, item)
        AppAnalytics.log("play_next", "song_id" to song.id.toString())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        val item = song.toMediaItem()
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item)
            c.prepare()
        } else {
            c.addMediaItem(c.mediaItemCount, item)
        }
        AppAnalytics.log("queue_add", "song_id" to song.id.toString())
    }

    fun favorite(song: Song) {
        repository.toggleFavorite(song.id)
        _libraryVersion.value += 1
        AppAnalytics.log("favorite_toggle", "song_id" to song.id.toString())
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

    fun recentlyPlayed(): List<Song> {
        val recent = repository.lastPlayedTimes()
        return _songs.value
            .filter { (recent[it.id] ?: 0L) > 0L }
            .sortedByDescending { recent[it.id] ?: 0L }
    }

    fun clearPlayHistory() {
        repository.clearPlayHistory()
        _libraryVersion.value += 1
        AppAnalytics.log("play_history_clear")
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
        val id = mediaItem?.mediaId?.toLongOrNull()
        _currentSong.value = id?.let { songId ->
            _songs.value.firstOrNull { it.id == songId }
        }
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
        audioEffects.release()
        super.onCleared()
    }
}
