package com.nexauren.musicplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public final class EqBandView extends View {
    public interface Listener { void onChanged(int percent); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int percent = 50;
    private String label = "";
    private String value = "+0.0";
    private Listener listener;

    public EqBandView(Context context) { super(context); setFocusable(true); }

    public void setData(String label, int percent) {
        this.label = label == null ? "" : label;
        this.percent = Math.max(0, Math.min(100, percent));
        this.value = formatGain(percent);
        invalidate();
    }

    public int getPercent() { return percent; }

    public void setListener(Listener listener) { this.listener = listener; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float x = w / 2f;
        float top = 14f, bottom = h - 45f;
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF242831);
        canvas.drawLine(x, top, x, bottom, paint);

        float y = bottom - (bottom - top) * percent / 100f;
        paint.setColor(0xFF2D9DEB);
        canvas.drawLine(x, bottom, x, y, paint);
        paint.setColor(0xFF0A0D12);
        canvas.drawCircle(x, y, 11f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(0xFF6D74C6);
        canvas.drawCircle(x, y, 11f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13f);
        paint.setColor(0xFF2D9DEB);
        canvas.drawText(label, x, h - 24, paint);
        paint.setTextSize(12f);
        canvas.drawText(value, x, h - 6, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN &&
                event.getAction() != MotionEvent.ACTION_MOVE &&
                event.getAction() != MotionEvent.ACTION_UP) return true;
        float top = 14f, bottom = getHeight() - 45f;
        percent = Math.max(0, Math.min(100, Math.round((bottom - event.getY()) * 100f / Math.max(1f, bottom - top))));
        value = formatGain(percent);
        invalidate();
        if (listener != null) listener.onChanged(percent);
        return true;
    }

    private static String formatGain(int percent) {
        float db = (percent - 50) * 0.48f;
        return String.format(java.util.Locale.getDefault(), "%+.1f", db);
    }
}
