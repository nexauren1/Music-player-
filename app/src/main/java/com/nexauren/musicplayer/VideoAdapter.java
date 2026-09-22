package com.nexauren.musicplayer;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import android.os.Build;

public final class VideoAdapter extends ListAdapter<VideoTrack, VideoAdapter.Holder> {
    public interface Listener { void onVideoClick(VideoTrack video); }

    private final Listener listener;

    public VideoAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<VideoTrack>() {
            @Override public boolean areItemsTheSame(@NonNull VideoTrack a,@NonNull VideoTrack b){ return a.id==b.id; }
            @Override public boolean areContentsTheSame(@NonNull VideoTrack a,@NonNull VideoTrack b){
                return a.id==b.id && a.title.equals(b.title) && a.durationMs==b.durationMs;
            }
        });
        this.listener=listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return getItem(position).id; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){
        LinearLayout row=new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad=(int)(8*parent.getResources().getDisplayMetrics().density+0.5f);
        row.setPadding(pad,pad,pad,pad);
        row.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        ImageView thumb=new ImageView(parent.getContext());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(thumb,new LinearLayout.LayoutParams(dp(parent,112),dp(parent,72)));

        LinearLayout labels=new LinearLayout(parent.getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(parent,10),0,dp(parent,6),0);
        TextView title=new TextView(parent.getContext());
        title.setTextSize(15);
        title.setTypeface(null,1);
        title.setMaxLines(2);
        TextView duration=new TextView(parent.getContext());
        duration.setTextSize(11);
        duration.setAlpha(.7f);
        labels.addView(title,new LinearLayout.LayoutParams(-1,dp(parent,42)));
        labels.addView(duration,new LinearLayout.LayoutParams(-1,dp(parent,22)));
        row.addView(labels,new LinearLayout.LayoutParams(0,dp(parent,72),1));

        TextView play=new TextView(parent.getContext());
        play.setText("▶");
        play.setTextSize(18);
        play.setGravity(Gravity.CENTER);
        row.addView(play,new LinearLayout.LayoutParams(dp(parent,48),dp(parent,64)));

        return new Holder(row,thumb,title,duration,play);
    }

    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        VideoTrack v=getItem(position);
        h.title.setText(v.title);
        h.duration.setText(format(v.durationMs));
        h.thumb.setImageResource(R.drawable.music_placeholder);
        h.itemView.setOnClickListener(view->listener.onVideoClick(v));
        loadThumbnail(v,h.thumb);
    }

    private void loadThumbnail(VideoTrack video,ImageView target){
        new Thread(()->{
            Bitmap bitmap=null;
            try{
                if(Build.VERSION.SDK_INT>=29) bitmap=target.getContext().getContentResolver().loadThumbnail(video.uri,new android.util.Size(320,200),null);
                else bitmap=ThumbnailUtils.createVideoThumbnail(video.uri.getPath(),MediaStore.Video.Thumbnails.MINI_KIND);
            }catch(Exception ignored){}
            Bitmap result=bitmap;
            target.post(()->{ if(result!=null) target.setImageBitmap(result); });
        },"nexauren-video-thumb").start();
    }

    private static int dp(ViewGroup p,int v){return(int)(v*p.getResources().getDisplayMetrics().density+.5f);}
    private static String format(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%d:%02d",s/60,s%60);}

    static final class Holder extends RecyclerView.ViewHolder{
        final ImageView thumb; final TextView title,duration,play;
        Holder(LinearLayout row,ImageView thumb,TextView title,TextView duration,TextView play){
            super(row);this.thumb=thumb;this.title=title;this.duration=duration;this.play=play;
        }
    }
}
