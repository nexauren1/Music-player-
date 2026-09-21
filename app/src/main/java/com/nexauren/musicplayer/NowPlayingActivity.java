package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;

public final class NowPlayingActivity extends AppCompatActivity {
    private final Handler handler = new Handler();
    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private ImageView art;
    private TextView title, artist, position, duration, play, shuffle, repeat, ab;
    private SeekBar seek;

    private final Runnable ticker = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 500); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(33,150,243));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        connect();
        handler.post(ticker);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080B10);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(33,150,243));
        TextView back = icon("‹", 38);
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        back.setOnClickListener(v -> finish());
        TextView label = text("A tocar agora", 19, Color.WHITE);
        bar.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView heart = icon("♡", 32);
        bar.addView(heart, new LinearLayout.LayoutParams(dp(54), dp(56)));
        heart.setOnClickListener(v -> toggleFavorite());
        TextView queue = icon("≡", 28);
        bar.addView(queue, new LinearLayout.LayoutParams(dp(54), dp(56)));
        queue.setOnClickListener(v -> {
            Toast.makeText(this, "Abra o menu da biblioteca para editar a fila.", Toast.LENGTH_SHORT).show();
        });
        root.addView(bar);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(12));

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageResource(R.drawable.music_placeholder);
        body.addView(art, new LinearLayout.LayoutParams(-1, dp(330)));

        title = text("Nexauren Player", 22, Color.WHITE);
        title.setTypeface(null,1);
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));
        artist = text("", 14, 0xFF9DA6B0);
        artist.setGravity(Gravity.CENTER);
        body.addView(artist, new LinearLayout.LayoutParams(-1, dp(28)));

        seek = new SeekBar(this);
        body.addView(seek, new LinearLayout.LayoutParams(-1, dp(44)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){ if(from) position.setText(format(p)); }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){ if(controller!=null) controller.seekTo(b.getProgress()); }
        });

        LinearLayout times = new LinearLayout(this);
        position = text("0:00",12,0xFF9DA6B0);
        duration = text("0:00",12,0xFF9DA6B0); duration.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        times.addView(position,new LinearLayout.LayoutParams(0,dp(24),1));
        times.addView(duration,new LinearLayout.LayoutParams(0,dp(24),1));
        body.addView(times);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        TextView prev = roundButton("⏮",58);
        play = roundButton("▶",76);
        TextView next = roundButton("⏭",58);
        controls.addView(prev,new LinearLayout.LayoutParams(dp(66),dp(66)));
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(dp(88),dp(82));
        pl.setMargins(dp(12),0,dp(12),0);
        controls.addView(play,pl);
        controls.addView(next,new LinearLayout.LayoutParams(dp(66),dp(66)));
        body.addView(controls);
        prev.setOnClickListener(v->{if(controller!=null)controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{if(controller!=null)controller.seekToNextMediaItem();});
        play.setOnClickListener(v->toggle());

        LinearLayout quick=new LinearLayout(this);
        quick.setGravity(Gravity.CENTER);
        TextView eq=chip("Equalizador");
        TextView speed=chip("Velocidade");
        quick.addView(eq,new LinearLayout.LayoutParams(0,dp(46),1));
        quick.addView(speed,new LinearLayout.LayoutParams(0,dp(46),1));
        body.addView(quick);

        LinearLayout modes=new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        shuffle=modeChip("Aleatório");
        repeat=modeChip("Repetir");
        ab=modeChip("A-B");
        modes.addView(shuffle,new LinearLayout.LayoutParams(0,dp(42),1));
        modes.addView(repeat,new LinearLayout.LayoutParams(0,dp(42),1));
        modes.addView(ab,new LinearLayout.LayoutParams(0,dp(42),1));
        body.addView(modes,new LinearLayout.LayoutParams(-1,dp(50)));

        eq.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).putExtra("page","audio"));});
        speed.setOnClickListener(v->showSpeed());
        shuffle.setOnClickListener(v->{if(controller!=null){controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());update();}});
        repeat.setOnClickListener(v->{if(controller!=null){int mode=controller.getRepeatMode();int nextMode=mode==Player.REPEAT_MODE_OFF?Player.REPEAT_MODE_ALL:mode==Player.REPEAT_MODE_ALL?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF;controller.setRepeatMode(nextMode);update();}});
        ab.setOnClickListener(v->{
            int state=PlaybackService.toggleABRepeat();
            ab.setText(state==1?"A •":"A-B");
            ab.setTextColor(state>0?0xFF2D9DEB:Color.WHITE);
            Toast.makeText(this,state==1?"Ponto A definido":state==2?"Repetição A-B ativada":"Repetição A-B desligada",Toast.LENGTH_SHORT).show();
        });

        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlaybackService.class));
        future=new MediaController.Builder(this,token).buildAsync();
        future.addListener(()->{try{controller=future.get();update();}catch(Exception e){Toast.makeText(this,"Não foi possível conectar ao player.",Toast.LENGTH_SHORT).show();}},ContextCompat.getMainExecutor(this));
    }

    private void update(){
        if(controller==null||!controller.isConnected())return;
        MediaItem item=controller.getCurrentMediaItem();
        if(item!=null){
            title.setText(item.mediaMetadata.title==null?"Nexauren Player":item.mediaMetadata.title.toString());
            artist.setText(item.mediaMetadata.artist==null?"":item.mediaMetadata.artist.toString());
            String id=item.mediaId;
            if(id!=null){try{loadArtwork(Long.parseLong(id));}catch(Exception ignored){}}
        }
        long d=Math.max(0,controller.getDuration()),p=Math.max(0,controller.getCurrentPosition());
        seek.setMax((int)Math.min(Integer.MAX_VALUE,d));seek.setProgress((int)Math.min(Integer.MAX_VALUE,p));
        position.setText(format(p));duration.setText(format(d));play.setText(controller.isPlaying()?"Ⅱ":"▶");
        if(shuffle!=null)shuffle.setTextColor(controller.getShuffleModeEnabled()?0xFF2D9DEB:Color.WHITE);
        if(repeat!=null)repeat.setTextColor(controller.getRepeatMode()==Player.REPEAT_MODE_OFF?Color.WHITE:0xFF2D9DEB);
        if(ab!=null){int state=PlaybackService.getABState();ab.setText(state==1?"A •":"A-B");ab.setTextColor(state>0?0xFF2D9DEB:Color.WHITE);}
    }

    private void loadArtwork(long id){
        if(ContextCompat.checkSelfPermission(this,Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)return;
        Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);
        new Thread(()->{
            Bitmap b=null;MediaMetadataRetriever r=new MediaMetadataRetriever();
            try{r.setDataSource(this,uri);byte[]data=r.getEmbeddedPicture();if(data!=null)b=BitmapFactory.decodeByteArray(data,0,data.length);}catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}
            Bitmap result=b;runOnUiThread(()->{if(result!=null){art.clearColorFilter();art.setImageBitmap(result);}});
        }).start();
    }

    private void toggle(){if(controller==null)return;if(controller.isPlaying())controller.pause();else controller.play();}
    private void toggleFavorite(){
        if(controller==null||controller.getCurrentMediaItem()==null)return;
        try{long id=Long.parseLong(controller.getCurrentMediaItem().mediaId);boolean fav=FavoritesStore.toggle(this,id);Toast.makeText(this,fav?"Adicionado aos favoritos":"Removido dos favoritos",Toast.LENGTH_SHORT).show();}catch(Exception ignored){}
    }
    private void showSpeed(){
        String[]v={"0.75x","1.00x","1.25x","1.50x","1.75x","2.00x"};
        new android.app.AlertDialog.Builder(this).setTitle("Velocidade").setItems(v,(d,w)->controller.setPlaybackSpeed(Float.parseFloat(v[w].replace("x","")))).show();
    }
    private TextView icon(String s,int size){TextView t=text(s,size,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private TextView button(String s,int size){TextView t=icon(s,size);t.setBackground(round(0xFF1A2230,22));return t;}
    private TextView roundButton(String s,int size){TextView t=icon(s,size);t.setBackground(round(0xFF1A2230,40));return t;}
    private TextView chip(String s){TextView t=text(s,14,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(round(0xFF2D9DEB,28));return t;}
    private TextView modeChip(String s){TextView t=text(s,13,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(round(0xFF151C27,20));return t;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private static String format(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%d:%02d",s/60,s%60);}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);if(future!=null)MediaController.releaseFuture(future);super.onDestroy();}
}
