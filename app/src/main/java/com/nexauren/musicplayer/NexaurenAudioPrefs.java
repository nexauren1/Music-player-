package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

public final class NexaurenAudioPrefs {
    private static final String PREFS = "nexauren_audio_2";

    private NexaurenAudioPrefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean effects(Context c) { return p(c).getBoolean("effects", true); }
    public static void effects(Context c, boolean value) { p(c).edit().putBoolean("effects", value).apply(); }

    public static boolean skipSilence(Context c) { return p(c).getBoolean("skip_silence", false); }
    public static void skipSilence(Context c, boolean value) { p(c).edit().putBoolean("skip_silence", value).apply(); }

    public static boolean mono(Context c) { return p(c).getBoolean("mono", false); }
    public static void mono(Context c, boolean value) { p(c).edit().putBoolean("mono", value).apply(); }

    public static float balance(Context c) { return p(c).getFloat("balance", 0f); }
    public static void balance(Context c, float value) { p(c).edit().putFloat("balance", value).apply(); }

    public static int bass(Context c) { return p(c).getInt("bass", 0); }
    public static void bass(Context c, int value) { p(c).edit().putInt("bass", value).apply(); }

    public static int virtualizer(Context c) { return p(c).getInt("virtualizer", 0); }
    public static void virtualizer(Context c, int value) { p(c).edit().putInt("virtualizer", value).apply(); }

    public static int preamp(Context c) { return p(c).getInt("preamp", 0); }
    public static void preamp(Context c, int value) { p(c).edit().putInt("preamp", value).apply(); }

    public static String reverb(Context c) { return p(c).getString("reverb", "Sinal seco"); }
    public static int reverbMix(Context c) { return p(c).getInt("reverb_mix", 0); }
    public static void reverb(Context c, String name, int mix) {
        p(c).edit().putString("reverb", name).putInt("reverb_mix", mix).apply();
    }

    public static float speed(Context c) { return p(c).getFloat("speed", 1f); }
    public static void speed(Context c, float value) { p(c).edit().putFloat("speed", value).apply(); }
}
