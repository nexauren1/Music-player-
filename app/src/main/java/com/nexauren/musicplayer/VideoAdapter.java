package com.nexauren.musicplayer;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoAdapter extends ListAdapter<VideoTrack,VideoAdapter.Holder> {
    public interface Listener{void onVideoClick(VideoTrack video);}
    private static final ExecutorService THUMB_EXECUTOR=Executors.newFixedThreadPool(3);
    private final Listener listener;

    public VideoAdapter(Listener listener){
        super(new DiffUtil.ItemCallback<VideoTrack>(){
            public boolean areItemsTheSame(@NonNull VideoTrack a,@NonNull VideoTrack b){return a.id==b.id;}
            public boolean areContentsTheSame(@NonNull VideoTrack a,@NonNull VideoTrack b){return a.id==b.id&&a.title.equals(b.title)&&a.durationMs==b.durationMs&&a.sizeBytes==b.sizeBytes;}
        });
        this.listener=listener;setHasStableIds(true);
    }

    @Override public long getItemId(int position){return getItem(position).id;}

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
        float d=parent.getResources().getDisplayMetrics().density;
        LinearLayout row=new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int)(8*d),(int)(6*d),(int)(7*d),(int)(6*d));
        row.setBackground(round(0xFF121925,16*d));

        ImageView thumb=new ImageView(parent.getContext());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(round(0xFF202938,14*d));
        thumb.setClipToOutline(true);
        row.addView(thumb,new LinearLayout.LayoutParams((int)(122*d),(int)(78*d)));

        LinearLayout labels=new LinearLayout(parent.getContext());
        labels.setOrientation(LinearLayout.VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding((int)(10*d),0,(int)(5*d),0);
        TextView title=new TextView(parent.getContext());
        title.setTextColor(0xFFF4F7F3);title.setTextSize(15);title.setTypeface(null,1);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta=new TextView(parent.getContext());
        meta.setTextColor(0xFF9AA5B1);meta.setTextSize(11);meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);
        TextView duration=new TextView(parent.getContext());
        duration.setTextColor(0xFF9AA5B1);duration.setTextSize(11);
        labels.addView(title,new LinearLayout.LayoutParams(-1,(int)(39*d)));
        labels.addView(meta,new LinearLayout.LayoutParams(-1,(int)(20*d)));
        labels.addView(duration,new LinearLayout.LayoutParams(-1,(int)(20*d)));
        row.addView(labels,new LinearLayout.LayoutParams(0,(int)(78*d),1));

        TextView play=new TextView(parent.getContext());
        play.setText("▶");play.setTextSize(17);play.setTextColor(Color.WHITE);play.setGravity(Gravity.CENTER);
        play.setBackground(round(0xFF2196F3,25*d));
        row.addView(play,new LinearLayout.LayoutParams((int)(46*d),(int)(46*d)));
        return new Holder(row,thumb,title,meta,duration,play);
    }

    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        VideoTrack v=getItem(position);
        h.title.setText(v.title);
        h.meta.setText(v.folder+"  •  "+resolution(v.width,v.height)+"  •  "+size(v.sizeBytes));
        h.duration.setText(format(v.durationMs));
        h.thumb.setTag(v.id);
        h.thumb.setImageResource(R.drawable.music_placeholder);
        h.itemView.setOnClickListener(view->listener.onVideoClick(v));
        h.play.setOnClickListener(view->listener.onVideoClick(v));
        loadThumbnail(v,h.thumb);
    }

    private void loadThumbnail(VideoTrack video,ImageView target){
        THUMB_EXECUTOR.execute(()->{
            Bitmap bitmap=null;
            try{
                if(android.os.Build.VERSION.SDK_INT>=29)bitmap=target.getContext().getContentResolver().loadThumbnail(video.uri,new android.util.Size(420,260),null);
                else bitmap=ThumbnailUtils.createVideoThumbnail(video.uri.getPath(),MediaStore.Video.Thumbnails.MINI_KIND);
            }catch(Exception ignored){}
            Bitmap result=bitmap;
            target.post(()->{
                Object tag=target.getTag();
                if(tag instanceof Long&&((Long)tag)==video.id&&result!=null)target.setImageBitmap(result);
            });
        });
    }

    private static String resolution(int w,int h){return w>0&&h>0?w+"×"+h:"Vídeo";}
    private static String size(long bytes){
        if(bytes<1024*1024)return Math.max(1,bytes/1024)+" KB";
        if(bytes<1024L*1024*1024)return String.format(Locale.getDefault(),"%.1f MB",bytes/(1024f*1024f));
        return String.format(Locale.getDefault(),"%.1f GB",bytes/(1024f*1024f*1024f));
    }
    private static String format(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%d:%02d",s/60,s%60);}
    private static android.graphics.drawable.GradientDrawable round(int c,float r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(r);return d;}

    static final class Holder extends RecyclerView.ViewHolder{
        final ImageView thumb;final TextView title,meta,duration,play;
        Holder(View row,ImageView thumb,TextView title,TextView meta,TextView duration,TextView play){
            super(row);this.thumb=thumb;this.title=title;this.meta=meta;this.duration=duration;this.play=play;
        }
    }
}
