package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public final class VideoLibraryActivity extends AppCompatActivity {
    private static final int REQ_VIDEO=9001;
    private RecyclerView recycler;
    private VideoAdapter adapter;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(33,150,243));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        ensurePermission();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF090C11);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(Color.rgb(33,150,243));
        TextView back=text("‹",38,Color.WHITE);back.setGravity(Gravity.CENTER);bar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(56)));back.setOnClickListener(v->finish());
        TextView title=text("Vídeos",19,Color.WHITE);title.setTypeface(null,1);bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView refresh=text("↻",27,Color.WHITE);refresh.setGravity(Gravity.CENTER);bar.addView(refresh,new LinearLayout.LayoutParams(dp(52),dp(56)));refresh.setOnClickListener(v->loadVideos());
        root.addView(bar);

        recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(10);
        adapter=new VideoAdapter(video->startActivity(new android.content.Intent(this,VideoPlayerActivity.class).putExtra("uri",video.uri.toString()).putExtra("title",video.title)));
        recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void ensurePermission(){
        String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED)loadVideos();
        else ActivityCompat.requestPermissions(this,new String[]{p},REQ_VIDEO);
    }

    private void loadVideos(){
        new Thread(()->{
            ArrayList<VideoTrack> result=new ArrayList<>();
            String[] projection={MediaStore.Video.Media._ID,MediaStore.Video.Media.TITLE,MediaStore.Video.Media.DURATION};
            try(android.database.Cursor c=getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,projection,null,null,MediaStore.Video.Media.DATE_ADDED+" DESC")){
                if(c!=null){
                    int idCol=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int titleCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                    int durCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    while(c.moveToNext()){
                        long id=c.getLong(idCol), duration=c.getLong(durCol);
                        result.add(new VideoTrack(id,c.getString(titleCol)==null?"Sem título":c.getString(titleCol),duration,
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id)));
                    }
                }
            }catch(Exception ignored){}
            runOnUiThread(()->{
                adapter.submitList(result);
                if(result.isEmpty())Toast.makeText(this,"Nenhum vídeo encontrado.",Toast.LENGTH_SHORT).show();
            });
        },"nexauren-video-query").start();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==REQ_VIDEO&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)loadVideos();
    }

    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
