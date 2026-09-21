package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

public final class TagStore {
    private static final String PREFS = "nexauren_tags";
    private TagStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get(Context context, long id, String field, String fallback) {
        return prefs(context).getString(id + ":" + field, fallback);
    }

    public static void put(Context context, long id, String field, String value) {
        prefs(context).edit().putString(id + ":" + field, value == null ? "" : value).apply();
    }

    public static void save(Context context, long id, String title, String artist, String album,
                            String albumArtist, String genre, String year, String track,
                            String disc, String composer) {
        prefs(context).edit()
                .putString(id + ":title", title == null ? "" : title)
                .putString(id + ":artist", artist == null ? "" : artist)
                .putString(id + ":album", album == null ? "" : album)
                .putString(id + ":albumArtist", albumArtist == null ? "" : albumArtist)
                .putString(id + ":genre", genre == null ? "" : genre)
                .putString(id + ":year", year == null ? "" : year)
                .putString(id + ":track", track == null ? "" : track)
                .putString(id + ":disc", disc == null ? "" : disc)
                .putString(id + ":composer", composer == null ? "" : composer)
                .apply();
    }
}
