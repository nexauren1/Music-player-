package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
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
import java.util.List;
import java.util.Locale;
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
    private EditText searchField;
    private WaveformView waveform;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int currentPage = 0;
    private boolean darkMode = false;
    private boolean searchMode = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.isConnected()) updatePlaybackUi();
            handler.postDelayed(this, 500);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackUi(); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { updatePlaybackUi(); }
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
        buildShell();
        connectController();
        requestNotificationPermission();
        ensureAudioPermission();
        handler.post(ticker);
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
        nowCard.setPadding(dp(14), dp(10), dp(14), dp(12));
        inside.addView(nowCard, new LinearLayout.LayoutParams(-1, dp(440)));

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
        nowCard.addView(artFrame, new LinearLayout.LayoutParams(-1, dp(245)));

        ImageView bigArt = new ImageView(this);
        bigArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bigArt.setImageResource(android.R.drawable.ic_menu_gallery);
        bigArt.setColorFilter(Color.rgb(90, 100, 110));
        artFrame.addView(bigArt, new FrameLayout.LayoutParams(-1, -1));

        TextView bigTitle = text("Nenhuma música selecionada", 22, Color.WHITE);
        bigTitle.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bigTitle.setShadowLayer(5f, 0, 2f, Color.BLACK);
        FrameLayout.LayoutParams bt = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        bt.setMargins(dp(16), 0, dp(16), dp(6));
        artFrame.addView(bigTitle, bt);

        waveform = new WaveformView(this);
        nowCard.addView(waveform, new LinearLayout.LayoutParams(-1, dp(88)));

        LinearLayout current = new LinearLayout(this);
        current.setGravity(Gravity.CENTER_VERTICAL);
        TextView cur = text("0:00", 12, textSecondary());
        TextView total = text("0:00", 12, textSecondary());
        total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        current.addView(cur, new LinearLayout.LayoutParams(0, dp(24), 1));
        current.addView(total, new LinearLayout.LayoutParams(0, dp(24), 1));
        nowCard.addView(current);

        LinearLayout mainControls = new LinearLayout(this);
        mainControls.setGravity(Gravity.CENTER);
        TextView prev = circleButton("◀");
        TextView play = circleButton("▶");
        TextView next = circleButton("▶|");
        TextView shuffle = circleButton("⤨");
        mainControls.addView(shuffle, new LinearLayout.LayoutParams(dp(52), dp(54)));
        mainControls.addView(prev, new LinearLayout.LayoutParams(dp(58), dp(54)));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(84), dp(72));
        playLp.setMargins(dp(10), 0, dp(10), 0);
        mainControls.addView(play, playLp);
        mainControls.addView(next, new LinearLayout.LayoutParams(dp(58), dp(54)));
        TextView repeat = circleButton("↻");
        mainControls.addView(repeat, new LinearLayout.LayoutParams(dp(52), dp(54)));
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
        bar.setPadding(dp(8), 0, dp(6), 0);
        bar.setBackgroundColor(Color.rgb(33, 150, 243));

        TextView left = topIcon(back ? "‹" : "☰");
        bar.addView(left, new LinearLayout.LayoutParams(dp(48), -1));
        left.setOnClickListener(v -> { if (back) showHome(true); else openDrawer(); });

        if (searchMode) {
            searchField = new EditText(this);
            searchField.setSingleLine(true);
            searchField.setHint("Pesquisar música");
            searchField.setTextColor(Color.WHITE);
            searchField.setHintTextColor(0xCCFFFFFF);
            searchField.setTextSize(17);
            searchField.setBackgroundColor(Color.TRANSPARENT);
            searchField.setPadding(0, 0, dp(8), 0);
            bar.addView(searchField, new LinearLayout.LayoutParams(0, -1, 1));
            searchField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int before, int count) { filterLibrary(s.toString()); }
                @Override public void afterTextChanged(Editable e) {}
            });
            searchField.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            pageTitle = text(title, 18, Color.WHITE);
            bar.addView(pageTitle, new LinearLayout.LayoutParams(0, -1, 1));
        }

        TextView search = topIcon(searchMode ? "×" : "⌕");
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), -1));
        search.setOnClickListener(v -> {
            searchMode = !searchMode;
            showHome(true);
        });

        TextView cast = topIcon("▣");
        bar.addView(cast, new LinearLayout.LayoutParams(dp(48), -1));
        cast.setOnClickListener(v -> Toast.makeText(this, "Dispositivo de transmissão: em breve", Toast.LENGTH_SHORT).show());

        TextView eq = topIcon("☷");
        bar.addView(eq, new LinearLayout.LayoutParams(dp(48), -1));
        eq.setOnClickListener(v -> showEqualizer());

        TextView more = topIcon("⋮");
        bar.addView(more, new LinearLayout.LayoutParams(dp(42), -1));
        more.setOnClickListener(v -> showGlobalMenu(more));
        return bar;
    }

    private void buildMiniPlayer(LinearLayout shell) {
        LinearLayout mini = roundedPanel(surface(), dp(18));
        mini.setPadding(dp(8), dp(6), dp(6), dp(6));
        shell.addView(mini, new LinearLayout.LayoutParams(-1, dp(78)));

        miniArt = new ImageView(this);
        miniArt.setImageResource(android.R.drawable.ic_menu_gallery);
        miniArt.setColorFilter(Color.rgb(80, 90, 100));
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mini.addView(miniArt, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);
        miniTitle = text("Nenhuma música", 14, textPrimary());
        miniTitle.setTypeface(null, 1);
        miniArtist = text("Selecione uma faixa", 12, textSecondary());
        labels.addView(miniTitle, new LinearLayout.LayoutParams(-1, dp(28)));
        labels.addView(miniArtist, new LinearLayout.LayoutParams(-1, dp(22)));
        mini.addView(labels, new LinearLayout.LayoutParams(0, dp(56), 1));

        miniPlay = circleButton("▶");
        mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(60), dp(56)));
        miniPlay.setOnClickListener(v -> togglePlayback());
        miniSeek = new SeekBar(this);
        miniSeek.setVisibility(View.GONE);
    }

    private void showEqualizer() {
        currentPage = 1;
        root.removeAllViews();
        LinearLayout shell = basePage("Equalizador");
        LinearLayout body = pageBody(shell);

        LinearLayout presets = new LinearLayout(this);
        presets.setGravity(Gravity.CENTER_VERTICAL);
        presets.setPadding(0, dp(4), 0, dp(12));
        String[] names = {"Plano", "Rock", "Jazz", "Clássico", "Mais"};
        for (String name : names) {
            TextView chip = chipText(name, name.equals("Plano") ? accent() : surface(), name.equals("Plano") ? Color.WHITE : textPrimary());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(name.equals("Mais") ? dp(58) : -2, dp(46));
            lp.setMargins(dp(4), 0, dp(6), 0);
            presets.addView(chip, lp);
            chip.setOnClickListener(v -> {
                if (!name.equals("Mais")) Toast.makeText(this, "Predefinição " + name + " selecionada", Toast.LENGTH_SHORT).show();
            });
        }
        body.addView(presets);

        EqualizerGraphView graph = new EqualizerGraphView(this);
        body.addView(graph, new LinearLayout.LayoutParams(-1, dp(380)));

        body.addView(dialRow(
                dial("Pré-amplificador", 83),
                dial("Graves", 95)));
        body.addView(dialRow(
                dial("Médios", 50),
                dial("Agudos", 50)));

        TextView reset = actionButton("↻  Repor tudo");
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(160), dp(46), Gravity.TOP | Gravity.END);
        root.addView(reset, rp);
        reset.setOnClickListener(v -> Toast.makeText(this, "Equalizador reposto", Toast.LENGTH_SHORT).show());
    }

    private void showSound() {
        currentPage = 2;
        root.removeAllViews();
        LinearLayout shell = basePage("Som");
        LinearLayout body = pageBody(shell);

        body.addView(dialRow(
                dial("Balanço", 50),
                dial("Som surround 3D", 0)));
        body.addView(dialRow(
                dial("Velocidade", 50, "1.00x", 0.5, 2.0),
                dial("Altura", 50, "1.00x", 0.5, 2.0)));

        LinearLayout stereo = new LinearLayout(this);
        stereo.setGravity(Gravity.CENTER);
        stereo.setPadding(dp(6), dp(4), dp(6), dp(14));
        TextView st = segmented("Estéreo", true);
        TextView mo = segmented("Mono", false);
        stereo.addView(st, new LinearLayout.LayoutParams(dp(150), dp(52)));
        stereo.addView(mo, new LinearLayout.LayoutParams(dp(150), dp(52)));
        body.addView(stereo);
        mo.setOnClickListener(v -> {
            mo.setBackground(roundAccent(dp(28)));
            st.setBackground(roundDrawable(0xFFD1D2D5, dp(28)));
            Toast.makeText(this, "Modo mono selecionado", Toast.LENGTH_SHORT).show();
        });
        st.setOnClickListener(v -> {
            st.setBackground(roundAccent(dp(28)));
            mo.setBackground(roundDrawable(0xFFD1D2D5, dp(28)));
            Toast.makeText(this, "Modo estéreo selecionado", Toast.LENGTH_SHORT).show();
        });

        TextView volume = text("Volume", 19, textPrimary());
        volume.setTypeface(null, 1);
        volume.setGravity(Gravity.CENTER);
        body.addView(volume, new LinearLayout.LayoutParams(-1, dp(34)));
        DialView big = new DialView(this);
        big.setPercent(47);
        body.addView(big, new LinearLayout.LayoutParams(dp(300), dp(260)));
        TextView volValue = text("47%", 14, textSecondary());
        volValue.setGravity(Gravity.CENTER);
        body.addView(volValue, new LinearLayout.LayoutParams(-1, dp(28)));
        big.setOnDialChangedListener(p -> {
            volValue.setText(p + "%");
            if (controller != null && controller.isConnected()) controller.setVolume(p / 100f);
        });
    }

    private void showReverb() {
        currentPage = 3;
        root.removeAllViews();
        LinearLayout shell = basePage("Reverberação");
        LinearLayout body = pageBody(shell);

        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {"Sinal seco", "Sala", "Studio", "Hall", "+"};
        for (String n : names) {
            TextView c = chipText(n, surface(), textPrimary());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(n.equals("+") ? dp(58) : -2, dp(50));
            lp.setMargins(dp(4), 0, dp(5), dp(12));
            chips.addView(c, lp);
        }
        body.addView(chips);

        DialView mix = new DialView(this);
        mix.setPercent(25);
        LinearLayout mixBox = new LinearLayout(this);
        mixBox.setGravity(Gravity.CENTER);
        mixBox.setOrientation(LinearLayout.VERTICAL);
        mixBox.addView(mix, new LinearLayout.LayoutParams(dp(270), dp(240)));
        TextView mt = text("Mistura", 18, textPrimary());
        mt.setGravity(Gravity.CENTER);
        mixBox.addView(mt, new LinearLayout.LayoutParams(-1, dp(32)));
        TextView mv = text("0.00", 13, textSecondary());
        mv.setGravity(Gravity.CENTER);
        mixBox.addView(mv, new LinearLayout.LayoutParams(-1, dp(28)));
        body.addView(mixBox, new LinearLayout.LayoutParams(-1, dp(300)));

        LinearLayout locked = roundedPanel(0xFFE0E0E2, dp(2));
        locked.setOrientation(LinearLayout.VERTICAL);
        locked.setPadding(dp(14), dp(14), dp(14), dp(8));
        body.addView(locked, new LinearLayout.LayoutParams(-1, dp(370)));
        locked.setAlpha(0.78f);

        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER);
        r1.addView(dial("Amortecimento", 50), new LinearLayout.LayoutParams(0, dp(155), 1));
        r1.addView(dial("Filtro", 68), new LinearLayout.LayoutParams(0, dp(155), 1));
        r1.addView(dial("Fade", 50), new LinearLayout.LayoutParams(0, dp(155), 1));
        locked.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.CENTER);
        r2.addView(dial("Pré-atraso", 0), new LinearLayout.LayoutParams(0, dp(155), 1));
        r2.addView(dial("Mistura de pré-delay", 100), new LinearLayout.LayoutParams(0, dp(155), 1));
        r2.addView(dial("Tamanho", 100), new LinearLayout.LayoutParams(0, dp(155), 1));
        locked.addView(r2);
        TextView lock = actionButton("Desbloquear");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(160), dp(56), Gravity.CENTER);
        root.addView(lock, lp);
        lock.setOnClickListener(v -> Toast.makeText(this, "Recursos premium ficarão disponíveis numa versão futura.", Toast.LENGTH_SHORT).show());
    }

    private void showSettings() {
        currentPage = 4;
        root.removeAllViews();
        LinearLayout shell = basePage("Configurações");
        LinearLayout body = pageBody(shell);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        section(list, "Reprodução");
        checkboxRow(list, "Repetir ganho", "Volume igual para todas as faixas.", false, v -> {});
        checkboxRow(list, "Utilize o equalizador do sistema", "", false, v -> showEqualizer());
        checkboxRow(list, "Transmita a faixa de música", "", true, v -> Toast.makeText(this, "Transmissão pronta para integração", Toast.LENGTH_SHORT).show());
        checkboxRow(list, "Fazer scrobble no Last.FM", "", false, v -> {});
        sliderRow(list, "Apagar músicas com menos de", "0 segundos", 0, 120);

        section(list, "Geral");
        clickableRow(list, "Atualizar biblioteca", "Procurar novas faixas no dispositivo", "↻", this::loadTracks);
        checkboxRow(list, "Mostrar controlos de notificação", "Controle a música a partir da barra de notificações.", true, v -> {});
        clickableRow(list, "Tema", darkMode ? "Escuro" : "Claro", "◐", this::toggleTheme);

        section(list, "Reprodução");
        clickableRow(list, "Mostrar apenas ficheiros de áudio", "Biblioteca padrão", "♫", () -> {});
        clickableRow(list, "Ordem da biblioteca", "Título · artista · álbum", "☷", () -> {});
        clickableRow(list, "Lista de reprodução padrão", "Todas as faixas", "≡", () -> {});

        section(list, "Ação de agitação");
        checkboxRow(list, "Ativar agitação", "", false, v -> {});
        disabledRow(list, "Ação de agitação", "Nenhuma ação");
        sliderRow(list, "Força de agitação", "70", 0, 100);

        section(list, "Sobre");
        clickableRow(list, "Assinatura Premium", "Recursos adicionais", "♛", () -> Toast.makeText(this, "Premium: em preparação", Toast.LENGTH_SHORT).show());
        clickableRow(list, "Curta a nossa página", "Nexauren", "♣", () -> Toast.makeText(this, "Obrigado por apoiar a Nexauren.", Toast.LENGTH_SHORT).show());
        clickableRow(list, "Sobre o Nexauren Music Player", "Versão 0.2.0", "ⓘ", () -> {});
    }

    private LinearLayout basePage(String title) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(topBar(title, true), new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        return shell;
    }

    private LinearLayout pageBody(LinearLayout shell) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(12));
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        return body;
    }

    private void openDrawer() {
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(darkMode ? Color.rgb(20,24,30) : Color.WHITE);
        overlay.addView(drawer, new FrameLayout.LayoutParams(dp(496), -1, Gravity.START));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(18), dp(16), dp(14));
        hero.setBackgroundColor(Color.rgb(33, 150, 243));
        drawer.addView(hero, new LinearLayout.LayoutParams(-1, dp(210)));

        TextView logo = text("N", 82, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, 1);
        hero.addView(logo, new LinearLayout.LayoutParams(-1, dp(126)));
        TextView name = text("NEXAUREN", 22, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(null, 1);
        hero.addView(name, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView sub = text("MUSIC PLAYER", 11, Color.WHITE);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        drawer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        drawerItem(menu, "♛", "Remover anúncios", () -> {});
        drawerItem(menu, "≡", "Biblioteca", () -> { overlay.setVisibility(View.GONE); showHome(true); });
        drawerItem(menu, "☷", "Equalizador", () -> { overlay.setVisibility(View.GONE); showEqualizer(); });
        drawerItem(menu, "▰", "Modo de condução", () -> Toast.makeText(this, "Modo de condução: em preparação", Toast.LENGTH_SHORT).show());
        drawerItem(menu, "◷", "Temporizador de sono", this::showSleepTimer);
        drawerItem(menu, "✂", "Cortador de MP3", () -> Toast.makeText(this, "Cortador de MP3: em preparação", Toast.LENGTH_SHORT).show());
        drawerItem(menu, "◉", "Tema", this::toggleTheme);
        drawerItem(menu, "▣", "Encontrar duplicados", () -> Toast.makeText(this, "Análise de duplicados: em preparação", Toast.LENGTH_SHORT).show());
        drawerItem(menu, "▢", "Adicionar aos favoritos", () -> Toast.makeText(this, "Favoritos serão adicionados à biblioteca", Toast.LENGTH_SHORT).show());
        drawerItem(menu, "⚙", "Configurações", () -> { overlay.setVisibility(View.GONE); showSettings(); });

        overlay.setOnClickListener(v -> root.removeView(overlay));
    }

    private void showGlobalMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] entries = {
                "Adicionar à lista de reprodução", "Eliminar", "Modo de condução",
                "Enviar", "Detalhes", "Velocidade de reprodução",
                "Visualizador de música", "Temporizador de sono", "Letra da música",
                "Definir como toque", "Mais do artista", "Mais do álbum",
                "Adicionar aos favoritos", "Limpar fila", "Cortar"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            String value = item.getTitle().toString();
            if ("Velocidade de reprodução".equals(value)) showSpeedDialog();
            else if ("Temporizador de sono".equals(value)) showSleepTimer();
            else Toast.makeText(this, value + ": disponível no Nexauren.", Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
    }

    private void showTrackMenu(View anchor, Track track) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] entries = {
                "Adicionar à lista de reprodução", "Enviar", "Detalhes",
                "Adicionar aos favoritos", "Mais do artista", "Mais do álbum",
                "Eliminar"
        };
        for (String e : entries) menu.getMenu().add(e);
        menu.setOnMenuItemClickListener(item -> {
            Toast.makeText(this, item.getTitle() + " · " + track.title, Toast.LENGTH_SHORT).show();
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
        final String[] values = {"Desligado", "15 minutos", "30 minutos", "60 minutos"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Temporizador de sono")
                .setItems(values, (d, which) -> Toast.makeText(this, "Temporizador: " + values[which], Toast.LENGTH_SHORT).show())
                .show();
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

    private void updatePlaybackUi() {
        if (controller == null || !controller.isConnected()) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) {
            String title = item.mediaMetadata.title == null ? "Nexauren Player" : item.mediaMetadata.title.toString();
            String artist = item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString();
            if (miniTitle != null) miniTitle.setText(title);
            if (miniArtist != null) miniArtist.setText(artist);
            if (miniPlay != null) miniPlay.setText(controller.isPlaying() ? "Ⅱ" : "▶");

            if (waveform != null) {
                waveform.setPlaying(controller.isPlaying());
                waveform.setProgress(controller.getDuration() > 0 ? (float) controller.getCurrentPosition() / (float) controller.getDuration() : 0f);
            }

            Track match = null;
            String id = item.mediaId;
            if (id != null) {
                try {
                    long trackId = Long.parseLong(id);
                    for (Track t : tracks) if (t.id == trackId) { match = t; break; }
                } catch (Exception ignored) {}
            }
            if (match != null && miniArt != null) loadArtwork(match.uri, miniArt);
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
        public EqualizerGraphView(Context context) { super(context); }
        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            paint.setColor(Color.rgb(18, 20, 23));
            c.drawRoundRect(0, 0, w, h, 28, 28, paint);
            paint.setStrokeWidth(4);
            float left = 35f, bottom = h - 58f, usableW = w - 70f;
            for (int i = 0; i < 10; i++) {
                float x = left + usableW * i / 9f;
                paint.setColor(0xFF1C1F25);
                c.drawLine(x, 26, x, bottom, paint);
            }
            float[] values = {0, 0, 0, -4, -10, -2, 1, 3, 2, 2};
            paint.setColor(0xFF8A8E95);
            paint.setStrokeWidth(3);
            android.graphics.Path path = new android.graphics.Path();
            for (int i = 0; i < values.length; i++) {
                float x = left + usableW * i / (values.length - 1f);
                float y = h / 2f - values[i] * 14;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            c.drawPath(path, paint);
            paint.setColor(0xFF2D9DEB);
            paint.setTextSize(18);
            String[] labels = {"31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"};
            for (int i = 0; i < labels.length; i++) {
                float x = left + usableW * i / 9f;
                c.drawText(labels[i], x - 12, h - 24, paint);
                c.drawText("+0.0", x - 18, h - 4, paint);
            }
        }
    }
}
