package com.nexauren.musicplayer;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

public final class FavoritesStore {
    private static final String PREF = "nexauren_favorites";
    private static final String KEY = "ids";
    private FavoritesStore() {}

    public static Set<String> get(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(KEY, new HashSet<>()));
    }

    public static boolean isFavorite(Context context, long id) {
        return get(context).contains(String.valueOf(id));
    }

    public static boolean toggle(Context context, long id) {
        Set<String> ids = get(context);
        String key = String.valueOf(id);
        boolean nowFavorite;
        if (ids.contains(key)) { ids.remove(key); nowFavorite = false; }
        else { ids.add(key); nowFavorite = true; }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet(KEY, ids).apply();
        return nowFavorite;
    }
}
