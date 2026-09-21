package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

public final class VisualizerActivity extends AppCompatActivity {
    private final Handler handler = new Handler();
    private SpectrumView spectrum;
    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private Visualizer visualizer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(33,150,243));
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080B10);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(33,150,243));
        TextView back = icon("‹", 38);
        bar.addView(back, new LinearLayout.LayoutParams(dp(52),dp(56)));
        back.setOnClickListener(v->finish());
        TextView title = text("Visualizador de música",18,Color.WHITE);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(bar);

        spectrum = new SpectrumView(this);
        root.addView(spectrum,new LinearLayout.LayoutParams(-1,0,1));

        TextView info=text("Visualização em tempo real da faixa atual",13,0xFF9DA6B0);
        info.setGravity(Gravity.CENTER);
        root.addView(info,new LinearLayout.LayoutParams(-1,dp(52)));
        setContentView(root);

        connect();
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlaybackService.class));
        future=new MediaController.Builder(this,token).buildAsync();
        future.addListener(()->{
            try{
                controller=future.get();
                attachVisualizer();
            }catch(Exception e){
                Toast.makeText(this,"Visualizador indisponível neste dispositivo.",Toast.LENGTH_LONG).show();
            }
        },ContextCompat.getMainExecutor(this));
    }

    private void attachVisualizer(){
        if(controller==null||controller.getAudioSessionId()<=0)return;
        try{
            visualizer=new Visualizer(controller.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate){}
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate){spectrum.setLevels(fft);}
            },Visualizer.getMaxCaptureRate()/2,false,true);
            visualizer.setEnabled(true);
        }catch(RuntimeException e){
            spectrum.animateFallback();
            Toast.makeText(this,"Visualizador limitado pelo sistema de áudio.",Toast.LENGTH_SHORT).show();
        }
    }

    private TextView icon(String s,int size){TextView t=text(s,size,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override protected void onDestroy(){
        if(visualizer!=null){try{visualizer.setEnabled(false);}catch(Exception ignored){} try{visualizer.release();}catch(Exception ignored){}}
        if(future!=null)MediaController.releaseFuture(future);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
