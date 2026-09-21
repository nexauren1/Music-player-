package com.nexauren.musicplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class WaveformView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f;
    private boolean playing = false;
    public WaveformView(Context context) { super(context); }
    public void setProgress(float value) { progress = Math.max(0f, Math.min(1f, value)); invalidate(); }
    public void setPlaying(boolean value) { playing = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int count = Math.max(24, w / 5);
        float barW = Math.max(2f, w / (float)count - 1.5f);
        for (int i = 0; i < count; i++) {
            float x = i * (w / (float)count);
            double wave = 0.25 + 0.75 * Math.abs(Math.sin(i * 0.42)) * (0.55 + 0.45 * Math.abs(Math.sin(i * 0.17 + 1)));
            float bh = (float)(h * 0.84 * wave);
            if (playing) bh *= (0.86 + 0.14 * Math.abs(Math.sin(i * 0.22 + System.currentTimeMillis() / 350.0)));
            paint.setColor(i < progress * count ? 0xFF2D9DEB : 0xFFD5D9DE);
            canvas.drawRect(x, (h - bh) / 2f, x + barW, (h + bh) / 2f, paint);
        }
        float px = progress * w;
        paint.setColor(0xFF2D9DEB);
        canvas.drawCircle(px, h / 2f, 7, paint);
        if (playing) postInvalidateDelayed(120);
    }
}
