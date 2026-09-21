package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlaybackStateStore {
    private static final String PREFS = "nexauren_playback_state";
    private PlaybackStateStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context c, List<Long> queueIds, long currentId, long positionMs,
                            int repeatMode, boolean shuffle, float speed, float pitch, float volume) {
        StringBuilder ids = new StringBuilder();
        for (Long id : queueIds) {
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        prefs(c).edit()
                .putString("queue", ids.toString())
                .putLong("current_id", currentId)
                .putLong("position_ms", Math.max(0, positionMs))
                .putInt("repeat_mode", repeatMode)
                .putBoolean("shuffle", shuffle)
                .putFloat("speed", speed)
                .putFloat("pitch", pitch)
                .putFloat("volume", volume)
                .apply();
    }

    public static List<Long> queue(Context c) {
        String raw = prefs(c).getString("queue", "");
        if (raw.isEmpty()) return Collections.emptyList();
        ArrayList<Long> out = new ArrayList<>();
        for (String item : raw.split(",")) {
            try { out.add(Long.parseLong(item)); } catch (Exception ignored) {}
        }
        return out;
    }

    public static long currentId(Context c) { return prefs(c).getLong("current_id", -1L); }
    public static long positionMs(Context c) { return prefs(c).getLong("position_ms", 0L); }
    public static int repeatMode(Context c) { return prefs(c).getInt("repeat_mode", 0); }
    public static boolean shuffle(Context c) { return prefs(c).getBoolean("shuffle", false); }
    public static float speed(Context c) { return prefs(c).getFloat("speed", 1f); }
    public static float pitch(Context c) { return prefs(c).getFloat("pitch", 1f); }
    public static float volume(Context c) { return prefs(c).getFloat("volume", 1f); }
}
