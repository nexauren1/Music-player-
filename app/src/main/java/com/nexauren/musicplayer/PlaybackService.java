package com.nexauren.musicplayer;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.audiofx.PresetReverb;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.C;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

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
    private final android.os.Handler abHandler = new android.os.Handler();
    private final Runnable abLoop = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying() && abStartMs >= 0 && abEndMs > abStartMs && player.getCurrentPosition() >= abEndMs) {
                player.seekTo(abStartMs);
            }
            abHandler.postDelayed(this, 120L);
        }
    };

    private final Player.Listener audioListener = new Player.Listener() {
        @Override public void onAudioSessionIdChanged(int audioSessionId) {
            if (audioSessionId > 0) effects.attachToSession(audioSessionId);
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
        player.addListener(audioListener);
        abHandler.post(abLoop);

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
        mediaSession = new MediaSession.Builder(this, player).setSessionActivity(pendingIntent).build();
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
        if (player == null || channelMixer == null) return;
        boolean playing = player.isPlaying();
        int index = Math.max(0, player.getCurrentMediaItemIndex());
        long position = Math.max(0, player.getCurrentPosition());
        try {
            if (mono) {
                channelMixer.putChannelMixingMatrix(new ChannelMixingMatrix(2, 1, new float[]{0.5f, 0.5f}));
            } else {
                float left = balance < 0 ? 1f : 1f - balance;
                float right = balance > 0 ? 1f : 1f + balance;
                channelMixer.putChannelMixingMatrix(new ChannelMixingMatrix(2, 2, new float[]{left, 0f, 0f, right}));
            }
            if (player.getMediaItemCount() > 0) {
                player.stop();
                player.prepare();
                player.seekTo(index, position);
                if (playing) player.play();
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
