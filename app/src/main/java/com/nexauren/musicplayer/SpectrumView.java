package com.nexauren.musicplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class SpectrumView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] levels = new float[32];
    public SpectrumView(Context context) { super(context); }
    public void setLevels(byte[] fft) {
        if (fft == null || fft.length < 2) return;
        int bars = Math.min(levels.length, fft.length / 2);
        for (int i = 0; i < bars; i++) {
            int index = 2 * i + 2;
            if (index + 1 >= fft.length) break;
            float mag = (float) Math.hypot(fft[index], fft[index + 1]);
            levels[i] = Math.min(1f, mag / 90f);
        }
        invalidate();
    }
    public void animateFallback() {
        long t = System.currentTimeMillis();
        for (int i = 0; i < levels.length; i++) levels[i] = 0.15f + 0.65f * Math.abs((float)Math.sin(i * .35 + t / 260.0));
        invalidate();
        postInvalidateDelayed(120);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        c.drawColor(0xFF090C11);
        float gap=4f, bw=(w-gap*(levels.length-1))/levels.length;
        for (int i=0;i<levels.length;i++) {
            float bh=Math.max(8f, levels[i]*h*.88f);
            float x=i*(bw+gap);
            paint.setColor(0xFF2D9DEB);
            c.drawRoundRect(x,h-bh,x+bw,h,5,5,paint);
        }
    }
}
