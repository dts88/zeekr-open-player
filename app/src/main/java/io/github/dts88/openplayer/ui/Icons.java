package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class Icons extends Drawable {
    private final String name;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public Icons(String name, int color, Context context) {
        this.name = name;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width();
        float h = getBounds().height();
        float s = Math.min(w, h) / 24f;
        c.save();
        c.translate(getBounds().left + (w - 24 * s) / 2f, getBounds().top + (h - 24 * s) / 2f);
        c.scale(s, s);
        path.reset();
        switch (name) {
            case "music":
                line(9, 18, 9, 5, 21, 3, 21, 16);
                c.drawPath(path, stroke);
                c.drawCircle(6, 18, 3, stroke);
                c.drawCircle(18, 16, 3, stroke);
                break;
            case "settings":
                line(4, 6, 20, 6);
                line(4, 12, 20, 12);
                line(4, 18, 20, 18);
                c.drawPath(path, stroke);
                dot(c, 9, 6);
                dot(c, 15, 12);
                dot(c, 7, 18);
                break;
            case "play":
                path.moveTo(8, 5);
                path.lineTo(19, 12);
                path.lineTo(8, 19);
                path.close();
                c.drawPath(path, fill);
                break;
            case "pause":
                rect.set(7, 5, 11, 19);
                c.drawRoundRect(rect, 1, 1, fill);
                rect.set(13, 5, 17, 19);
                c.drawRoundRect(rect, 1, 1, fill);
                break;
            case "next":
                path.moveTo(5, 5);
                path.lineTo(15, 12);
                path.lineTo(5, 19);
                path.close();
                c.drawPath(path, fill);
                rect.set(16.5f, 5, 19, 19);
                c.drawRoundRect(rect, 1, 1, fill);
                break;
            case "previous":
                path.moveTo(19, 5);
                path.lineTo(9, 12);
                path.lineTo(19, 19);
                path.close();
                c.drawPath(path, fill);
                rect.set(5, 5, 7.5f, 19);
                c.drawRoundRect(rect, 1, 1, fill);
                break;
            case "rewind":
            case "forward": {
                boolean back = name.equals("rewind");
                rect.set(4, 5, 20, 21);
                path.addArc(rect, back ? -60 : -120, back ? 300 : -300);
                c.drawPath(path, stroke);
                path.reset();
                if (back) line(11.5f, 1.5f, 15.6f, 5.6f, 11.5f, 9.2f);
                else line(12.5f, 1.5f, 8.4f, 5.6f, 12.5f, 9.2f);
                c.drawPath(path, stroke);
                break;
            }
            case "back":
                line(14.5f, 5, 7.5f, 12, 14.5f, 19);
                c.drawPath(path, stroke);
                break;
            case "chevron":
                line(9.5f, 5, 16.5f, 12, 9.5f, 19);
                c.drawPath(path, stroke);
                break;
            case "close":
                line(6, 6, 18, 18);
                line(18, 6, 6, 18);
                c.drawPath(path, stroke);
                break;
            case "subtitle":
                rect.set(3, 5, 21, 19);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                line(7, 11, 11, 11);
                line(14, 11, 17, 11);
                line(7, 15, 9, 15);
                line(12, 15, 17, 15);
                c.drawPath(path, stroke);
                break;
            case "audio":
                line(4, 10, 4, 14);
                line(8, 6, 8, 18);
                line(12, 3, 12, 21);
                line(16, 7, 16, 17);
                line(20, 10, 20, 14);
                c.drawPath(path, stroke);
                break;
            case "speaker":
                path.moveTo(4, 9.5f);
                path.lineTo(8, 9.5f);
                path.lineTo(13, 5);
                path.lineTo(13, 19);
                path.lineTo(8, 14.5f);
                path.lineTo(4, 14.5f);
                path.close();
                c.drawPath(path, stroke);
                path.reset();
                rect.set(11, 7.5f, 20, 16.5f);
                path.addArc(rect, -50, 100);
                rect.set(9, 4, 23, 20);
                path.addArc(rect, -50, 100);
                c.drawPath(path, stroke);
                break;
            case "picture":
                rect.set(3, 4.5f, 21, 16.5f);
                c.drawRoundRect(rect, 2, 2, stroke);
                line(8.5f, 20.5f, 15.5f, 20.5f);
                line(12, 16.5f, 12, 20.5f);
                c.drawPath(path, stroke);
                break;
            case "speed":
                rect.set(3, 4, 21, 22);
                path.addArc(rect, 160, 220);
                c.drawPath(path, stroke);
                path.reset();
                line(12, 13, 16.5f, 8);
                c.drawPath(path, stroke);
                dot(c, 12, 13);
                break;
            case "list":
                line(9, 6, 20, 6);
                line(9, 12, 20, 12);
                line(9, 18, 20, 18);
                c.drawPath(path, stroke);
                dot(c, 4.5f, 6);
                dot(c, 4.5f, 12);
                dot(c, 4.5f, 18);
                break;
            case "folder":
                path.moveTo(3, 7);
                path.lineTo(3, 18.5f);
                path.lineTo(21, 18.5f);
                path.lineTo(21, 8.5f);
                path.lineTo(12, 8.5f);
                path.lineTo(10, 5.5f);
                path.lineTo(3, 5.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case "film":
                rect.set(3, 4, 21, 20);
                c.drawRoundRect(rect, 2, 2, stroke);
                line(7.5f, 4, 7.5f, 20);
                line(16.5f, 4, 16.5f, 20);
                line(3, 9.3f, 7.5f, 9.3f);
                line(3, 14.7f, 7.5f, 14.7f);
                line(16.5f, 9.3f, 21, 9.3f);
                line(16.5f, 14.7f, 21, 14.7f);
                c.drawPath(path, stroke);
                break;
            case "info":
                c.drawCircle(12, 12, 9, stroke);
                line(12, 11, 12, 16.5f);
                c.drawPath(path, stroke);
                dot(c, 12, 7.6f);
                break;
            case "stop":
                rect.set(6.5f, 6.5f, 17.5f, 17.5f);
                c.drawRoundRect(rect, 2, 2, fill);
                break;
            case "power":
                rect.set(5, 5.5f, 19, 19.5f);
                path.addArc(rect, -55, 290);
                c.drawPath(path, stroke);
                path.reset();
                line(12, 3.5f, 12, 11.5f);
                c.drawPath(path, stroke);
                break;
            case "check":
                line(5, 12.5f, 10, 17.5f, 19.5f, 7);
                c.drawPath(path, stroke);
                break;
            case "disc":
                c.drawCircle(12, 12, 9, stroke);
                c.drawCircle(12, 12, 2.5f, stroke);
                break;
            default:
                c.drawCircle(12, 12, 8, stroke);
        }
        c.restore();
    }

    private void line(float... p) {
        path.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
    }

    private void dot(Canvas c, float x, float y) {
        c.drawCircle(x, y, 1.5f, fill);
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        stroke.setColorFilter(colorFilter);
        fill.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
