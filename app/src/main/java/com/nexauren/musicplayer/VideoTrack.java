package com.nexauren.musicplayer;

import android.net.Uri;

public final class VideoTrack {
    public final long id;
    public final String title;
    public final long durationMs;
    public final Uri uri;

    public VideoTrack(long id, String title, long durationMs, Uri uri) {
        this.id = id;
        this.title = title;
        this.durationMs = durationMs;
        this.uri = uri;
    }
}
