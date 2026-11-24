package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class SeekLine extends View {
    interface Listener {
        void onScrub(long positionMs);

        void onSeek(long positionMs);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private long positionMs;
    private long bufferedMs;
    private long durationMs;
    private long[] marks = new long[0];
    private boolean dragging;
    private long dragMs;
    private Listener listener;

    SeekLine(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean dragging() {
        return dragging;
    }

    void set(long position, long buffered, long duration) {
        if (position == positionMs && buffered == bufferedMs && duration == durationMs) return;
        positionMs = position;
        bufferedMs = buffered;
        durationMs = duration;
        if (!dragging) invalidate();
    }

    void setMarks(long[] startsMs) {
        marks = startsMs == null ? new long[0] : startsMs;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(48 * density));
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = 14 * density;
        float left = pad;
        float right = getWidth() - pad;
        float cy = getHeight() / 2f;
        float half = (dragging ? 5 : 3) * density;
        float radius = half;
        float span = right - left;
        long d = Math.max(1, durationMs);
        long shown = dragging ? dragMs : positionMs;
        float played = left + span * Math.min(1f, shown / (float) d);
        float buffered = left + span * Math.min(1f, bufferedMs / (float) d);

        paint.setColor(0x40FFFFFF);
        rect.set(left, cy - half, right, cy + half);
        c.drawRoundRect(rect, radius, radius, paint);
        if (durationMs > 0) {
            paint.setColor(0x59FFFFFF);
            rect.set(left, cy - half, buffered, cy + half);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setColor(Ui.ACCENT);
            rect.set(left, cy - half, played, cy + half);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setColor(0xCC000000);
            for (long m : marks) {
                if (m <= 0 || m >= durationMs) continue;
                float x = left + span * (m / (float) d);
                c.drawRect(x - density, cy - half, x + density, cy + half, paint);
            }
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(played, cy, (dragging ? 13 : 9) * density, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (durationMs <= 0) return false;
        float pad = 14 * density;
        float span = Math.max(1, getWidth() - 2 * pad);
        long at = Math.round(Math.max(0, Math.min(1, (e.getX() - pad) / span)) * durationMs);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                dragMs = at;
                if (listener != null) listener.onScrub(at);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                dragMs = at;
                if (listener != null) listener.onScrub(at);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                positionMs = at;
                if (listener != null) listener.onSeek(at);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
            default:
                return false;
        }
    }
}
