package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nexauren Music Player 2.0 visual shell.
 *
 * This activity is intentionally additive: the existing MainActivity,
 * PlaybackService and stores remain untouched. It reuses the same Media3
 * playback service and MediaStore library so the stable playback base is
 * preserved while the UI evolves independently.
 */
public final class NexaurenModernActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO = 9201;

    private final List<Song> songs = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private FrameLayout drawer;
    private LinearLayout page;
    private LinearLayout content;
    private TextView title;
    private ImageView miniArt;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView miniPlay;
    private TextView songCount;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int accent;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { runOnUiThread(() -> updateMini()); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { runOnUiThread(() -> updateMini()); }
        @Override public void onPlaybackStateChanged(int state) { runOnUiThread(() -> updateMini()); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        accent = AppearanceStore.accent(this);
        if (accent == 0) accent = AppearanceStore.BLUE;
        Window window = getWindow();
        window.setStatusBarColor(accent);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        buildShell();
        connectController();
        if (hasAudioPermission()) loadSongs(); else requestAudioPermission();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 249, 253));
        setContentView(root);
        showHome();
    }

    private void showHome() {
        buildPage("Nexauren Music Player");
        addHero();
        addQuickActions();
        addSectionHeader(NexaurenLanguageStore.t(this,"recent"), "Ver tudo", v -> showLibrary());
        addSongList(6);
    }

    private void showLibrary() {
        buildPage(NexaurenLanguageStore.t(this,"library"));
        addSectionHeader(NexaurenLanguageStore.t(this,"library"), songs.isEmpty() ? "…" : songs.size() + " músicas", null);
        addLibraryCard("🔥", NexaurenLanguageStore.t(this,"most_played"), "As músicas que você mais ouve", v -> showSongList(NexaurenLanguageStore.t(this,"most_played")));
        addLibraryCard("◷", NexaurenLanguageStore.t(this,"recent"), "Reproduzidas recentemente", v -> showSongList(NexaurenLanguageStore.t(this,"recent")));
        addLibraryCard("☷", "Playlists", "Suas playlists criadas", v -> showPlaylists());
        addLibraryCard("✦", "Sugestões", "Baseadas no seu gosto", v -> showSongList("Sugestões"));
        addLibraryCard("♥", NexaurenLanguageStore.t(this,"favorites"), "Suas músicas favoritas", v -> showFavorites());
    }

    private void showPlaylists() {
        buildPage(NexaurenLanguageStore.t(this,"playlists"));
        addCreateCard();
        String[] names = {"Minhas Favoritas", "Chill Vibes", "Afro House", "Workout", "Relax"};
        String[] counts = {"25 músicas", "18 músicas", "32 músicas", "22 músicas", "16 músicas"};
        for (int i = 0; i < names.length; i++) addPlaylistRow(names[i], counts[i], i);
    }

    private void showSettings() {
        startActivity(new Intent(this, NexaurenSettingsActivity.class));
    }

    private void showLegacySettings() {
        buildPage("Configurações");
        addSetting("Conta", "Nexauren Music Player", "●", null);
        addSetting("Notificações", "Controles de reprodução", "♟", null);
        addSetting("Aparência", "Tema e fundo", "◐", v -> startActivity(new Intent(this, AppearanceSetupActivity.class).putExtra("edit", true)));
        addSetting("Qualidade de áudio", "Alta", "♫", v -> openClassic("equalizer"));
        addSetting("Atualização do app", "Atualização automática ativa", "☁", v -> Toast.makeText(this, "O sistema de atualização existente continua ativo.", Toast.LENGTH_SHORT).show());
        addSetting("Sobre", "Nexauren Music Player", "ⓘ", v -> Toast.makeText(this, "Nexauren Music Player 2.0", Toast.LENGTH_SHORT).show());
    }

    private void showSongList(String heading) {
        buildPage(heading);
        ArrayList<Song> source = new ArrayList<>(songs);
        if ("Mais tocadas".equalsIgnoreCase(heading)) {
            source.sort((a, b) -> Integer.compare(
                    PlayStatsStore.count(this, b.id),
                    PlayStatsStore.count(this, a.id)));
        }
        addSectionHeader(heading, source.size() + " músicas", null);
        addSongListFrom(source);
    }

    private void showFavorites() {
        buildPage(NexaurenLanguageStore.t(this,"favorites"));
        List<Song> favorites = new ArrayList<>();
        for (Song song : songs) if (FavoritesStore.isFavorite(this, song.id)) favorites.add(song);
        addSectionHeader(NexaurenLanguageStore.t(this,"favorites"), favorites.size() + " músicas", null);
        addSongListFrom(favorites);
    }

    private void buildPage(String pageTitle) {
        root.removeAllViews();
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.addView(topBar(pageTitle), new LinearLayout.LayoutParams(-1, dp(58)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(98));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addMiniPlayer(page);
    }

    private View topBar(String pageTitle) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(4), 0);
        bar.setBackgroundColor(accent);

        TextView menu = topIcon("☰");
        bar.addView(menu, new LinearLayout.LayoutParams(dp(50), -1));
        menu.setOnClickListener(v -> openDrawer());

        title = text(pageTitle, 18, Color.WHITE);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView search = topIcon("⌕");
        bar.addView(search, new LinearLayout.LayoutParams(dp(46), -1));
        search.setOnClickListener(v -> showSearchDialog());

        TextView more = topIcon("⋮");
        bar.addView(more, new LinearLayout.LayoutParams(dp(42), -1));
        more.setOnClickListener(v -> { android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this).setTitle("Nexauren Music Player"); b.setItems(new String[]{NexaurenLanguageStore.t(this,"random"),NexaurenLanguageStore.t(this,"smart"),NexaurenLanguageStore.t(this,"settings")}, (d,w)-> { if(w==0) playRandomAll(); else if(w==1) showSmartMix(); else startActivity(new Intent(this,NexaurenSettingsActivity.class)); }); b.show(); });
        return bar;
    }

    private void showSearchDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("Música, artista ou álbum");
        input.setPadding(dp(18), 0, dp(18), 0);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Pesquisar biblioteca")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pesquisar", (dialog, which) -> {
                    String query = input.getText() == null ? "" : input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    if (query.isEmpty()) {
                        showLibrary();
                        return;
                    }
                    ArrayList<Song> matches = new ArrayList<>();
                    for (Song song : songs) {
                        String haystack = (song.title + " " + song.artist + " " + song.album).toLowerCase(java.util.Locale.ROOT);
                        if (haystack.contains(query)) matches.add(song);
                    }
                    showSearchResults(query, matches);
                })
                .show();
    }

    private void showSearchResults(String query, List<Song> matches) {
        buildPage("Resultados");
        addSectionHeader("Pesquisa", matches.size() + " encontrados", null);
        TextView hint = text("Resultados para \"" + query + "\"", 12, textSecondary());
        hint.setPadding(dp(8), dp(2), dp(8), dp(10));
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        addSongListFrom(matches);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accent, Color.rgb(24, 83, 170)});
        bg.setCornerRadius(dp(20));
        hero.setBackground(bg);
        TextView over = text("A SUA MÚSICA", 12, 0xCCFFFFFF);
        over.setTypeface(null, 1);
        hero.addView(over, new LinearLayout.LayoutParams(-1, dp(24)));
        TextView h = text("A música move a sua vida", 23, Color.WHITE);
        h.setTypeface(null, 1);
        hero.addView(h, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView sub = text("Ouça o que você ama", 13, 0xFFEAF4FF);
        hero.addView(sub, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView open = text("ABRIR PLAYER  ›", 12, Color.WHITE);
        open.setGravity(Gravity.CENTER);
        open.setTypeface(null, 1);
        open.setBackground(round(0x33FFFFFF, 24));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(142), dp(40));
        op.topMargin = dp(10);
        hero.addView(open, op);
        open.setOnLongClickListener(v -> { playRandomAll(); return true; });
        open.setOnClickListener(v -> openNowPlaying());
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(176)));
    }

    private void addQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, dp(6));
        addQuick(row, "🔥", NexaurenLanguageStore.t(this,"most_played"), v -> showSongList(NexaurenLanguageStore.t(this,"most_played")));
        addQuick(row, "◷", NexaurenLanguageStore.t(this,"recent"), v -> showSongList(NexaurenLanguageStore.t(this,"recent")));
        addQuick(row, "☷", NexaurenLanguageStore.t(this,"playlists"), v -> showPlaylists());
        addQuick(row, "♥", NexaurenLanguageStore.t(this,"favorites"), v -> showFavorites());
        addQuick(row, "✦", NexaurenLanguageStore.t(this,"smart"), v -> showSmartMix());
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(96)));
    }

    private void addQuick(LinearLayout parent, String icon, String label, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(round(surface(), 18));
        TextView i = text(icon, 25, accent);
        i.setGravity(Gravity.CENTER);
        card.addView(i, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView l = text(label, 11, textPrimary());
        l.setGravity(Gravity.CENTER);
        card.addView(l, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(card, lp);
        card.setOnClickListener(click);
    }

    private void showSmartMix() {
        buildPage(NexaurenLanguageStore.t(this,"smart"));
        ArrayList<Song> ranked = new ArrayList<>(songs);
        ranked.sort((a,b) -> {
            int scoreA = PlayStatsStore.count(this,a.id) * 4 + (FavoritesStore.isFavorite(this,a.id) ? 10 : 0);
            int scoreB = PlayStatsStore.count(this,b.id) * 4 + (FavoritesStore.isFavorite(this,b.id) ? 10 : 0);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return Long.compare(a.id, b.id);
        });
        addSectionHeader(NexaurenLanguageStore.t(this,"smart"), ranked.size()+" músicas", null);
        addSongListFrom(ranked);
    }

    private void playRandomAll() {
        if (controller == null || !controller.isConnected() || songs.isEmpty()) {
            Toast.makeText(this,"Nenhuma música disponível.",Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Song> shuffled = new ArrayList<>(songs);
        java.util.Collections.shuffle(shuffled);
        ArrayList<MediaItem> items = new ArrayList<>();
        for (Song song : shuffled) {
            items.add(new MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build())
                    .build());
        }
        controller.setMediaItems(items);
        controller.setShuffleModeEnabled(false);
        controller.prepare();
        controller.play();
        PlayStatsStore.increment(this, shuffled.get(0).id);
        updateMini();
    }

    private void addSectionHeader(String left, String right, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = text(left, 17, textPrimary());
        l.setTypeface(null, 1);
        row.addView(l, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView r = text(right, 11, accent);
        r.setGravity(Gravity.CENTER);
        row.addView(r, new LinearLayout.LayoutParams(dp(105), dp(46)));
        if (click != null) r.setOnClickListener(click);
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addSongList(int limit) { addSongListFrom(songs.subList(0, Math.min(limit, songs.size()))); }

    private void addSongListFrom(List<Song> list) {
        if (list.isEmpty()) {
            TextView empty = text("Nenhuma música encontrada. Dê permissão para a biblioteca de áudio.", 13, textSecondary());
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            content.addView(empty, new LinearLayout.LayoutParams(-1, dp(90)));
            return;
        }
        for (Song song : list) addSongRow(song);
    }

    private void addSongRow(Song song) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(5), dp(5));
        row.setBackground(round(Color.WHITE, 15));
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.music_placeholder);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(art, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(10), 0, dp(6), 0);
        TextView t = text(song.title, 14, textPrimary());
        t.setTypeface(null, 1);
        t.setSingleLine(true);
        TextView a = text(song.artist, 11, textSecondary());
        a.setSingleLine(true);
        meta.addView(t, new LinearLayout.LayoutParams(-1, dp(28)));
        meta.addView(a, new LinearLayout.LayoutParams(-1, dp(22)));
        row.addView(meta, new LinearLayout.LayoutParams(0, dp(64), 1));
        TextView fav = text(FavoritesStore.isFavorite(this, song.id) ? "♥" : "♡", 22, accent);
        row.addView(fav, new LinearLayout.LayoutParams(dp(46), dp(60)));
        fav.setGravity(Gravity.CENTER);
        fav.setOnClickListener(v -> { FavoritesStore.toggle(this, song.id); fav.setText(FavoritesStore.isFavorite(this, song.id) ? "♥" : "♡"); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(66));
        lp.bottomMargin = dp(7);
        content.addView(row, lp);
        row.setOnClickListener(v -> play(song));
    }

    private void addLibraryCard(String icon, String name, String desc, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(10), dp(8));
        card.setBackground(round(Color.WHITE, 18));
        TextView i = text(icon, 28, accent);
        i.setGravity(Gravity.CENTER);
        card.addView(i, new LinearLayout.LayoutParams(dp(58), dp(62)));
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(name, 14, textPrimary()); n.setTypeface(null, 1);
        TextView d = text(desc, 11, textSecondary());
        meta.addView(n, new LinearLayout.LayoutParams(-1, dp(28)));
        meta.addView(d, new LinearLayout.LayoutParams(-1, dp(24)));
        card.addView(meta, new LinearLayout.LayoutParams(0, dp(62), 1));
        TextView arrow = text("›", 26, accent); arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(62)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(80)); lp.bottomMargin = dp(8);
        content.addView(card, lp); card.setOnClickListener(click);
    }

    private void addCreateCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(10), dp(8));
        card.setBackground(round(Color.WHITE, 18));
        TextView plus = text("+", 32, Color.WHITE); plus.setGravity(Gravity.CENTER); plus.setBackground(round(accent, 15));
        card.addView(plus, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL); meta.setPadding(dp(12),0,0,0);
        TextView a = text("Criar nova playlist", 14, textPrimary()); a.setTypeface(null,1);
        TextView b = text("Monte sua própria playlist", 11, textSecondary());
        meta.addView(a,new LinearLayout.LayoutParams(-1,dp(28))); meta.addView(b,new LinearLayout.LayoutParams(-1,dp(24)));
        card.addView(meta,new LinearLayout.LayoutParams(0,dp(58),1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(76)); lp.bottomMargin=dp(12); content.addView(card,lp);
        card.setOnClickListener(v -> Toast.makeText(this,"Criador de playlists será ligado ao catálogo existente na próxima etapa.",Toast.LENGTH_SHORT).show());
    }

    private void addPlaylistRow(String name, String count, int index) {
        LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(8),dp(5),dp(6),dp(5)); card.setBackground(round(Color.WHITE,16));
        TextView art=text(new String[]{"♥","♫","✦","⚡","☀"}[index],28,accent); art.setGravity(Gravity.CENTER); art.setBackground(round(0xFFEAF3FF,14)); card.addView(art,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout meta=new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL); meta.setPadding(dp(10),0,0,0);
        TextView n=text(name,14,textPrimary());n.setTypeface(null,1); TextView c=text(count,11,textSecondary()); meta.addView(n,new LinearLayout.LayoutParams(-1,dp(28)));meta.addView(c,new LinearLayout.LayoutParams(-1,dp(22)));card.addView(meta,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView more=text("⋮",22,textSecondary());more.setGravity(Gravity.CENTER);card.addView(more,new LinearLayout.LayoutParams(dp(38),dp(62)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.bottomMargin=dp(7);content.addView(card,lp);
        card.setOnClickListener(v -> showSongList(name));
    }

    private void addSetting(String name, String desc, String icon, View.OnClickListener click) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));row.setBackground(round(Color.WHITE,16));
        TextView i=text(icon,22,accent);i.setGravity(Gravity.CENTER);row.addView(i,new LinearLayout.LayoutParams(dp(48),dp(58)));
        LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);m.setPadding(dp(10),0,0,0);TextView n=text(name,14,textPrimary());n.setTypeface(null,1);TextView d=text(desc,11,textSecondary());m.addView(n,new LinearLayout.LayoutParams(-1,dp(27)));m.addView(d,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(m,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView arrow=text("›",25,accent);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(58)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(74));lp.bottomMargin=dp(8);content.addView(row,lp);if(click!=null)row.setOnClickListener(click);
    }

    private void addMiniPlayer(LinearLayout shell) {
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(5),dp(8),dp(5));bar.setBackground(round(Color.WHITE,18));bar.setElevation(dp(8));
        miniArt=new ImageView(this);miniArt.setImageResource(R.drawable.music_placeholder);miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);bar.addView(miniArt,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.setPadding(dp(10),0,dp(4),0);miniTitle=text("Nenhuma música",13,textPrimary());miniTitle.setTypeface(null,1);miniTitle.setSingleLine(true);miniArtist=text("Escolha uma faixa",10,textSecondary());miniArtist.setSingleLine(true);meta.addView(miniTitle,new LinearLayout.LayoutParams(-1,dp(25)));meta.addView(miniArtist,new LinearLayout.LayoutParams(-1,dp(20)));bar.addView(meta,new LinearLayout.LayoutParams(0,dp(54),1));
        miniPlay=text("▶",24,accent);miniPlay.setGravity(Gravity.CENTER);bar.addView(miniPlay,new LinearLayout.LayoutParams(dp(52),dp(54)));miniPlay.setOnClickListener(v -> {if(controller!=null&&controller.isConnected()){if(controller.isPlaying())controller.pause();else controller.play();}});
        bar.setOnClickListener(v -> openNowPlaying());
        shell.addView(bar,new LinearLayout.LayoutParams(-1,dp(68)));
        updateMini();
    }

    private void addBottomNavigation(LinearLayout shell) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(3), dp(6), dp(3));
        nav.setBackground(round(Color.WHITE, 0));
        nav.setElevation(dp(10));

        addBottomItem(nav, "⌂", "Início", () -> showHome());
        addBottomItem(nav, "♫", "Biblioteca", () -> showLibrary());
        addBottomItem(nav, "☷", "Playlists", () -> showPlaylists());
        addBottomItem(nav, "♥", "Favoritos", () -> showFavorites());

        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void addBottomItem(LinearLayout parent, String icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView i = text(icon, 21, accent);
        i.setGravity(Gravity.CENTER);
        TextView l = text(label, 9, textSecondary());
        l.setGravity(Gravity.CENTER);
        item.addView(i, new LinearLayout.LayoutParams(-1, dp(26)));
        item.addView(l, new LinearLayout.LayoutParams(-1, dp(20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(item, lp);
        item.setOnClickListener(v -> { closeDrawer(); action.run(); });
    }

    private void updateMini(){
        if(controller==null||!controller.isConnected())return;
        CharSequence t=controller.getMediaMetadata().title;
        CharSequence a=controller.getMediaMetadata().artist;
        miniTitle.setText(t==null||t.length()==0?"Nenhuma música":t);
        miniArtist.setText(a==null||a.length()==0?"Escolha uma faixa":a);
        miniPlay.setText(controller.isPlaying()?"Ⅱ":"▶");
        try {
            MediaItem current = controller.getCurrentMediaItem();
            if (current != null && current.localConfiguration != null) {
                loadMiniArtwork(current.localConfiguration.uri);
            }
        } catch (Throwable ignored) {}
    }

    private void connectController(){
        SessionToken token=new SessionToken(this,new android.content.ComponentName(this,PlaybackService.class));
        controllerFuture=new MediaController.Builder(this,token).buildAsync();
        controllerFuture.addListener(() -> {try{controller=controllerFuture.get();controller.addListener(playerListener);updateMini();}catch(Exception ignored){}},getMainExecutor());
    }

    private void loadSongs(){
        executor.execute(() -> {
            ArrayList<Song> result=new ArrayList<>();
            String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION};
            String selection=MediaStore.Audio.Media.IS_MUSIC+" != 0";
            try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,selection,null,MediaStore.Audio.Media.DATE_ADDED+" DESC")){
                if(c!=null){int id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);int ti=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);int ar=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);int al=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);int du=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);while(c.moveToNext()){long songId=c.getLong(id);String title=c.getString(ti);String artist=c.getString(ar);String album=c.getString(al);long duration=c.getLong(du);Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,songId);result.add(new Song(songId,title==null||title.isEmpty()?"Faixa sem título":title,artist==null||artist.isEmpty()?"Artista desconhecido":artist,album==null?"":album,duration,uri));}}
            }catch(Exception ignored){}
            runOnUiThread(()->{songs.clear();songs.addAll(result);if(songCount!=null)songCount.setText(result.size()+" músicas");if(content!=null&&title!=null&&"Nexauren Music Player".contentEquals(title.getText()))showHome();});
        });
    }

    private void play(Song song){
        if(controller==null||!controller.isConnected()){
            Toast.makeText(this,"Player ainda está a iniciar.",Toast.LENGTH_SHORT).show();
            return;
        }
        MediaItem item=new MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build())
                .build();
        controller.setMediaItem(item);
        controller.prepare();
        controller.play();
        PlayStatsStore.increment(this, song.id);
        updateMini();
        loadMiniArtwork(song.uri);
    }

    private void loadMiniArtwork(Uri uri) {
        if (miniArt == null || uri == null) return;
        new Thread(() -> {
            Bitmap bmp = null;
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                byte[] data = retriever.getEmbeddedPicture();
                if (data != null) bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Throwable ignored) {
            } finally {
                try { retriever.release(); } catch (Throwable ignored) {}
            }
            Bitmap result = bmp;
            runOnUiThread(() -> miniArt.setImageBitmap(
                    result != null ? result : BitmapFactory.decodeResource(getResources(), R.drawable.music_placeholder)));
        }).start();
    }

    private void openNowPlaying(){startActivity(new Intent(this,NexaurenNowPlayingActivity.class));}
    private void openClassic(String pageName){Intent i=new Intent(this,MainActivity.class);if(pageName!=null)i.putExtra("page",pageName);startActivity(i);}

    private void openDrawer(){
        if(drawer!=null){closeDrawer();return;}
        drawer=new FrameLayout(this);drawer.setBackgroundColor(0x66000000);root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(24),dp(12),dp(12));panel.setBackground(round(Color.WHITE,0));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(310),-1);pp.gravity=Gravity.START;drawer.addView(panel,pp);
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);TextView logo=text("A",25,Color.WHITE);logo.setGravity(Gravity.CENTER);logo.setBackground(round(accent,30));brand.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout bm=new LinearLayout(this);bm.setOrientation(LinearLayout.VERTICAL);bm.setPadding(dp(12),0,0,0);TextView bn=text("Nexauren",20,textPrimary());bn.setTypeface(null,1);TextView bs=text("Music Player",11,textSecondary());bm.addView(bn,new LinearLayout.LayoutParams(-1,dp(28)));bm.addView(bs,new LinearLayout.LayoutParams(-1,dp(22)));brand.addView(bm,new LinearLayout.LayoutParams(0,dp(58),1));panel.addView(brand,new LinearLayout.LayoutParams(-1,dp(82)));
        addDrawerItem(panel,"⌂",NexaurenLanguageStore.t(this,"home"),this::showHome);addDrawerItem(panel,"♫",NexaurenLanguageStore.t(this,"library"),this::showLibrary);addDrawerItem(panel,"♥",NexaurenLanguageStore.t(this,"favorites"),this::showFavorites);addDrawerItem(panel,"☷",NexaurenLanguageStore.t(this,"playlists"),this::showPlaylists);addDrawerItem(panel,"🔥",NexaurenLanguageStore.t(this,"most_played"),()->showSongList(NexaurenLanguageStore.t(this,"most_played")));addDrawerItem(panel,"◷",NexaurenLanguageStore.t(this,"recent"),()->showSongList(NexaurenLanguageStore.t(this,"recent")));
        View sep=new View(this);sep.setBackgroundColor(0xFFE3E8EF);panel.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));
        addDrawerItem(panel,"⚙",NexaurenLanguageStore.t(this,"settings"),this::showSettings);addDrawerItem(panel,"ⓘ","Sobre",()->Toast.makeText(this,"Nexauren Music Player 2.0",Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams spacer=new LinearLayout.LayoutParams(-1,0,1);panel.addView(new View(this),spacer);
        TextView classic=text("Abrir interface clássica",12,accent);classic.setGravity(Gravity.CENTER);panel.addView(classic,new LinearLayout.LayoutParams(-1,dp(48)));classic.setOnClickListener(v->openClassic(null));
        drawer.setOnClickListener(v->closeDrawer());panel.bringToFront();
    }

    private void addDrawerItem(LinearLayout panel,String icon,String label,Runnable action){
        TextView item=text(icon+"     "+label,14,textPrimary());item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10),0,dp(8),0);panel.addView(item,new LinearLayout.LayoutParams(-1,dp(52)));item.setOnClickListener(v->{closeDrawer();action.run();});
    }

    private void closeDrawer(){if(drawer!=null){root.removeView(drawer);drawer=null;}}

    private boolean hasAudioPermission(){String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;return ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED;}
    private void requestAudioPermission(){String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;ActivityCompat.requestPermissions(this,new String[]{p},REQUEST_AUDIO);}
    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQUEST_AUDIO&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)loadSongs();else Toast.makeText(this,"Permita o acesso à música para preencher a Biblioteca.",Toast.LENGTH_LONG).show();}

    private TextView topIcon(String value){TextView t=text(value,24,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int surface(){return Color.WHITE;}
    private int textPrimary(){return Color.rgb(31,41,55);}
    private int textSecondary(){return Color.rgb(107,118,133);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    @Override protected void onDestroy(){if(controller!=null)controller.removeListener(playerListener);if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);executor.shutdownNow();super.onDestroy();}

    private static final class Song{
        final long id;final String title;final String artist;final String album;final long duration;final Uri uri;
        Song(long id,String title,String artist,String album,long duration,Uri uri){this.id=id;this.title=title;this.artist=artist;this.album=album;this.duration=duration;this.uri=uri;}
    }
}
