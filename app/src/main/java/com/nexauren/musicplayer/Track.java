package com.nexauren.musicplayer;

import android.net.Uri;

public final class Track {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final Uri uri;

    public Track(long id, String title, String artist, String album, long durationMs, Uri uri) {
        this.id = id; this.title = title; this.artist = artist; this.album = album; this.durationMs = durationMs; this.uri = uri;
    }
}
