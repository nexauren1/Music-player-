package com.nexauren.musicplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated Nexauren Music Player 2.0 "Now Playing" screen.
 *
 * It is intentionally separate from the legacy NowPlayingActivity so the
 * existing stable player UI remains untouched while the new shell evolves.
 * Playback continues to use the existing Media3 PlaybackService.
 */
public final class NexaurenNowPlayingActivity extends AppCompatActivity {
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout content;
    private ImageView artwork;
    private TextView trackTitle;
    private TextView trackArtist;
    private TextView currentTime;
    private TextView totalTime;
    private TextView playPause;
    private TextView repeat;
    private TextView shuffle;
    private SeekBar progress;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int accent;
    private boolean dragging;
    private boolean repeatOne;
    private boolean shuffleEnabled;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updatePlaybackUi();
            handler.postDelayed(this, 400L);
        }
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackUi(); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) {
            updatePlaybackUi();
            loadCurrentArtwork();
            rebuildQueue();
        }
        @Override public void onPlaybackStateChanged(int state) { updatePlaybackUi(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        accent = AppearanceStore.accent(this);
        if (accent == 0) accent = AppearanceStore.BLUE;

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(8, 19, 38));
        w.setNavigationBarColor(Color.rgb(8, 19, 38));
        if (Build.VERSION.SDK_INT >= 26) w.getDecorView().setSystemUiVisibility(0);

        build();
        connectController();
        handler.post(ticker);
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 19, 38));
        setContentView(root);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(6), 0, dp(6), 0);
        toolbar.setBackgroundColor(Color.rgb(8, 19, 38));

        TextView back = topIcon("‹");
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        back.setOnClickListener(v -> finish());

        TextView heading = text("Agora tocando", 18, Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, dp(56), 1));

        TextView more = topIcon("⋮");
        toolbar.addView(more, new LinearLayout.LayoutParams(dp(45), dp(56)));
        more.setOnClickListener(v -> showPlayerMenu());

        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.music_placeholder);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(round(Color.rgb(26, 39, 60), 24));
        artwork.setClipToOutline(true);
        content.addView(artwork, new LinearLayout.LayoutParams(dp(300), dp(300)));

        trackTitle = text("Nenhuma música", 22, Color.WHITE);
        trackTitle.setGravity(Gravity.CENTER);
        trackTitle.setTypeface(null, 1);
        trackTitle.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(58));
        titleLp.topMargin = dp(16);
        content.addView(trackTitle, titleLp);

        trackArtist = text("Selecione uma faixa para começar", 13, 0xFFB9C7DA);
        trackArtist.setGravity(Gravity.CENTER);
        trackArtist.setSingleLine(true);
        content.addView(trackArtist, new LinearLayout.LayoutParams(-1, dp(30)));

        progress = new SeekBar(this);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(-1, dp(46));
        seekLp.topMargin = dp(12);
        content.addView(progress, seekLp);

        LinearLayout times = new LinearLayout(this);
        currentTime = text("0:00", 11, 0xFFB9C7DA);
        totalTime = text("0:00", 11, 0xFFB9C7DA);
        times.addView(currentTime, new LinearLayout.LayoutParams(0, dp(24), 1));
        totalTime.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        times.addView(totalTime, new LinearLayout.LayoutParams(0, dp(24), 1));
        content.addView(times, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, dp(8));

        shuffle = control("🔀", 20);
        repeat = control("↻", 20);
        TextView previous = control("⏮", 28);
        playPause = control("▶", 31);
        TextView next = control("⏭", 28);

        controls.addView(shuffle, controlLp(54));
        controls.addView(previous, controlLp(62));
        controls.addView(playPause, mainControlLp());
        controls.addView(next, controlLp(62));
        controls.addView(repeat, controlLp(54));
        content.addView(controls, new LinearLayout.LayoutParams(-1, dp(92)));

        TextView queueTitle = text("Fila de reprodução", 15, Color.WHITE);
        queueTitle.setTypeface(null, 1);
        queueTitle.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(queueTitle, new LinearLayout.LayoutParams(-1, dp(40)));

        seekBehavior(previous, next);
        playPause.setOnClickListener(v -> togglePlay());
        shuffle.setOnClickListener(v -> toggleShuffle());
        repeat.setOnClickListener(v -> toggleRepeat());

        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && controller != null && controller.isConnected() && controller.getDuration() > 0) {
                    long position = controller.getDuration() * value / 1000L;
                    currentTime.setText(formatMs(position));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (controller != null && controller.isConnected() && controller.getDuration() > 0) {
                    controller.seekTo(controller.getDuration() * bar.getProgress() / 1000L);
                }
            }
        });

        rebuildQueue();
    }

    private void seekBehavior(TextView previous, TextView next) {
        previous.setOnClickListener(v -> {
            if (controller != null && controller.isConnected()) controller.seekToPreviousMediaItem();
        });
        next.setOnClickListener(v -> {
            if (controller != null && controller.isConnected()) controller.seekToNextMediaItem();
        });
    }

    private void rebuildQueue() {
        if (content == null) return;

        while (content.getChildCount() > 7) {
            content.removeViewAt(7);
        }

        if (controller == null || !controller.isConnected() || controller.getMediaItemCount() == 0) {
            TextView empty = text("A fila está vazia.", 12, 0xFF8899AD);
            empty.setPadding(dp(8), dp(6), dp(8), dp(12));
            content.addView(empty);
            return;
        }

        int current = controller.getCurrentMediaItemIndex();
        int total = controller.getMediaItemCount();
        int end = Math.min(total, current + 5);

        for (int i = current; i < end; i++) {
            MediaItem item = controller.getMediaItemAt(i);
            CharSequence title = item.mediaMetadata.title;
            CharSequence artist = item.mediaMetadata.artist;

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(5), dp(10), dp(5));
            row.setBackground(round(i == current ? 0xFF142D54 : 0xFF10233D, 16));

            TextView number = text(i == current ? "▶" : String.valueOf(i + 1), 14, i == current ? Color.WHITE : 0xFF91A6BF);
            number.setGravity(Gravity.CENTER);
            row.addView(number, new LinearLayout.LayoutParams(dp(40), dp(52)));

            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(title == null ? "Faixa sem título" : title.toString(), 13, Color.WHITE);
            n.setTypeface(null, i == current ? 1 : 0);
            TextView a = text(artist == null ? "Artista desconhecido" : artist.toString(), 10, 0xFF9DB0C7);
            meta.addView(n, new LinearLayout.LayoutParams(-1, dp(26)));
            meta.addView(a, new LinearLayout.LayoutParams(-1, dp(20)));
            row.addView(meta, new LinearLayout.LayoutParams(0, dp(54), 1));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
            lp.bottomMargin = dp(6);
            content.addView(row, lp);
        }
    }

    private void connectController() {
        SessionToken token = new SessionToken(this, new android.content.ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(listener);
                repeatOne = controller.getRepeatMode() == Player.REPEAT_MODE_ONE;
                shuffleEnabled = controller.getShuffleModeEnabled();
                updatePlaybackUi();
                loadCurrentArtwork();
                rebuildQueue();
            } catch (Exception ignored) {}
        }, getMainExecutor());
    }

    private void updatePlaybackUi() {
        if (controller == null || !controller.isConnected()) return;

        CharSequence t = controller.getMediaMetadata().title;
        CharSequence a = controller.getMediaMetadata().artist;
        trackTitle.setText(t == null || t.length() == 0 ? "Nenhuma música" : t);
        trackArtist.setText(a == null || a.length() == 0 ? "Selecione uma faixa para começar" : a);
        playPause.setText(controller.isPlaying() ? "Ⅱ" : "▶");

        long duration = Math.max(0L, controller.getDuration());
        long position = Math.max(0L, controller.getCurrentPosition());
        totalTime.setText(formatMs(duration));
        currentTime.setText(formatMs(position));

        if (!dragging && duration > 0L) {
            progress.setProgress((int) Math.min(1000L, position * 1000L / duration));
        }

        repeat.setText(repeatOne ? "↻1" : "↻");
        shuffle.setAlpha(shuffleEnabled ? 1f : 0.55f);
        repeat.setAlpha(repeatOne ? 1f : 0.55f);
    }

    private void loadCurrentArtwork() {
        if (controller == null || !controller.isConnected()) return;
        final Uri uri;
        try {
            uri = controller.getCurrentMediaItem() == null
                    || controller.getCurrentMediaItem().localConfiguration == null
                    ? null
                    : controller.getCurrentMediaItem().localConfiguration.uri;
        } catch (Throwable ignored) {
            return;
        }
        if (uri == null) {
            artwork.setImageResource(R.drawable.music_placeholder);
            return;
        }

        artworkExecutor.execute(() -> {
            Bitmap bmp = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                byte[] data = retriever.getEmbeddedPicture();
                if (data != null) bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Throwable ignored) {
            } finally {
                try { retriever.release(); } catch (Throwable ignored) {}
            }
            Bitmap result = bmp;
            runOnUiThread(() -> artwork.setImageBitmap(result != null ? result : BitmapFactory.decodeResource(getResources(), R.drawable.music_placeholder)));
        });
    }

    private void togglePlay() {
        if (controller == null || !controller.isConnected()) return;
        if (controller.isPlaying()) controller.pause(); else controller.play();
    }

    private void toggleShuffle() {
        if (controller == null || !controller.isConnected()) return;
        shuffleEnabled = !shuffleEnabled;
        controller.setShuffleModeEnabled(shuffleEnabled);
        updatePlaybackUi();
    }

    private void toggleRepeat() {
        if (controller == null || !controller.isConnected()) return;
        repeatOne = !repeatOne;
        controller.setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        updatePlaybackUi();
    }

    private void showPlayerMenu() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Player")
                .setItems(new String[]{"Equalizador", "Visualizador", "Interface clássica"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(this, MainActivity.class);
                        i.putExtra("page", "equalizer");
                        startActivity(i);
                    } else if (which == 1) {
                        startActivity(new Intent(this, VisualizerActivity.class));
                    } else {
                        startActivity(new Intent(this, NowPlayingActivity.class));
                    }
                }).show();
    }

    private TextView control(String value, int size) {
        TextView t = text(value, size, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView topIcon(String value) { return text(value, 28, Color.WHITE); }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams controlLp(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(66));
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams mainControlLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
        lp.setMargins(dp(5), 0, dp(5), 0);
        GradientDrawable bg = round(accent, 40);
        playPause.setBackground(bg);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatMs(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long min = sec / 60L;
        long rem = sec % 60L;
        return String.format(java.util.Locale.ROOT, "%d:%02d", min, rem);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (controller != null) controller.removeListener(listener);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }
}
