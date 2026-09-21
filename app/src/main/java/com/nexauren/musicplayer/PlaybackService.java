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
    public static boolean setPreamp(int percent) {
        return instance != null && instance.effects.setPreampAndReturn(percent);
    }

    public static void setMonoMode(boolean enable) {
        if (instance == null || instance.player == null || instance.channelMixer == null) return;
        instance.mono = enable;
        boolean playing = instance.player.isPlaying();
        int index = Math.max(0, instance.player.getCurrentMediaItemIndex());
        long position = Math.max(0, instance.player.getCurrentPosition());
        try {
            if (enable) {
                instance.channelMixer.putChannelMixingMatrix(
                        new ChannelMixingMatrix(2, 1, new float[]{0.5f, 0.5f}));
            } else {
                instance.channelMixer.putChannelMixingMatrix(
                        new ChannelMixingMatrix(2, 2, new float[]{1f, 0f, 0f, 1f}));
            }
            if (instance.player.getMediaItemCount() > 0) {
                instance.player.stop();
                instance.player.prepare();
                instance.player.seekTo(index, position);
                if (playing) instance.player.play();
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

    public static void setPlaybackSpeedPitch(float speed, float pitch) {
        if (instance != null && instance.player != null) {
            instance.player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed, pitch));
        }
    }

    @Override public void onDestroy() {
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
