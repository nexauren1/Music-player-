package com.musicplayer.app

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
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
    private val videoRepository = VideoRepository(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos = _videos.asStateFlow()
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo = _currentVideo.asStateFlow()
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatModeState = _repeatMode.asStateFlow()
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
    private val audioLabPrefs = application.getSharedPreferences("audio_lab", Context.MODE_PRIVATE)
    private val replayGainDb = mutableMapOf<Long, Float>()
    private val _crossfadeSeconds = MutableStateFlow(audioLabPrefs.getFloat("crossfade", 4f).coerceIn(0f, 12f))
    val crossfadeSeconds = _crossfadeSeconds.asStateFlow()
    private val _replayGainEnabled = MutableStateFlow(audioLabPrefs.getBoolean("replay_gain", true))
    val replayGainEnabled = _replayGainEnabled.asStateFlow()
    private val _speed = MutableStateFlow(playbackPrefs.getFloat("speed", 1f).coerceIn(.5f, 2f))
    val speed = _speed.asStateFlow()
    private val _pitch = MutableStateFlow(playbackPrefs.getFloat("pitch", 1f).coerceIn(.5f, 1.5f))
    val pitch = _pitch.asStateFlow()
    private var positionJob: Job? = null
    private var sleepJob: Job? = null
    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs = _sleepRemainingMs.asStateFlow()

    init {
        viewModelScope.launch {
            connect()
            scan()
            scanVideos()
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
                        applyEffectiveVolume()
                        syncWidget()
                    }

                    override fun onMetadata(metadata: Metadata) {
                        val id = connected.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                        ReplayGain.gainDb(metadata)?.let { replayGainDb[id] = it }
                        applyEffectiveVolume()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (mediaItem?.mediaId?.startsWith("video:") == true) {
                            syncVideo(mediaItem)
                            _currentSong.value = null
                        } else {
                            syncCurrent(mediaItem)
                            _currentVideo.value = null
                        }
                        replayGainDb.remove(mediaItem?.mediaId?.toLongOrNull())
                        applyEffectiveVolume()
                        syncWidget()
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
                    _volume.value = connected.volume.coerceIn(0f, 1f)
                    applyEffectiveVolume()
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

    fun scanVideos() {
        viewModelScope.launch {
            _videos.value = videoRepository.scan()
            syncVideo(controller?.currentMediaItem)
        }
    }

    fun playVideo(video: VideoItem) {
        val c = controller ?: return
        val items = _videos.value.map { it.toMediaItem() }
        val index = _videos.value.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        _currentVideo.value = video
        _currentSong.value = null
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        syncWidget()
    }

    fun playerController(): androidx.media3.common.Player? = controller


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
        applyEffectiveVolume()
        syncCurrent(c.currentMediaItem)
        syncWidget()
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
        _volume.value = normalized
        applyEffectiveVolume()
    }

    fun setCrossfadeSeconds(value: Float) {
        val normalized = value.coerceIn(0f, 12f)
        _crossfadeSeconds.value = normalized
        audioLabPrefs.edit().putFloat("crossfade", normalized).apply()
        applyEffectiveVolume()
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        _replayGainEnabled.value = enabled
        audioLabPrefs.edit().putBoolean("replay_gain", enabled).apply()
        applyEffectiveVolume()
    }

    fun toggleShuffle(): Boolean {
        val enabled = !(controller?.shuffleModeEnabled ?: false)
        controller?.shuffleModeEnabled = enabled
        _shuffleEnabled.value = enabled
        return enabled
    }

    fun toggleRepeat(): Int {
        val mode = when (controller?.repeatMode ?: Player.REPEAT_MODE_OFF) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = mode
        _repeatMode.value = mode
        return mode
    }

    fun isShuffleEnabled(): Boolean = controller?.shuffleModeEnabled == true

    fun repeatMode(): Int = _repeatMode.value

    fun audioSessionId(): Int = controller?.audioSessionId ?: 0

    fun visualizerSessionId(): Int = controller?.audioSessionId ?: 0

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

    fun addSongsToQueue(songs: List<Song>) {
        songs.forEach(::addToQueue)
    }

    fun addVideosToQueue(videos: List<VideoItem>) {
        val c = controller ?: return
        videos.forEach { video ->
            val item = video.toMediaItem()
            if (c.mediaItemCount == 0) {
                c.setMediaItem(item)
            } else {
                c.addMediaItem(c.mediaItemCount, item)
            }
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        AppAnalytics.log("queue_videos_add", "count" to videos.size.toString())
    }

    data class QueueEntry(
        val mediaId: String,
        val title: String,
        val subtitle: String,
        val isVideo: Boolean
    )

    fun queueEntries(): List<QueueEntry> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).mapNotNull { index ->
            val item = c.getMediaItemAt(index)
            val mediaId = item.mediaId
            if (mediaId.startsWith("video:")) {
                val id = mediaId.removePrefix("video:").toLongOrNull()
                val video = _videos.value.firstOrNull { it.id == id }
                QueueEntry(mediaId, video?.title ?: item.mediaMetadata.title?.toString().orEmpty(), video?.folder ?: "Video", true)
            } else {
                val id = mediaId.toLongOrNull()
                val song = _songs.value.firstOrNull { it.id == id }
                QueueEntry(mediaId, song?.title ?: item.mediaMetadata.title?.toString().orEmpty(), song?.artist ?: item.mediaMetadata.artist?.toString().orEmpty(), false)
            }
        }
    }

    fun queueSongs(): List<Song> = queueEntries().filter { !it.isVideo }.mapNotNull { entry ->
        entry.mediaId.toLongOrNull()?.let { id -> _songs.value.firstOrNull { it.id == id } }
    }

    fun queueCurrentSongId(): Long? =
        controller?.currentMediaItem?.mediaId?.toLongOrNull()

    fun removeFromQueueMedia(mediaId: String) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { i ->
            c.getMediaItemAt(i).mediaId == mediaId
        } ?: return
        c.removeMediaItem(index)
        syncCurrent(c.currentMediaItem)
        syncVideo(c.currentMediaItem)
    }

    fun removeFromQueue(songId: Long) {
        removeFromQueueMedia(songId.toString())
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
        val total = minutes.coerceAtLeast(1) * 60_000L
        _sleepRemainingMs.value = total
        sleepJob = viewModelScope.launch {
            var remaining = total
            while (remaining > 0L) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _sleepRemainingMs.value = remaining
            }
            controller?.pause()
            _sleepRemainingMs.value = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = 0L
    }

    fun refreshCurrent() { syncCurrent(controller?.currentMediaItem) }

    private fun syncCurrent(mediaItem: MediaItem?) {
        val id = mediaItem?.mediaId?.toLongOrNull()
        _currentSong.value = id?.let { songId ->
            _songs.value.firstOrNull { it.id == songId }
        }
    }

    private fun syncVideo(mediaItem: MediaItem?) {
        val id = mediaItem?.mediaId?.removePrefix("video:")?.toLongOrNull()
        _currentVideo.value = id?.let { videoId ->
            _videos.value.firstOrNull { it.id == videoId }
        }
    }

    private fun syncPosition() {
        controller?.let {
            _position.value = it.currentPosition.coerceAtLeast(0L)
            _duration.value = it.duration.coerceAtLeast(0L)
            _isPlaying.value = it.isPlaying
        }
    }

    private fun applyEffectiveVolume() {
        val c = controller ?: return
        val currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
        val gain = if (_replayGainEnabled.value) replayGainDb[currentId] ?: 0f else 0f
        val gainMultiplier = ReplayGain.multiplier(gain)
        val durationMs = c.duration
        val positionMs = c.currentPosition.coerceAtLeast(0L)
        val transitionMs = (_crossfadeSeconds.value * 1000f).toLong()
        val envelope = if (!c.isPlaying || transitionMs <= 0L || durationMs <= 0L) {
            1f
        } else {
            val fadeIn = if (positionMs < transitionMs) positionMs.toFloat() / transitionMs else 1f
            val remaining = durationMs - positionMs
            val fadeOut = if (remaining < transitionMs) remaining.toFloat() / transitionMs else 1f
            (0.22f + 0.78f * minOf(fadeIn, fadeOut).coerceIn(0f, 1f))
        }
        c.volume = (_volume.value * gainMultiplier * envelope).coerceIn(0f, 1f)
    }

    private fun syncWidget() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            getApplication<Application>()
                .getSharedPreferences("now_playing_widget", Context.MODE_PRIVATE)
                .edit()
                .putString("title", song.title)
                .putString("artist", song.artist)
                .putBoolean("playing", _isPlaying.value)
                .apply()
            runCatching { MusicWidget.update(getApplication()) }
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                syncPosition()
                applyEffectiveVolume()
                delay(250)
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

    private fun VideoItem.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("video:" + id)
            .setUri(uri)
            .setMimeType("video/*")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()

    val isVideoPlaying: Boolean
        get() = _currentVideo.value != null

    override fun onCleared() {
        positionJob?.cancel()
        sleepJob?.cancel()
        _sleepRemainingMs.value = 0L
        controller?.release()
        audioEffects.release()
        super.onCleared()
    }
}
