package com.nexauren.musicplayer;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;

public final class AudioEffectsController {
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private LoudnessEnhancer loudnessEnhancer;
    private int sessionId = 0;

    public synchronized void attachToSession(int newSessionId) {
        if (newSessionId <= 0 || newSessionId == sessionId) return;
        release();
        sessionId = newSessionId;
        try {
            equalizer = new Equalizer(1000, sessionId);
            equalizer.setEnabled(true);
        } catch (Throwable ignored) {
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(1000, sessionId);
            bassBoost.setEnabled(false);
        } catch (Throwable ignored) {
            bassBoost = null;
        }
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            try {
                loudnessEnhancer = new LoudnessEnhancer(sessionId);
                loudnessEnhancer.setEnabled(true);
                loudnessEnhancer.setTargetGain(0);
            } catch (Throwable ignored) {
                loudnessEnhancer = null;
            }
        }
    }

    public synchronized boolean isAvailable() {
        return equalizer != null;
    }

    public synchronized int getBandCount() {
        return equalizer == null ? 0 : equalizer.getNumberOfBands();
    }

    public synchronized String[] getCenterFrequencies(int uiCount) {
        String[] result = new String[uiCount];
        if (equalizer == null) return result;
        for (int i = 0; i < uiCount; i++) {
            short band = mapUiBand(i, uiCount);
            int hz = equalizer.getCenterFreq(band) / 1000;
            result[i] = hz >= 1000 ? ((hz % 1000 == 0) ? (hz / 1000) + "k" : String.format(java.util.Locale.getDefault(), "%.1fk", hz / 1000f)) : String.valueOf(hz);
        }
        return result;
    }

    public synchronized int[] getLevelsPercent(int uiCount) {
        int[] values = new int[uiCount];
        if (equalizer == null) return values;
        short[] range = equalizer.getBandLevelRange();
        int min = range[0], max = range[1];
        for (int i = 0; i < uiCount; i++) {
            short band = mapUiBand(i, uiCount);
            int level = equalizer.getBandLevel(band);
            values[i] = max == min ? 50 : Math.round((level - min) * 100f / (max - min));
        }
        return values;
    }

    public synchronized void setUiBand(int uiIndex, int uiCount, int percent) {
        if (equalizer == null) return;
        short[] range = equalizer.getBandLevelRange();
        int min = range[0], max = range[1];
        int level = min + Math.round((max - min) * Math.max(0, Math.min(100, percent)) / 100f);
        equalizer.setBandLevel(mapUiBand(uiIndex, uiCount), (short) level);
    }

    public synchronized void applyPreset(String name, int uiCount) {
        if (equalizer == null) return;
        float[] db = presetValues(name, uiCount);
        short[] range = equalizer.getBandLevelRange();
        for (int i = 0; i < uiCount; i++) {
            int mb = Math.round(db[i] * 100f);
            mb = Math.max(range[0], Math.min(range[1], mb));
            equalizer.setBandLevel(mapUiBand(i, uiCount), (short) mb);
        }
    }

    public synchronized void setBass(int percent) {
        if (bassBoost == null) return;
        int p = Math.max(0, Math.min(100, percent));
        try {
            bassBoost.setStrength((short) Math.round(p * 10f));
            bassBoost.setEnabled(p > 0);
        } catch (Throwable ignored) {}
    }

    public synchronized void setPreamp(int percent) {
        if (loudnessEnhancer == null) return;
        int p = Math.max(0, Math.min(100, percent));
        int gainMb = Math.round((p - 50) * 80f);
        try {
            loudnessEnhancer.setTargetGain(gainMb);
            loudnessEnhancer.setEnabled(gainMb != 0);
        } catch (Throwable ignored) {}
    }

    private short mapUiBand(int uiIndex, int uiCount) {
        int actual = Math.max(1, getBandCount());
        if (uiCount <= 1) return 0;
        return (short) Math.min(actual - 1, Math.round(uiIndex * (actual - 1f) / (uiCount - 1f)));
    }

    private float[] presetValues(String name, int count) {
        float[] flat = new float[count];
        float[] source;
        if ("Rock".equalsIgnoreCase(name)) source = new float[]{4, 3, 1, 0, -1, 1, 3, 4, 4, 3};
        else if ("Jazz".equalsIgnoreCase(name)) source = new float[]{3, 2, 0, 2, 3, 3, 2, 1, 2, 3};
        else if ("Clássico".equalsIgnoreCase(name)) source = new float[]{4, 3, 2, 1, 0, 0, 2, 3, 4, 4};
        else if ("Bass".equalsIgnoreCase(name)) source = new float[]{6, 5, 4, 2, 1, 0, 0, 0, 0, 0};
        else source = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < count; i++) {
            flat[i] = source[Math.min(source.length - 1, Math.round(i * (source.length - 1f) / Math.max(1, count - 1f)))];
        }
        return flat;
    }

    public synchronized void release() {
        try { if (equalizer != null) equalizer.release(); } catch (Throwable ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Throwable ignored) {}
        try { if (loudnessEnhancer != null) loudnessEnhancer.release(); } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
        loudnessEnhancer = null;
    }
}
