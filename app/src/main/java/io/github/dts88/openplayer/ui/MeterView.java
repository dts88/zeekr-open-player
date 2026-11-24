package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import io.github.dts88.openplayer.Tx;

final class MeterView extends View {
    private static final float FLOOR_DB = -60f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float heightDp;
    private final float labelDp;
    private final float lineDp;
    private final float firstDp;
    private final float barDp;
    private float[] shown = new float[0];
    private float[] peak = new float[0];
    private String[] names = new String[0];

    MeterView(Context c) {
        this(c, false);
    }

    MeterView(Context c, boolean compact) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        heightDp = compact ? 120 : 196;
        labelDp = compact ? 34 : 50;
        lineDp = compact ? 15 : 21;
        firstDp = compact ? 15 : 22;
        barDp = compact ? 16 : 26;
        text.setColor(Ui.TEXT2);
        text.setTextSize((compact ? 12 : 15) * c.getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void set(float[] db, String[] channelNames) {
        if (db.length != shown.length) {
            shown = new float[db.length];
            peak = new float[db.length];
            java.util.Arrays.fill(shown, FLOOR_DB);
            java.util.Arrays.fill(peak, FLOOR_DB);
        }
        names = channelNames;
        for (int i = 0; i < db.length; i++) {
            float v = Math.max(FLOOR_DB, db[i]);
            shown[i] = Math.max(v, shown[i] - 2.5f);
            peak[i] = Math.max(v, peak[i] - 0.5f);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(heightDp * density));
    }

    private boolean twoLines(float slot) {
        for (String n : names) {
            if (n.codePointCount(0, n.length()) > 1 && text.measureText(n) > slot - 2 * density) return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = shown.length;
        if (n == 0) {
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(Tx.t("未播放，或当前为下混输出（不显示电平）", "Not playing, or downmix output (no levels)"), 0, getHeight() / 2f, text);
            text.setTextAlign(Paint.Align.CENTER);
            return;
        }
        float slot = getWidth() / (float) n;
        boolean split = twoLines(slot);
        float lineHeight = lineDp * density;
        float label = labelDp * density;
        float top = 4 * density;
        float bottom = getHeight() - label;
        float bar = Math.min(barDp * density, slot * 0.56f);
        for (int i = 0; i < n; i++) {
            float cx = slot * (i + 0.5f);
            paint.setColor(0x26FFFFFF);
            rect.set(cx - bar / 2, top, cx + bar / 2, bottom);
            c.drawRoundRect(rect, 4 * density, 4 * density, paint);
            float f = (shown[i] - FLOOR_DB) / -FLOOR_DB;
            if (f > 0.005f) {
                paint.setColor(shown[i] > -3f ? Ui.BAD : Ui.ACCENT);
                rect.set(cx - bar / 2, bottom - (bottom - top) * f, cx + bar / 2, bottom);
                c.drawRoundRect(rect, 4 * density, 4 * density, paint);
            }
            float pf = (peak[i] - FLOOR_DB) / -FLOOR_DB;
            if (pf > 0.01f) {
                paint.setColor(0xCCFFFFFF);
                float y = bottom - (bottom - top) * pf;
                c.drawRect(cx - bar / 2, y - density, cx + bar / 2, y + density, paint);
            }
            if (i >= names.length) continue;
            String name = names[i];
            float first = bottom + firstDp * density;
            if (split && name.codePointCount(0, name.length()) > 1) {
                int cut = name.offsetByCodePoints(name.length(), -1);
                c.drawText(name, 0, cut, cx, first, text);
                c.drawText(name, cut, name.length(), cx, first + lineHeight, text);
            } else {
                c.drawText(name, cx, first, text);
            }
        }
    }
}
