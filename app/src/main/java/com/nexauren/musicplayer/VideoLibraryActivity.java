package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoLibraryActivity extends AppCompatActivity {
    private static final int REQ_VIDEO = 9001;
    private static final int REQ_DELETE_VIDEO = 9301;

    private volatile List<VideoTrack> allVideos = java.util.Collections.emptyList();
    private final LinkedHashMap<String, Integer> folderCounts = new LinkedHashMap<>();
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();
    private final Handler filterHandler = new Handler(Looper.getMainLooper());
    private ContentObserver mediaObserver;
    private int filterGeneration = 0;
    private RecyclerView recycler;
    private VideoAdapter adapter;
    private TextView count;
    private EditText search;
    private int sortMode = 0;
    private String folderFilter = "";
    private GridLayoutManager gridManager;
    private boolean compactGrid = false;
    private LinearLayout folderChips;

    private final Runnable filterRunnable = () -> {
        final String query = search == null ? "" : search.getText().toString();
        final String activeFolder = folderFilter;
        final int generation = ++filterGeneration;
        final List<VideoTrack> snapshot = allVideos;
        filterExecutor.execute(() -> {
            String normalizedQuery = Track.normalizeForSearch(query);
            ArrayList<VideoTrack> list = new ArrayList<>();
            for (VideoTrack v : snapshot) {
                if ((normalizedQuery.isEmpty()
                        || Track.normalizeForSearch(v.title).contains(normalizedQuery)
                        || Track.normalizeForSearch(v.folder).contains(normalizedQuery))
                        && (activeFolder.isEmpty() || activeFolder.equals(v.folder))) {
                    list.add(v);
                }
            }
            if (sortMode == 1) {
                list.sort(Comparator.comparing(v -> Track.normalizeForSearch(v.title)));
            } else if (sortMode == 2) {
                list.sort((a, b) -> Long.compare(b.durationMs, a.durationMs));
            } else {
                list.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));
            }
            runOnUiThread(() -> {
                if (generation != filterGeneration || isFinishing()) return;
                adapter.submitList(list);
                count.setText(list.size() + " " + (list.size() == 1 ? "vídeo" : "vídeos"));
            });
        });
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(33, 150, 243));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        registerMediaObserver();
        ensurePermission();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080B10);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5), 0, dp(5), 0);
        bar.setBackgroundColor(Color.rgb(33, 150, 243));

        TextView back = text("‹", 38, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(58)));
        back.setOnClickListener(v -> finish());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Vídeos", 19, Color.WHITE);
        title.setTypeface(null, 1);
        count = text("A carregar…", 10, 0xD9FFFFFF);
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        heading.addView(count, new LinearLayout.LayoutParams(-1, dp(18)));
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView folder = text("▰", 23, Color.WHITE);
        folder.setGravity(Gravity.CENTER);
        bar.addView(folder, new LinearLayout.LayoutParams(dp(44), dp(58)));
        folder.setOnClickListener(v -> showFolderSummary());

        TextView layout = text("▦", 23, Color.WHITE);
        layout.setGravity(Gravity.CENTER);
        bar.addView(layout, new LinearLayout.LayoutParams(dp(44), dp(58)));
        layout.setOnClickListener(v -> toggleLayout(layout));

        TextView sort = text("⇅", 25, Color.WHITE);
        sort.setGravity(Gravity.CENTER);
        bar.addView(sort, new LinearLayout.LayoutParams(dp(48), dp(58)));
        sort.setOnClickListener(v -> cycleSort());

        root.addView(bar);

        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setPadding(dp(10), dp(8), dp(10), dp(4));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar vídeos…");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF7F8A97);
        search.setTextSize(14);
        search.setBackground(round(0xFF141B25, 18));
        search.setPadding(dp(14), 0, dp(14), 0);
        searchWrap.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(searchWrap);

        HorizontalScrollView foldersScroll = new HorizontalScrollView(this);
        foldersScroll.setHorizontalScrollBarEnabled(false);
        folderChips = new LinearLayout(this);
        folderChips.setPadding(dp(8), dp(2), dp(8), dp(5));
        foldersScroll.addView(folderChips, new ViewGroup.LayoutParams(-2, dp(42)));
        root.addView(foldersScroll, new LinearLayout.LayoutParams(-1, dp(45)));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { scheduleFilter(); }
            public void afterTextChanged(Editable e) {}
        });

        recycler = new RecyclerView(this);
        int columns = getResources().getConfiguration().screenWidthDp >= 720 ? 3 : 2;
        gridManager = new GridLayoutManager(this, columns);
        recycler.setLayoutManager(gridManager);
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(12);
        recycler.setItemAnimator(null);

        adapter = new VideoAdapter(new VideoAdapter.Listener() {
            @Override public void onVideoClick(VideoTrack video) {
                startActivity(new Intent(VideoLibraryActivity.this, VideoPlayerActivity.class)
                        .putExtra("uri", video.uri.toString())
                        .putExtra("title", video.title));
            }

            @Override public void onVideoMenu(View anchor, VideoTrack video) {
                showVideoMenu(anchor, video);
            }
        });
        recycler.setAdapter(adapter);
        recycler.setPadding(dp(7), dp(5), dp(7), dp(12));
        recycler.setClipToPadding(false);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void scheduleFilter() {
        filterHandler.removeCallbacks(filterRunnable);
        filterHandler.postDelayed(filterRunnable, 120L);
    }

    private void rebuildFolderChips() {
        if (folderChips == null) return;
        folderChips.removeAllViews();

        TextView all = chip("Todos", folderFilter.isEmpty());
        folderChips.addView(all, new LinearLayout.LayoutParams(dp(78), dp(36)));
        all.setOnClickListener(v -> {
            folderFilter = "";
            scheduleFilter();
            rebuildFolderChips();
        });

        int shown = 0;
        for (String name : folderCounts.keySet()) {
            if (shown++ >= 18) break;
            TextView chip = chip(name, folderFilter.equals(name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(36));
            lp.setMargins(dp(4), 0, dp(4), 0);
            folderChips.addView(chip, lp);
            chip.setOnClickListener(v -> {
                folderFilter = name;
                scheduleFilter();
                rebuildFolderChips();
            });
        }
    }

    private TextView chip(String label, boolean selected) {
        TextView t = text(label, 11, selected ? Color.WHITE : 0xFFD2D8DF);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setBackground(round(selected ? Color.rgb(33, 150, 243) : 0xFF141B25, 18));
        t.setPadding(dp(8), 0, dp(8), 0);
        return t;
    }

    private void toggleLayout(TextView button) {
        compactGrid = !compactGrid;
        int width = getResources().getConfiguration().screenWidthDp;
        int columns = compactGrid ? (width >= 720 ? 4 : 3) : (width >= 720 ? 3 : 2);
        gridManager.setSpanCount(columns);
        button.setText(compactGrid ? "▤" : "▦");
    }

    private void showFolderSummary() {
        if (folderCounts.isEmpty()) {
            Toast.makeText(this, "Nenhuma pasta de vídeos encontrada.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = folderCounts.keySet().toArray(new String[0]);
        String[] labels = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            labels[i] = names[i] + "  •  " + folderCounts.get(names[i]) + " vídeos";
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Pastas de vídeos")
                .setItems(labels, (d, which) -> {
                    folderFilter = names[which];
                    search.setText("");
                    rebuildFolderChips();
                    scheduleFilter();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showVideoMenu(View anchor, VideoTrack video) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add("Reproduzir");
        menu.getMenu().add("Enviar");
        menu.getMenu().add("Detalhes");
        menu.getMenu().add("Eliminar");
        menu.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if ("Reproduzir".equals(action)) {
                startActivity(new Intent(this, VideoPlayerActivity.class)
                        .putExtra("uri", video.uri.toString())
                        .putExtra("title", video.title));
            } else if ("Enviar".equals(action)) {
                shareVideo(video);
            } else if ("Detalhes".equals(action)) {
                showVideoDetails(video);
            } else {
                deleteVideo(video);
            }
            return true;
        });
        menu.show();
    }

    private void shareVideo(VideoTrack video) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(video.mimeType == null || video.mimeType.isEmpty() ? "video/*" : video.mimeType);
        send.putExtra(Intent.EXTRA_STREAM, video.uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "Enviar vídeo"));
        } catch (Exception e) {
            Toast.makeText(this, "Não existe app para partilhar vídeo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoDetails(VideoTrack video) {
        String resolution = video.width > 0 && video.height > 0
                ? video.width + " × " + video.height : "Desconhecida";
        String message = "Pasta: " + safe(video.folder, "Vídeos")
                + "\nFormato: " + safe(video.mimeType, "video/*")
                + "\nResolução: " + resolution
                + "\nDuração: " + formatMs(video.durationMs)
                + "\nTamanho: " + formatSize(video.sizeBytes);
        new android.app.AlertDialog.Builder(this)
                .setTitle(video.title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void deleteVideo(VideoTrack video) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                ArrayList<android.net.Uri> uris = new ArrayList<>();
                uris.add(video.uri);
                startIntentSenderForResult(
                        MediaStore.createDeleteRequest(getContentResolver(), uris).getIntentSender(),
                        REQ_DELETE_VIDEO, null, 0, 0, 0, null);
            } else {
                int deleted = getContentResolver().delete(video.uri, null, null);
                if (deleted > 0) loadVideos();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível eliminar o vídeo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleSort() {
        sortMode = (sortMode + 1) % 3;
        if (sortMode == 0) Toast.makeText(this, "Ordenar: mais recentes", Toast.LENGTH_SHORT).show();
        else if (sortMode == 1) Toast.makeText(this, "Ordenar: nome", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "Ordenar: maior duração", Toast.LENGTH_SHORT).show();
        scheduleFilter();
    }

    private void ensurePermission() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) loadVideos();
        else ActivityCompat.requestPermissions(this, new String[]{p}, REQ_VIDEO);
    }

    private void loadVideos() {
        new Thread(() -> {
            ArrayList<VideoTrack> result = new ArrayList<>();
            String[] projection = {
                    MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT
            };
            final int pageSize = 1000;
            int offset = 0;
            try {
                while (true) {
                    android.os.Bundle args = new android.os.Bundle();
                    args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, MediaStore.Video.Media.DURATION + " > 0");
                    args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Video.Media.DATE_ADDED + " DESC");
                    args.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, pageSize);
                    args.putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset);
                    int pageCount = 0;
                    try (android.database.Cursor c = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, args, null)) {
                        if (c == null) break;
                        int id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                        int title = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                        int folder = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                        int mime = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);
                        int size = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                        int dur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                        int date = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                        int w = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                        int h = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);

                        while (c.moveToNext()) {
                            long videoId = c.getLong(id);
                            result.add(new VideoTrack(
                                    videoId,
                                    safe(c.getString(title), "Sem título"),
                                    safe(c.getString(folder), "Vídeos"),
                                    safe(c.getString(mime), "video/*"),
                                    c.getLong(size),
                                    c.getLong(dur),
                                    c.getLong(date),
                                    c.getInt(w),
                                    c.getInt(h),
                                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)));
                            pageCount++;
                        }
                    }
                    if (pageCount == 0) break;
                    offset += pageCount;
                    if (result.size() % 5000 < pageCount) {
                        final int loaded = result.size();
                        runOnUiThread(() -> count.setText("A indexar " + loaded + " vídeos…"));
                    }
                    if (pageCount < pageSize) break;
                }
            } catch (Exception ignored) {}

            final ArrayList<VideoTrack> immutableResult = result;
            runOnUiThread(() -> {
                allVideos = immutableResult;
                folderCounts.clear();
                for (VideoTrack v : immutableResult) {
                    folderCounts.put(v.folder, folderCounts.getOrDefault(v.folder, 0) + 1);
                }
                rebuildFolderChips();
                scheduleFilter();
                if (immutableResult.isEmpty()) {
                    Toast.makeText(this, "Nenhum vídeo encontrado.", Toast.LENGTH_SHORT).show();
                }
            });
        }, "nexauren-video-query").start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_VIDEO && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else if (requestCode == REQ_VIDEO) {
            Toast.makeText(this, "Acesso aos vídeos não autorizado.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DELETE_VIDEO && resultCode == RESULT_OK) loadVideos();
    }

    @Override protected void onDestroy() {
        filterHandler.removeCallbacksAndMessages(null);
        filterExecutor.shutdownNow();
        if (mediaObserver != null) {
            try { getContentResolver().unregisterContentObserver(mediaObserver); } catch (Throwable ignored) {}
            mediaObserver = null;
        }
        super.onDestroy();
    }

    private void registerMediaObserver() {
        if (mediaObserver != null) return;
        mediaObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, android.net.Uri uri) {
                scheduleReload();
            }
        };
        try {
            getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        } catch (Throwable ignored) {}
    }

    private final Runnable reloadRunnable = () -> {
        if (!isFinishing()) loadVideos();
    };

    private void scheduleReload() {
        filterHandler.removeCallbacks(reloadRunnable);
        filterHandler.postDelayed(reloadRunnable, 700L);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String formatMs(long ms) {
        long total = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024L) + " KB";
        if (bytes < 1024L * 1024L * 1024L)
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024f * 1024f * 1024f));
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private android.graphics.drawable.GradientDrawable round(int c, int r) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(r));
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }
}
