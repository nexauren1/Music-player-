package com.nexauren.musicplayer;

import android.net.Uri;

import java.text.Normalizer;
import java.util.Locale;

public final class Track {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final Uri uri;
    public final long dateAdded;
    public final String searchKey;

    public Track(long id, String title, String artist, String album, long durationMs, Uri uri) {
        this(id, title, artist, album, durationMs, uri, -1L);
    }

    public Track(long id, String title, String artist, String album, long durationMs, Uri uri, long dateAdded) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.uri = uri;
        this.dateAdded = dateAdded;
        this.searchKey = buildSearchKey(title, artist, album);
    }

    public static String normalizeForSearch(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private static String buildSearchKey(String title, String artist, String album) {
        return normalizeForSearch(title) + " " + normalizeForSearch(artist) + " " + normalizeForSearch(album);
    }
}
