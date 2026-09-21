package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO = 1001;
    private final List<Track> tracks = new ArrayList<>();
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final android.os.Handler handler = new android.os.Handler();
    private LinearLayout listContainer;
    private TextView countText, nowTitle, nowArtist, positionText, durationText;
    private Button playButton, previousButton, nextButton;
    private SeekBar seekBar;
    private ProgressBar loading;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private final Runnable progressTicker = new Runnable() {
        @Override public void run() { if (controller != null && controller.isConnected()) updateProgress(); handler.postDelayed(this, 500); }
    };
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackUi(); }
        @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { updatePlaybackUi(); }
        @Override public void onPlaybackStateChanged(int state) { updatePlaybackUi(); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); buildUi(); connectController(); requestNotificationPermission(); ensureAudioPermission(); handler.post(progressTicker);
    }
    private void buildUi() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        LinearLayout vertical = new LinearLayout(this); vertical.setOrientation(LinearLayout.VERTICAL); vertical.setPadding(dp(18), dp(12), dp(18), dp(12)); root.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
        TextView brand = text("NEXAUREN", 28, R.color.text_primary); brand.setTypeface(null, 1); vertical.addView(brand, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView subtitle = text("MUSIC PLAYER", 12, R.color.text_secondary); vertical.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(24)));
        countText = text("A carregar…", 14, R.color.text_secondary); vertical.addView(countText, new LinearLayout.LayoutParams(-1, dp(32)));
        ScrollView scroll = new ScrollView(this); listContainer = new LinearLayout(this); listContainer.setOrientation(LinearLayout.VERTICAL); scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2)); vertical.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout playerCard = new LinearLayout(this); playerCard.setOrientation(LinearLayout.VERTICAL); playerCard.setPadding(dp(12), dp(10), dp(12), dp(8)); playerCard.setBackgroundColor(ContextCompat.getColor(this, R.color.surface)); vertical.addView(playerCard, new LinearLayout.LayoutParams(-1, dp(170)));
        nowTitle = text("Nenhuma música", 17, R.color.text_primary); nowTitle.setTypeface(null, 1); playerCard.addView(nowTitle, new LinearLayout.LayoutParams(-1, dp(28)));
        nowArtist = text("Escolha uma faixa da sua biblioteca", 13, R.color.text_secondary); playerCard.addView(nowArtist, new LinearLayout.LayoutParams(-1, dp(24)));
        seekBar = new SeekBar(this); playerCard.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(40)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser && controller != null && controller.isConnected()) positionText.setText(formatMs(progress)); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { if (controller != null && controller.isConnected()) controller.seekTo(bar.getProgress()); }
        });
        LinearLayout times = new LinearLayout(this); times.setGravity(Gravity.CENTER_VERTICAL); positionText = text("0:00", 11, R.color.text_secondary); durationText = text("0:00", 11, R.color.text_secondary); times.addView(positionText, new LinearLayout.LayoutParams(0, dp(20), 1)); times.addView(durationText, new LinearLayout.LayoutParams(0, dp(20), 1)); durationText.setGravity(Gravity.END); playerCard.addView(times, new LinearLayout.LayoutParams(-1, dp(20)));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER); previousButton = button("⏮"); playButton = button("▶"); nextButton = button("⏭"); controls.addView(previousButton, new LinearLayout.LayoutParams(dp(64), dp(48))); controls.addView(playButton, new LinearLayout.LayoutParams(dp(72), dp(48))); controls.addView(nextButton, new LinearLayout.LayoutParams(dp(64), dp(48))); playerCard.addView(controls, new LinearLayout.LayoutParams(-1, dp(52)));
        previousButton.setOnClickListener(v -> { if (controller != null) controller.seekToPreviousMediaItem(); });
        nextButton.setOnClickListener(v -> { if (controller != null) controller.seekToNextMediaItem(); });
        playButton.setOnClickListener(v -> { if (controller == null || !controller.isConnected()) return; if (controller.isPlaying()) controller.pause(); else controller.play(); });
        loading = new ProgressBar(this); FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER); loading.setVisibility(View.GONE); root.addView(loading, lp); setContentView(root);
    }
    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> { try { controller = controllerFuture.get(); controller.addListener(playerListener); updatePlaybackUi(); } catch (Exception e) { Toast.makeText(this, "Não foi possível iniciar o áudio.", Toast.LENGTH_LONG).show(); } }, ContextCompat.getMainExecutor(this));
    }
    private void ensureAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadTracks(); else ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_AUDIO);
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
    }
    private void loadTracks() {
        loading.setVisibility(View.VISIBLE);
        queryExecutor.execute(() -> {
            ArrayList<Track> found = new ArrayList<>();
            String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
            try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " > 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
                if (c != null) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID), titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE), artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST), albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM), durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    while (c.moveToNext()) {
                        long id = c.getLong(idCol); String title = clean(c.getString(titleCol), "Sem título"); String artist = clean(c.getString(artistCol), "Artista desconhecido"); String album = clean(c.getString(albumCol), "Álbum desconhecido"); long duration = c.getLong(durationCol);
                        found.add(new Track(id, title, artist, album, duration, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)));
                    }
                }
            } catch (SecurityException ignored) { }
            runOnUiThread(() -> { tracks.clear(); tracks.addAll(found); loading.setVisibility(View.GONE); renderLibrary(); countText.setText(String.format(Locale.getDefault(), "%d faixas no dispositivo", tracks.size())); });
        });
    }
    private void renderLibrary() {
        listContainer.removeAllViews();
        if (tracks.isEmpty()) { TextView empty = text("Nenhuma música encontrada. Dê permissão para acessar o áudio do dispositivo.", 15, R.color.text_secondary); empty.setPadding(0, dp(28), 0, dp(28)); listContainer.addView(empty); return; }
        for (int i = 0; i < tracks.size(); i++) addTrackRow(tracks.get(i), i);
    }
    private void addTrackRow(Track track, int index) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), dp(8), dp(10), dp(8)); row.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        TextView num = text(String.valueOf(index + 1), 12, R.color.accent); num.setGravity(Gravity.CENTER); row.addView(num, new LinearLayout.LayoutParams(dp(34), dp(56)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); TextView title = text(track.title, 15, R.color.text_primary); title.setMaxLines(1); TextView artist = text(track.artist, 12, R.color.text_secondary); artist.setMaxLines(1); labels.addView(title, new LinearLayout.LayoutParams(-1, dp(30))); labels.addView(artist, new LinearLayout.LayoutParams(-1, dp(22))); row.addView(labels, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView duration = text(formatMs(track.durationMs), 11, R.color.text_secondary); row.addView(duration, new LinearLayout.LayoutParams(dp(58), dp(56))); row.setOnClickListener(v -> playIndex(index)); listContainer.addView(row, new LinearLayout.LayoutParams(-1, dp(74)));
    }
    private void playIndex(int index) {
        if (controller == null || !controller.isConnected() || tracks.isEmpty()) return;
        ArrayList<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) items.add(new MediaItem.Builder().setMediaId(String.valueOf(t.id)).setUri(t.uri).setMediaMetadata(new MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build());
        controller.setMediaItems(items, index, 0); controller.prepare(); controller.play();
    }
    private void updatePlaybackUi() {
        if (controller == null || !controller.isConnected()) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) { nowTitle.setText(item.mediaMetadata.title == null ? "Nexauren Music Player" : item.mediaMetadata.title.toString()); nowArtist.setText(item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString()); }
        playButton.setText(controller.isPlaying() ? "⏸" : "▶"); updateProgress();
    }
    private void updateProgress() {
        if (controller == null || !controller.isConnected()) return; long duration = Math.max(0, controller.getDuration()); long position = Math.max(0, controller.getCurrentPosition()); seekBar.setMax((int) Math.min(duration, Integer.MAX_VALUE)); seekBar.setProgress((int) Math.min(position, Integer.MAX_VALUE)); positionText.setText(formatMs(position)); durationText.setText(formatMs(duration));
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQUEST_AUDIO) { if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadTracks(); else { countText.setText("Acesso ao áudio não autorizado"); loading.setVisibility(View.GONE); } }
    }
    @Override protected void onDestroy() { handler.removeCallbacks(progressTicker); queryExecutor.shutdownNow(); if (controller != null) controller.removeListener(playerListener); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); super.onDestroy(); }
    private TextView text(String value, float size, int colorRes) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(ContextCompat.getColor(this, colorRes)); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(18); b.setTextColor(ContextCompat.getColor(this, R.color.text_primary)); b.setAllCaps(false); b.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_2)); return b; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static String clean(String value, String fallback) { return value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value) ? fallback : value.trim(); }
    private static String formatMs(long ms) { long total = Math.max(0, ms / 1000); return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60); }
}
