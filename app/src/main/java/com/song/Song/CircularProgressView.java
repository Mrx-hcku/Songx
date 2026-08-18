package com.song.Song;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws a track circle + a progress arc around it, like a "now playing" ring.
 * No external library — just Canvas.drawArc. Sits behind a circular album
 * art ImageView in a FrameLayout, slightly larger so the ring peeks out.
 */
public class CircularProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f; // 0..100
    private final RectF arcRect = new RectF();

    public CircularProgressView(Context context) { super(context); init(); }
    public CircularProgressView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CircularProgressView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        float strokeWidth = 8f * getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(0x33FFFFFF);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(0xFF1DD881); // primary_accent
    }

    public void setProgress(float percent) {
        progress = Math.max(0f, Math.min(100f, percent));
        invalidate();
    }

    public void setAccentColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = progressPaint.getStrokeWidth() / 2f + 2f;
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawOval(arcRect, trackPaint);

        float sweep = 360f * (progress / 100f);
        canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);
    }
}

