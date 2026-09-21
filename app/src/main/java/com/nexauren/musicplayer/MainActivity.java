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
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.Toast;

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
    private final Handler handler = new Handler();

    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout libraryContainer;
    private TextView pageTitle;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView miniArt;
    private SeekBar miniSeek;
    private TextView miniPlay;
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

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.isConnected()) updatePlaybackUi();
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
        @Override public void onMediaItemTransition(MediaItem item, int reason) { markCurrentRecent(); updatePlaybackUi(); }
        @Override public void onPlaybackStateChanged(int state) { updatePlaybackUi(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(33, 150, 243));
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
        handler.post(ticker);
        if ("equalizer".equalsIgnoreCase(getIntent().getStringExtra("page"))) showEqualizer();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(bg());
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(inside, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nowCard = roundedPanel(surface(), dp(22));
        nowCard.setOrientation(LinearLayout.VERTICAL);
        nowCard.setPadding(dp(14), dp(10), dp(14), dp(12));
        inside.addView(nowCard, new LinearLayout.LayoutParams(-1, dp(315)));

        LinearLayout nowHeader = new LinearLayout(this);
        nowHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView nowLabel = text("A tocar agora", 15, textPrimary());
        nowLabel.setTypeface(null, 1);
        nowHeader.addView(nowLabel, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView quality = chipText("8/100", accent(), Color.WHITE);
        nowHeader.addView(quality, new LinearLayout.LayoutParams(dp(72), dp(34)));
        nowCard.addView(nowHeader);

        FrameLayout artFrame = new FrameLayout(this);
        GradientDrawable artBg = roundedDrawable(Color.rgb(235, 238, 242), dp(18));
        artFrame.setBackground(artBg);
        artFrame.setClipToOutline(true);
        artFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        nowCard.addView(artFrame, new LinearLayout.LayoutParams(-1, dp(150)));

        bigArt = new ImageView(this);
        bigArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bigArt.setImageResource(android.R.drawable.ic_menu_gallery);
        bigArt.setColorFilter(Color.rgb(90, 100, 110));
        artFrame.addView(bigArt, new FrameLayout.LayoutParams(-1, -1));

        bigTitle = text("Nenhuma música selecionada", 22, Color.WHITE);
        bigTitle.setMaxLines(2);
        bigTitle.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bigTitle.setShadowLayer(5f, 0, 2f, Color.BLACK);
        FrameLayout.LayoutParams bt = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        bt.setMargins(dp(16), 0, dp(16), dp(6));
        artFrame.addView(bigTitle, bt);

        waveform = new WaveformView(this);
        nowCard.addView(waveform, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout current = new LinearLayout(this);
        current.setGravity(Gravity.CENTER_VERTICAL);
        bigPosition = text("0:00", 12, textSecondary());
        bigDuration = text("0:00", 12, textSecondary());
        TextView cur = bigPosition;
        TextView total = bigDuration;
        total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        current.addView(cur, new LinearLayout.LayoutParams(0, dp(24), 1));
        current.addView(total, new LinearLayout.LayoutParams(0, dp(24), 1));
        nowCard.addView(current);

        LinearLayout mainControls = new LinearLayout(this);
        mainControls.setGravity(Gravity.CENTER);
        TextView prev = circleButton("◀");
        TextView play = circleButton("▶");
        homePlay = play;
        TextView next = circleButton("▶|");
        TextView shuffle = circleButton("⤨");
        mainControls.addView(shuffle, new LinearLayout.LayoutParams(dp(46), dp(48)));
        mainControls.addView(prev, new LinearLayout.LayoutParams(dp(50), dp(48)));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(68), dp(58));
        playLp.setMargins(dp(10), 0, dp(10), 0);
        mainControls.addView(play, playLp);
        mainControls.addView(next, new LinearLayout.LayoutParams(dp(50), dp(48)));
        TextView repeat = circleButton("↻");
        mainControls.addView(repeat, new LinearLayout.LayoutParams(dp(46), dp(48)));
        nowCard.addView(mainControls);

        play.setOnClickListener(v -> togglePlayback());
        prev.setOnClickListener(v -> { if (controller != null) controller.seekToPreviousMediaItem(); });
        next.setOnClickListener(v -> { if (controller != null) controller.seekToNextMediaItem(); });
        shuffle.setOnClickListener(v -> { if (controller != null) controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled()); });
        repeat.setOnClickListener(v -> cycleRepeat());

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeader.setPadding(dp(2), dp(14), dp(2), dp(6));
        TextView title = text("Biblioteca", 20, textPrimary());
        title.setTypeface(null, 1);
        libraryHeader.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        countText = text("A carregar…", 12, textSecondary());
        countText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        libraryHeader.addView(countText, new LinearLayout.LayoutParams(dp(180), dp(42)));
        inside.addView(libraryHeader);

        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        inside.addView(libraryContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView adSlot = text("Nexauren • experiência sem distrações", 11, textSecondary());
        adSlot.setGravity(Gravity.CENTER);
        adSlot.setPadding(0, dp(10), 0, dp(12));
        inside.addView(adSlot);
        renderLibrary();
    }

    private View topBar(String title, boolean back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(4), 0);
        bar.setBackgroundColor(Color.rgb(33, 150, 243));

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
                @Override public void onTextChanged(CharSequence s, int st, int before, int count) { filterLibrary(s.toString()); }
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
        eq.setOnClickListener(v -> showEqualizer());

        TextView more = topIcon("⋮");
        bar.addView(more, new LinearLayout.LayoutParams(dp(40), -1));
        more.setOnClickListener(v -> showGlobalMenu(more));
        return bar;
    }

    private void buildMiniPlayer(LinearLayout shell) {
        LinearLayout mini = roundedPanel(surface(), dp(18));
        mini.setPadding(dp(8), dp(6), dp(6), dp(6));
        shell.addView(mini, new LinearLayout.LayoutParams(-1, dp(66)));
        mini.setOnClickListener(v -> startActivity(new Intent(this, NowPlayingActivity.class)));

        miniArt = new ImageView(this);
        miniArt.setImageResource(android.R.drawable.ic_menu_gallery);
        miniArt.setColorFilter(Color.rgb(80, 90, 100));
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mini.addView(miniArt, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);
        miniTitle = text("Nenhuma música", 14, textPrimary());
        miniTitle.setTypeface(null, 1);
        miniArtist = text("Selecione uma faixa", 12, textSecondary());
        labels.addView(miniTitle, new LinearLayout.LayoutParams(-1, dp(25)));
        labels.addView(miniArtist, new LinearLayout.LayoutParams(-1, dp(20)));
        mini.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1));

        miniPlay = circleButton("▶");
        mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(50), dp(50)));
        miniPlay.setOnClickListener(v -> togglePlayback());
        miniSeek = new SeekBar(this);
        miniSeek.setVisibility(View.GONE);
    }

    private void showEqualizer() {
        currentPage = 1;
        searchMode = false;
        root.removeAllViews();
        LinearLayout shell = basePage("Equalizador");
        LinearLayout body = pageBody(shell);

        LinearLayout intro = roundedPanel(surface(), dp(18));
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout introLine = new LinearLayout(this);
        introLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Equalizador gráfico", 18, textPrimary());
        title.setTypeface(null, 1);
        introLine.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView state = chipText(effectsEnabled ? "ATIVO" : "DESLIGADO",
                effectsEnabled ? accent() : surface_2(), Color.WHITE);
        state.setTextSize(11);
        introLine.addView(state, new LinearLayout.LayoutParams(dp(84), dp(32)));
        intro.addView(introLine);

        TextView caption = text("10 bandas • alterações aplicadas em tempo real", 12, textSecondary());
        intro.addView(caption, new LinearLayout.LayoutParams(-1, dp(22)));
        body.addView(intro, new LinearLayout.LayoutParams(-1, dp(74)));

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = new LinearLayout(this);
        presets.setPadding(0, dp(8), dp(8), dp(6));
        String[] names = {"Plano","Rock","Jazz","Clássico","Bass","Vocal"};
        for (String name : names) {
            TextView chip = chipText(name, "Plano".equals(name) ? accent() : surface(), textPrimary());
            if ("Plano".equals(name)) chip.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(42));
            lp.setMargins(dp(3),0,dp(3),0);
            presets.addView(chip,lp);
            chip.setOnClickListener(v -> {
                if (PlaybackService.applyEqualizerPreset(name,10)) {
                    Toast.makeText(this,"Predefinição "+name+" aplicada.",Toast.LENGTH_SHORT).show();
                    showEqualizer();
                } else Toast.makeText(this,"Toque uma música para ativar o equalizador.",Toast.LENGTH_SHORT).show();
            });
        }
        presetScroll.addView(presets,new ViewGroup.LayoutParams(-2,dp(50)));
        body.addView(presetScroll);

        EqualizerGraphView graph = new EqualizerGraphView(this);
        graph.setLevels(PlaybackService.getEqualizerLevels(10));
        body.addView(graph,new LinearLayout.LayoutParams(-1,dp(238)));

        HorizontalScrollView bandScroll = new HorizontalScrollView(this);
        bandScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bands = new LinearLayout(this);
        bands.setGravity(Gravity.CENTER_VERTICAL);
        bands.setPadding(dp(2),dp(6),dp(14),dp(2));

        int[] levels = PlaybackService.getEqualizerLevels(10);
        String[] labels = {"31","62","125","250","500","1k","2k","4k","8k","16k"};
        for (int i=0;i<10;i++) {
            final int bandIndex=i;
            EqBandView band=new EqBandView(this);
            band.setData(labels[i], PlaybackService.getEqualizerBandCount()>0 ? levels[i] : 50);
            band.setListener(percent -> {
                if (!PlaybackService.setEqualizerBand(bandIndex,10,percent)) {
                    Toast.makeText(this,"O dispositivo não disponibilizou Equalizer para esta sessão.",Toast.LENGTH_SHORT).show();
                    return;
                }
                graph.setLevels(PlaybackService.getEqualizerLevels(10));
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(58),dp(205));
            lp.setMargins(dp(2),0,dp(2),0);
            bands.addView(band,lp);
        }
        bandScroll.addView(bands,new ViewGroup.LayoutParams(-2,dp(215)));
        body.addView(bandScroll);

        LinearLayout effectsRow = new LinearLayout(this);
        effectsRow.setGravity(Gravity.CENTER);
        effectsRow.setPadding(0,dp(6),0,dp(2));
        effectsRow.addView(dialEffect("Pré-amplificador",50,true),new LinearLayout.LayoutParams(0,dp(178),1));
        effectsRow.addView(dialEffect("Graves",0,false),new LinearLayout.LayoutParams(0,dp(178),1));
        body.addView(effectsRow);

        TextView hint=text("Dica: mantenha o pré-amplificador perto de 50% para evitar saturação.",12,textSecondary());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(8),dp(6),dp(8),dp(12));
        body.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
    }


    private void rebuildEqualizerBands() {
        showEqualizer();
    }

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
        });
        return box;
    }

    private void showSound() {
        currentPage = 2;
        searchMode = false;
        root.removeAllViews();
        LinearLayout shell = basePage("Som");
        LinearLayout body = pageBody(shell);

        body.addView(dialRow(
                dialBalance(),
                dialVirtualizer()));

        body.addView(dialRow(
                dialSpeed("Velocidade", 50, true),
                dialSpeed("Altura", 50, false)));

        TextView modeLabel=text("Modo de saída",15,textSecondary());
        modeLabel.setGravity(Gravity.CENTER);
        modeLabel.setTypeface(null,1);
        body.addView(modeLabel,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout stereo = new LinearLayout(this);
        stereo.setGravity(Gravity.CENTER);
        stereo.setPadding(dp(6),dp(4),dp(6),dp(12));
        TextView st=segmented("Estéreo",true);
        TextView mo=segmented("Mono",false);
        stereo.addView(st,new LinearLayout.LayoutParams(0,dp(52),1));
        stereo.addView(mo,new LinearLayout.LayoutParams(0,dp(52),1));
        body.addView(stereo);
        st.setOnClickListener(v->{PlaybackService.setMonoMode(false);st.setBackground(roundAccent(dp(28)));mo.setBackground(roundDrawable(0xFFD1D2D5,dp(28)));});
        mo.setOnClickListener(v->{PlaybackService.setMonoMode(true);mo.setBackground(roundAccent(dp(28)));st.setBackground(roundDrawable(0xFFD1D2D5,dp(28)));});

        LinearLayout volCard=roundedPanel(surface(),dp(18));
        volCard.setOrientation(LinearLayout.VERTICAL);
        volCard.setPadding(dp(14),dp(10),dp(14),dp(10));
        TextView vt=text("Volume do player",17,textPrimary());vt.setTypeface(null,1);
        volCard.addView(vt,new LinearLayout.LayoutParams(-1,dp(30)));
        SeekBar volumeBar=new SeekBar(this);
        volumeBar.setMax(100);
        int start=controller!=null&&controller.isConnected()?Math.round(controller.getVolume()*100f):75;
        volumeBar.setProgress(start);
        volCard.addView(volumeBar,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView vv=text(start+"%",13,textSecondary());vv.setGravity(Gravity.CENTER);
        volCard.addView(vv,new LinearLayout.LayoutParams(-1,dp(24)));
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){vv.setText(p+"%");if(from&&controller!=null&&controller.isConnected())controller.setVolume(p/100f);}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        body.addView(volCard,new LinearLayout.LayoutParams(-1,dp(118)));
    }

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

    private void showReverb() {
        currentPage = 3;
        searchMode = false;
        root.removeAllViews();
        LinearLayout shell = basePage("Reverberação");
        LinearLayout body = pageBody(shell);

        TextView intro=text("Espaço e profundidade para a faixa atual",14,textSecondary());
        intro.setGravity(Gravity.CENTER);
        body.addView(intro,new LinearLayout.LayoutParams(-1,dp(36)));

        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setPadding(0,dp(2),dp(8),dp(10));
        String[] names={"Sinal seco","Sala","Studio","Hall"};
        for(String n:names){
            TextView c=chipText(n,"Sinal seco".equals(n)?accent():surface(),"Sinal seco".equals(n)?Color.WHITE:textPrimary());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(102),dp(46));lp.setMargins(dp(3),0,dp(3),0);chips.addView(c,lp);
            c.setOnClickListener(v->{
                reverbPreset=n;
                int p="Sinal seco".equals(n)?0:reverbMix;
                PlaybackService.setReverb(n,p);
                renderReverbPresetState(chips,n);
            });
        }
        scroll.addView(chips,new ViewGroup.LayoutParams(-2,dp(58)));
        body.addView(scroll);

        LinearLayout mixCard=roundedPanel(surface(),dp(20));
        mixCard.setOrientation(LinearLayout.VERTICAL);mixCard.setGravity(Gravity.CENTER);mixCard.setPadding(dp(10),dp(8),dp(10),dp(10));
        DialView mix=new DialView(this);mix.setPercent(reverbMix);
        mixCard.addView(mix,new LinearLayout.LayoutParams(dp(220),dp(210)));
        TextView ml=text("Mistura",18,textPrimary());ml.setTypeface(null,1);ml.setGravity(Gravity.CENTER);
        mixCard.addView(ml,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView mv=text(reverbMix+"%",13,textSecondary());mv.setGravity(Gravity.CENTER);
        mixCard.addView(mv,new LinearLayout.LayoutParams(-1,dp(24)));
        mix.setOnDialChangedListener(p->{reverbMix=p;mv.setText(p+"%");PlaybackService.setReverb(reverbPreset,p);});
        body.addView(mixCard,new LinearLayout.LayoutParams(-1,dp(292)));

        LinearLayout quick=roundedPanel(surface(),dp(18));
        quick.setPadding(dp(14),dp(10),dp(14),dp(10));
        quick.setOrientation(LinearLayout.VERTICAL);
        TextView q=text("Perfis rápidos",15,textPrimary());q.setTypeface(null,1);quick.addView(q,new LinearLayout.LayoutParams(-1,dp(28)));
        addReverbPresetRow(quick,"Pequeno","Sala",25);
        addReverbPresetRow(quick,"Studio","Studio",35);
        addReverbPresetRow(quick,"Grande","Hall",45);
        body.addView(quick,new LinearLayout.LayoutParams(-1,dp(200)));

        TextView note=text("O processamento usa o motor de reverberação do Android quando disponível.",12,textSecondary());
        note.setGravity(Gravity.CENTER);note.setPadding(dp(8),dp(8),dp(8),dp(16));
        body.addView(note,new LinearLayout.LayoutParams(-1,dp(54)));
    }

    private String reverbPreset="Sinal seco";
    private int reverbMix=0;

    private void renderReverbPresetState(LinearLayout chips,String selected){
        for(int i=0;i<chips.getChildCount();i++){
            TextView v=(TextView)chips.getChildAt(i);
            boolean on=v.getText().toString().equals(selected);
            v.setBackground(roundDrawable(on?accent():surface(),dp(30)));
            v.setTextColor(on?Color.WHITE:textPrimary());
        }
    }

    private void addReverbPresetRow(LinearLayout parent,String label,String preset,int mix){
        TextView b=chipText(label+"  •  "+mix+"%",surface(),textPrimary());
        b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(14),0,dp(14),0);
        parent.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        b.setOnClickListener(v->{reverbPreset=preset;reverbMix=mix;PlaybackService.setReverb(preset,mix);Toast.makeText(this,label+" aplicado.",Toast.LENGTH_SHORT).show();});
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
        clickableRow(list, "Sobre o Nexauren Music Player", "Versão 0.3.0", "ⓘ", () -> showAbout("Nexauren Music Player", "Versão 0.4.0 • player local, efeitos, favoritos, fila e pesquisa."));
    }

    private LinearLayout basePage(String title) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        if (currentPage >= 1 && currentPage <= 3) shell.addView(audioTopBar(title, currentPage), new LinearLayout.LayoutParams(-1, dp(56)));
        else shell.addView(topBar(title, true), new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        return shell;
    }

    private View audioTopBar(String title, int activePage) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(6), 0);
        bar.setBackgroundColor(Color.rgb(33, 150, 243));

        TextView back = topIcon("‹");
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), -1));
        back.setOnClickListener(v -> showHome(true));

        TextView label = text(title, 18, Color.WHITE);
        bar.addView(label, new LinearLayout.LayoutParams(0, -1, 1));

        TextView eq = audioTool("☷", activePage == 1);
        TextView sound = audioTool("◖", activePage == 2);
        TextView reverb = audioTool("◎", activePage == 3);
        bar.addView(eq, new LinearLayout.LayoutParams(dp(46), -1));
        bar.addView(sound, new LinearLayout.LayoutParams(dp(46), -1));
        bar.addView(reverb, new LinearLayout.LayoutParams(dp(46), -1));

        Switch master = new Switch(this);
        master.setChecked(effectsEnabled);
        master.setShowText(false);
        master.setScaleX(0.82f);
        master.setScaleY(0.82f);
        master.setContentDescription("Efeitos de áudio");
        bar.addView(master, new LinearLayout.LayoutParams(dp(56), -1));
        master.setOnCheckedChangeListener((buttonView, checked) -> {
            effectsEnabled = checked;
            getSharedPreferences("nexauren", MODE_PRIVATE).edit().putBoolean("effects", checked).apply();
            PlaybackService.setEffectsEnabled(checked);
        });

        eq.setOnClickListener(v -> showEqualizer());
        sound.setOnClickListener(v -> showSound());
        reverb.setOnClickListener(v -> showReverb());
        return bar;
    }

    private TextView audioTool(String icon, boolean active) {
        TextView t = topIcon(icon);
        t.setTextSize(25);
        t.setBackground(active ? roundDrawable(0x3358B7F0, dp(16)) : roundDrawable(Color.TRANSPARENT, dp(16)));
        return t;
    }

    private LinearLayout pageBody(LinearLayout shell) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return body;
    }

    private void openDrawer() {
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(darkMode ? Color.rgb(20,24,30) : Color.WHITE);
        int width = (int)Math.min(dp(330), getResources().getDisplayMetrics().widthPixels * 0.86f);
        overlay.addView(drawer, new FrameLayout.LayoutParams(width, -1, Gravity.START));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(10));
        hero.setBackgroundColor(Color.rgb(33, 150, 243));
        drawer.addView(hero, new LinearLayout.LayoutParams(-1, dp(190)));

        TextView logo = text("N", 70, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, 1);
        hero.addView(logo, new LinearLayout.LayoutParams(-1, dp(98)));
        TextView name = text("NEXAUREN", 22, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(null, 1);
        hero.addView(name, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView sub = text("MUSIC PLAYER", 11, Color.WHITE);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub, new LinearLayout.LayoutParams(-1, dp(25)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        drawer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        drawerItem(menu, "⌂", "Biblioteca", () -> { root.removeView(overlay); showHome(true); });
        drawerItem(menu, "♡", "Favoritos", () -> { root.removeView(overlay); showFavorites(); });
        drawerItem(menu, "◷", "Reproduzido recentemente", () -> { root.removeView(overlay); showRecent(); });
        drawerItem(menu, "☷", "Fila de reprodução", () -> { root.removeView(overlay); showQueue(); });
        drawerItem(menu, "▤", "Minha playlist", () -> { root.removeView(overlay); showPlaylist(); });
        drawerItem(menu, "☰", "Equalizador", () -> { root.removeView(overlay); showEqualizer(); });
        drawerItem(menu, "◉", "Visualizador de música", () -> startActivity(new Intent(this, VisualizerActivity.class)));
        drawerItem(menu, "◈", "Som", () -> { root.removeView(overlay); showSound(); });
        drawerItem(menu, "◌", "Reverberação", () -> { root.removeView(overlay); showReverb(); });
        drawerItem(menu, "▣", "Modo de condução", () -> { root.removeView(overlay); showDrivingMode(); });
        drawerItem(menu, "⏱", "Temporizador de sono", this::showSleepTimer);
        drawerItem(menu, "⧉", "Encontrar duplicados", () -> { root.removeView(overlay); showDuplicates(); });
        drawerItem(menu, "◐", "Tema claro/escuro", () -> { root.removeView(overlay); toggleTheme(); showHome(true); });
        drawerItem(menu, "⚙", "Configurações", () -> { root.removeView(overlay); showSettings(); });

        overlay.setOnClickListener(v -> root.removeView(overlay));
    }

    private void showGlobalMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] entries = {
                "Pesquisar", "Adicionar à minha playlist", "Eliminar faixa atual",
                "Enviar faixa", "Detalhes", "Velocidade de reprodução",
                "Visualizador de música", "Temporizador de sono", "Letra da música",
                "Definir como toque", "Mais do artista", "Mais do álbum",
                "Adicionar aos favoritos", "Abrir fila", "Cortar trecho"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            String value = item.getTitle().toString();
            if ("Pesquisar".equals(value)) { searchMode=true; showHome(true); }
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
                "Adicionar à minha playlist", "Enviar", "Detalhes",
                "Mais do artista", "Mais do álbum", "Eliminar"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            String value=item.getTitle().toString();
            if(value.contains("favoritos")) {
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

    private void showSleepTimer() {
        String[] values = {"Desligado", "15 minutos", "30 minutos", "45 minutos", "60 minutos"};
        new AlertDialog.Builder(this).setTitle("Temporizador de sono").setItems(values, (d, which) -> {
            if (which == 0) sleepEndAtMs = 0L;
            else sleepEndAtMs = System.currentTimeMillis() + Long.parseLong(values[which].split(" ")[0]) * 60_000L;
            Toast.makeText(this, which == 0 ? "Temporizador desligado." : "A música vai parar em " + values[which] + ".", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void cycleRepeat() {
        if (controller == null) return;
        int mode = controller.getRepeatMode();
        int next = mode == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL :
                mode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        controller.setRepeatMode(next);
        Toast.makeText(this, next == Player.REPEAT_MODE_ONE ? "Repetir uma" : next == Player.REPEAT_MODE_ALL ? "Repetir tudo" : "Repetição desligada", Toast.LENGTH_SHORT).show();
    }

    private void togglePlayback() {
        if (controller == null || !controller.isConnected()) return;
        if (controller.isPlaying()) controller.pause(); else controller.play();
    }

    private void filterLibrary(String q) {
        String query = q.trim().toLowerCase(Locale.getDefault());
        visibleTracks.clear();
        for (Track t : tracks) {
            if (query.isEmpty() || t.title.toLowerCase(Locale.getDefault()).contains(query) ||
                    t.artist.toLowerCase(Locale.getDefault()).contains(query) ||
                    t.album.toLowerCase(Locale.getDefault()).contains(query)) visibleTracks.add(t);
        }
        renderLibraryList();
    }

    private void renderLibrary() {
        visibleTracks.clear();
        visibleTracks.addAll(tracks);
        renderLibraryList();
    }

    private void renderLibraryList() {
        if (libraryContainer == null) return;
        libraryContainer.removeAllViews();
        if (visibleTracks.isEmpty()) {
            TextView empty = text("Nenhuma música encontrada. Permita o acesso ao áudio e atualize a biblioteca.", 15, textSecondary());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(30), dp(10), dp(30));
            libraryContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
            return;
        }
        for (int i = 0; i < visibleTracks.size(); i++) addTrackRow(visibleTracks.get(i), i);
        countText.setText(visibleTracks.size() + (visibleTracks.size() == 1 ? " faixa" : " faixas"));
    }

    private void addTrackRow(Track track, int index) {
        LinearLayout row = roundedPanel(surface(), dp(16));
        row.setPadding(dp(8), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(78));
        rowLp.setMargins(0, 0, 0, dp(6));
        libraryContainer.addView(row, rowLp);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageResource(android.R.drawable.ic_menu_gallery);
        art.setColorFilter(0xFF65707C);
        row.addView(art, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(6), 0);
        TextView title = text(track.title, 16, textPrimary());
        title.setMaxLines(2);
        title.setTypeface(null, 1);
        TextView sub = text(track.artist + " · " + track.album, 12, textSecondary());
        sub.setMaxLines(1);
        labels.addView(title, new LinearLayout.LayoutParams(-1, dp(40)));
        labels.addView(sub, new LinearLayout.LayoutParams(-1, dp(22)));
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(62), 1));

        TextView duration = text(formatMs(track.durationMs), 12, textSecondary());
        duration.setGravity(Gravity.CENTER);
        row.addView(duration, new LinearLayout.LayoutParams(dp(48), dp(62)));

        TextView more = topIconSmall("⋮");
        more.setTextColor(textSecondary());
        row.addView(more, new LinearLayout.LayoutParams(dp(36), dp(62)));
        more.setOnClickListener(v -> showTrackMenu(more, track));
        row.setOnClickListener(v -> playTrack(track));
        loadArtwork(track.uri, art);
    }

    private void playTrack(Track selected) {
        if (controller == null || !controller.isConnected() || tracks.isEmpty()) return;
        ArrayList<MediaItem> items = new ArrayList<>(tracks.size());
        int selectedIndex = 0;
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (t.id == selected.id) selectedIndex = i;
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build();
            items.add(new MediaItem.Builder().setMediaId(String.valueOf(t.id)).setUri(t.uri).setMediaMetadata(metadata).build());
        }
        controller.setMediaItems(items, selectedIndex, 0);
        controller.prepare();
        controller.play();
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
        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(list.isEmpty()){
            TextView empty=text("Ainda não há faixas nesta coleção.",16,textSecondary());empty.setGravity(Gravity.CENTER);box.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        } else for(int i=0;i<list.size();i++)addCollectionRow(box,list.get(i),i);
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
            String a = item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString();
            if (miniTitle != null) miniTitle.setText(t);
            if (miniArtist != null) miniArtist.setText(a);
            if (miniPlay != null) miniPlay.setText(controller.isPlaying() ? "Ⅱ" : "▶");
            if (homePlay != null) homePlay.setText(controller.isPlaying() ? "Ⅱ" : "▶");
            if (bigTitle != null) bigTitle.setText(t);
            if (waveform != null) {
                waveform.setPlaying(controller.isPlaying());
                waveform.setProgress(controller.getDuration() > 0 ? (float) controller.getCurrentPosition() / (float) controller.getDuration() : 0f);
            }
            long d=Math.max(0,controller.getDuration()), p=Math.max(0,controller.getCurrentPosition());
            if(bigPosition!=null)bigPosition.setText(formatMs(p));
            if(bigDuration!=null)bigDuration.setText(formatMs(d));

            Track match = null;
            String id = item.mediaId;
            if (id != null) {
                try {
                    long trackId = Long.parseLong(id);
                    for (Track tr : tracks) if (tr.id == trackId) { match = tr; break; }
                } catch (Exception ignored) {}
            }
            if (match != null) {
                if (match.id != lastArtworkId) {
                    lastArtworkId = match.id;
                    if (miniArt != null) loadArtwork(match.uri, miniArt);
                    if (bigArt != null) loadArtwork(match.uri, bigArt);
                }
            }
        }
    }

    private void loadArtwork(final android.net.Uri uri, final ImageView target) {
        queryExecutor.execute(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                byte[] data = retriever.getEmbeddedPicture();
                if (data != null) bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignored) {
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (result != null) {
                    target.clearColorFilter();
                    target.setImageBitmap(result);
                }
            });
        });
    }

    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
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
        if (libraryContainer == null) return;
        libraryContainer.removeAllViews();
        TextView loading = text("A atualizar a biblioteca…", 15, textSecondary());
        loading.setGravity(Gravity.CENTER);
        libraryContainer.addView(loading, new LinearLayout.LayoutParams(-1, dp(96)));

        queryExecutor.execute(() -> {
            ArrayList<Track> found = new ArrayList<>();
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                    MediaStore.Audio.Media.DURATION + " > 0";
            try (Cursor c = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
                if (c != null) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    while (c.moveToNext()) {
                        long id = c.getLong(idCol);
                        String title = clean(c.getString(titleCol), "Sem título");
                        String artist = clean(c.getString(artistCol), "Artista desconhecido");
                        String album = clean(c.getString(albumCol), "Álbum desconhecido");
                        long duration = c.getLong(durationCol);
                        found.add(new Track(id, title, artist, album, duration,
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)));
                    }
                }
            } catch (SecurityException ignored) {}

            runOnUiThread(() -> {
                tracks.clear();
                tracks.addAll(found);
                renderLibrary();
                countText.setText(found.size() + (found.size() == 1 ? " faixa" : " faixas"));
            });
        });
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
    private int surface() { return darkMode ? Color.rgb(25, 30, 37) : Color.WHITE; }
    private int textPrimary() { return darkMode ? Color.WHITE : Color.rgb(38, 39, 42); }
    private int textSecondary() { return darkMode ? Color.rgb(160, 168, 177) : Color.rgb(103, 108, 115); }
    private int accent() { return Color.rgb(45, 157, 235); }
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
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        queryExecutor.shutdownNow();
        if (controller != null) controller.removeListener(playerListener);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
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
