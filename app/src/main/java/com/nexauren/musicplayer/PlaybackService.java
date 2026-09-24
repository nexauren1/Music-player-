package com.nexauren.musicplayer;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.media.audiofx.PresetReverb;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.C;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

@OptIn(markerClass = UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private static PlaybackService instance;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private final AudioEffectsController effects = new AudioEffectsController();
    private ChannelMixingAudioProcessor channelMixer;
    private PresetReverb reverb;
    private boolean mono;
    private float balance;
    private boolean skipSilence;
    private long abStartMs = -1L;
    private long abEndMs = -1L;
    private long sleepEndAtMs = 0L;
    private String recoveryMediaId = "";
    private int recoveryAttempts = 0;
    private final android.os.Handler recoveryHandler = new android.os.Handler();
    private final Runnable recoveryRunnable = () -> {
        if (player == null || !player.isCommandAvailable(Player.COMMAND_PREPARE)) return;
        try {
            int index = Math.max(0, player.getCurrentMediaItemIndex());
            long position = Math.max(0L, player.getCurrentPosition());
            player.prepare();
            if (player.getMediaItemCount() > 0) player.seekTo(index, position);
            player.play();
        } catch (Throwable ignored) {
            skipAfterPlaybackError();
        }
    };
    private final android.os.Handler sleepHandler = new android.os.Handler();
    private final Runnable sleepTimer = new Runnable() {
        @Override public void run() {
            if (sleepEndAtMs > 0L && System.currentTimeMillis() >= sleepEndAtMs) {
                sleepEndAtMs = 0L;
                getSharedPreferences("nexauren_sleep", MODE_PRIVATE).edit().remove("end_at").apply();
                if (player != null) player.pause();
            }
            sleepHandler.postDelayed(this, 1000L);
        }
    };
    private final android.os.Handler abHandler = new android.os.Handler();
    private final Runnable abLoop = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying() && abStartMs >= 0 && abEndMs > abStartMs && player.getCurrentPosition() >= abEndMs) {
                player.seekTo(abStartMs);
            }
            abHandler.postDelayed(this, 120L);
        }
    };

    private final MediaSession.Callback sessionCallback = new MediaSession.Callback() {
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                MediaSession mediaSession, MediaSession.ControllerInfo controllerInfo, boolean isForPlayback) {
            long currentId = PlaybackStateStore.currentId(PlaybackService.this);
            if (currentId < 0L) return Futures.immediateCancelledFuture();

            java.util.ArrayList<Long> savedIds =
                    new java.util.ArrayList<>(PlaybackStateStore.queue(PlaybackService.this));
            if (savedIds.isEmpty()) savedIds.add(currentId);

            java.util.ArrayList<androidx.media3.common.MediaItem> items = new java.util.ArrayList<>();
            int startIndex = -1;
            for (Long id : savedIds) {
                if (id == null || id < 0L) continue;
                Uri uri = ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                if (id == currentId) startIndex = items.size();
                items.add(new androidx.media3.common.MediaItem.Builder()
                        .setMediaId(String.valueOf(id)).setUri(uri).build());
            }
            if (startIndex < 0) {
                Uri uri = ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentId);
                startIndex = 0;
                items.add(0, new androidx.media3.common.MediaItem.Builder()
                        .setMediaId(String.valueOf(currentId)).setUri(uri).build());
            }

            long position = Math.max(0L, PlaybackStateStore.positionMs(PlaybackService.this));
            if (isForPlayback) {
                player.setRepeatMode(PlaybackStateStore.repeatMode(PlaybackService.this));
                player.setShuffleModeEnabled(PlaybackStateStore.shuffle(PlaybackService.this));
                player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(
                        PlaybackStateStore.speed(PlaybackService.this),
                        PlaybackStateStore.pitch(PlaybackService.this)));
                player.setVolume(PlaybackStateStore.volume(PlaybackService.this));
            }
            return Futures.immediateFuture(new MediaSession.MediaItemsWithStartPosition(
                    items, startIndex, position));
        }
    };

    private final Player.Listener audioListener = new Player.Listener() {
        @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
            resetAB();
            recoveryMediaId = item == null ? "" : item.mediaId;
            recoveryAttempts = 0;
        }

        @Override public void onPlayerError(PlaybackException error) {
            androidx.media3.common.MediaItem item = player == null ? null : player.getCurrentMediaItem();
            String id = item == null ? "" : item.mediaId;
            if (!id.equals(recoveryMediaId)) {
                recoveryMediaId = id;
                recoveryAttempts = 0;
            }
            if (recoveryAttempts < 1) {
                recoveryAttempts++;
                recoveryHandler.removeCallbacks(recoveryRunnable);
                recoveryHandler.postDelayed(recoveryRunnable, 350L);
            } else {
                skipAfterPlaybackError();
            }
        }

        @Override public void onAudioSessionIdChanged(int audioSessionId) {
            if (audioSessionId > 0) {
                effects.attachToSession(audioSessionId);
                effects.restoreFromPreferences(PlaybackService.this);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;

        channelMixer = new ChannelMixingAudioProcessor();
        channelMixer.putChannelMixingMatrix(new ChannelMixingMatrix(1, 1, new float[]{1f}));
        channelMixer.putChannelMixingMatrix(new ChannelMixingMatrix(2, 2, new float[]{1f, 0f, 0f, 1f}));
        channelMixer.putChannelMixingMatrix(new ChannelMixingMatrix(2, 1, new float[]{0.5f, 0.5f}));

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Override protected AudioSink buildAudioSink(android.content.Context context,
                    boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{channelMixer})
                        .build();
            }
        };

        player = new ExoPlayer.Builder(this, renderersFactory).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setSeekBackIncrementMs(10_000L);
        player.setSeekForwardIncrementMs(30_000L);
        boolean savedEffects = getSharedPreferences("nexauren", MODE_PRIVATE)
                .getBoolean("effects", true);
        skipSilence = getSharedPreferences("nexauren", MODE_PRIVATE)
                .getBoolean("skip_silence", false);
        effects.setEnabled(savedEffects);
        player.setSkipSilenceEnabled(skipSilence);
        player.addListener(audioListener);
        abHandler.post(abLoop);
        sleepEndAtMs = getSharedPreferences("nexauren_sleep", MODE_PRIVATE).getLong("end_at", 0L);
        sleepHandler.post(sleepTimer);

        try {
            reverb = new PresetReverb(0, 0);
            reverb.setPreset(PresetReverb.PRESET_NONE);
            reverb.setEnabled(false);
        } catch (Throwable ignored) {
            reverb = null;
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .setCallback(sessionCallback)
                .build();
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    public static boolean setEqualizerBand(int index, int count, int percent) {
        return instance != null && instance.effects.setUiBandAndReturn(index, count, percent);
    }
    public static int getEqualizerBandCount() {
        return instance == null ? 0 : instance.effects.getBandCount();
    }
    public static int[] getEqualizerLevels(int count) {
        return instance == null ? new int[count] : instance.effects.getLevelsPercent(count);
    }
    public static boolean applyEqualizerPreset(String name, int count) {
        return instance != null && instance.effects.applyPresetAndReturn(name, count);
    }
    public static boolean setBass(int percent) {
        return instance != null && instance.effects.setBassAndReturn(percent);
    }
    public static boolean setVirtualizer(int percent) {
        return instance != null && instance.effects.setVirtualizerAndReturn(percent);
    }
    public static void saveAudioPrefs(android.content.Context context) {
        if (instance != null) instance.effects.saveToPreferences(context);
    }

    public static void restoreAudioPrefs(android.content.Context context) {
        if (instance != null) instance.effects.restoreFromPreferences(context);
    }

    public static void setEffectsEnabled(boolean enabled) {
        if (instance != null) instance.effects.setEnabled(enabled);
    }
    public static boolean areEffectsEnabled() {
        return instance == null || instance.effects.isEnabled();
    }
    public static void setSkipSilence(boolean enabled) {
        if (instance == null || instance.player == null) return;
        instance.skipSilence = enabled;
        instance.player.setSkipSilenceEnabled(enabled);
    }
    public static boolean setPreamp(int percent) {
        return instance != null && instance.effects.setPreampAndReturn(percent);
    }

    public static void setMonoMode(boolean enable) {
        if (instance == null || instance.player == null || instance.channelMixer == null) return;
        instance.mono = enable;
        instance.applyChannelMix();
    }

    public static void setBalance(float value) {
        if (instance == null || instance.channelMixer == null) return;
        instance.balance = Math.max(-1f, Math.min(1f, value));
        if (!instance.mono) instance.applyChannelMix();
    }

    private void applyChannelMix() {
        if (channelMixer == null) return;
        try {
            if (mono) {
                channelMixer.putChannelMixingMatrix(
                        new ChannelMixingMatrix(2, 1, new float[]{0.5f, 0.5f}));
            } else {
                float left = balance < 0 ? 1f : 1f - balance;
                float right = balance > 0 ? 1f : 1f + balance;
                channelMixer.putChannelMixingMatrix(
                        new ChannelMixingMatrix(2, 2, new float[]{left, 0f, 0f, right}));
            }
        } catch (Throwable ignored) {}
    }

    private void skipAfterPlaybackError() {
        if (player == null || player.getMediaItemCount() == 0) return;
        try {
            int index = player.getCurrentMediaItemIndex();
            if (index >= 0 && index + 1 < player.getMediaItemCount()) {
                player.seekToNextMediaItem();
                player.prepare();
                player.play();
            } else {
                player.pause();
            }
        } catch (Throwable ignored) {}
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void setReverb(String name, int mixPercent) {
        if (instance == null || instance.player == null || instance.reverb == null) return;
        try {
            short preset = PresetReverb.PRESET_NONE;
            if ("Sala".equalsIgnoreCase(name)) preset = PresetReverb.PRESET_SMALLROOM;
            else if ("Studio".equalsIgnoreCase(name)) preset = PresetReverb.PRESET_MEDIUMROOM;
            else if ("Hall".equalsIgnoreCase(name)) preset = PresetReverb.PRESET_LARGEHALL;
            float send = Math.max(0f, Math.min(1f, mixPercent / 100f));
            if (preset == PresetReverb.PRESET_NONE || send <= 0f) {
                instance.reverb.setEnabled(false);
                instance.player.setAuxEffectInfo(new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f));
            } else {
                instance.reverb.setPreset(preset);
                instance.reverb.setEnabled(true);
                instance.player.setAuxEffectInfo(new AuxEffectInfo(instance.reverb.getId(), send));
            }
        } catch (Throwable ignored) {}
    }

    public static int toggleABRepeat() {
        if (instance == null || instance.player == null || instance.player.getCurrentMediaItem() == null) return -1;
        long position = Math.max(0L, instance.player.getCurrentPosition());
        if (instance.abStartMs < 0) {
            instance.abStartMs = position;
            instance.abEndMs = -1L;
            return 1;
        }
        if (instance.abEndMs < 0) {
            if (position <= instance.abStartMs) return 1;
            instance.abEndMs = position;
            return 2;
        }
        instance.abStartMs = -1L;
        instance.abEndMs = -1L;
        return 0;
    }

    public static int getABState() {
        if (instance == null) return 0;
        if (instance.abStartMs >= 0 && instance.abEndMs < 0) return 1;
        return instance.abStartMs >= 0 && instance.abEndMs > instance.abStartMs ? 2 : 0;
    }

    private void resetAB() {
        abStartMs = -1L;
        abEndMs = -1L;
    }

    public static void setSleepTimer(Context context, long minutes) {
        if (instance == null) return;
        long end = minutes <= 0L ? 0L : System.currentTimeMillis() + minutes * 60_000L;
        instance.sleepEndAtMs = end;
        context.getApplicationContext().getSharedPreferences("nexauren_sleep", MODE_PRIVATE)
                .edit().putLong("end_at", end).apply();
    }

    public static long getSleepTimerEndAt() {
        return instance == null ? 0L : instance.sleepEndAtMs;
    }

    public static void stop() {
        if (instance != null && instance.player != null) instance.player.stop();
    }

    public static void setPlaybackSpeedPitch(float speed, float pitch) {
        if (instance != null && instance.player != null) {
            instance.player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed, pitch));
        }
    }

    @Override public void onDestroy() {
        abHandler.removeCallbacks(abLoop);
        sleepHandler.removeCallbacks(sleepTimer);
        recoveryHandler.removeCallbacks(recoveryRunnable);
        resetAB();
        if (player != null) player.removeListener(audioListener);
        try { if (reverb != null) reverb.release(); } catch (Throwable ignored) {}
        reverb = null;
        effects.release();
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        if (player != null) { player.release(); player = null; }
        channelMixer = null;
        instance = null;
        super.onDestroy();
    }
}
