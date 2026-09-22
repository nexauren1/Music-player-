package com.nexauren.musicplayer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoAdapter extends ListAdapter<VideoTrack, VideoAdapter.Holder> {
    public interface Listener {
        void onVideoClick(VideoTrack video);
        void onVideoMenu(View anchor, VideoTrack video);
    }

    private static final ExecutorService THUMB_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final LruCache<Long, Bitmap> THUMB_CACHE = new LruCache<Long, Bitmap>(12 * 1024) {
        @Override protected int sizeOf(@NonNull Long key, @NonNull Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private final Listener listener;

    public VideoAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<VideoTrack>() {
            @Override public boolean areItemsTheSame(@NonNull VideoTrack a, @NonNull VideoTrack b) {
                return a.id == b.id;
            }

            @Override public boolean areContentsTheSame(@NonNull VideoTrack a, @NonNull VideoTrack b) {
                return a.id == b.id && a.title.equals(b.title) && a.durationMs == b.durationMs
                        && a.sizeBytes == b.sizeBytes && a.dateAdded == b.dateAdded
                        && a.width == b.width && a.height == b.height;
            }
        });
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return getItem(position).id; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        float d = parent.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)(5*d), (int)(5*d), (int)(5*d), (int)(7*d));
        card.setBackground(round(0xFF121925, 18*d));

        FrameLayout media = new FrameLayout(parent.getContext());
        media.setBackground(round(0xFF202938, 16*d));
        media.setClipToOutline(true);

        ImageView thumb = new ImageView(parent.getContext());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setImageResource(R.drawable.music_placeholder);
        media.addView(thumb, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextSize(17);
        play.setTextColor(Color.WHITE);
        play.setGravity(Gravity.CENTER);
        play.setBackground(round(0xCC2196F3, 28*d));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams((int)(50*d), (int)(50*d), Gravity.CENTER);
        media.addView(play, pp);

        TextView menu = new TextView(parent.getContext());
        menu.setText("⋮");
        menu.setTextSize(21);
        menu.setTextColor(Color.WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(round(0xB8000000, 22*d));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams((int)(38*d), (int)(38*d), Gravity.TOP | Gravity.END);
        mp.setMargins(0, (int)(7*d), (int)(7*d), 0);
        media.addView(menu, mp);

        TextView duration = new TextView(parent.getContext());
        duration.setTextColor(Color.WHITE);
        duration.setTextSize(10);
        duration.setGravity(Gravity.CENTER);
        duration.setBackground(round(0xAA000000, 12*d));
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams((int)(58*d), (int)(24*d), Gravity.BOTTOM | Gravity.END);
        dp.setMargins(0, 0, (int)(7*d), (int)(7*d));
        media.addView(duration, dp);

        card.addView(media, new LinearLayout.LayoutParams(-1, (int)(118*d)));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(0xFFF4F7F3);
        title.setTextSize(14);
        title.setTypeface(null, 1);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title, new LinearLayout.LayoutParams(-1, (int)(30*d)));

        TextView meta = new TextView(parent.getContext());
        meta.setTextColor(0xFF9AA5B1);
        meta.setTextSize(10);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(meta, new LinearLayout.LayoutParams(-1, (int)(22*d)));

        return new Holder(card, media, thumb, title, meta, duration, play, menu);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        VideoTrack v = getItem(position);
        h.title.setText(v.title);
        h.meta.setText(v.folder + "  •  " + resolution(v.width, v.height) + "  •  " + size(v.sizeBytes));
        h.duration.setText(format(v.durationMs));

        h.thumb.setTag(v.id);
        Bitmap cached;
        synchronized (THUMB_CACHE) { cached = THUMB_CACHE.get(v.id); }
        if (cached != null) {
            h.thumb.setImageBitmap(cached);
        } else {
            h.thumb.setImageResource(R.drawable.music_placeholder);
            loadThumbnail(v, h.thumb);
        }

        h.itemView.setOnClickListener(view -> listener.onVideoClick(v));
        h.play.setOnClickListener(view -> listener.onVideoClick(v));
        h.menu.setOnClickListener(view -> listener.onVideoMenu(h.menu, v));
    }

    private void loadThumbnail(VideoTrack video, ImageView target) {
        THUMB_EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    bitmap = target.getContext().getContentResolver().loadThumbnail(
                            video.uri, new android.util.Size(640, 360), null);
                } else {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(target.getContext(), video.uri);
                        bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } finally {
                        try { retriever.release(); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            if (bitmap != null) synchronized (THUMB_CACHE) { THUMB_CACHE.put(video.id, bitmap); }
            Bitmap result = bitmap;
            target.post(() -> {
                Object tag = target.getTag();
                if (tag instanceof Long && ((Long) tag) == video.id && result != null) {
                    target.setImageBitmap(result);
                }
            });
        });
    }

    private static String resolution(int w, int h) {
        return w > 0 && h > 0 ? w + "×" + h : "Vídeo";
    }

    private static String size(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024f * 1024 * 1024));
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    private static android.graphics.drawable.GradientDrawable round(int c, float r) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(r);
        return d;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout media;
        final ImageView thumb;
        final TextView title;
        final TextView meta;
        final TextView duration;
        final TextView play;
        final TextView menu;

        Holder(View row, FrameLayout media, ImageView thumb, TextView title, TextView meta,
               TextView duration, TextView play, TextView menu) {
            super(row);
            this.media = media;
            this.thumb = thumb;
            this.title = title;
            this.meta = meta;
            this.duration = duration;
            this.play = play;
            this.menu = menu;
        }
    }
}
