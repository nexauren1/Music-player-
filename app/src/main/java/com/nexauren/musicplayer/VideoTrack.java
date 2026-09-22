package com.nexauren.musicplayer;

import android.net.Uri;

public final class VideoTrack {
    public final long id;
    public final String title;
    public final String folder;
    public final String mimeType;
    public final long sizeBytes;
    public final long durationMs;
    public final long dateAdded;
    public final int width;
    public final int height;
    public final Uri uri;

    public VideoTrack(long id,String title,String folder,String mimeType,long sizeBytes,long durationMs,long dateAdded,int width,int height,Uri uri){
        this.id=id;this.title=title;this.folder=folder;this.mimeType=mimeType;this.sizeBytes=sizeBytes;
        this.durationMs=durationMs;this.dateAdded=dateAdded;this.width=width;this.height=height;this.uri=uri;
    }
}
