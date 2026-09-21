package com.nexauren.musicplayer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public final class NowPlayingActivity extends AppCompatActivity {
    private final Handler handler = new Handler();
    private MediaController controller;
    private ListenableFuture<MediaController> future;

    private ImageView art;
    private TextView title;
    private TextView artist;
    private TextView position;
    private TextView duration;
    private TextView play;
    private TextView shuffle;
    private TextView repeat;
    private TextView ab;
    private TextView favorite;
    private SeekBar seek;
    private ObjectAnimator artSpin;
    private int baseBackground = Color.rgb(9, 12, 17);
    private long sleepUntil = 0L;
    private boolean seeking = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            update();
            if (sleepUntil > 0 && System.currentTimeMillis() >= sleepUntil && controller != null && controller.isConnected()) {
                controller.pause();
                sleepUntil = 0L;
                Toast.makeText(NowPlayingActivity.this, "Temporizador de sono terminou.", Toast.LENGTH_SHORT).show();
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(AppearanceStore.accent(this));
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        connect();
        handler.post(ticker);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(AppearanceBackgroundDrawable.forContext(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setBackgroundColor(AppearanceStore.accent(this));

        TextView back = icon("‹", 38);
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
        back.setOnClickListener(v -> finish());

        LinearLayout barTitle = new LinearLayout(this);
        barTitle.setOrientation(LinearLayout.VERTICAL);
        TextView screenTitle = text("A tocar agora", 18, Color.WHITE);
        screenTitle.setTypeface(null, 1);
        barTitle.addView(screenTitle, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView barHint = text("Nexauren Player", 10, 0xD9FFFFFF);
        barTitle.addView(barHint, new LinearLayout.LayoutParams(-1, dp(20)));
        bar.addView(barTitle, new LinearLayout.LayoutParams(0, dp(56), 1));

        favorite = icon("♡", 31);
        bar.addView(favorite, new LinearLayout.LayoutParams(dp(50), dp(56)));
        favorite.setOnClickListener(v -> toggleFavorite());

        TextView more = icon("⋮", 30);
        bar.addView(more, new LinearLayout.LayoutParams(dp(46), dp(56)));
        more.setOnClickListener(v -> showNowPlayingMenu(more));

        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(18),dp(18),dp(18),dp(26));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));

        LinearLayout artworkCard = new LinearLayout(this);
        artworkCard.setGravity(Gravity.CENTER);
        artworkCard.setPadding(dp(8),dp(8),dp(8),dp(8));
        artworkCard.setBackground(round(0xCC151B23,28));
        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageResource(R.drawable.music_placeholder);
        art.setBackground(round(0xFF202936,22));
        art.setClipToOutline(true);
        artworkCard.addView(art,new LinearLayout.LayoutParams(dp(286),dp(286)));
        LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(-1,dp(312));
        body.addView(artworkCard,acp);

        artSpin = ObjectAnimator.ofFloat(art, View.ROTATION, 0f, 360f);
        artSpin.setDuration(9000L);
        artSpin.setInterpolator(new LinearInterpolator());
        artSpin.setRepeatCount(ObjectAnimator.INFINITE);

        title = text("Nexauren Player", 23, Color.WHITE);
        title.setTypeface(null,1);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        body.addView(title,new LinearLayout.LayoutParams(-1,dp(62)));

        artist = text("",14,0xFFAFB7C2);
        artist.setGravity(Gravity.CENTER);
        artist.setMaxLines(2);
        body.addView(artist,new LinearLayout.LayoutParams(-1,dp(38)));

        LinearLayout hint = new LinearLayout(this);
        hint.setGravity(Gravity.CENTER);
        TextView lyricHint = text("♪  Toque para procurar letras",13,0xFF8C96A3);
        lyricHint.setGravity(Gravity.CENTER);
        hint.addView(lyricHint,new LinearLayout.LayoutParams(0,dp(32),1));
        hint.setOnClickListener(v -> showLyrics());
        body.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout seekRow=new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        position=text("0:00",11,0xFFB5BEC9);
        duration=text("0:00",11,0xFFB5BEC9);duration.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        seek=new SeekBar(this);seek.setMax(1000);
        seekRow.addView(position,new LinearLayout.LayoutParams(dp(34),dp(26)));
        seekRow.addView(seek,new LinearLayout.LayoutParams(0,dp(30),1));
        seekRow.addView(duration,new LinearLayout.LayoutParams(dp(34),dp(26)));
        body.addView(seekRow,new LinearLayout.LayoutParams(-1,dp(36)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                seeking=fromUser;
                if(fromUser&&controller!=null&&controller.isConnected()){
                    long d=controller.getDuration();
                    position.setText(format(d>0?d*value/1000L:0));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar){seeking=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                seeking=false;
                if(controller!=null&&controller.isConnected()&&controller.getDuration()>0){
                    controller.seekTo((long)controller.getDuration()*bar.getProgress()/1000L);
                }
            }
        });

        LinearLayout transport=new LinearLayout(this);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        shuffle=control("⤨",20);
        TextView prev=control("⏮",28);
        play=control("▶",30);
        TextView next=control("⏭",28);
        repeat=control("↻",20);
        transport.addView(shuffle,new LinearLayout.LayoutParams(0,dp(60),1));
        transport.addView(prev,new LinearLayout.LayoutParams(0,dp(60),1));
        transport.addView(play,new LinearLayout.LayoutParams(dp(82),dp(82)));
        transport.addView(next,new LinearLayout.LayoutParams(0,dp(60),1));
        transport.addView(repeat,new LinearLayout.LayoutParams(0,dp(60),1));
        body.addView(transport,new LinearLayout.LayoutParams(-1,dp(88)));
        prev.setOnClickListener(v->{if(controller!=null)controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{if(controller!=null)controller.seekToNextMediaItem();});
        play.setOnClickListener(v->toggle());
        shuffle.setOnClickListener(v->{if(controller!=null){controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());update();}});
        repeat.setOnClickListener(v->cycleRepeat());

        LinearLayout quick1=new LinearLayout(this);
        quick1.setGravity(Gravity.CENTER);
        ab=actionChip("A-B");TextView equalizer=actionChip("Equalizador");TextView speed=actionChip("Velocidade");
        quick1.addView(ab,new LinearLayout.LayoutParams(0,46,1));
        quick1.addView(equalizer,new LinearLayout.LayoutParams(0,46,1));
        quick1.addView(speed,new LinearLayout.LayoutParams(0,46,1));
        body.addView(quick1,new LinearLayout.LayoutParams(-1,dp(54)));
        ab.setOnClickListener(v->toggleAB());
        equalizer.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("page","audio")));
        speed.setOnClickListener(v->showSpeed());

        LinearLayout quick2=new LinearLayout(this);
        quick2.setGravity(Gravity.CENTER);
        TextView timer=actionChip("⏱ Sono");TextView queue=actionChip("☷ Fila");TextView lyrics=actionChip("♪ Letras");
        quick2.addView(timer,new LinearLayout.LayoutParams(0,42,1));
        quick2.addView(queue,new LinearLayout.LayoutParams(0,42,1));
        quick2.addView(lyrics,new LinearLayout.LayoutParams(0,42,1));
        body.addView(quick2,new LinearLayout.LayoutParams(-1,dp(50)));
        timer.setOnClickListener(v->showSleepTimer());
        queue.setOnClickListener(v->showQueueHint());
        lyrics.setOnClickListener(v->showLyrics());

        TextView separator=text("Ferramentas da faixa",12,0xFF66717E);
        separator.setGravity(Gravity.CENTER);
        separator.setPadding(0,dp(12),0,dp(6));
        body.addView(separator,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout tools=new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        TextView tags=actionChip("Editar etiquetas");TextView cut=actionChip("Cortar áudio");
        TextView ringtone=actionChip("Definir como toque");
        tools.addView(tags,new LinearLayout.LayoutParams(0,46,1));
        tools.addView(cut,new LinearLayout.LayoutParams(0,46,1));
        tools.addView(ringtone,new LinearLayout.LayoutParams(0,46,1));
        body.addView(tools,new LinearLayout.LayoutParams(-1,dp(56)));
        tags.setOnClickListener(v->openEditor());
        cut.setOnClickListener(v->openCutter());
        ringtone.setOnClickListener(v->setAsRingtone());

        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlaybackService.class));
        future=new MediaController.Builder(this,token).buildAsync();
        future.addListener(()->{
            try{controller=future.get();update();}
            catch(Exception e){Toast.makeText(this,"Não foi possível conectar ao player.",Toast.LENGTH_SHORT).show();}
        },ContextCompat.getMainExecutor(this));
    }

    private void update(){
        if(controller==null||!controller.isConnected())return;
        MediaItem item=controller.getCurrentMediaItem();
        if(item!=null){
            String t=item.mediaMetadata.title==null?"Nexauren Player":item.mediaMetadata.title.toString();
            String a=item.mediaMetadata.artist==null?"Artista desconhecido":item.mediaMetadata.artist.toString();
            title.setText(t);artist.setText(a);
            try{long id=Long.parseLong(item.mediaId);loadArtwork(id);}catch(Exception ignored){}
            boolean fav=false;try{fav=FavoritesStore.isFavorite(this,Long.parseLong(item.mediaId));}catch(Exception ignored){}
            favorite.setText(fav?"♥":"♡");
            favorite.setTextColor(fav?Color.WHITE:Color.WHITE);
        }
        long d=Math.max(0,controller.getDuration());
        long p=Math.max(0,controller.getCurrentPosition());
        if(!seeking)seek.setProgress(d>0?(int)Math.min(1000L,p*1000L/d):0);
        position.setText(format(p));duration.setText(format(d));
        play.setText(controller.isPlaying()?"Ⅱ":"▶");
        shuffle.setTextColor(controller.getShuffleModeEnabled()?Color.WHITE:0xFF9EA7B2);
        repeat.setTextColor(controller.getRepeatMode()==Player.REPEAT_MODE_OFF?0xFF9EA7B2:Color.WHITE);
        int state=PlaybackService.getABState();
        ab.setText(state==1?"A •":"A-B");
        ab.setTextColor(state>0?Color.WHITE:0xFF9EA7B2);

        if(controller.isPlaying()){
            if(artSpin.isPaused())artSpin.resume();
            else if(!artSpin.isStarted())artSpin.start();
        }else if(artSpin.isStarted())artSpin.pause();
    }

    private void loadArtwork(long id){
        if(ContextCompat.checkSelfPermission(this,Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)return;
        Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);
        new Thread(()->{
            Bitmap b=null;
            if(ArtworkStore.exists(this,id)) b=BitmapFactory.decodeFile(ArtworkStore.file(this,id).getAbsolutePath());
            if(b==null){
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try{retriever.setDataSource(this,uri);byte[]data=retriever.getEmbeddedPicture();if(data!=null)b=BitmapFactory.decodeByteArray(data,0,data.length);}
                catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
            }
            Bitmap result=b;
            runOnUiThread(()->{
                if(result!=null){
                    art.clearColorFilter();
                    art.setImageBitmap(result);
                    baseBackground=sampleBackground(result);
                    getWindow().getDecorView().setBackgroundColor(baseBackground);
                }
            });
        }).start();
    }

    private int sampleBackground(Bitmap bitmap){
        Bitmap small=Bitmap.createScaledBitmap(bitmap,1,1,true);
        int c=small.getPixel(0,0);
        small.recycle();
        int r=(int)(Color.red(c)*0.18f),g=(int)(Color.green(c)*0.18f),b=(int)(Color.blue(c)*0.18f);
        return Color.rgb(Math.max(6,r),Math.max(8,g),Math.max(12,b));
    }

    private void toggle(){if(controller==null)return;if(controller.isPlaying())controller.pause();else controller.play();}

    private void cycleRepeat(){
        if(controller==null||!controller.isConnected())return;
        int mode=controller.getRepeatMode();
        int next=mode==Player.REPEAT_MODE_OFF?Player.REPEAT_MODE_ALL:mode==Player.REPEAT_MODE_ALL?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF;
        controller.setRepeatMode(next);update();
    }

    private void toggleAB(){
        int state=PlaybackService.toggleABRepeat();
        ab.setText(state==1?"A •":"A-B");
        ab.setTextColor(state>0?Color.WHITE:0xFF9EA7B2);
        Toast.makeText(this,state==1?"Ponto A definido. Toque novamente para marcar B.":state==2?"A-B ativado.":"A-B desligado.",Toast.LENGTH_SHORT).show();
    }

    private void toggleFavorite(){
        if(controller==null||controller.getCurrentMediaItem()==null)return;
        try{
            long id=Long.parseLong(controller.getCurrentMediaItem().mediaId);
            boolean value=FavoritesStore.toggle(this,id);
            favorite.setText(value?"♥":"♡");
            Toast.makeText(this,value?"Adicionado aos favoritos":"Removido dos favoritos",Toast.LENGTH_SHORT).show();
        }catch(Exception ignored){}
    }

    private void openEditor(){
        Track t=currentTrack();if(t==null)return;
        startActivityForResult(new Intent(this,EditTagsActivity.class).putExtra("track_id",t.id),7810);
    }

    private void openCutter(){
        Track t=currentTrack();if(t==null)return;
        startActivityForResult(new Intent(this,AudioCutterActivity.class)
                .putExtra("track_id",t.id).putExtra("duration",t.durationMs).putExtra("title",t.title),7811);
    }

    private Track currentTrack(){
        if(controller==null||controller.getCurrentMediaItem()==null)return null;
        String id=controller.getCurrentMediaItem().mediaId;
        try{
            long target=Long.parseLong(id);
            String titleValue=controller.getCurrentMediaItem().mediaMetadata.title==null?"Sem título":controller.getCurrentMediaItem().mediaMetadata.title.toString();
            String artistValue=controller.getCurrentMediaItem().mediaMetadata.artist==null?"Artista desconhecido":controller.getCurrentMediaItem().mediaMetadata.artist.toString();
            String albumValue=controller.getCurrentMediaItem().mediaMetadata.albumTitle==null?"Álbum desconhecido":controller.getCurrentMediaItem().mediaMetadata.albumTitle.toString();
            long d=controller.getDuration();
            return new Track(target,titleValue,artistValue,albumValue,d,ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,target));
        }catch(Exception e){return null;}
    }

    private void setAsRingtone(){
        Track t=currentTrack();
        if(t==null){Toast.makeText(this,"Nenhuma faixa.",Toast.LENGTH_SHORT).show();return;}
        try{
            ContentValuesCompatHelper helper=new ContentValuesCompatHelper();
            helper.copyToRingtone(this,t);
            Toast.makeText(this,"Toque definido pelo Android.",Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,"O sistema não permitiu definir o toque.",Toast.LENGTH_LONG).show();
        }
    }

    private static final class ContentValuesCompatHelper {
        void copyToRingtone(Context context,Track t)throws Exception{
            android.content.ContentValues values=new android.content.ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME,t.title.replaceAll("[\\/:*?\"<>|]","_")+".mp3");
            values.put(MediaStore.Audio.Media.TITLE,t.title);
            values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mpeg");
            if(Build.VERSION.SDK_INT>=29){
                values.put(MediaStore.Audio.Media.RELATIVE_PATH,android.os.Environment.DIRECTORY_RINGTONES);
                values.put(MediaStore.Audio.Media.IS_RINGTONE,1);
                values.put(MediaStore.Audio.Media.IS_MUSIC,0);
                values.put(MediaStore.Audio.Media.IS_PENDING,1);
                Uri dest=context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);
                if(dest==null)throw new IllegalStateException();
                try(InputStream in=context.getContentResolver().openInputStream(t.uri);OutputStream out=context.getContentResolver().openOutputStream(dest)){
                    byte[]buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                }
                android.content.ContentValues done=new android.content.ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);
                context.getContentResolver().update(dest,done,null,null);
                RingtoneManager.setActualDefaultRingtoneUri(context,RingtoneManager.TYPE_RINGTONE,dest);
            }else{
                throw new UnsupportedOperationException("legacy ringtone not implemented");
            }
        }
    }

    private void showNowPlayingMenu(View anchor){
        Track t=currentTrack();
        if(t==null){Toast.makeText(this,"Nenhuma faixa em reprodução.",Toast.LENGTH_SHORT).show();return;}
        final PopupWindow[] ref=new PopupWindow[1];
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(8),dp(8),dp(8));
        list.setBackground(round(0xFF151C24,20));
        String[] rows={"✎  Editar etiquetas","✂  Cortar áudio","♫  Definir como toque",
                "☷  Adicionar à lista de reprodução","♡  Adicionar aos favoritos","↗  Enviar",
                "ⓘ  Detalhes","◉  Velocidade","〰  Visualizador de música","◷  Temporizador de sono",
                "♫  Letra da música","▣  Modo de condução"};
        for(String label:rows){
            TextView row=text(label,14,Color.WHITE);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(8),0);
            list.addView(row,new LinearLayout.LayoutParams(dp(300),dp(48)));
            row.setOnClickListener(v->{
                if(ref[0]!=null)ref[0].dismiss();
                if(label.contains("Editar"))openEditor();
                else if(label.contains("Cortar"))openCutter();
                else if(label.contains("toque"))setAsRingtone();
                else if(label.contains("lista"))Toast.makeText(this,"Use a fila da biblioteca para gerir playlists.",Toast.LENGTH_SHORT).show();
                else if(label.contains("favoritos"))toggleFavorite();
                else if(label.contains("Enviar"))shareTrack(t);
                else if(label.contains("Detalhes"))showDetails(t);
                else if(label.contains("Velocidade"))showSpeed();
                else if(label.contains("Visualizador"))startActivity(new Intent(this,VisualizerActivity.class));
                else if(label.contains("Temporizador"))showSleepTimer();
                else if(label.contains("Letra"))showLyrics();
                else if(label.contains("condução"))showDrivingInfo();
            });
        }
        PopupWindow popup=new PopupWindow(list,dp(316),Math.min(dp(590),rows.length*dp(48)+dp(20)),true);
        ref[0]=popup;popup.setBackgroundDrawable(round(0xFF151C24,20));popup.setOutsideTouchable(true);popup.setElevation(dp(14));
        popup.showAsDropDown(anchor,-dp(270),-Math.min(dp(590),rows.length*dp(48)+dp(68)));
    }

    private void shareTrack(Track t){
        try{
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("audio/*");send.putExtra(Intent.EXTRA_STREAM,t.uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,"Enviar faixa"));
        }catch(Exception e){Toast.makeText(this,"Não foi possível partilhar.",Toast.LENGTH_SHORT).show();}
    }

    private void showDetails(Track t){
        String text="Título: "+t.title+"\nArtista: "+t.artist+"\nÁlbum: "+t.album+"\nDuração: "+format(t.durationMs);
        new android.app.AlertDialog.Builder(this).setTitle("Detalhes da faixa").setMessage(text).setPositiveButton("OK",null).show();
    }

    private void showSpeed(){
        String[]values={"0.75x","1.00x","1.25x","1.50x","1.75x","2.00x"};
        new android.app.AlertDialog.Builder(this).setTitle("Velocidade").setItems(values,(d,w)->{if(controller!=null)controller.setPlaybackSpeed(Float.parseFloat(values[w].replace("x","")));}).show();
    }

    private void showSleepTimer(){
        String[]values={"Desligado","15 minutos","30 minutos","45 minutos","60 minutos"};
        new android.app.AlertDialog.Builder(this).setTitle("Temporizador de sono").setItems(values,(d,w)->{
            if(w==0){sleepUntil=0;Toast.makeText(this,"Temporizador desligado.",Toast.LENGTH_SHORT).show();}
            else{sleepUntil=System.currentTimeMillis()+w*15L*60_000L;Toast.makeText(this,values[w]+" definido.",Toast.LENGTH_SHORT).show();}
        }).show();
    }

    private void showQueueHint(){Toast.makeText(this,"A fila é controlada pelos próximos/anterior do player.",Toast.LENGTH_SHORT).show();}
    private void showDrivingInfo(){new android.app.AlertDialog.Builder(this).setTitle("Modo de condução").setMessage("Controles grandes e essenciais para condução segura.").setPositiveButton("OK",null).show();}
    private void showLyrics(){new android.app.AlertDialog.Builder(this).setTitle("Letra da música").setMessage("A procura integrada de letras será adicionada quando existir uma fonte configurada para esta instalação.").setPositiveButton("OK",null).show();}

    private TextView control(String s,int size){
        TextView t=icon(s,size);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(t==play?0xFF182331:0xFF111821,36));
        return t;
    }

    private TextView actionChip(String s){
        TextView t=text(s,12,Color.WHITE);t.setGravity(Gravity.CENTER);t.setTypeface(null,1);t.setBackground(round(0xFF17202B,22));
        return t;
    }

    private TextView icon(String s,int size){TextView t=text(s,size,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String format(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%d:%02d",sec/60,sec%60);}

    @Override protected void onDestroy(){
        handler.removeCallbacks(ticker);
        if(artSpin!=null)artSpin.cancel();
        if(future!=null)MediaController.releaseFuture(future);
        super.onDestroy();
    }
}
