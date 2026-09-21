package com.nexauren.musicplayer;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

public final class AudioEffectsController {
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private LoudnessEnhancer loudnessEnhancer;
    private Virtualizer virtualizer;
    private int sessionId = 0;
    private boolean enabled = true;

    public synchronized void attachToSession(int newSessionId) {
        if (newSessionId <= 0 || newSessionId == sessionId) return;
        release();
        sessionId = newSessionId;
        try {
            equalizer = new Equalizer(1000, sessionId);
            equalizer.setEnabled(enabled);
            // Start at a true flat 0 dB curve instead of the device driver's arbitrary default.
            short[] range = equalizer.getBandLevelRange();
            short flat = (short) Math.max(range[0], Math.min(range[1], 0));
            for (short b = 0; b < equalizer.getNumberOfBands(); b++) {
                try { equalizer.setBandLevel(b, flat); } catch (Throwable ignored) {}
            }
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
                loudnessEnhancer.setEnabled(enabled);
                loudnessEnhancer.setTargetGain(0);
            } catch (Throwable ignored) {
                loudnessEnhancer = null;
            }
            try {
                virtualizer = new Virtualizer(1000, sessionId);
                virtualizer.setStrength((short) 0);
                virtualizer.setEnabled(false);
            } catch (Throwable ignored) {
                virtualizer = null;
            }
        }
    }

    public synchronized boolean isAvailable() {
        return equalizer != null;
    }

    public synchronized void setEnabled(boolean value) {
        enabled = value;
        try { if (equalizer != null) equalizer.setEnabled(value); } catch (Throwable ignored) {}
        try { if (bassBoost != null) bassBoost.setEnabled(value && bassBoost.getRoundedStrength() > 0); } catch (Throwable ignored) {}
        try { if (loudnessEnhancer != null) loudnessEnhancer.setEnabled(value); } catch (Throwable ignored) {}
        try { if (virtualizer != null) virtualizer.setEnabled(value && virtualizer.getRoundedStrength() > 0); } catch (Throwable ignored) {}
    }

    public synchronized boolean isEnabled() { return enabled; }

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

    public synchronized boolean setUiBandAndReturn(int uiIndex, int uiCount, int percent) {
        if (equalizer == null) return false;
        setUiBand(uiIndex, uiCount, percent);
        return true;
    }

    public synchronized boolean applyPresetAndReturn(String name, int uiCount) {
        if (equalizer == null) return false;
        applyPreset(name, uiCount);
        return true;
    }

    public synchronized boolean setBassAndReturn(int percent) {
        if (bassBoost == null) return false;
        setBass(percent);
        return true;
    }

    public synchronized boolean setPreampAndReturn(int percent) {
        if (loudnessEnhancer == null) return false;
        setPreamp(percent);
        return true;
    }

    public synchronized boolean setVirtualizerAndReturn(int percent) {
        if (virtualizer == null) return false;
        int p = Math.max(0, Math.min(100, percent));
        try {
            virtualizer.setStrength((short) Math.round(p * 10f));
            virtualizer.setEnabled(enabled && p > 0);
        } catch (Throwable ignored) {}
        return true;
    }

    public synchronized void resetAll() {
        applyPreset("Plano", 10);
        setBass(0);
        setPreamp(50);
        setVirtualizerAndReturn(0);
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
            bassBoost.setEnabled(enabled && p > 0);
        } catch (Throwable ignored) {}
    }

    public synchronized void setPreamp(int percent) {
        if (loudnessEnhancer == null) return;
        int p = Math.max(0, Math.min(100, percent));
        int gainMb = Math.round((p - 50) * 80f);
        try {
            loudnessEnhancer.setTargetGain(gainMb);
            loudnessEnhancer.setEnabled(enabled && gainMb != 0);
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
        try { if (virtualizer != null) virtualizer.release(); } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
        loudnessEnhancer = null;
        virtualizer = null;
    }
}
