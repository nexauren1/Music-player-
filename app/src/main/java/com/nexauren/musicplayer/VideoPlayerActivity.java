package com.nexauren.musicplayer;

import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public final class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView title;
    private boolean immersive=false;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        String raw=getIntent().getStringExtra("uri");
        if(raw==null||raw.isEmpty()){finish();return;}
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new androidx.media3.common.Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){
                Toast.makeText(VideoPlayerActivity.this,"Este vídeo não pôde ser reproduzido neste dispositivo.",Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.parse(raw)));
        player.prepare();player.play();
    }

    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);
        playerView.setUseController(true);playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

        TextView back=button("‹",38);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.TOP|Gravity.START);bp.topMargin=dp(18);bp.leftMargin=dp(8);
        root.addView(back,bp);back.setOnClickListener(v->finish());

        title=text(getIntent().getStringExtra("title"),15,Color.WHITE);title.setTypeface(null,1);title.setSingleLine(true);title.setPadding(dp(8),0,dp(8),0);title.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(54),Gravity.TOP);tp.leftMargin=dp(64);tp.rightMargin=dp(62);tp.topMargin=dp(18);
        root.addView(title,tp);

        TextView pip=button("▣",18);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);pp.topMargin=dp(21);pp.rightMargin=dp(8);
        root.addView(pip,pp);
        pip.setOnClickListener(v->enterPip());

        TextView speed=button("1×",14);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(52),dp(44),Gravity.BOTTOM|Gravity.END);sp.bottomMargin=dp(18);sp.rightMargin=dp(10);
        root.addView(speed,sp);
        speed.setOnClickListener(v->cycleSpeed(speed));

        TextView fit=button("↗",18);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(48),dp(44),Gravity.BOTTOM|Gravity.END);fp.bottomMargin=dp(18);fp.rightMargin=dp(68);
        root.addView(fit,fp);
        fit.setOnClickListener(v->{immersive=!immersive;applyImmersive();});

        setContentView(root);
    }

    private void cycleSpeed(TextView b){
        float current=player==null?1f:player.getPlaybackParameters().speed;
        float next=current>=2f?0.75f:current>=1.5f?2f:current>=1.25f?1.5f:current>=1f?1.25f:1f;
        player.setPlaybackSpeed(next);b.setText(next+"×");
    }

    private void applyImmersive(){
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){
                if(immersive)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());
            }
        }else{
            getWindow().getDecorView().setSystemUiVisibility(immersive?
                    View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:0);
        }
    }

    private void enterPip(){
        if(Build.VERSION.SDK_INT>=26&&getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            if(Build.VERSION.SDK_INT>=26)enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16,9)).build());
        }else Toast.makeText(this,"Picture-in-picture não é suportado neste dispositivo.",Toast.LENGTH_SHORT).show();
    }

    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();}

    @Override protected void onStop(){super.onStop();if(player!=null&&!isInPictureInPictureMode())player.pause();}
    @Override protected void onDestroy(){if(playerView!=null)playerView.setPlayer(null);if(player!=null){player.release();player=null;}super.onDestroy();}

    private TextView button(String s,float z){TextView t=text(s,z,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(round(0x66000000,24));return t;}
    private TextView text(String s,float z,int c){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(z);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
