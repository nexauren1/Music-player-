package com.nexauren.musicplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;


import java.util.Random;

public final class AppearanceBackgroundDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final String kind;
    private final Bitmap photo;

    public AppearanceBackgroundDrawable(int accent,String kind){
        this(accent,kind,null);
    }

    public AppearanceBackgroundDrawable(int accent,String kind,Bitmap photo){
        this.accent=accent;
        this.kind=kind;
        this.photo=photo;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
    }

    public static Drawable forContext(Context context){
        String kind=AppearanceStore.background(context);
        if(AppearanceStore.BG_PHOTO.equals(kind)){
            String uriString=AppearanceStore.photoUri(context);
            if(uriString!=null){
                try{
                    Bitmap b=BitmapFactory.decodeStream(context.getContentResolver().openInputStream(android.net.Uri.parse(uriString)));
                    if(b!=null){
                        int max=Math.max(b.getWidth(),b.getHeight());
                        if(max>1400){
                            float scale=1400f/max;
                            b=Bitmap.createScaledBitmap(b,Math.max(1,(int)(b.getWidth()*scale)),Math.max(1,(int)(b.getHeight()*scale)),true);
                        }
                        return new AppearanceBackgroundDrawable(AppearanceStore.accent(context),kind,b);
                    }
                }catch(Exception ignored){}
            }
        }
        return new AppearanceBackgroundDrawable(AppearanceStore.accent(context),kind);
    }

    @Override public void draw(Canvas c){
        int w=getBounds().width(),h=getBounds().height();
        if(w<=0||h<=0)return;
        paint.setStyle(Paint.Style.FILL);

        if(AppearanceStore.BG_PHOTO.equals(kind) && photo!=null){
            android.graphics.Rect src=new android.graphics.Rect(0,0,photo.getWidth(),photo.getHeight());
            android.graphics.RectF dst=new android.graphics.RectF(0,0,w,h);
            float srcRatio=photo.getWidth()/(float)photo.getHeight(), dstRatio=w/(float)h;
            if(srcRatio>dstRatio){
                int newW=(int)(photo.getHeight()*dstRatio);
                int left=(photo.getWidth()-newW)/2;
                src.set(left,0,left+newW,photo.getHeight());
            }else{
                int newH=(int)(photo.getWidth()/dstRatio);
                int top=(photo.getHeight()-newH)/2;
                src.set(0,top,photo.getWidth(),top+newH);
            }
            c.drawBitmap(photo,src,dst,paint);
            paint.setColor(0x66000000);
            c.drawRect(0,0,w,h,paint);
            paint.setColor(withAlpha(accent,35));
            c.drawRect(0,0,w,h,paint);
        } else if(AppearanceStore.BG_AURORA.equals(kind)){
            paint.setShader(new LinearGradient(0,0,w,h,
                    Color.rgb(8,12,20), darken(accent,0.28f), Color.rgb(11,16,24), Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setColor(withAlpha(accent,62));
            c.drawCircle(w*0.18f,h*0.25f,w*0.32f,paint);
            paint.setColor(withAlpha(0xFF7C3AED,44));
            c.drawCircle(w*0.82f,h*0.34f,w*0.35f,paint);
            paint.setColor(withAlpha(0xFF06B6D4,36));
            c.drawCircle(w*0.50f,h*0.83f,w*0.42f,paint);
        } else if(AppearanceStore.BG_WAVES.equals(kind)){
            paint.setColor(Color.rgb(9,13,19));c.drawRect(0,0,w,h,paint);
            Path p=new Path();
            for(int layer=0;layer<6;layer++){
                float y=h*(0.18f+layer*0.14f);
                p.reset();p.moveTo(0,y);
                for(int x=0;x<=w;x+=24){
                    float yy=(float)(y+Math.sin((x/110.0)+layer*0.9)*h*0.035);
                    p.lineTo(x,yy);
                }
                p.lineTo(w,h);p.lineTo(0,h);p.close();
                paint.setColor(withAlpha(accent,18+layer*7));
                c.drawPath(p,paint);
            }
        } else if(AppearanceStore.BG_GEOMETRY.equals(kind)){
            paint.setColor(Color.rgb(10,14,20));c.drawRect(0,0,w,h,paint);
            stroke.setColor(withAlpha(accent,44));
            for(int x=-h;x<w+h;x+=dp(64)){
                c.drawLine(x,0,x+h,h,stroke);
            }
            stroke.setColor(withAlpha(Color.WHITE,18));
            for(int y=0;y<h;y+=dp(74))c.drawLine(0,y,w,y,stroke);
        } else if(AppearanceStore.BG_STARS.equals(kind)){
            paint.setColor(Color.rgb(6,9,15));c.drawRect(0,0,w,h,paint);
            Random r=new Random(9157L);
            for(int i=0;i<160;i++){
                float x=r.nextFloat()*w,y=r.nextFloat()*h;
                float s=0.6f+r.nextFloat()*2.1f;
                paint.setColor(withAlpha(i%7==0?accent:Color.WHITE,40+r.nextInt(100)));
                c.drawCircle(x,y,s,paint);
            }
        } else {
            paint.setColor(Color.rgb(9,13,19));c.drawRect(0,0,w,h,paint);
        }
    }

    private int dp(int v){return v;}
    private int withAlpha(int color,int alpha){return (alpha<<24)|(color&0x00FFFFFF);}
    private int darken(int color,float factor){
        return Color.rgb((int)(Color.red(color)*factor),(int)(Color.green(color)*factor),(int)(Color.blue(color)*factor));
    }
    @Override public void setAlpha(int alpha){ }
    @Override public void setColorFilter(android.graphics.ColorFilter f){ }
    @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
}
