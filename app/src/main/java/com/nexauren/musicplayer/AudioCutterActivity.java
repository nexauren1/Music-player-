package com.nexauren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class AudioCutterActivity extends AppCompatActivity {
    private long trackId;
    private Uri sourceUri;
    private long durationMs;
    private long startMs = 0;
    private long endMs = 0;
    private SeekBar startSeek,endSeek;
    private TextView startValue,endValue,status;
    private ProgressBar progress;
    private Transformer transformer;
    private final android.os.Handler handler=new android.os.Handler();
    private final Runnable progressTicker=new Runnable(){
        @Override public void run(){
            if(transformer!=null){
                ProgressHolder holder=new ProgressHolder();
                int state=transformer.getProgress(holder);
                if(state==Transformer.PROGRESS_STATE_AVAILABLE)progress.setProgress(holder.progress);
                handler.postDelayed(this,400);
            }
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        trackId=getIntent().getLongExtra("track_id",-1L);
        if(trackId<0){finish();return;}
        sourceUri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,trackId);
        durationMs=getIntent().getLongExtra("duration",0L);
        if(durationMs<=0)durationMs=readDuration();
        endMs=Math.max(1000,durationMs);
        buildUi();
    }

    private void buildUi(){
        getWindow().setStatusBarColor(AppearanceStore.accent(this));
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF080B10);

        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(AppearanceStore.accent(this));
        TextView back=text("‹",38,Color.WHITE);back.setGravity(Gravity.CENTER);
        bar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(56)));back.setOnClickListener(v->finish());
        TextView title=text("Cortar áudio",19,Color.WHITE);bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(bar);

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(16),dp(16),dp(24));
        ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setImageResource(R.drawable.music_placeholder);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(180),dp(180));ap.gravity=Gravity.CENTER;body.addView(art,ap);
        loadArtwork(art);

        TextView titleText=text("Selecione o trecho que deseja exportar",18,Color.WHITE);titleText.setGravity(Gravity.CENTER);titleText.setTypeface(null,1);
        body.addView(titleText,new LinearLayout.LayoutParams(-1,dp(46)));

        body.addView(label("Início"));startValue=text(format(startMs),13,0xFF9DA6B0);startValue.setGravity(Gravity.CENTER);body.addView(startValue,new LinearLayout.LayoutParams(-1,dp(25)));
        startSeek=new SeekBar(this);startSeek.setMax((int)Math.min(Integer.MAX_VALUE,durationMs));startSeek.setProgress(0);body.addView(startSeek,new LinearLayout.LayoutParams(-1,dp(48)));

        body.addView(label("Fim"));endValue=text(format(endMs),13,0xFF9DA6B0);endValue.setGravity(Gravity.CENTER);body.addView(endValue,new LinearLayout.LayoutParams(-1,dp(25)));
        endSeek=new SeekBar(this);endSeek.setMax((int)Math.min(Integer.MAX_VALUE,durationMs));endSeek.setProgress((int)Math.min(Integer.MAX_VALUE,endMs));body.addView(endSeek,new LinearLayout.LayoutParams(-1,dp(48)));

        startSeek.setOnSeekBarChangeListener(seekListener(true));
        endSeek.setOnSeekBarChangeListener(seekListener(false));

        LinearLayout info=panel();info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView help=text("Intervalo",13,0xFF2D9DEB);help.setTypeface(null,1);info.addView(help,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView summary=text("0:00 – "+format(endMs),14,Color.WHITE);summary.setGravity(Gravity.CENTER);
        info.addView(summary,new LinearLayout.LayoutParams(-1,dp(28)));
        body.addView(info,new LinearLayout.LayoutParams(-1,dp(70)));

        TextView export=text("EXPORTAR TRECHO",14,Color.WHITE);export.setTypeface(null,1);export.setGravity(Gravity.CENTER);export.setBackground(round(0xFF2D9DEB,25));
        body.addView(export,new LinearLayout.LayoutParams(-1,dp(52)));
        export.setOnClickListener(v->{exportClip();});

        status=text("A saída será guardada em Música/Nexauren/Clips.",12,0xFF9DA6B0);status.setGravity(Gravity.CENTER);status.setPadding(dp(6),dp(10),dp(6),dp(6));
        body.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(0);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(24)));

        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        SystemBarInsets.apply(root);
        setContentView(root);
        final TextView sum=summary;
        android.view.View.OnLayoutChangeListener updater=(v,l,t,r,b,ol,ot,or,ob)->sum.setText(format(startMs)+" – "+format(endMs));
        body.addOnLayoutChangeListener(updater);
    }

    private SeekBar.OnSeekBarChangeListener seekListener(boolean start){
        return new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean from){
                if(!from)return;
                if(start){
                    startMs=p;
                    if(startMs>=endMs) { endMs=Math.min(durationMs,startMs+1000);endSeek.setProgress((int)endMs); }
                    startValue.setText(format(startMs));
                }else{
                    endMs=p;
                    if(endMs<=startMs) { startMs=Math.max(0,endMs-1000);startSeek.setProgress((int)startMs); }
                    endValue.setText(format(endMs));
                }
                status.setText("Trecho: "+format(startMs)+" – "+format(endMs));
            }
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        };
    }

    private void exportClip(){
        if(endMs<=startMs||endMs-startMs<500){Toast.makeText(this,"Escolha um intervalo maior que 0,5 segundos.",Toast.LENGTH_SHORT).show();return;}
        File temp=new File(getCacheDir(),"nexauren_clip_"+System.currentTimeMillis()+".mp4");
        MediaItem item=new MediaItem.Builder().setUri(sourceUri).setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build();
        EditedMediaItem edited=new EditedMediaItem.Builder(item).build();
        transformer=new Transformer.Builder(this).setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(androidx.media3.transformer.Composition composition, ExportResult result){
                        handler.removeCallbacks(progressTicker);
                        progress.setProgress(100);
                        saveToMediaStore(temp);
                    }
                    @Override public void onError(androidx.media3.transformer.Composition composition,ExportResult result,ExportException exception){
                        handler.removeCallbacks(progressTicker);transformer=null;
                        status.setText("Falha ao cortar o áudio.");
                        Toast.makeText(AudioCutterActivity.this,"Não foi possível exportar este trecho.",Toast.LENGTH_LONG).show();
                    }
                }).build();
        status.setText("A exportar…");
        progress.setProgress(0);handler.post(progressTicker);
        try{transformer.start(edited,temp.getAbsolutePath());}
        catch(Exception e){handler.removeCallbacks(progressTicker);transformer=null;status.setText("Falha ao iniciar.");}
    }

    private void saveToMediaStore(File temp){
        new Thread(()->{
            try{
                String base=getIntent().getStringExtra("title");
                if(base==null||base.trim().isEmpty())base="Nexauren Clip";
                String name=base.replaceAll("[\\\\/:*?\"<>|]", "_") + " - " + format(startMs).replace(':','_') + "-" + format(endMs).replace(':','_') + ".m4a";
                Uri dest;
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues v=new ContentValues();
                    v.put(MediaStore.Audio.Media.DISPLAY_NAME,name);v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp4");
                    v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/Nexauren/Clips");
                    v.put(MediaStore.Audio.Media.IS_PENDING,1);
                    dest=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
                }else{
                    if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
                    File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),"Nexauren/Clips");if(!dir.exists())dir.mkdirs();
                    File target=new File(dir,name);
                    copy(temp,target);
                    sendBroadcast(new Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(target)));
                    runOnUiThread(()->finishSuccess(target.getAbsolutePath()));
                    return;
                }
                if(dest==null)throw new IllegalStateException();
                try(InputStream in=new java.io.FileInputStream(temp);OutputStream out=getContentResolver().openOutputStream(dest)){
                    byte[]buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                }
                ContentValues done=new ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);getContentResolver().update(dest,done,null,null);
                temp.delete();
                Uri resultUri=dest;
                runOnUiThread(()->finishSuccess(resultUri.toString()));
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Trecho exportado, mas não foi possível registá-lo na biblioteca.",Toast.LENGTH_LONG).show());}
        }).start();
    }

    private void finishSuccess(String where){
        status.setText("Trecho guardado.");
        new AlertDialog.Builder(this).setTitle("Corte concluído")
                .setMessage("O trecho foi guardado em Música/Nexauren/Clips.")
                .setPositiveButton("OK",(d,w)->{setResult(RESULT_OK);finish();}).show();
    }

    private long readDuration(){
        MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(this,sourceUri);String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);return d==null?0:Long.parseLong(d);}catch(Exception e){return 0;}finally{try{r.release();}catch(Exception ignored){}}
    }

    private void loadArtwork(ImageView view){
        new Thread(()->{Bitmap b=null;MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(this,sourceUri);byte[]d=r.getEmbeddedPicture();if(d!=null)b=BitmapFactory.decodeByteArray(d,0,d.length);}catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}Bitmap out=b;runOnUiThread(()->{if(out!=null){view.clearColorFilter();view.setImageBitmap(out);}});}).start();
    }

    private void copy(File a,File b)throws Exception{try(InputStream in=new java.io.FileInputStream(a);OutputStream out=new java.io.FileOutputStream(b)){byte[]buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}}
    private LinearLayout panel(){LinearLayout p=new LinearLayout(this);p.setBackground(round(0xFF121925,20));return p;}
    private TextView label(String s){TextView t=text(s,14,0xFF2D9DEB);t.setTypeface(null,1);return t;}
    private TextView text(String s,float z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String format(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%d:%02d",s/60,s%60);}
}
