package com.nexauren.musicplayer;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

        TextView back=new TextView(this);
        back.setText("‹");back.setTextSize(40);back.setTextColor(Color.WHITE);back.setGravity(Gravity.CENTER);back.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.TOP|Gravity.START);bp.topMargin=dp(18);
        root.addView(back,bp);back.setOnClickListener(v->finish());

        TextView title=new TextView(this);
        title.setText(getIntent().getStringExtra("title"));title.setTextSize(15);title.setTypeface(null,1);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER_VERTICAL);title.setSingleLine(true);
        title.setPadding(dp(8),0,dp(8),0);title.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP);tp.leftMargin=dp(58);tp.rightMargin=dp(8);tp.topMargin=dp(18);
        root.addView(title,tp);

        setContentView(root);
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new androidx.media3.common.Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){Toast.makeText(VideoPlayerActivity.this,"Formato de vídeo não suportado neste dispositivo.",Toast.LENGTH_LONG).show();}
        });
        String raw=getIntent().getStringExtra("uri");
        if(raw==null||raw.isEmpty()){finish();return;}
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.parse(raw)));
        player.prepare();
        player.play();
    }

    @Override protected void onStop(){
        super.onStop();
        if(player!=null)player.pause();
    }

    @Override protected void onDestroy(){
        if(playerView!=null)playerView.setPlayer(null);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }

    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
