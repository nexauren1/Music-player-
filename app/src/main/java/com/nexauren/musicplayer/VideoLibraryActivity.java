package com.nexauren.musicplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public final class VideoLibraryActivity extends AppCompatActivity {
    private static final int REQ_VIDEO=9001;
    private final ArrayList<VideoTrack> allVideos=new ArrayList<>();
    private RecyclerView recycler;
    private VideoAdapter adapter;
    private TextView count;
    private EditText search;
    private int sortMode=0;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(33,150,243));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        ensurePermission();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080B10);

        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5),0,dp(5),0);
        bar.setBackgroundColor(Color.rgb(33,150,243));
        TextView back=text("‹",38,Color.WHITE);back.setGravity(Gravity.CENTER);
        bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(58)));back.setOnClickListener(v->finish());
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Vídeos",19,Color.WHITE);title.setTypeface(null,1);
        count=text("A carregar…",10,0xD9FFFFFF);
        heading.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));heading.addView(count,new LinearLayout.LayoutParams(-1,dp(18)));
        bar.addView(heading,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView sort=text("⇅",25,Color.WHITE);sort.setGravity(Gravity.CENTER);
        bar.addView(sort,new LinearLayout.LayoutParams(dp(48),dp(58)));
        sort.setOnClickListener(v->cycleSort());
        root.addView(bar);

        LinearLayout searchWrap=new LinearLayout(this);
        searchWrap.setPadding(dp(10),dp(8),dp(10),dp(4));
        search=new EditText(this);search.setSingleLine(true);search.setHint("Pesquisar vídeos…");
        search.setTextColor(Color.WHITE);search.setHintTextColor(0xFF7F8A97);search.setTextSize(14);
        search.setBackground(round(0xFF141B25,18));search.setPadding(dp(14),0,dp(14),0);
        searchWrap.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        root.addView(searchWrap);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){renderFiltered();}
            public void afterTextChanged(Editable e){}
        });

        recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(10);
        adapter=new VideoAdapter(video->startActivity(new Intent(this,VideoPlayerActivity.class)
                .putExtra("uri",video.uri.toString()).putExtra("title",video.title)));
        recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void cycleSort(){
        sortMode=(sortMode+1)%3;
        if(sortMode==0)Toast.makeText(this,"Ordenar: mais recentes",Toast.LENGTH_SHORT).show();
        else if(sortMode==1)Toast.makeText(this,"Ordenar: nome",Toast.LENGTH_SHORT).show();
        else Toast.makeText(this,"Ordenar: maior duração",Toast.LENGTH_SHORT).show();
        renderFiltered();
    }

    private void ensurePermission(){
        String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED)loadVideos();
        else ActivityCompat.requestPermissions(this,new String[]{p},REQ_VIDEO);
    }

    private void loadVideos(){
        new Thread(()->{
            ArrayList<VideoTrack> result=new ArrayList<>();
            String[] projection={
                    MediaStore.Video.Media._ID,MediaStore.Video.Media.TITLE,MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED,MediaStore.Video.Media.WIDTH,MediaStore.Video.Media.HEIGHT
            };
            try(android.database.Cursor c=getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,projection,
                    MediaStore.Video.Media.DURATION+" > 0",null,MediaStore.Video.Media.DATE_ADDED+" DESC")){
                if(c!=null){
                    int id=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int title=c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                    int folder=c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                    int mime=c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);
                    int size=c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                    int dur=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    int date=c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                    int w=c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                    int h=c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
                    while(c.moveToNext()){
                        long videoId=c.getLong(id);
                        result.add(new VideoTrack(videoId,
                                safe(c.getString(title),"Sem título"),
                                safe(c.getString(folder),"Vídeos"),
                                safe(c.getString(mime),"video/*"),
                                c.getLong(size),c.getLong(dur),c.getLong(date),
                                c.getInt(w),c.getInt(h),
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,videoId)));
                    }
                }
            }catch(Exception ignored){}
            runOnUiThread(()->{
                allVideos.clear();allVideos.addAll(result);renderFiltered();
                if(result.isEmpty())Toast.makeText(this,"Nenhum vídeo encontrado.",Toast.LENGTH_SHORT).show();
            });
        },"nexauren-video-query").start();
    }

    private void renderFiltered(){
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.getDefault());
        ArrayList<VideoTrack> list=new ArrayList<>();
        for(VideoTrack v:allVideos){
            if(q.isEmpty()||v.title.toLowerCase(Locale.getDefault()).contains(q)||v.folder.toLowerCase(Locale.getDefault()).contains(q))list.add(v);
        }
        if(sortMode==1)list.sort(Comparator.comparing(v->v.title.toLowerCase(Locale.getDefault())));
        else if(sortMode==2)list.sort((a,b)->Long.compare(b.durationMs,a.durationMs));
        else list.sort((a,b)->Long.compare(b.dateAdded,a.dateAdded));
        adapter.submitList(list);
        count.setText(list.size()+" "+(list.size()==1?"vídeo":"vídeos"));
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==REQ_VIDEO&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)loadVideos();
    }

    private static String safe(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
