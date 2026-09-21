package com.nexauren.musicplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class DialView extends View {
    public interface OnDialChangedListener { void onChanged(int percent); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int percent = 50;
    private OnDialChangedListener listener;

    public DialView(Context context) { super(context); setFocusable(true); }

    public void setPercent(int value) {
        percent = Math.max(0, Math.min(100, value));
        invalidate();
    }

    public void setOnDialChangedListener(OnDialChangedListener l) { listener = l; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f - 8f;
        float radius = Math.min(getWidth(), getHeight()) * 0.34f;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFE0E1E4);
        canvas.drawArc(arc, 135, 270, false, paint);

        paint.setColor(0xFF2D9DEB);
        canvas.drawArc(arc, 135, 270f * percent / 100f, false, paint);

        double angle = Math.toRadians(135 + 270 * percent / 100f);
        float kx = cx + (float)Math.cos(angle) * radius;
        float ky = cy + (float)Math.sin(angle) * radius;
        paint.setColor(0xFF34383D);
        paint.setStrokeWidth(4);
        canvas.drawLine(cx, cy, kx, ky, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE &&
            event.getAction() != MotionEvent.ACTION_UP) return true;
        float cx = getWidth() / 2f, cy = getHeight() / 2f - 8f;
        double a = Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
        double sweep = a - 135;
        while (sweep < 0) sweep += 360;
        if (sweep > 270) sweep = 270;
        percent = (int)Math.round((sweep / 270d) * 100d);
        invalidate();
        if (listener != null) listener.onChanged(percent);
        return true;
    }
}
