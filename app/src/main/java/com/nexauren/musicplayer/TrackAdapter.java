package com.nexauren.musicplayer;

import android.graphics.Color;
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

public final class TrackAdapter extends ListAdapter<Track, TrackAdapter.Holder> {
    public interface Listener {
        void onTrackClick(Track track);
        void onTrackMenu(View anchor, Track track);
        void onTrackBound(Track track, ImageView target);
    }

    private final Listener listener;
    private final int primary;
    private final int secondary;
    private final int surface;

    public TrackAdapter(Listener listener, int primary, int secondary, int surface) {
        super(new DiffUtil.ItemCallback<Track>() {
            @Override public boolean areItemsTheSame(@NonNull Track a,@NonNull Track b){ return a.id==b.id; }
            @Override public boolean areContentsTheSame(@NonNull Track a,@NonNull Track b){
                return a.id==b.id && safe(a.title).equals(safe(b.title))
                        && safe(a.artist).equals(safe(b.artist))
                        && safe(a.album).equals(safe(b.album))
                        && a.durationMs==b.durationMs;
            }
            private String safe(String s){ return s==null?"":s; }
        });
        this.listener=listener;
        this.primary=primary;
        this.secondary=secondary;
        this.surface=surface;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position){ return getItem(position).id; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){
        float d=parent.getResources().getDisplayMetrics().density;
        LinearLayout row=new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int)(8*d),(int)(6*d),(int)(5*d),(int)(6*d));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(surface);bg.setCornerRadius(16*d);row.setBackground(bg);

        ImageView art=new ImageView(parent.getContext());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageResource(R.drawable.music_placeholder);
        row.addView(art,new LinearLayout.LayoutParams((int)(62*d),(int)(62*d)));

        LinearLayout labels=new LinearLayout(parent.getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding((int)(10*d),0,(int)(4*d),0);
        TextView title=new TextView(parent.getContext());
        title.setTextColor(primary);title.setTextSize(15);title.setTypeface(null,1);title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub=new TextView(parent.getContext());
        sub.setTextColor(secondary);sub.setTextSize(11);sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title,new LinearLayout.LayoutParams(-1,(int)(40*d)));
        labels.addView(sub,new LinearLayout.LayoutParams(-1,(int)(22*d)));
        row.addView(labels,new LinearLayout.LayoutParams(0,(int)(62*d),1));

        TextView duration=new TextView(parent.getContext());
        duration.setTextColor(secondary);duration.setTextSize(11);duration.setGravity(Gravity.CENTER);
        row.addView(duration,new LinearLayout.LayoutParams((int)(46*d),(int)(62*d)));

        TextView more=new TextView(parent.getContext());
        more.setText("⋮");more.setTextColor(secondary);more.setTextSize(24);more.setGravity(Gravity.CENTER);
        row.addView(more,new LinearLayout.LayoutParams((int)(34*d),(int)(62*d)));

        return new Holder(row,art,title,sub,duration,more);
    }

    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        Track t=getItem(position);
        h.title.setText(t.title);
        String artist=t.artist==null?"":t.artist;
        String album=t.album==null?"":t.album;
        h.sub.setText(album.isEmpty()?artist:(artist+" · "+album));
        h.duration.setText(format(t.durationMs));
        h.art.setImageResource(R.drawable.music_placeholder);
        h.itemView.setOnClickListener(v->listener.onTrackClick(t));
        h.more.setOnClickListener(v->listener.onTrackMenu(h.more,t));
        listener.onTrackBound(t,h.art);
    }

    private static String format(long ms){
        long s=Math.max(0,ms/1000);
        return String.format(Locale.getDefault(),"%d:%02d",s/60,s%60);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView art;final TextView title,sub,duration,more;
        Holder(View row,ImageView art,TextView title,TextView sub,TextView duration,TextView more){
            super(row);this.art=art;this.title=title;this.sub=sub;this.duration=duration;this.more=more;
        }
    }
}
