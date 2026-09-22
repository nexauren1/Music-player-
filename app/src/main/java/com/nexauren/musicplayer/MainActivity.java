package com.nexauren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.RingtoneManager;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.util.LruCache;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.LinearInterpolator;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private final List<Track> tracks = new ArrayList<>();
    private final List<Track> visibleTracks = new ArrayList<>();
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkExecutor = Executors.newFixedThreadPool(3);
    private final LruCache<Long, Bitmap> artworkCache = new LruCache<Long, Bitmap>(6144) {
        @Override protected int sizeOf(Long key, Bitmap value) { return value.getByteCount() / 1024; }
    };
    private static final int PLAYLIST_WINDOW = 600;
    private int queueWindowStart = 0;
    private final Handler handler = new Handler();

    private FrameLayout root;
    private LinearLayout content;
    private RecyclerView libraryContainer;
    private TrackAdapter trackAdapter;
    private TextView pageTitle;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView miniArt;
    private SeekBar miniSeek;
    private TextView miniPlay;
    private TextView miniShuffle;
    private TextView miniRepeat;
    private TextView miniAB;
    private TextView miniFavorite;
    private TextView miniMore;
    private TextView miniCurrent;
    private TextView miniTotal;
    private SeekBar miniProgress;
    private View miniProgressTrack;
    private View miniProgressFill;
    private boolean miniTracking;
    private TextView countText;
    private TextView bigTitle;
    private TextView bigPosition;
    private TextView bigDuration;
    private ImageView bigArt;
    private TextView homePlay;
    private EditText searchField;
    private WaveformView waveform;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int currentPage = 0;
    private boolean darkMode = false;
    private boolean searchMode = false;
    private long sleepEndAtMs = 0L;
    private long lastArtworkId = -1L;
    private boolean effectsEnabled = true;
    private boolean playbackStateRestored = false;
    private String pendingSearchQuery = "";
    private final Runnable searchFilterRunnable = () -> {
        final String query = pendingSearchQuery;
        queryExecutor.execute(() -> {
            final String normalized = query.trim().toLowerCase(Locale.getDefault());
            ArrayList<Track> result = new ArrayList<>();
            for (Track t : tracks) {
                if (normalized.isEmpty() || t.title.toLowerCase(Locale.getDefault()).contains(normalized)
                        || t.artist.toLowerCase(Locale.getDefault()).contains(normalized)
                        || t.album.toLowerCase(Locale.getDefault()).contains(normalized)) result.add(t);
            }
            runOnUiThread(() -> {
                if (!query.equals(pendingSearchQuery)) return;
                visibleTracks.clear(); visibleTracks.addAll(result); renderLibraryList();
            });
        });
    };
    private String reverbPreset = "Sinal seco";
    private int reverbMix = 0;
    private ObjectAnimator miniSpin;
    private Uri pendingTagUri;
    private android.content.ContentValues pendingTagValues;
    private static final int REQUEST_WRITE_MEDIA = 7801;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.isConnected()) {
                updatePlaybackUi();
                savePlaybackState();
            }
            if (sleepEndAtMs > 0 && System.currentTimeMillis() >= sleepEndAtMs && controller != null && controller.isConnected()) {
                controller.pause();
                sleepEndAtMs = 0L;
                Toast.makeText(MainActivity.this, "Temporizador de sono terminou.", Toast.LENGTH_SHORT).show();
            }
            handler.postDelayed(this, 500);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackUi(); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) {
            markCurrentRecent();
            ensureQueueAhead();
            updatePlaybackUi();
        }
        @Override public void onPlaybackStateChanged(int state) { updatePlaybackUi(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        if (!AppearanceStore.isSetupDone(this)) {
            startActivity(new Intent(this, AppearanceSetupActivity.class));
            finish();
            return;
        }
        Window window = getWindow();
        window.setStatusBarColor(AppearanceStore.accent(this));
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        darkMode = getSharedPreferences("nexauren", MODE_PRIVATE).getBoolean("dark", false);
        effectsEnabled = getSharedPreferences("nexauren", MODE_PRIVATE).getBoolean("effects", true);
        PlaybackService.setEffectsEnabled(effectsEnabled);
        buildShell();
        connectController();
        requestNotificationPermission();
        ensureAudioPermission();
        UpdateNotifications.ensureChannel(this);
        UpdateScheduler.ensure(this);
        checkForUpdateOnEntry();
        handleUpdateIntent(getIntent());
        handler.post(ticker);
        if ("equalizer".equalsIgnoreCase(getIntent().getStringExtra("page"))) showEqualizer();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackground(AppearanceBackgroundDrawable.forContext(this));
        setContentView(root);
        showHome(false);
    }

    private void showHome(boolean animate) {
        currentPage = 0;
        root.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(topBar("Nexauren Player", false), new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(4));
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        body.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        buildHomeContent();

        buildMiniPlayer(shell);
        if (animate) content.setAlpha(0f);
        if (animate) content.animate().alpha(1f).setDuration(160).start();
    }

    private void buildHomeContent() {
        LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        content.addView(inside, new LinearLayout.LayoutParams(-1,-1));

        LinearLayout nowCard=roundedPanel(surface(),dp(18));
        nowCard.setPadding(dp(10),dp(8),dp(10),dp(6));
        inside.addView(nowCard,new LinearLayout.LayoutParams(-1,dp(138)));

        LinearLayout nowTop=new LinearLayout(this);
        nowTop.setGravity(Gravity.CENTER_VERTICAL);
        nowCard.addView(nowTop,new LinearLayout.LayoutParams(-1,dp(94)));

        bigArt=new ImageView(this);
        bigArt.setImageResource(R.drawable.music_placeholder);
        bigArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bigArt.setBackground(roundDrawable(Color.rgb(55,63,74),dp(16)));
        bigArt.setClipToOutline(true);
        nowTop.addView(bigArt,new LinearLayout.LayoutParams(dp(82),dp(82)));

        LinearLayout meta=new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(10),0,dp(6),0);
        bigTitle=text("Nenhuma música",15,textPrimary());bigTitle.setTypeface(null,1);bigTitle.setMaxLines(2);
        meta.addView(bigTitle,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView nowSub=text("Nenhuma faixa em reprodução",11,textSecondary());nowSub.setSingleLine(true);
        meta.addView(nowSub,new LinearLayout.LayoutParams(-1,dp(26)));
        nowTop.addView(meta,new LinearLayout.LayoutParams(0,dp(76),1));

        homePlay=circleButton("▶");
        nowTop.addView(homePlay,new LinearLayout.LayoutParams(dp(52),dp(54)));
        homePlay.setOnClickListener(v->togglePlayback());
        nowCard.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));
        bigArt.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));

        waveform=new WaveformView(this);
        nowCard.addView(waveform,new LinearLayout.LayoutParams(-1,dp(18)));

        HorizontalScrollView tabsScroll=new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(0,dp(5),dp(8),dp(2));
        String[] names={"Músicas","Vídeos","Inteligente","Álbuns","Artistas","Pastas","Favoritos","Recentes","Playlist"};
        for(String name:names){
            TextView tab=chipText(name,"Músicas".equals(name)?accent():surface(),"Músicas".equals(name)?Color.WHITE:textPrimary());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(86),dp(39));lp.setMargins(dp(3),0,dp(3),0);
            tabs.addView(tab,lp);
            tab.setOnClickListener(v->{
                if("Músicas".equals(name))showHome(true);
                else if("Vídeos".equals(name))startActivity(new Intent(this,VideoLibraryActivity.class));
                else if("Inteligente".equals(name))showSmartLibrary();
                else if("Álbuns".equals(name))showAlbums();
                else if("Artistas".equals(name))showArtists();
                else if("Pastas".equals(name))showFolders();
                else if("Favoritos".equals(name))showFavorites();
                else if("Recentes".equals(name))showRecent();
                else showPlaylist();
            });
        }
        tabsScroll.addView(tabs,new ViewGroup.LayoutParams(-2,dp(45)));
        inside.addView(tabsScroll,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView libTitle=text("Biblioteca",20,textPrimary());libTitle.setTypeface(null,1);
        header.addView(libTitle,new LinearLayout.LayoutParams(0,dp(42),1));
        countText=text("A carregar…",11,textSecondary());countText.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        header.addView(countText,new LinearLayout.LayoutParams(dp(120),dp(42)));
        inside.addView(header,new LinearLayout.LayoutParams(-1,dp(45)));

        libraryContainer=new RecyclerView(this);
        libraryContainer.setLayoutManager(new LinearLayoutManager(this));
        libraryContainer.setHasFixedSize(true);
        libraryContainer.setItemViewCacheSize(12);
        libraryContainer.setNestedScrollingEnabled(false);
        trackAdapter=new TrackAdapter(new TrackAdapter.Listener(){
            @Override public void onTrackClick(Track track){playTrack(track);}
            @Override public void onTrackMenu(View anchor,Track track){showTrackMenu(anchor,track);}
            @Override public void onTrackBound(Track track,ImageView target){loadArtwork(track.uri,target,track.id);}
        },textPrimary(),textSecondary(),surface());
        libraryContainer.setAdapter(trackAdapter);
        inside.addView(libraryContainer,new LinearLayout.LayoutParams(-1,0,1));
        renderLibrary();
    }

    private View topBar(String title, boolean back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(4), 0);
        bar.setBackgroundColor(accent());

        TextView left = topIcon(back ? "‹" : "☰");
        bar.addView(left, new LinearLayout.LayoutParams(dp(48), -1));
        left.setOnClickListener(v -> { if (back) showHome(true); else openDrawer(); });

        if (searchMode && !back) {
            searchField = new EditText(this);
            searchField.setSingleLine(true);
            searchField.setHint("Pesquisar música, artista ou álbum");
            searchField.setTextColor(Color.WHITE);
            searchField.setHintTextColor(0xCCFFFFFF);
            searchField.setTextSize(16);
            searchField.setBackgroundColor(Color.TRANSPARENT);
            searchField.setPadding(0, 0, dp(4), 0);
            bar.addView(searchField, new LinearLayout.LayoutParams(0, -1, 1));

            TextView close = topIcon("×");
            bar.addView(close, new LinearLayout.LayoutParams(dp(50), -1));
            close.setOnClickListener(v -> { searchMode=false; showHome(true); });

            searchField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int st, int before, int count) { scheduleFilter(s.toString()); }
                @Override public void afterTextChanged(Editable e) {}
            });
            searchField.requestFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchField,InputMethodManager.SHOW_IMPLICIT);
            return bar;
        }

        pageTitle = text(title, 18, Color.WHITE);
        pageTitle.setTypeface(null, 0);
        bar.addView(pageTitle, new LinearLayout.LayoutParams(0, -1, 1));

        TextView search = topIcon("⌕");
        bar.addView(search, new LinearLayout.LayoutParams(dp(44), -1));
        search.setOnClickListener(v -> { searchMode=true; showHome(true); });

        TextView cast = topIcon("▣");
        bar.addView(cast, new LinearLayout.LayoutParams(dp(44), -1));
        cast.setOnClickListener(v -> Toast.makeText(this,"Seleção de dispositivo: utilize o painel de saída de áudio do sistema.",Toast.LENGTH_SHORT).show());

        TextView eq = topIcon("☷");
        bar.addView(eq, new LinearLayout.LayoutParams(dp(44), -1));
        eq.setOnClickListener(v -> showAudioLab());

        TextView more = topIcon("⋮");
        bar.addView(more, new LinearLayout.LayoutParams(dp(40), -1));
        more.setOnClickListener(v -> showGlobalMenu(more));
        return bar;
    }

    private void buildMiniPlayer(LinearLayout shell) {
        FrameLayout card=new FrameLayout(this);
        card.setBackground(roundDrawable(darkMode?Color.rgb(20,25,32):Color.WHITE,dp(18)));
        card.setElevation(dp(8));
        shell.addView(card,new LinearLayout.LayoutParams(-1,dp(78)));

        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9),dp(7),dp(7),dp(7));
        card.addView(row,new FrameLayout.LayoutParams(-1,dp(75)));

        miniArt=new ImageView(this);
        miniArt.setImageResource(R.drawable.music_placeholder);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artShape=roundDrawable(Color.rgb(55,63,74),dp(16));
        artShape.setShape(GradientDrawable.OVAL);
        miniArt.setBackground(artShape);
        miniArt.setClipToOutline(true);
        row.addView(miniArt,new LinearLayout.LayoutParams(dp(58),dp(58)));

        miniSpin=ObjectAnimator.ofFloat(miniArt,View.ROTATION,0f,360f);
        miniSpin.setDuration(5200L);
        miniSpin.setInterpolator(new LinearInterpolator());
        miniSpin.setRepeatCount(ObjectAnimator.INFINITE);

        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10),0,dp(4),0);
        miniTitle=text("Nenhuma música",14,textPrimary());
        miniTitle.setTypeface(null,1);
        miniTitle.setSingleLine(true);
        miniArtist=text("Selecione uma faixa",11,textSecondary());
        miniArtist.setSingleLine(true);
        labels.addView(miniTitle,new LinearLayout.LayoutParams(-1,dp(27)));
        labels.addView(miniArtist,new LinearLayout.LayoutParams(-1,dp(21)));
        row.addView(labels,new LinearLayout.LayoutParams(0,dp(58),1));

        miniFavorite=miniIcon("♡",24);
        row.addView(miniFavorite,new LinearLayout.LayoutParams(dp(40),dp(58)));
        miniFavorite.setOnClickListener(v->toggleCurrentFavorite());

        miniMore=miniIcon("⋮",24);
        row.addView(miniMore,new LinearLayout.LayoutParams(dp(34),dp(58)));
        miniMore.setOnClickListener(v->showMiniMenu(v));

        miniPlay=miniIcon("▶",19);
        miniPlay.setTextColor(Color.WHITE);
        miniPlay.setBackground(roundAccent(dp(25)));
        row.addView(miniPlay,new LinearLayout.LayoutParams(dp(50),dp(50)));
        miniPlay.setOnClickListener(v->togglePlayback());

        View open=card;
        card.setOnClickListener(v->{
            if(v==open)startActivity(new Intent(this,NowPlayingActivity.class));
        });
        miniArt.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));
        labels.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));

        LinearLayout.LayoutParams fillParams=new LinearLayout.LayoutParams(0,dp(3));
        View track=new View(this);
        track.setBackground(roundDrawable(darkMode?Color.rgb(54,61,70):Color.rgb(223,226,231),dp(2)));
        card.addView(track,new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM));

        miniProgressFill=new View(this);
        miniProgressFill.setBackground(roundAccent(dp(2)));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(1),dp(3),Gravity.BOTTOM|Gravity.START);
        card.addView(miniProgressFill,fp);
        miniProgressTrack=track;
    }

    private void cycleRepeat() {
        if (controller == null || !controller.isConnected()) return;
        int mode = controller.getRepeatMode();
        int next = mode == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL :
                mode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        controller.setRepeatMode(next);
        updatePlaybackUi();
        Toast.makeText(this, next == Player.REPEAT_MODE_ONE ? "Repetir uma" :
                next == Player.REPEAT_MODE_ALL ? "Repetir tudo" : "Repetição desligada", Toast.LENGTH_SHORT).show();
    }

    private void toggleABRepeat() {
        int state = PlaybackService.toggleABRepeat();
        if (miniAB != null) {
            miniAB.setText(state == 1 ? "A •" : "A-B");
            miniAB.setTextColor(state > 0 ? accent() : textSecondary());
        }
        if (state == 1) Toast.makeText(this, "Ponto A definido. Toque novamente para marcar B.", Toast.LENGTH_SHORT).show();
        else if (state == 2) Toast.makeText(this, "Repetição A-B ativada.", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "Repetição A-B desligada.", Toast.LENGTH_SHORT).show();
    }

    private void togglePlayback() {
        if (controller == null || !controller.isConnected()) return;
        if (controller.isPlaying()) controller.pause(); else controller.play();
    }

    private void filterLibrary(String q) { scheduleFilter(q); }

    private void scheduleFilter(String q) {
        pendingSearchQuery = q == null ? "" : q;
        handler.removeCallbacks(searchFilterRunnable);
        handler.postDelayed(searchFilterRunnable, 120);
    }

    private void renderLibrary() {
        visibleTracks.clear();
        visibleTracks.addAll(tracks);
        renderLibraryList();
    }

    private void renderLibraryList() {
        if (trackAdapter == null || countText == null) return;
        ArrayList<Track> copy = new ArrayList<>(visibleTracks);
        trackAdapter.submitList(copy);
        countText.setText(copy.size() + (copy.size() == 1 ? " faixa" : " faixas"));
    }

    private void playTrack(Track selected){
        if(controller==null||!controller.isConnected()||tracks.isEmpty())return;
        int selectedIndex=0;
        for(int i=0;i<tracks.size();i++)if(tracks.get(i).id==selected.id){selectedIndex=i;break;}
        queueWindowStart=Math.max(0,Math.min(selectedIndex,Math.max(0,tracks.size()-PLAYLIST_WINDOW)));
        int end=Math.min(tracks.size(),queueWindowStart+PLAYLIST_WINDOW);
        ArrayList<MediaItem> items=new ArrayList<>(end-queueWindowStart);
        for(int i=queueWindowStart;i<end;i++)items.add(mediaItem(tracks.get(i)));
        controller.setMediaItems(items,selectedIndex-queueWindowStart,0);
        controller.setShuffleModeEnabled(getSharedPreferences("nexauren_playback_state",MODE_PRIVATE).getBoolean("shuffle",false));
        controller.setRepeatMode(getSharedPreferences("nexauren_playback_state",MODE_PRIVATE).getInt("repeat_mode",Player.REPEAT_MODE_OFF));
        controller.setPlaybackSpeed(getSharedPreferences("nexauren_playback_state",MODE_PRIVATE).getFloat("speed",1f));
        controller.prepare();controller.play();PlayStatsStore.increment(this,selected.id);savePlaybackState();
    }

    private MediaItem mediaItem(Track t){
        MediaMetadata metadata=new MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build();
        return new MediaItem.Builder().setMediaId(String.valueOf(t.id)).setUri(t.uri).setMediaMetadata(metadata).build();
    }

    private void ensureQueueAhead(){
        if(controller==null||!controller.isConnected()||tracks.isEmpty())return;
        if(controller.getShuffleModeEnabled()||controller.getRepeatMode()!=Player.REPEAT_MODE_OFF)return;
        int index=controller.getCurrentMediaItemIndex();
        if(index<0)return;

        // Discard already-played items so a 50,000-song library never becomes a 50,000-item player queue.
        if(index>300 && controller.getMediaItemCount()>720){
            int remove=Math.min(300,index-1);
            controller.removeMediaItems(0,remove);
            queueWindowStart+=remove;
            index-=remove;
        }
        if(controller.getMediaItemCount()-index>100)return;

        int nextStart=queueWindowStart+controller.getMediaItemCount();
        if(nextStart>=tracks.size())return;
        int end=Math.min(tracks.size(),nextStart+PLAYLIST_WINDOW);
        ArrayList<MediaItem> next=new ArrayList<>(end-nextStart);
        for(int i=nextStart;i<end;i++)next.add(mediaItem(tracks.get(i)));
        if(!next.isEmpty())controller.addMediaItems(next);
    }

    private void savePlaybackState() {
        if (controller == null || !controller.isConnected()) return;
        ArrayList<Long> ids = new ArrayList<>();
        for (int i = 0; i < controller.getMediaItemCount(); i++) {
            try { ids.add(Long.parseLong(controller.getMediaItemAt(i).mediaId)); } catch (Exception ignored) {}
        }
        long currentId = -1L;
        if (controller.getCurrentMediaItem() != null) {
            try { currentId = Long.parseLong(controller.getCurrentMediaItem().mediaId); } catch (Exception ignored) {}
        }
        float speed = controller.getPlaybackParameters().speed;
        float pitch = controller.getPlaybackParameters().pitch;
        float volume = controller.getVolume();
        PlaybackStateStore.save(this, ids, currentId, controller.getCurrentPosition(),
                controller.getRepeatMode(), controller.getShuffleModeEnabled(), speed, pitch, volume);
    }

    private void maybeRestorePlaybackState(){
        if(playbackStateRestored||controller==null||!controller.isConnected()||tracks.isEmpty())return;
        List<Long> savedIds=PlaybackStateStore.queue(this);
        long currentId=PlaybackStateStore.currentId(this);
        if(savedIds.isEmpty()||currentId<0){playbackStateRestored=true;return;}
        java.util.HashMap<Long,Track> byId=new java.util.HashMap<>(tracks.size()*2);
        for(Track t:tracks)byId.put(t.id,t);
        int savedIndex=savedIds.indexOf(currentId);
        if(savedIndex<0){playbackStateRestored=true;return;}
        int from=Math.max(0,savedIndex-120),to=Math.min(savedIds.size(),from+PLAYLIST_WINDOW);
        ArrayList<MediaItem> items=new ArrayList<>(to-from);int currentIndex=0;
        for(int i=from;i<to;i++){Track t=byId.get(savedIds.get(i));if(t==null)continue;if(savedIds.get(i)==currentId)currentIndex=items.size();items.add(mediaItem(t));}
        if(items.isEmpty()){playbackStateRestored=true;return;}
        queueWindowStart=Math.max(0,savedIndex-120);
        controller.setMediaItems(items,currentIndex,PlaybackStateStore.positionMs(this));
        controller.setRepeatMode(PlaybackStateStore.repeatMode(this));
        controller.setShuffleModeEnabled(PlaybackStateStore.shuffle(this));
        controller.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(PlaybackStateStore.speed(this),PlaybackStateStore.pitch(this)));
        controller.setVolume(PlaybackStateStore.volume(this));controller.prepare();playbackStateRestored=true;
    }

    private void markCurrentRecent() {
        Track t=currentTrack();
        if(t!=null) markRecent(t);
    }

    private void markRecent(Track track) {
        String raw=getSharedPreferences("nexauren_recent",MODE_PRIVATE).getString("ids","");
        ArrayList<String> ids=new ArrayList<>();
        if(!raw.isEmpty()) for(String s:raw.split(",")) if(!s.isEmpty()&&!s.equals(String.valueOf(track.id))) ids.add(s);
        ids.add(0,String.valueOf(track.id));
        if(ids.size()>20) ids=new ArrayList<>(ids.subList(0,20));
        getSharedPreferences("nexauren_recent",MODE_PRIVATE).edit().putString("ids",String.join(",",ids)).apply();
    }

    private Track currentTrack() {
        if(controller==null||controller.getCurrentMediaItem()==null)return null;
        String id=controller.getCurrentMediaItem().mediaId;
        if(id==null)return null;
        try{long n=Long.parseLong(id);for(Track t:tracks)if(t.id==n)return t;}catch(Exception ignored){}
        return null;
    }

    private void toggleCurrentFavorite() {
        Track t=currentTrack(); if(t==null){Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();return;}
        boolean fav=FavoritesStore.toggle(this,t.id);
        Toast.makeText(this,fav?"Adicionado aos favoritos":"Removido dos favoritos",Toast.LENGTH_SHORT).show();
    }

    private void addCurrentToPlaylist() { Track t=currentTrack(); if(t!=null) addTrackToPlaylist(t); }

    private void addTrackToPlaylist(Track t) {
        Set<String> ids=new HashSet<>(getSharedPreferences("nexauren_playlist",MODE_PRIVATE).getStringSet("ids",new HashSet<>()));
        ids.add(String.valueOf(t.id));
        getSharedPreferences("nexauren_playlist",MODE_PRIVATE).edit().putStringSet("ids",ids).apply();
        Toast.makeText(this,"Adicionado à Minha playlist.",Toast.LENGTH_SHORT).show();
    }

    private List<Track> tracksFromIds(Set<String> ids) {
        ArrayList<Track> out=new ArrayList<>();
        for(Track t:tracks)if(ids.contains(String.valueOf(t.id)))out.add(t);
        return out;
    }

    private void showFolders() {
        root.removeAllViews();
        LinearLayout shell=basePage("Pastas");
        LinearLayout body=pageBody(shell);
        TextView loading=text("A organizar pastas…",15,textSecondary());
        loading.setGravity(Gravity.CENTER);
        body.addView(loading,new LinearLayout.LayoutParams(-1,dp(100)));

        queryExecutor.execute(() -> {
            java.util.LinkedHashMap<String,ArrayList<Track>> groups=new java.util.LinkedHashMap<>();
            java.util.HashMap<Long,Track> byId=new java.util.HashMap<>(Math.max(16,tracks.size()*2));
            for(Track t:tracks)byId.put(t.id,t);
            String folderColumn=Build.VERSION.SDK_INT>=29?MediaStore.Audio.Media.RELATIVE_PATH:MediaStore.Audio.Media.DATA;
            String[] projection=Build.VERSION.SDK_INT>=29
                    ? new String[]{MediaStore.Audio.Media._ID,MediaStore.Audio.Media.RELATIVE_PATH}
                    : new String[]{MediaStore.Audio.Media._ID,MediaStore.Audio.Media.DATA};
            try(Cursor cursor=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,
                    MediaStore.Audio.Media.MIME_TYPE+" LIKE 'audio/%' AND "+MediaStore.Audio.Media.DURATION+" > 0",
                    null,folderColumn+" COLLATE NOCASE ASC")){
                if(cursor!=null){
                    int idCol=cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int folderCol=cursor.getColumnIndex(folderColumn);
                    while(cursor.moveToNext()){
                        long id=cursor.getLong(idCol);
                        Track match=byId.get(id);
                        if(match==null)continue;
                        String raw=folderCol>=0&&!cursor.isNull(folderCol)?cursor.getString(folderCol):"";
                        String folder=raw;
                        if(Build.VERSION.SDK_INT>=29&&folder.contains("/")){
                            String[] parts=folder.split("/");
                            folder=parts.length>0?parts[0]:folder;
                        } else if(!raw.isEmpty()){
                            java.io.File parent=new java.io.File(raw).getParentFile();
                            folder=parent==null?"Pasta desconhecida":parent.getName();
                        }
                        if(folder==null||folder.trim().isEmpty())folder="Pasta desconhecida";
                        groups.computeIfAbsent(folder,k->new ArrayList<>()).add(match);
                    }
                }
            }catch(Exception ignored){}
            final java.util.LinkedHashMap<String,ArrayList<Track>> result=groups;
            runOnUiThread(()->{
                body.removeAllViews();
                if(result.isEmpty()){
                    TextView empty=text("Nenhuma pasta encontrada.",15,textSecondary());
                    empty.setGravity(Gravity.CENTER);
                    body.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
                    return;
                }
                for(java.util.Map.Entry<String,ArrayList<Track>> entry:result.entrySet()){
                    LinearLayout card=roundedPanel(surface(),dp(16));
                    card.setPadding(dp(14),dp(10),dp(12),dp(10));card.setOrientation(LinearLayout.VERTICAL);
                    TextView name=text("▰  "+entry.getKey(),16,textPrimary());name.setTypeface(null,1);
                    TextView count=text(entry.getValue().size()+(entry.getValue().size()==1?" faixa":" faixas"),11,textSecondary());
                    card.addView(name,new LinearLayout.LayoutParams(-1,dp(28)));
                    card.addView(count,new LinearLayout.LayoutParams(-1,dp(22)));
                    ArrayList<Track> folderTracks=entry.getValue();
                    card.setOnClickListener(v->showTrackCollection(entry.getKey(),folderTracks));
                    body.addView(card,new LinearLayout.LayoutParams(-1,dp(66)));addSpacer(body,6);
                }
            });
        });
    }

    private void showFavorites() { showTrackCollection("Favoritos", tracksFromIds(FavoritesStore.get(this))); }
    private void showPlaylist() { showTrackCollection("Minha playlist", tracksFromIds(getSharedPreferences("nexauren_playlist",MODE_PRIVATE).getStringSet("ids",new HashSet<>()))); }

    private void showRecent() {
        String raw=getSharedPreferences("nexauren_recent",MODE_PRIVATE).getString("ids","");
        ArrayList<Track> out=new ArrayList<>();
        if(!raw.isEmpty()) for(String s:raw.split(",")) try{long id=Long.parseLong(s);for(Track t:tracks)if(t.id==id){out.add(t);break;}}catch(Exception ignored){}
        showTrackCollection("Reproduzido recentemente",out);
    }

    private void showTrackCollection(String title,List<Track> list) {
        root.removeAllViews();
        LinearLayout shell=basePage(title);
        LinearLayout body=pageBody(shell);
        RecyclerView recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(12);
        TrackAdapter adapter=new TrackAdapter(new TrackAdapter.Listener(){
            @Override public void onTrackClick(Track track){playTrack(track);}
            @Override public void onTrackMenu(View anchor,Track track){showTrackMenu(anchor,track);}
            @Override public void onTrackBound(Track track,ImageView target){loadArtwork(track.uri,target,track.id);}
        },textPrimary(),textSecondary(),surface());
        recycler.setAdapter(adapter);
        recycler.setPadding(0,dp(3),0,dp(12));recycler.setClipToPadding(false);
        body.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        adapter.submitList(new ArrayList<>(list));
    }

    private void addCollectionRow(LinearLayout box,Track t,int index){
        LinearLayout row=roundedPanel(surface(),dp(16));row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(8),dp(5),dp(6),dp(5));
        ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setImageResource(android.R.drawable.ic_menu_gallery);art.setColorFilter(0xFF65707C);
        row.addView(art,new LinearLayout.LayoutParams(dp(58),dp(58)));
        row.addView(labels(t.title,t.artist),new LinearLayout.LayoutParams(0,dp(58),1));
        TextView more=topIconSmall("⋮");row.addView(more,new LinearLayout.LayoutParams(dp(38),dp(58)));
        more.setOnClickListener(v->showTrackMenu(more,t));row.setOnClickListener(v->playTrack(t));box.addView(row,new LinearLayout.LayoutParams(-1,dp(70)));
        loadArtwork(t.uri,art);
    }

    private void showQueue() {
        root.removeAllViews();
        LinearLayout shell=basePage("Fila de reprodução");
        LinearLayout body=pageBody(shell);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);scroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(controller==null||controller.getMediaItemCount()==0){TextView e=text("A fila está vazia.",16,textSecondary());e.setGravity(Gravity.CENTER);box.addView(e,new LinearLayout.LayoutParams(-1,dp(120)));return;}
        for(int i=0;i<controller.getMediaItemCount();i++){MediaItem m=controller.getMediaItemAt(i);String t=m.mediaMetadata.title==null?"Faixa":m.mediaMetadata.title.toString();String a=m.mediaMetadata.artist==null?"":m.mediaMetadata.artist.toString();box.addView(labels((i+1)+". "+t,a),new LinearLayout.LayoutParams(-1,dp(58)));} 
    }

    private void showSortDialog() {
        String[] values={"Título (A–Z)","Artista (A–Z)","Álbum (A–Z)","Duração (maior primeiro)","Mais recentes adicionadas"};
        new AlertDialog.Builder(this).setTitle("Ordem da biblioteca").setItems(values,(d,w)->{
            if(w==0)tracks.sort(Comparator.comparing(t->t.title.toLowerCase(Locale.getDefault())));
            else if(w==1)tracks.sort(Comparator.comparing(t->t.artist.toLowerCase(Locale.getDefault())));
            else if(w==2)tracks.sort(Comparator.comparing(t->t.album.toLowerCase(Locale.getDefault())));
            else if(w==3)tracks.sort((a,b)->Long.compare(b.durationMs,a.durationMs));
            renderLibrary();
        }).show();
    }

    private void showDuplicates() {
        java.util.Map<String,Integer> counts=new java.util.LinkedHashMap<>();
        for(Track t:tracks){String key=t.title+"|"+t.artist+"|"+t.durationMs;counts.put(key,counts.getOrDefault(key,0)+1);}
        ArrayList<Track> dupes=new ArrayList<>();
        for(Track t:tracks)if(counts.get(t.title+"|"+t.artist+"|"+t.durationMs)>1)dupes.add(t);
        showTrackCollection("Encontrar duplicados",dupes);
    }

    private void shareTrack(Track t) {
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("audio/*");send.putExtra(Intent.EXTRA_STREAM,t.uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(send,"Enviar faixa"));}catch(Exception e){Toast.makeText(this,"Não existe app para partilhar áudio.",Toast.LENGTH_SHORT).show();}
    }
    private void shareCurrent(){Track t=currentTrack();if(t!=null)shareTrack(t);}
    private void showDetails(Track t){new AlertDialog.Builder(this).setTitle(t.title).setMessage("Artista: "+t.artist+"\nÁlbum: "+t.album+"\nDuração: "+formatMs(t.durationMs)+"\nID: "+t.id).setPositiveButton("OK",null).show();}
    private void showCurrentDetails(){Track t=currentTrack();if(t!=null)showDetails(t);}

    private void deleteTrack(Track t) {
        try{
            if(Build.VERSION.SDK_INT>=30){
                startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(),java.util.Collections.singletonList(t.uri)).getIntentSender(),77,null,0,0,0,null);
            }else{
                getContentResolver().delete(t.uri,null,null);loadTracks();
            }
        }catch(Exception e){Toast.makeText(this,"Não foi possível eliminar a faixa.",Toast.LENGTH_SHORT).show();}
    }
    private void deleteCurrentTrack(){Track t=currentTrack();if(t!=null)deleteTrack(t);}

    private void filterByCurrentArtist() {
        Track t=currentTrack();
        if(t!=null) filterByArtist(t.artist);
        else Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();
    }

    private void filterByCurrentAlbum() {
        Track t=currentTrack();
        if(t!=null) filterByAlbum(t.album);
        else Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();
    }

    private void filterByArtist(String artist){searchMode=true;showHome(false);if(searchField!=null)searchField.setText(artist);}
    private void filterByAlbum(String album){searchMode=true;showHome(false);if(searchField!=null)searchField.setText(album);}

    private void showLyrics(){
        Track t=currentTrack();if(t==null){Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();return;}
        MediaMetadataRetriever r=new MediaMetadataRetriever();String path=null;
        try{r.setDataSource(this,t.uri);path=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);}catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}
        showAbout("Letra da música","Não foi encontrada uma fonte de letras integrada para esta faixa. A reprodução local continua normalmente.");
    }

    private void setCurrentAsRingtone(){
        Track t=currentTrack();if(t==null){Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();return;}
        try{
            android.content.ContentValues values=new android.content.ContentValues();
            values.put(MediaStore.Audio.Media.IS_RINGTONE,1);
            values.put(MediaStore.Audio.Media.TITLE,t.title);
            values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/*");
            Uri dest=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);
            if(dest==null)throw new IllegalStateException();
            try(java.io.InputStream in=getContentResolver().openInputStream(t.uri);java.io.OutputStream out=getContentResolver().openOutputStream(dest)){
                if(in==null||out==null)throw new java.io.IOException();
                byte[]buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
            }
            RingtoneManager.setActualDefaultRingtoneUri(this,RingtoneManager.TYPE_RINGTONE,dest);
            Toast.makeText(this,"Toque definido.",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"O sistema não permitiu definir o toque.",Toast.LENGTH_SHORT).show();}
    }

    private void showDrivingMode(){
        root.removeAllViews();LinearLayout shell=basePage("Modo de condução");LinearLayout body=pageBody(shell);
        TextView big=text("CONTROLO SIMPLIFICADO",20,textPrimary());big.setTypeface(null,1);big.setGravity(Gravity.CENTER);
        body.addView(big,new LinearLayout.LayoutParams(-1,dp(54)));
        body.addView(dialRow(circleAction("⏮","Anterior",()->{if(controller!=null)controller.seekToPreviousMediaItem();}),circleAction("⏭","Próxima",()->{if(controller!=null)controller.seekToNextMediaItem();})));
        TextView p=actionButton(controller!=null&&controller.isPlaying()?"Pausar":"Reproduzir");p.setTextSize(22);body.addView(p,new LinearLayout.LayoutParams(-1,dp(72)));p.setOnClickListener(v->togglePlayback());
        TextView stop=chipText("■  Parar",surface(),textPrimary());stop.setGravity(Gravity.CENTER);body.addView(stop,new LinearLayout.LayoutParams(-1,dp(54)));stop.setOnClickListener(v->PlaybackService.stop());
    }

    private View circleAction(String icon,String label,Runnable action){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);
        TextView i=circleButton(icon);b.addView(i,new LinearLayout.LayoutParams(dp(86),dp(76)));
        TextView l=text(label,13,textSecondary());l.setGravity(Gravity.CENTER);b.addView(l,new LinearLayout.LayoutParams(-1,dp(28)));
        b.setOnClickListener(v->action.run());return b;
    }

    private void showAbout(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}

    private void updatePlaybackUi() {
        if (controller == null || !controller.isConnected()) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) {
            String t = item.mediaMetadata.title == null ? "Nexauren Player" : item.mediaMetadata.title.toString();
            String ar = item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString();
            if (miniTitle != null) miniTitle.setText(t);
            if (bigTitle != null) bigTitle.setText(t);
            if (miniArtist != null) miniArtist.setText(ar);
            if (miniPlay != null) miniPlay.setText(controller.isPlaying() ? "Ⅱ" : "▶");
            syncMiniSpin(controller.isPlaying());
            if (homePlay != null) homePlay.setText(controller.isPlaying() ? "Ⅱ" : "▶");
            if (miniShuffle != null) miniShuffle.setTextColor(controller.getShuffleModeEnabled() ? accent() : textSecondary());
            if (miniRepeat != null) miniRepeat.setTextColor(controller.getRepeatMode() == Player.REPEAT_MODE_OFF ? textSecondary() : accent());

            int ab = PlaybackService.getABState();
            if (miniAB != null) {
                miniAB.setText(ab == 1 ? "A •" : "A-B");
                miniAB.setTextColor(ab > 0 ? accent() : textSecondary());
            }

            long duration = Math.max(0, controller.getDuration());
            long position = Math.max(0, controller.getCurrentPosition());
            if (miniProgress != null && !miniTracking) {
                miniProgress.setProgress(duration > 0 ? (int) Math.min(1000L, position * 1000L / duration) : 0);
            }
            if (miniCurrent != null) miniCurrent.setText(formatMs(position));
            if (miniTotal != null) miniTotal.setText(formatMs(duration));
            if (bigPosition != null) bigPosition.setText(formatMs(position));
            if (bigDuration != null) bigDuration.setText(formatMs(duration));

            if (waveform != null) {
                waveform.setPlaying(controller.isPlaying());
                waveform.setProgress(duration > 0 ? (float) position / duration : 0f);
            }

            Track match = null;
            try {
                long trackId = Long.parseLong(item.mediaId);
                for (Track tr : tracks) if (tr.id == trackId) { match = tr; break; }
            } catch (Exception ignored) {}

            if (match != null && match.id != lastArtworkId) {
                lastArtworkId = match.id;
                if (miniArt != null) loadArtwork(match.uri, miniArt);
                if (bigArt != null) loadArtwork(match.uri, bigArt);
            }
        }
    }

    private void loadArtwork(final Uri uri, final ImageView target) {
        long guessedId=-1L;
        try{guessedId=ContentUris.parseId(uri);}catch(Exception ignored){}
        loadArtwork(uri,target,guessedId);
    }

    private void loadArtwork(final Uri uri, final ImageView target, final long trackId){
        target.setTag(trackId);
        Bitmap cached=trackId>=0?artworkCache.get(trackId):null;
        if(cached!=null){target.clearColorFilter();target.setImageBitmap(cached);return;}
        target.setImageResource(R.drawable.music_placeholder);
        artworkExecutor.execute(() -> {
            Bitmap bitmap=null;
            if(trackId>=0&&ArtworkStore.exists(this,trackId))bitmap=BitmapFactory.decodeFile(ArtworkStore.file(this,trackId).getAbsolutePath());
            if(bitmap==null){
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try{retriever.setDataSource(this,uri);byte[]data=retriever.getEmbeddedPicture();if(data!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);}}
                catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
            }
            if(bitmap!=null){
                if(trackId>=0)artworkCache.put(trackId,bitmap);
                Bitmap result=bitmap;
                target.post(()->{Object tag=target.getTag();if(tag instanceof Long&&((Long)tag)==trackId){target.clearColorFilter();target.setImageBitmap(result);}});
            }
        });
    }

    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                maybeRestorePlaybackState();
                updatePlaybackUi();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível iniciar o áudio.", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void ensureAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadTracks();
        else ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_AUDIO);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void loadTracks() {
        if(libraryContainer==null||trackAdapter==null)return;
        queryExecutor.execute(() -> {
            ArrayList<Track> found=new ArrayList<>();
            java.util.Map<String,?> overrides=getSharedPreferences("nexauren_tags",MODE_PRIVATE).getAll();
            String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION};
            String selection=MediaStore.Audio.Media.MIME_TYPE+" LIKE 'audio/%' AND "+MediaStore.Audio.Media.DURATION+" > 0";
            final int pageSize=2000;
            int offset=0;
            try {
                while(true){
                    android.os.Bundle args=new android.os.Bundle();
                    args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,selection);
                    args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC");
                    args.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT,pageSize);
                    args.putInt(android.content.ContentResolver.QUERY_ARG_OFFSET,offset);
                    int pageCount=0;
                    try(android.database.Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,args,null)){
                        if(c==null)break;
                        int idCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int titleCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int artistCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        int albumCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                        int durationCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                        while(c.moveToNext()){
                            long id=c.getLong(idCol);
                            String title=override(overrides,id,"title",clean(c.getString(titleCol),"Sem título"));
                            String artist=override(overrides,id,"artist",clean(c.getString(artistCol),"Artista desconhecido"));
                            String album=override(overrides,id,"album",clean(c.getString(albumCol),"Álbum desconhecido"));
                            found.add(new Track(id,title,artist,album,c.getLong(durationCol),ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id)));
                            pageCount++;
                        }
                    }
                    if(pageCount==0)break;
                    offset+=pageCount;
                    if(found.size()%10000<pageCount){
                        final int loaded=found.size();
                        runOnUiThread(()->countText.setText("A indexar "+loaded+" faixas…"));
                    }
                    if(pageCount<pageSize)break;
                }
            }catch(SecurityException ignored){}catch(Exception ignored){}
            runOnUiThread(() -> {
                tracks.clear();tracks.addAll(found);renderLibrary();
                countText.setText(found.size()+" "+(found.size()==1?"faixa":"faixas"));
                maybeRestorePlaybackState();
            });
        });
    }

    private String override(java.util.Map<String,?> map,long id,String field,String fallback){
        Object v=map.get(id+":"+field);
        return v==null||String.valueOf(v).trim().isEmpty()?fallback:String.valueOf(v);
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        getSharedPreferences("nexauren", MODE_PRIVATE).edit().putBoolean("dark", darkMode).apply();
        Toast.makeText(this, "Tema " + (darkMode ? "escuro" : "claro") + " guardado. Reabra a página para aplicar.", Toast.LENGTH_SHORT).show();
    }

    private void section(LinearLayout list, String title) {
        TextView t = text(title, 16, accent());
        t.setTypeface(null, 1);
        t.setPadding(0, dp(16), 0, dp(6));
        list.addView(t, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void checkboxRow(LinearLayout list, String title, String sub, boolean checked, View.OnClickListener listener) {
        LinearLayout row = rowBase();
        TextView icon = text("□", 24, textSecondary());
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(70)));
        LinearLayout labels = labels(title, sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(70), 1));
        TextView box = text(checked ? "☑" : "□", 28, checked ? accent() : textSecondary());
        row.addView(box, new LinearLayout.LayoutParams(dp(48), dp(70)));
        View.OnClickListener l = v -> {
            box.setText(box.getText().toString().equals("☑") ? "□" : "☑");
            box.setTextColor(box.getText().toString().equals("☑") ? accent() : textSecondary());
            listener.onClick(v);
        };
        row.setOnClickListener(l);
        list.addView(row);
    }

    private void clickableRow(LinearLayout list, String title, String sub, String icon, Runnable action) {
        LinearLayout row = rowBase();
        TextView ic = text(icon, 24, textSecondary());
        row.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(72)));
        row.addView(labels(title, sub), new LinearLayout.LayoutParams(0, dp(72), 1));
        TextView chevron = text("›", 26, textSecondary());
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(40), dp(72)));
        row.setOnClickListener(v -> action.run());
        list.addView(row);
    }

    private void disabledRow(LinearLayout list, String title, String sub) {
        LinearLayout row = rowBase();
        TextView ic = text("☷", 22, 0xFFBDBDBD);
        row.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(68)));
        TextView labels = text(title + "\n" + sub, 16, 0xFFBDBDBD);
        labels.setPadding(dp(10), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(68), 1));
        list.addView(row);
    }

    private void sliderRow(LinearLayout list, String title, String value, int min, int max) {
        LinearLayout row = rowBase();
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(title, 16, textPrimary()), new LinearLayout.LayoutParams(0, dp(34), 1));
        line.addView(text(value, 13, textSecondary()), new LinearLayout.LayoutParams(dp(80), dp(34)));
        row.addView(line, new LinearLayout.LayoutParams(-1, dp(40)));
        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.max(1, max - min));
        bar.setProgress(Math.min(max - min, 0));
        row.addView(bar, new LinearLayout.LayoutParams(-1, dp(34)));
        list.addView(row, new LinearLayout.LayoutParams(-1, dp(88)));
    }

    private LinearLayout drawerItemContainer() {
        return null;
    }

    private void drawerItem(LinearLayout menu, String icon, String title, Runnable action) {
        LinearLayout row = rowBase();
        row.setPadding(dp(18), 0, dp(12), 0);
        TextView ic = text(icon, 26, 0xFF4F5358);
        row.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(64)));
        TextView label = text(title, 16, 0xFF303238);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(64), 1));
        row.setOnClickListener(v -> action.run());
        menu.addView(row);
    }

    private LinearLayout rowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(bg());
        row.setPadding(dp(2), 0, dp(2), 0);
        return row;
    }

    private LinearLayout labels(String title, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = text(title, 16, textPrimary());
        a.setMaxLines(1);
        box.addView(a, new LinearLayout.LayoutParams(-1, dp(30)));
        if (sub != null && !sub.isEmpty()) {
            TextView b = text(sub, 12, textSecondary());
            b.setMaxLines(2);
            box.addView(b, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        return box;
    }

    private LinearLayout dialRow(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(a, new LinearLayout.LayoutParams(0, dp(190), 1));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(190), 1));
        return row;
    }

    private View dial(String label, int percent) {
        return dial(label, percent, percent + "%", 0, 1);
    }

    private View dial(String label, int percent, String value, double min, double max) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        DialView d = new DialView(this);
        d.setPercent(percent);
        box.addView(d, new LinearLayout.LayoutParams(dp(150), dp(145)));
        TextView l = text(label, 15, textPrimary());
        l.setTypeface(null, 1);
        l.setGravity(Gravity.CENTER);
        box.addView(l, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView v = text(value, 12, textSecondary());
        v.setGravity(Gravity.CENTER);
        box.addView(v, new LinearLayout.LayoutParams(-1, dp(22)));
        d.setOnDialChangedListener(p -> {
            if (min == 0.5 && max == 2.0) {
                float speed = 0.5f + p / 100f * 1.5f;
                v.setText(String.format(Locale.getDefault(), "%.2fx", speed));
                if (controller != null && controller.isConnected()) controller.setPlaybackSpeed(speed);
            } else {
                v.setText(p + "%");
            }
        });
        return box;
    }

    private TextView segmented(String value, boolean selected) {
        TextView t = text(value, 17, selected ? Color.WHITE : 0xFF5B5D60);
        t.setGravity(Gravity.CENTER);
        t.setBackground(selected ? roundAccent(dp(28)) : roundDrawable(0xFFD1D2D5, dp(28)));
        return t;
    }

    private TextView chipText(String value, int background, int color) {
        TextView t = text(value, 15, color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(roundDrawable(background, dp(30)));
        return t;
    }

    private LinearLayout basePage(String title){LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.addView(topBar(title,true),new LinearLayout.LayoutParams(-1,dp(56)));root.addView(shell,new FrameLayout.LayoutParams(-1,-1));return shell;}

    private LinearLayout pageBody(LinearLayout shell){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(8),dp(14),dp(28));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return body;}

    private void openDrawer(){final FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(0x66000000);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));LinearLayout drawer=new LinearLayout(this);drawer.setOrientation(LinearLayout.VERTICAL);drawer.setBackgroundColor(darkMode?Color.rgb(20,24,30):Color.WHITE);int width=(int)Math.min(dp(330),getResources().getDisplayMetrics().widthPixels*0.86f);overlay.addView(drawer,new FrameLayout.LayoutParams(width,-1,Gravity.START));LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(dp(16),dp(16),dp(16),dp(10));hero.setBackgroundColor(Color.rgb(33,150,243));drawer.addView(hero,new LinearLayout.LayoutParams(-1,dp(190)));TextView logo=text("N",70,Color.WHITE);logo.setGravity(Gravity.CENTER);logo.setTypeface(null,1);hero.addView(logo,new LinearLayout.LayoutParams(-1,dp(98)));TextView name=text("NEXAUREN",22,Color.WHITE);name.setGravity(Gravity.CENTER);name.setTypeface(null,1);hero.addView(name,new LinearLayout.LayoutParams(-1,dp(38)));TextView sub=text("MUSIC PLAYER",11,Color.WHITE);sub.setGravity(Gravity.CENTER);hero.addView(sub,new LinearLayout.LayoutParams(-1,dp(25)));ScrollView scroll=new ScrollView(this);LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);scroll.addView(menu,new ScrollView.LayoutParams(-1,-2));drawer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));drawerItem(menu,"⌂","Biblioteca",()->{root.removeView(overlay);showHome(true);});drawerItem(menu,"♡","Favoritos",()->{root.removeView(overlay);showFavorites();});drawerItem(menu,"◷","Reproduzido recentemente",()->{root.removeView(overlay);showRecent();});drawerItem(menu,"☷","Fila de reprodução",()->{root.removeView(overlay);showQueue();});drawerItem(menu,"▤","Minha playlist",()->{root.removeView(overlay);showPlaylist();});drawerItem(menu,"☷","Áudio",()->{root.removeView(overlay);showAudioLab();});drawerItem(menu,"▣","Vídeos",()->{root.removeView(overlay);startActivity(new Intent(this,VideoLibraryActivity.class));});drawerItem(menu,"✦","Biblioteca inteligente",()->{root.removeView(overlay);showSmartLibrary();});drawerItem(menu,"◉","Visualizador de música",()->{root.removeView(overlay);startActivity(new Intent(this,VisualizerActivity.class));});drawerItem(menu,"▣","Modo de condução",()->{root.removeView(overlay);showDrivingMode();});drawerItem(menu,"⏱","Temporizador de sono",()->{root.removeView(overlay);showSleepTimer();});drawerItem(menu,"⧉","Encontrar duplicados",()->{root.removeView(overlay);showDuplicates();});drawerItem(menu,"◐","Tema claro/escuro",()->{root.removeView(overlay);toggleTheme();showHome(true);});drawerItem(menu,"⚙","Configurações",()->{root.removeView(overlay);showSettings();});overlay.setOnClickListener(v->root.removeView(overlay));}

    private void showSmartLibrary(){
        currentPage=0;searchMode=false;root.removeAllViews();
        LinearLayout shell=basePage("Biblioteca inteligente");
        LinearLayout body=pageBody(shell);

        LinearLayout summary=roundedPanel(surface(),dp(16));summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(14),dp(12),dp(14),dp(12));
        TextView title=text("Nexauren Intelligence",20,textPrimary());title.setTypeface(null,1);
        TextView sub=text("Coleções automáticas que se adaptam à sua biblioteca.",12,textSecondary());
        summary.addView(title,new LinearLayout.LayoutParams(-1,dp(32)));summary.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(statBox("Músicas",String.valueOf(tracks.size())),new LinearLayout.LayoutParams(0,dp(62),1));
        long total=0;for(Track t:tracks)total+=t.durationMs;
        stats.addView(statBox("Duração",formatDuration(total)),new LinearLayout.LayoutParams(0,dp(62),1));
        stats.addView(statBox("Favoritos",String.valueOf(FavoritesStore.get(this).size())),new LinearLayout.LayoutParams(0,dp(62),1));
        summary.addView(stats);
        body.addView(summary,new LinearLayout.LayoutParams(-1,dp(132)));addSpacer(body,8);

        sectionTitleView("COLEÇÕES AUTOMÁTICAS","Filtros calculados localmente, sem enviar sua música para a internet.");
        smartRow(body,"▶","Continuar ouvindo","Abrir a última faixa reproduzida",()->showRecent());
        smartRow(body,"★","Mais reproduzidas","Baseado no histórico deste dispositivo",()->showMostPlayed());
        smartRow(body,"♡","Favoritos","Todas as faixas marcadas como favoritas",()->showFavorites());
        smartRow(body,"♫","Sem artista","Faixas que precisam de organização de metadados",()->showSmartMissingArtist());
        smartRow(body,"◌","Faixas longas","Músicas com 8 minutos ou mais",()->showSmartLongTracks());
        smartRow(body,"⌁","Descobrir","Uma seleção aleatória da biblioteca",()->showSmartRandom());
    }

    private TextView statBox(String label,String value){
        TextView t=text(value+"\n"+label,13,textPrimary());t.setGravity(Gravity.CENTER);t.setTypeface(null,1);return t;
    }

    private void smartRow(LinearLayout body,String icon,String title,String sub,Runnable action){
        LinearLayout row=rowBase();TextView ic=text(icon,23,accent());row.addView(ic,new LinearLayout.LayoutParams(dp(46),dp(68)));
        row.addView(labels(title,sub),new LinearLayout.LayoutParams(0,dp(68),1));
        TextView go=text("›",25,textSecondary());go.setGravity(Gravity.CENTER);row.addView(go,new LinearLayout.LayoutParams(dp(38),dp(68)));
        row.setOnClickListener(v->action.run());body.addView(row,new LinearLayout.LayoutParams(-1,dp(72)));addSpacer(body,5);
    }

    private String formatDuration(long ms){
        long minutes=Math.max(0,ms/60000);long hours=minutes/60;minutes%=60;
        return hours>0?hours+"h "+minutes+"m":minutes+"m";
    }

    private void showMostPlayed(){showTrackCollection("Mais reproduzidas",tracksForIds(PlayStatsStore.top(this,100)));}
    private void showSmartMissingArtist(){ArrayList<Track> list=new ArrayList<>();for(Track t:tracks)if(t.artist==null||t.artist.trim().isEmpty()||t.artist.equalsIgnoreCase("Artista desconhecido"))list.add(t);showTrackCollection("Sem artista",list);}
    private void showSmartLongTracks(){ArrayList<Track> list=new ArrayList<>();for(Track t:tracks)if(t.durationMs>=8*60*1000L)list.add(t);showTrackCollection("Faixas longas",list);}
    private void showSmartRandom(){ArrayList<Track> list=new ArrayList<>(tracks);java.util.Collections.shuffle(list);if(list.size()>100)list=new ArrayList<>(list.subList(0,100));showTrackCollection("Descobrir",list);}
    private ArrayList<Track> tracksForIds(List<Long> ids){java.util.HashMap<Long,Track> map=new java.util.HashMap<>();for(Track t:tracks)map.put(t.id,t);ArrayList<Track> out=new ArrayList<>();for(Long id:ids){Track t=map.get(id);if(t!=null)out.add(t);}return out;}

    private void showAlbums() {
        currentPage=0; searchMode=false; root.removeAllViews();
        LinearLayout shell=basePage("Álbuns");
        LinearLayout body=pageBody(shell);
        java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();
        for(Track t:tracks){String a=(t.album==null||t.album.isEmpty())?"Álbum desconhecido":t.album;counts.put(a,counts.containsKey(a)?counts.get(a)+1:1);}
        for(String album:counts.keySet()){
            LinearLayout card=roundedPanel(surface(),dp(14));card.setPadding(dp(14),dp(10),dp(14),dp(10));
            TextView n=text(album,16,textPrimary());n.setTypeface(null,1);
            TextView sub=text(counts.get(album)+" faixa"+(counts.get(album)==1?"":"s"),11,textSecondary());
            card.setOrientation(LinearLayout.VERTICAL);card.addView(n,new LinearLayout.LayoutParams(-1,dp(28)));card.addView(sub,new LinearLayout.LayoutParams(-1,dp(22)));
            card.setOnClickListener(v->filterByAlbum(album));
            body.addView(card,new LinearLayout.LayoutParams(-1,dp(68)));
            addSpacer(body,6);
        }
        if(counts.isEmpty())body.addView(text("Nenhum álbum encontrado.",15,textSecondary()),new LinearLayout.LayoutParams(-1,dp(60)));
    }

    private void showArtists() {
        currentPage=0; searchMode=false; root.removeAllViews();
        LinearLayout shell=basePage("Artistas");
        LinearLayout body=pageBody(shell);
        java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();
        for(Track t:tracks){String a=(t.artist==null||t.artist.isEmpty())?"Artista desconhecido":t.artist;counts.put(a,counts.containsKey(a)?counts.get(a)+1:1);}
        for(String artist:counts.keySet()){
            LinearLayout card=roundedPanel(surface(),dp(14));card.setPadding(dp(14),dp(10),dp(14),dp(10));
            TextView n=text(artist,16,textPrimary());n.setTypeface(null,1);
            TextView sub=text(counts.get(artist)+" faixa"+(counts.get(artist)==1?"":"s"),11,textSecondary());
            card.setOrientation(LinearLayout.VERTICAL);card.addView(n,new LinearLayout.LayoutParams(-1,dp(28)));card.addView(sub,new LinearLayout.LayoutParams(-1,dp(22)));
            card.setOnClickListener(v->filterByArtist(artist));
            body.addView(card,new LinearLayout.LayoutParams(-1,dp(68)));
            addSpacer(body,6);
        }
        if(counts.isEmpty())body.addView(text("Nenhum artista encontrado.",15,textSecondary()),new LinearLayout.LayoutParams(-1,dp(60)));
    }

    private void addSpacer(LinearLayout body,int height){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}

    private void showGlobalMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] entries = {
                "Pesquisar", "Editar etiquetas", "Cortar áudio", "Adicionar à minha playlist", "Eliminar faixa atual",
                "Enviar faixa", "Detalhes", "Velocidade de reprodução",
                "Visualizador de música", "Temporizador de sono", "Letra da música",
                "Definir como toque", "Mais do artista", "Mais do álbum",
                "Adicionar aos favoritos", "Abrir fila", "Cortar trecho"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            String value = item.getTitle().toString();
            if ("Pesquisar".equals(value)) { searchMode=true; showHome(true); }
            else if ("Editar etiquetas".equals(value)) showEditTags();
            else if ("Cortar áudio".equals(value)) {
                Track current=currentTrack();
                if(current!=null) startActivityForResult(new Intent(this,AudioCutterActivity.class)
                        .putExtra("track_id",current.id).putExtra("duration",current.durationMs).putExtra("title",current.title),7811);
            }
            else if ("Adicionar à minha playlist".equals(value)) addCurrentToPlaylist();
            else if ("Eliminar faixa atual".equals(value)) deleteCurrentTrack();
            else if ("Enviar faixa".equals(value)) shareCurrent();
            else if ("Detalhes".equals(value)) showCurrentDetails();
            else if ("Velocidade de reprodução".equals(value)) showSpeedDialog();
            else if ("Visualizador de música".equals(value)) startActivity(new Intent(this, VisualizerActivity.class));
            else if ("Temporizador de sono".equals(value)) showSleepTimer();
            else if ("Letra da música".equals(value)) showLyrics();
            else if ("Definir como toque".equals(value)) setCurrentAsRingtone();
            else if ("Mais do artista".equals(value)) filterByCurrentArtist();
            else if ("Mais do álbum".equals(value)) filterByCurrentAlbum();
            else if ("Adicionar aos favoritos".equals(value)) toggleCurrentFavorite();
            else if ("Abrir fila".equals(value)) showQueue();
            else if ("Cortar trecho".equals(value)) {
                Track current=currentTrack();
                if(current!=null) showAbout("Cortar trecho","Use a versão seguinte para exportação de trechos. A reprodução e as outras ferramentas já estão ativas.");
            } else Toast.makeText(this, value + ": disponível no Nexauren.", Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
    }

    private void showTrackMenu(View anchor, Track track) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] entries = {
                FavoritesStore.isFavorite(this, track.id) ? "Remover dos favoritos" : "Adicionar aos favoritos",
                "Editar etiquetas", "Cortar áudio", "Adicionar à minha playlist", "Enviar", "Detalhes",
                "Mais do artista", "Mais do álbum", "Eliminar"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            String value=item.getTitle().toString();
            if(value.equals("Editar etiquetas")) startActivityForResult(new Intent(this,EditTagsActivity.class).putExtra("track_id",track.id),7810);
            else if(value.equals("Cortar áudio")) startActivityForResult(new Intent(this,AudioCutterActivity.class)
                    .putExtra("track_id",track.id).putExtra("duration",track.durationMs).putExtra("title",track.title),7811);
            else if(value.contains("favoritos")) {
                boolean fav=FavoritesStore.toggle(this,track.id);
                Toast.makeText(this,fav?"Adicionado aos favoritos":"Removido dos favoritos",Toast.LENGTH_SHORT).show();
            } else if(value.equals("Adicionar à minha playlist")) addTrackToPlaylist(track);
            else if(value.equals("Enviar")) shareTrack(track);
            else if(value.equals("Detalhes")) showDetails(track);
            else if(value.equals("Mais do artista")) filterByArtist(track.artist);
            else if(value.equals("Mais do álbum")) filterByAlbum(track.album);
            else if(value.equals("Eliminar")) deleteTrack(track);
            return true;
        });
        menu.show();
    }

    private void showAudioLab() {
        currentPage = 1;
        searchMode = false;
        root.removeAllViews();

        LinearLayout shell = basePage("Áudio");
        LinearLayout body = pageBody(shell);

        LinearLayout master = roundedPanel(surface(), dp(16));
        master.setOrientation(LinearLayout.VERTICAL);
        master.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout masterLine = new LinearLayout(this);
        masterLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView masterTitle = text("Efeitos de áudio", 16, textPrimary());
        masterTitle.setTypeface(null, 1);
        masterLine.addView(masterTitle, new LinearLayout.LayoutParams(0, dp(27), 1));
        Switch masterSwitch = new Switch(this);
        masterSwitch.setChecked(effectsEnabled);
        masterSwitch.setShowText(false);
        masterLine.addView(masterSwitch, new LinearLayout.LayoutParams(dp(50), dp(34)));
        master.addView(masterLine);
        master.addView(text("Equalizador • Som • Reverberação", 11, textSecondary()), new LinearLayout.LayoutParams(-1, dp(18)));
        masterSwitch.setOnCheckedChangeListener((button, checked) -> {
            effectsEnabled = checked;
            getSharedPreferences("nexauren", MODE_PRIVATE).edit().putBoolean("effects", checked).apply();
            PlaybackService.setEffectsEnabled(checked);
            PlaybackService.saveAudioPrefs(this);
        });
        body.addView(master, new LinearLayout.LayoutParams(-1, dp(64)));

        body.addView(sectionTitleView("EQUALIZADOR", "10 bandas"));
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetBox = new LinearLayout(this);
        presetBox.setPadding(0, 0, dp(8), dp(5));
        String[] presetNames = {"Plano","Rock","Jazz","Clássico","Bass","Vocal"};
        for (String name : presetNames) {
            TextView chip = chipText(name, "Plano".equals(name) ? accent() : surface(), "Plano".equals(name) ? Color.WHITE : textPrimary());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), dp(39));
            lp.setMargins(dp(3),0,dp(3),0);
            presetBox.addView(chip,lp);
            chip.setOnClickListener(v -> {
                if (PlaybackService.applyEqualizerPreset(name,10)) {
                    PlaybackService.saveAudioPrefs(this);
                    showAudioLab();
                }
                else Toast.makeText(this,"Toque uma música para ativar o equalizador.",Toast.LENGTH_SHORT).show();
            });
        }
        presetScroll.addView(presetBox,new ViewGroup.LayoutParams(-2,dp(44)));
        body.addView(presetScroll);

        EqualizerGraphView graph = new EqualizerGraphView(this);
        graph.setLevels(PlaybackService.getEqualizerLevels(10));
        body.addView(graph,new LinearLayout.LayoutParams(-1,dp(196)));

        HorizontalScrollView bandScroll = new HorizontalScrollView(this);
        bandScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bandBox = new LinearLayout(this);
        bandBox.setPadding(dp(2),dp(4),dp(12),dp(2));
        String[] bandLabels={"31","62","125","250","500","1k","2k","4k","8k","16k"};
        int[] bandLevels=PlaybackService.getEqualizerLevels(10);
        for(int i=0;i<10;i++){
            final int idx=i;
            EqBandView band=new EqBandView(this);
            band.setData(bandLabels[i],PlaybackService.getEqualizerBandCount()>0?bandLevels[i]:50);
            band.setListener(percent->{
                if(PlaybackService.setEqualizerBand(idx,10,percent)){
                    PlaybackService.saveAudioPrefs(this);
                    graph.setLevels(PlaybackService.getEqualizerLevels(10));
                }
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(55),dp(180));lp.setMargins(dp(2),0,dp(2),0);bandBox.addView(band,lp);
        }
        bandScroll.addView(bandBox,new ViewGroup.LayoutParams(-2,dp(187)));
        body.addView(bandScroll);
        LinearLayout eqFx=new LinearLayout(this);eqFx.setGravity(Gravity.CENTER);
        eqFx.addView(dialEffect("Pré-amplificador",50,true),new LinearLayout.LayoutParams(0,dp(164),1));
        eqFx.addView(dialEffect("Graves",0,false),new LinearLayout.LayoutParams(0,dp(164),1));
        body.addView(eqFx);

        body.addView(sectionTitleView("SOM","Balanço • 3D • velocidade • altura"));
        body.addView(dialRow(dialBalance(),dialVirtualizer()));
        body.addView(dialRow(dialSpeed("Velocidade",50,true),dialSpeed("Altura",50,false)));

        LinearLayout output=new LinearLayout(this);output.setGravity(Gravity.CENTER);
        TextView stereo=segmented("Estéreo",true),mono=segmented("Mono",false);
        output.addView(stereo,new LinearLayout.LayoutParams(0,dp(46),1));
        output.addView(mono,new LinearLayout.LayoutParams(0,dp(46),1));
        body.addView(output,new LinearLayout.LayoutParams(-1,dp(56)));
        stereo.setOnClickListener(v->{PlaybackService.setMonoMode(false);stereo.setBackground(roundAccent(dp(23)));mono.setBackground(roundDrawable(0xFFD1D2D5,dp(23)));});
        mono.setOnClickListener(v->{PlaybackService.setMonoMode(true);mono.setBackground(roundAccent(dp(23)));stereo.setBackground(roundDrawable(0xFFD1D2D5,dp(23)));});

        LinearLayout volume=roundedPanel(surface(),dp(15));volume.setOrientation(LinearLayout.VERTICAL);volume.setPadding(dp(10),dp(5),dp(10),dp(5));
        TextView volumeTitle=text("Volume do player",15,textPrimary());volumeTitle.setTypeface(null,1);volume.addView(volumeTitle,new LinearLayout.LayoutParams(-1,dp(24)));
        SeekBar volumeBar=new SeekBar(this);volumeBar.setMax(100);int vol0=controller!=null&&controller.isConnected()?Math.round(controller.getVolume()*100f):75;volumeBar.setProgress(vol0);volume.addView(volumeBar,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView volumeValue=text(vol0+"%",11,textSecondary());volumeValue.setGravity(Gravity.CENTER);volume.addView(volumeValue,new LinearLayout.LayoutParams(-1,dp(18)));
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){volumeValue.setText(p+"%");if(from&&controller!=null&&controller.isConnected())controller.setVolume(p/100f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        body.addView(volume,new LinearLayout.LayoutParams(-1,dp(86)));

        body.addView(sectionTitleView("REVERBERAÇÃO","Ambiente e profundidade"));
        HorizontalScrollView reverbScroll=new HorizontalScrollView(this);reverbScroll.setHorizontalScrollBarEnabled(false);LinearLayout reverbBox=new LinearLayout(this);reverbBox.setPadding(0,0,dp(8),dp(5));
        String[] reverbs={"Sinal seco","Sala","Studio","Hall"};
        for(String name:reverbs){
            TextView chip=chipText(name,reverbPreset.equals(name)?accent():surface(),reverbPreset.equals(name)?Color.WHITE:textPrimary());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(95),dp(39));lp.setMargins(dp(3),0,dp(3),0);reverbBox.addView(chip,lp);
            chip.setOnClickListener(v->{reverbPreset=name;if("Sinal seco".equals(name))reverbMix=0;PlaybackService.setReverb(name,reverbMix);showAudioLab();});
        }
        reverbScroll.addView(reverbBox,new ViewGroup.LayoutParams(-2,dp(44)));body.addView(reverbScroll);

        LinearLayout mixCard=roundedPanel(surface(),dp(16));mixCard.setOrientation(LinearLayout.VERTICAL);mixCard.setGravity(Gravity.CENTER);
        DialView mixDial=new DialView(this);mixDial.setPercent(reverbMix);mixCard.addView(mixDial,new LinearLayout.LayoutParams(dp(205),dp(180)));
        TextView mixValue=text("Mistura  "+reverbMix+"%",16,textPrimary());mixValue.setTypeface(null,1);mixValue.setGravity(Gravity.CENTER);mixCard.addView(mixValue,new LinearLayout.LayoutParams(-1,dp(28)));
        mixDial.setOnDialChangedListener(p->{reverbMix=p;if(p>0&&"Sinal seco".equals(reverbPreset))reverbPreset="Sala";mixValue.setText("Mistura  "+p+"%");PlaybackService.setReverb(reverbPreset,p);});
        body.addView(mixCard,new LinearLayout.LayoutParams(-1,dp(218)));

        LinearLayout quick=roundedPanel(surface(),dp(15));quick.setOrientation(LinearLayout.VERTICAL);quick.setPadding(dp(9),dp(5),dp(9),dp(5));
        TextView quickTitle=text("Perfis rápidos",14,textPrimary());quickTitle.setTypeface(null,1);quick.addView(quickTitle,new LinearLayout.LayoutParams(-1,dp(24)));
        addReverbPresetRow(quick,"Pequeno","Sala",25);addReverbPresetRow(quick,"Studio","Studio",35);addReverbPresetRow(quick,"Grande","Hall",45);
        body.addView(quick,new LinearLayout.LayoutParams(-1,dp(182)));
        body.addView(text("Os efeitos dependem do suporte de áudio do dispositivo.",11,textSecondary()),new LinearLayout.LayoutParams(-1,dp(38)));
    }

    private void showEqualizer(){showAudioLab();}

    private void showSound(){showAudioLab();}

    private void showReverb(){showAudioLab();}

    private View dialBalance() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        DialView d=new DialView(this);d.setPercent(50);box.addView(d,new LinearLayout.LayoutParams(dp(155),dp(150)));
        TextView l=text("Balanço",15,textPrimary());l.setTypeface(null,1);l.setGravity(Gravity.CENTER);box.addView(l,new LinearLayout.LayoutParams(-1,dp(26)));
        TextView v=text("0.00",12,textSecondary());v.setGravity(Gravity.CENTER);box.addView(v,new LinearLayout.LayoutParams(-1,dp(22)));
        d.setOnDialChangedListener(p->{float balance=(p-50)/50f;v.setText(String.format(Locale.getDefault(),"%+.2f",balance));PlaybackService.setBalance(balance);});
        return box;
    }

    private View dialVirtualizer() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        DialView d=new DialView(this);d.setPercent(0);box.addView(d,new LinearLayout.LayoutParams(dp(155),dp(150)));
        TextView l=text("Som surround 3D",15,textPrimary());l.setTypeface(null,1);l.setGravity(Gravity.CENTER);box.addView(l,new LinearLayout.LayoutParams(-1,dp(26)));
        TextView v=text("0%",12,textSecondary());v.setGravity(Gravity.CENTER);box.addView(v,new LinearLayout.LayoutParams(-1,dp(22)));
        d.setOnDialChangedListener(p->{v.setText(p+"%");if(!PlaybackService.setVirtualizer(p))Toast.makeText(this,"Surround 3D não é suportado neste dispositivo.",Toast.LENGTH_SHORT).show();});
        return box;
    }

    private View dialSpeed(String label, int percent, boolean speed) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        DialView d = new DialView(this);
        d.setPercent(percent);
        box.addView(d, new LinearLayout.LayoutParams(dp(155), dp(150)));
        TextView l = text(label, 16, textPrimary());
        l.setTypeface(null, 1);
        l.setGravity(Gravity.CENTER);
        box.addView(l, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView v = text("1.00x", 13, textSecondary());
        v.setGravity(Gravity.CENTER);
        box.addView(v, new LinearLayout.LayoutParams(-1, dp(24)));
        d.setOnDialChangedListener(p -> {
            float value = 0.5f + p / 100f * 1.5f;
            v.setText(String.format(Locale.getDefault(), "%.2fx", value));
            if (controller != null && controller.isConnected()) {
                float currentSpeed = controller.getPlaybackParameters().speed;
                float currentPitch = controller.getPlaybackParameters().pitch;
                if (speed) PlaybackService.setPlaybackSpeedPitch(value, currentPitch);
                else PlaybackService.setPlaybackSpeedPitch(currentSpeed, value);
            }
        });
        return box;
    }

    private void showSettings() {
        currentPage = 4;
        root.removeAllViews();
        LinearLayout shell = basePage("Configurações");
        LinearLayout body = pageBody(shell);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(-1, -2));

        section(list, "Reprodução");
        checkboxRow(list, "Efeitos de áudio", "Equalizador, graves, 3D e pré-amplificador.", effectsEnabled, v -> {
            effectsEnabled = !effectsEnabled;
            getSharedPreferences("nexauren", MODE_PRIVATE).edit().putBoolean("effects", effectsEnabled).apply();
            PlaybackService.setEffectsEnabled(effectsEnabled);
        });
        checkboxRow(list, "Silenciar pausas longas", "Ignora automaticamente trechos de silêncio.", false, v -> PlaybackService.setSkipSilence(true));
        checkboxRow(list, "Equalizador interno", "Usa o equalizador Nexauren em vez do sistema.", true, v -> showEqualizer());
        checkboxRow(list, "Controlos na notificação", "Play, pausa, anterior e próxima faixa.", true, v -> {});
        sliderRow(list, "Apagar músicas com menos de", "0 segundos", 0, 120);

        section(list, "Geral");
        clickableRow(list, "Atualizar biblioteca", "Procurar novas faixas no dispositivo", "↻", this::loadTracks);
        checkboxRow(list, "Mostrar controlos de notificação", "Controle a música a partir da barra de notificações.", true, v -> {});
        clickableRow(list, "Tema", darkMode ? "Escuro" : "Claro", "◐", () -> { toggleTheme(); showSettings(); });
        clickableRow(list, "Personalizar aparência", "Tema e fundo são independentes", "◆", () -> startActivityForResult(new Intent(this,AppearanceSetupActivity.class).putExtra("edit",true),7901));

        section(list, "Reprodução");
        clickableRow(list, "Mostrar apenas ficheiros de áudio", "Biblioteca padrão", "♫", () -> {});
        clickableRow(list, "Ordem da biblioteca", "Escolher ordenação", "☷", this::showSortDialog);
        clickableRow(list, "Lista de reprodução padrão", "Minha playlist", "≡", this::showPlaylist);

        section(list, "Biblioteca");
        clickableRow(list, "Favoritos", "Abrir as faixas marcadas", "♡", this::showFavorites);
        clickableRow(list, "Reproduzido recentemente", "Últimas faixas tocadas", "◷", this::showRecent);
        clickableRow(list, "Encontrar duplicados", "Comparar título, artista e duração", "⧉", this::showDuplicates);

        section(list, "Sobre");
        clickableRow(list, "Assinatura Premium", "Recursos adicionais", "♛", () -> showAbout("Nexauren Premium", "Recursos avançados serão ativados sem bloquear a reprodução básica."));
        clickableRow(list, "Curta a nossa página", "Nexauren", "♣", () -> Toast.makeText(this, "Obrigado por apoiar a Nexauren.", Toast.LENGTH_SHORT).show());
        clickableRow(list, "Sobre o Nexauren Music Player", "Versão 1.1.0", "ⓘ", () -> showAbout("Nexauren Music Player", "Versão 1.1.1 • player local, efeitos, favoritos, fila e pesquisa."));
    }

    private void showEditTags() {
        Track track = currentTrack();
        if (track == null) {
            Toast.makeText(this, "Nenhuma faixa em reprodução.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(
                new Intent(this, EditTagsActivity.class).putExtra("track_id", track.id),
                7810);
    }

    private void showSleepTimer() {
        String[] values = {"Desligado", "15 minutos", "30 minutos", "45 minutos", "60 minutos"};
        new AlertDialog.Builder(this).setTitle("Temporizador de sono").setItems(values, (d, which) -> {
            if (which == 0) sleepEndAtMs = 0L;
            else sleepEndAtMs = System.currentTimeMillis() + Long.parseLong(values[which].split(" ")[0]) * 60_000L;
            Toast.makeText(this, which == 0 ? "Temporizador desligado." : "A música vai parar em " + values[which] + ".", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void syncMiniSpin(boolean playing) {
        if (miniSpin == null) return;
        if (playing) {
            if (miniSpin.isPaused()) miniSpin.resume();
            else if (!miniSpin.isStarted()) miniSpin.start();
        } else if (miniSpin.isStarted()) {
            miniSpin.pause();
        }
    }

    private void showMiniMenu(View anchor) {
        Track track = currentTrack();
        if (track == null) {
            Toast.makeText(this, "Nenhuma faixa em reprodução.", Toast.LENGTH_SHORT).show();
            return;
        }
        final PopupWindow[] popupRef = new PopupWindow[1];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.setBackground(roundDrawable(darkMode ? Color.rgb(20,25,33) : Color.WHITE, dp(18)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list, new ViewGroup.LayoutParams(dp(330), -2));

        String[] labels = {
                "✎  Editar etiquetas",
                "✂  Cortar áudio",
                "♫  Definir como toque",
                "☷  Adicionar à lista de reprodução",
                "♡  Adicionar aos favoritos",
                "⌁  Enviar",
                "ⓘ  Detalhes",
                "◉  Velocidade de reprodução",
                "〰  Visualizador de música",
                "◷  Temporizador de sono",
                "♫  Letra da música",
                "♙  Mais do artista",
                "☷  Mais do álbum",
                "≡  Abrir fila",
                "▣  Modo de condução"
        };
        for (String label : labels) {
            TextView row = text(label, 14, darkMode ? Color.WHITE : Color.rgb(35,38,42));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(8), 0);
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            row.setOnClickListener(v -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                if (label.contains("Editar etiquetas")) {
                    startActivityForResult(new Intent(this, EditTagsActivity.class).putExtra("track_id", track.id), 7810);
                } else if (label.contains("Cortar áudio")) {
                    startActivityForResult(new Intent(this, AudioCutterActivity.class)
                            .putExtra("track_id", track.id).putExtra("duration", track.durationMs).putExtra("title", track.title), 7811);
                } else if (label.contains("Definir como toque")) setCurrentAsRingtone();
                else if (label.contains("lista de reprodução")) addCurrentToPlaylist();
                else if (label.contains("favoritos")) toggleCurrentFavorite();
                else if (label.contains("Enviar")) shareCurrent();
                else if (label.contains("Detalhes")) showCurrentDetails();
                else if (label.contains("Velocidade")) showSpeedDialog();
                else if (label.contains("Visualizador")) startActivity(new Intent(this, VisualizerActivity.class));
                else if (label.contains("Temporizador")) showSleepTimer();
                else if (label.contains("Letra")) showLyrics();
                else if (label.contains("artista")) filterByCurrentArtist();
                else if (label.contains("álbum")) filterByCurrentAlbum();
                else if (label.contains("fila")) showQueue();
                else if (label.contains("condução")) showDrivingMode();
            });
        }

        PopupWindow popup = new PopupWindow(scroll, dp(346), Math.min(dp(620), dp(48) * labels.length + dp(20)), true);
        popupRef[0] = popup;
        popup.setBackgroundDrawable(roundDrawable(darkMode ? Color.rgb(20,25,33) : Color.WHITE, dp(18)));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.setOverlapAnchor(false);
        popup.showAsDropDown(anchor, -dp(310), -Math.min(dp(620), dp(48) * labels.length + dp(68)));
    }

    private void checkForUpdateOnEntry() {
        UpdateManager.checkAsync(this,new UpdateManager.Callback(){
            @Override public void onResult(UpdateManager.ReleaseInfo info) {
                if(UpdateManager.isNewer(info.version,BuildConfig.VERSION_NAME)){
                    runOnUiThread(() -> showUpdateDialog(info));
                } else {
                    showWhatsNewIfNeeded(info);
                }
            }
            @Override public void onError(Exception error) { }
        });
    }

    private void handleUpdateIntent(Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(UpdateManager.ACTION_DOWNLOAD_UPDATE.equals(action)) {
            String version=intent.getStringExtra(UpdateManager.EXTRA_VERSION);
            String url=intent.getStringExtra(UpdateManager.EXTRA_URL);
            String notes=intent.getStringExtra(UpdateManager.EXTRA_NOTES);
            String digest=intent.getStringExtra(UpdateManager.EXTRA_DIGEST);
            if(url!=null&&!url.isEmpty()) runOnUiThread(() -> showUpdateDialog(new UpdateManager.ReleaseInfo(version==null?"":version,"Nexauren "+version,notes,url,digest)));
        } else if(UpdateManager.ACTION_INSTALL_UPDATE.equals(action)) {
            runOnUiThread(() -> UpdateManager.install(this));
        }
    }

    private TextView miniIcon(String icon,int size){TextView t=text(icon,size,textSecondary());t.setGravity(Gravity.CENTER);return t;}

    private View dialEffect(String label, int percent, boolean preamp) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        DialView d = new DialView(this);
        d.setPercent(percent);
        box.addView(d, new LinearLayout.LayoutParams(dp(150), dp(145)));
        TextView l = text(label, 15, textPrimary());
        l.setTypeface(null, 1);
        l.setGravity(Gravity.CENTER);
        box.addView(l, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView value = text(percent + "%", 12, textSecondary());
        value.setGravity(Gravity.CENTER);
        box.addView(value, new LinearLayout.LayoutParams(-1, dp(22)));
        d.setOnDialChangedListener(p -> {
            value.setText(p + "%");
            if (preamp) PlaybackService.setPreamp(p); else PlaybackService.setBass(p);
            PlaybackService.saveAudioPrefs(this);
        });
        return box;
    }

    private View sectionTitleView(String title,String subtitle){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(2),dp(9),dp(2),dp(2));
        TextView a=text(title,13,accent());a.setTypeface(null,1);
        TextView b=text(subtitle,11,textSecondary());
        box.addView(a,new LinearLayout.LayoutParams(-1,dp(21)));
        box.addView(b,new LinearLayout.LayoutParams(-1,dp(19)));
        return box;
    }

    private void addReverbPresetRow(LinearLayout parent,String label,String preset,int mix){
        TextView b=chipText(label+"  •  "+mix+"%",surface(),textPrimary());
        b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(14),0,dp(14),0);
        parent.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        b.setOnClickListener(v->{reverbPreset=preset;reverbMix=mix;PlaybackService.setReverb(preset,mix);Toast.makeText(this,label+" aplicado.",Toast.LENGTH_SHORT).show();});
    }

    private void showSpeedDialog() {
        final String[] values = {"0.75x", "1.00x", "1.25x", "1.50x", "1.75x", "2.00x"};
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("Velocidade de reprodução");
        b.setItems(values, (d, which) -> {
            if (controller != null && controller.isConnected()) controller.setPlaybackSpeed(Float.parseFloat(values[which].replace("x", "")));
            Toast.makeText(this, "Velocidade: " + values[which], Toast.LENGTH_SHORT).show();
        });
        b.show();
    }

    private void showUpdateDialog(UpdateManager.ReleaseInfo info) {
        if(isFinishing()||info==null||!UpdateManager.isNewer(info.version,BuildConfig.VERSION_NAME))return;
        String notes=info.notes==null?"Novas melhorias e correções.":info.notes.trim();
        if(notes.length()>900)notes=notes.substring(0,900)+"…";

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);

        TextView spin=text("♫",52,Color.rgb(45,157,235));spin.setGravity(Gravity.CENTER);
        ObjectAnimator animator=ObjectAnimator.ofFloat(spin,View.ROTATION,0f,360f);animator.setDuration(1200);animator.setRepeatCount(ObjectAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.start();
        box.addView(spin,new LinearLayout.LayoutParams(-1,dp(62)));

        TextView v=text("Nexauren "+info.version,20,textPrimary());v.setTypeface(null,1);box.addView(v,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView n=text("Novidades",13,accent());n.setTypeface(null,1);box.addView(n,new LinearLayout.LayoutParams(-1,dp(25)));
        TextView body=text(notes,12,textSecondary());box.addView(body,new LinearLayout.LayoutParams(-1,dp(100)));

        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Nova versão disponível").setView(box)
                .setNegativeButton("Baixar depois",(d,w)->deferUpdate(info.version))
                .setPositiveButton("Baixar atualização",(d,w)->startUpdateDownload(info,box))
                .create();

        dialog.setOnShowListener(vv -> {});
        dialog.show();
    }

    private void showWhatsNewIfNeeded(UpdateManager.ReleaseInfo info) {
        if (info == null || !BuildConfig.VERSION_NAME.equals(info.version)) return;
        android.content.SharedPreferences prefs=getSharedPreferences(UpdateManager.PREFS,MODE_PRIVATE);
        if (BuildConfig.VERSION_NAME.equals(prefs.getString("whats_new_seen",""))) return;
        runOnUiThread(() -> {
            String notes=info.notes==null?"Melhorias e correções.":info.notes.trim();
            if(notes.length()>1100)notes=notes.substring(0,1100)+"…";
            LinearLayout box=new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(18),0,dp(18),0);
            TextView icon=text("♫",50,accent());icon.setGravity(Gravity.CENTER);
            box.addView(icon,new LinearLayout.LayoutParams(-1,dp(62)));
            TextView headline=text("Nexauren "+BuildConfig.VERSION_NAME,20,textPrimary());headline.setTypeface(null,1);
            box.addView(headline,new LinearLayout.LayoutParams(-1,dp(34)));
            TextView sub=text("O que há de novo",13,accent());sub.setTypeface(null,1);
            box.addView(sub,new LinearLayout.LayoutParams(-1,dp(25)));
            TextView content=text(notes,12,textSecondary());
            box.addView(content,new LinearLayout.LayoutParams(-1,dp(118)));
            new AlertDialog.Builder(this).setTitle("Novidades da versão").setView(box)
                    .setPositiveButton("Entendi",(d,w)->prefs.edit().putString("whats_new_seen",BuildConfig.VERSION_NAME).apply())
                    .setOnDismissListener(d->prefs.edit().putString("whats_new_seen",BuildConfig.VERSION_NAME).apply())
                    .show();
        });
    }

    private void deferUpdate(String version) {
        getSharedPreferences(UpdateManager.PREFS,MODE_PRIVATE).edit()
                .putString("deferred_version",version)
                .putLong("deferred_until",System.currentTimeMillis()+24L*60L*60L*1000L).apply();
    }

    private void startUpdateDownload(UpdateManager.ReleaseInfo info,LinearLayout ignored) {
        if(info==null||info.apkUrl==null||info.apkUrl.isEmpty())return;

        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
        TextView note=text("♫",58,Color.rgb(45,157,235));note.setGravity(Gravity.CENTER);
        ObjectAnimator spin=ObjectAnimator.ofFloat(note,View.ROTATION,0f,360f);spin.setDuration(1000);spin.setRepeatCount(ObjectAnimator.INFINITE);spin.setInterpolator(new LinearInterpolator());spin.start();
        box.addView(note,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView status=text("A preparar download…",14,textPrimary());status.setGravity(Gravity.CENTER);box.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView percent=text("0%",12,textSecondary());percent.setGravity(Gravity.CENTER);box.addView(percent,new LinearLayout.LayoutParams(-1,dp(24)));

        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Atualizar Nexauren").setView(box)
                .setNegativeButton("Executar em segundo plano",null).create();
        dialog.show();

        Data data=new Data.Builder().putString(UpdateDownloadWorker.INPUT_URL,info.apkUrl)
                .putString(UpdateDownloadWorker.INPUT_VERSION,info.version)
                .putString(UpdateDownloadWorker.INPUT_DIGEST,info.digest==null?"":info.digest).build();

        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(UpdateDownloadWorker.class).setInputData(data).build();
        WorkManager.getInstance(this).enqueueUniqueWork(UpdateManager.WORK_DOWNLOAD,ExistingWorkPolicy.REPLACE,request);

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.getId()).observe(this,(WorkInfo work)->{
            if(work==null)return;
            int p=work.getProgress().getInt("progress",0);
            progress.setProgress(p);percent.setText(p+"%");
            if(work.getState()==WorkInfo.State.RUNNING)status.setText("A baixar atualização "+info.version+"…");
            if(work.getState()==WorkInfo.State.SUCCEEDED){
                spin.cancel();status.setText("Download concluído. A abrir instalador do Android…");progress.setProgress(100);percent.setText("100%");
                new Handler().postDelayed(()->{dialog.dismiss();UpdateManager.install(this);},600);
            } else if(work.getState()==WorkInfo.State.FAILED){
                spin.cancel();status.setText("Não foi possível concluir o download.");percent.setText("Falha");
            }
        });
    }

    private TextView actionButton(String text) {
        TextView t = chipText(text, Color.rgb(45, 157, 235), Color.WHITE);
        t.setTypeface(null, 1);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView circleButton(String icon) {
        TextView t = text(icon, 24, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundAccent(dp(36)));
        return t;
    }

    private TextView topIcon(String icon) {
        TextView t = text(icon, 30, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView topIconSmall(String icon) {
        TextView t = text(icon, 26, textSecondary());
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable roundDrawable(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        return roundDrawable(color, radius);
    }

    private GradientDrawable roundAccent(int radius) {
        return roundDrawable(Color.rgb(45, 157, 235), radius);
    }

    private LinearLayout roundedPanel(int color, int radius) {
        LinearLayout panel = new LinearLayout(this);
        panel.setBackground(roundDrawable(color, radius));
        return panel;
    }

    private int bg() { return darkMode ? Color.rgb(12, 15, 19) : Color.rgb(247, 248, 250); }
    private int surface() {
        if (AppearanceStore.BG_PLAIN.equals(AppearanceStore.background(this))) {
            return darkMode ? Color.rgb(25, 30, 37) : Color.WHITE;
        }
        return darkMode ? 0xE91B222C : 0xF2FFFFFF;
    }
    private int textPrimary() { return darkMode ? Color.WHITE : Color.rgb(38, 39, 42); }
    private int textSecondary() { return darkMode ? Color.rgb(160, 168, 177) : Color.rgb(103, 108, 115); }
    private int accent() { return AppearanceStore.accent(this); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value)) return fallback;
        return value.trim();
    }

    private static String formatMs(long ms) {
        long total = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadTracks();
            else Toast.makeText(this, "Acesso ao áudio não autorizado.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 7810 || requestCode == 7811) && resultCode == RESULT_OK) loadTracks();
        else if (requestCode == 7901 && resultCode == RESULT_OK) recreate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlaybackState();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        queryExecutor.shutdownNow();
        if (controller != null) controller.removeListener(playerListener);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        savePlaybackState();
        queryExecutor.shutdownNow();
        artworkExecutor.shutdownNow();
        if (miniSpin != null) miniSpin.cancel();
        super.onDestroy();
    }

    public static final class EqualizerGraphView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private int[] levels = new int[10];

        public EqualizerGraphView(Context context) { super(context); }

        public void setLevels(int[] values) {
            if (values == null || values.length == 0) return;
            levels = java.util.Arrays.copyOf(values, values.length);
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            paint.setColor(0xFF101216);
            c.drawRoundRect(0, 0, w, h, 26, 26, paint);

            float left = 34f, right = w - 24f, top = 24f, bottom = h - 54f;
            float usableW = right - left;
            paint.setStrokeWidth(3f);
            for (int i=0;i<10;i++) {
                float x=left+usableW*i/9f;
                paint.setColor(0xFF20242B);
                c.drawLine(x,top,x,bottom,paint);
            }

            android.graphics.Path area=new android.graphics.Path();
            android.graphics.Path line=new android.graphics.Path();
            for(int i=0;i<10;i++){
                float x=left+usableW*i/9f;
                int p=i<levels.length?levels[i]:50;
                float y=bottom-(bottom-top)*p/100f;
                if(i==0){area.moveTo(x,bottom);area.lineTo(x,y);line.moveTo(x,y);}
                else {area.lineTo(x,y);line.lineTo(x,y);}
            }
            area.lineTo(right,bottom);area.close();
            paint.setColor(0xFF747982);
            c.drawPath(area,paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0xFF9A9EA5);
            c.drawPath(line,paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);

            String[] labels={"31","62","125","250","500","1k","2k","4k","8k","16k"};
            paint.setColor(0xFF2D9DEB);
            paint.setTextSize(14);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            for(int i=0;i<labels.length;i++){
                float x=left+usableW*i/9f;
                c.drawText(labels[i],x,h-23,paint);
                int p=i<levels.length?levels[i]:50;
                String gain=String.format(Locale.getDefault(),"%+.1f",(p-50)*0.48f);
                c.drawText(gain,x,h-5,paint);
            }
        }
    }
}
