package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF121314;
    public static final int SURFACE = 0xFF2A2C30;
    public static final int SUNKEN = 0xFF3A3D42;
    public static final int LINE = 0xFF34373B;
    public static final int TEXT = 0xFFF0F1F2;
    public static final int TEXT2 = 0xFFA3A6AB;
    public static final int TEXT3 = 0xFF75787D;
    public static final int ENERGY = 0xFFF26B38;
    public static final int ENERGY_TEXT = 0xFFF58A5E;
    public static final int ENERGY_QUIET = 0xFF3A2419;
    public static final int ON_ENERGY = 0xFFFFFFFF;
    public static final int ALERT = 0xFFF0555A;
    public static final int RIPPLE = 0x1FF0F1F2;
    public static final int OVERLAY = 0xF2121314;

    public static final int RAIL = BG;
    public static final int SURFACE_HI = SUNKEN;
    public static final int ACCENT = ENERGY;
    public static final int ON_ACCENT = ON_ENERGY;
    public static final int BAD = ALERT;

    public static final int MICRO = 16;
    public static final int CAPTION = 20;
    public static final int BODY = 22;
    public static final int ROW = 24;
    public static final int TITLE = 32;

    public static final int TITLE_BAR = 64;
    public static final int LIST_WIDTH = 280;
    public static final int GUTTER = 24;
    public static final int GAP = 12;
    public static final int RADIUS = 10;
    public static final int TOUCH = 64;
    public static final int ICON_BUTTON = 48;

    private static final int ROW_PADDING = 20;
    private static final int SWITCH_WIDTH = 60;
    private static final int SWITCH_GAP = 18;
    private static final int SWITCH_OFF = 0x7375787D;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, CharSequence s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    public static TextView bold(Context c, CharSequence s, int sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView oneLine(TextView t) {
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable outline(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = round(c, fill, radiusDp);
        d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    public static RippleDrawable pressable(Context c, int color, float radiusDp) {
        GradientDrawable shape = round(c, color, radiusDp);
        GradientDrawable mask = round(c, 0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, mask);
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static View space(Context c, int widthDp, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), dp(c, heightDp)));
        return v;
    }

    public static View spring(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fillWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams sized(Context c, int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(widthDp < 0 ? widthDp : dp(c, widthDp), heightDp < 0 ? heightDp : dp(c, heightDp));
    }

    public static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static TextView button(Context c, String label, boolean primary, Runnable onClick) {
        TextView b = text(c, label, BODY, primary ? ON_ENERGY : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 52));
        b.setMinWidth(dp(c, 112));
        b.setPadding(dp(c, 24), 0, dp(c, 24), 0);
        b.setBackground(pressable(c, primary ? ENERGY : SURFACE, RADIUS));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static LinearLayout iconButton(Context c, String icon, String label, boolean primary, Runnable onClick) {
        LinearLayout l = row(c);
        l.setGravity(Gravity.CENTER);
        l.setMinimumHeight(dp(c, ICON_BUTTON));
        l.setPadding(dp(c, 12), 0, dp(c, 18), 0);
        l.setBackground(pressable(c, primary ? ENERGY : SUNKEN, RADIUS));
        l.addView(icon(c, icon, 24, primary ? ON_ENERGY : TEXT));
        TextView t = text(c, label, BODY, primary ? ON_ENERGY : TEXT);
        t.setPadding(dp(c, 6), 0, 0, 0);
        l.addView(t);
        l.setOnClickListener(v -> onClick.run());
        return l;
    }

    public static ImageView roundIcon(Context c, String icon, int sizeDp, int iconDp, int bg, int color, Runnable onClick) {
        ImageView i = new ImageView(c);
        i.setImageDrawable(new Icons(icon, color, c));
        int pad = dp(c, (sizeDp - iconDp) / 2f);
        i.setPadding(pad, pad, pad, pad);
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        i.setBackground(pressable(c, bg, sizeDp / 2f));
        i.setContentDescription(icon);
        i.setOnClickListener(v -> onClick.run());
        return i;
    }

    public static ImageView icon(Context c, String name, int sizeDp, int color) {
        ImageView i = new ImageView(c);
        i.setImageDrawable(new Icons(name, color, c));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    public static TextView chip(Context c, String s, boolean strong) {
        TextView t = text(c, s, MICRO, strong ? ENERGY_TEXT : TEXT2);
        t.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 6));
        t.setBackground(outline(c, strong ? ENERGY_QUIET : 0x00000000, strong ? ENERGY : TEXT3, 8));
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, CAPTION, TEXT2);
        t.setPadding(0, dp(c, 8), 0, dp(c, 10));
        return t;
    }

    public static LinearLayout segmented(Context c, String[] labels, int selected, IntCallback onSelect) {
        LinearLayout box = row(c);
        box.setBackground(round(c, SURFACE, 12));
        int pad = dp(c, 3);
        box.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < labels.length; i++) {
            TextView t = text(c, labels[i], CAPTION, TEXT2);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(c, 46));
            t.setMinWidth(dp(c, 96));
            t.setPadding(dp(c, 18), 0, dp(c, 18), 0);
            int index = i;
            t.setOnClickListener(v -> {
                paintSegments(c, box, index);
                onSelect.call(index);
            });
            box.addView(t);
        }
        paintSegments(c, box, selected);
        return box;
    }

    public static void paintSegments(Context c, LinearLayout box, int selected) {
        for (int i = 0; i < box.getChildCount(); i++) {
            TextView t = (TextView) box.getChildAt(i);
            boolean on = i == selected;
            t.setTextColor(on ? ON_ENERGY : TEXT2);
            t.setBackground(on ? round(c, ENERGY, 9) : null);
        }
        box.setTag(selected);
    }

    public static FrameLayout toggle(Context c, boolean on, BoolCallback onChange) {
        FrameLayout track = new FrameLayout(c);
        track.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 56), dp(c, 30)));
        View knob = new View(c);
        knob.setBackground(round(c, ON_ENERGY, 6));
        int k = dp(c, 22);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(k, k);
        lp.gravity = Gravity.CENTER_VERTICAL;
        track.addView(knob, lp);
        track.setTag(on);
        paintToggle(c, track);
        track.setOnClickListener(v -> {
            boolean now = !(Boolean) track.getTag();
            track.setTag(now);
            paintToggle(c, track);
            onChange.call(now);
        });
        return track;
    }

    private static void paintToggle(Context c, FrameLayout track) {
        boolean on = (Boolean) track.getTag();
        track.setBackground(round(c, on ? ENERGY : SWITCH_OFF, 9));
        View knob = track.getChildAt(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) knob.getLayoutParams();
        lp.leftMargin = dp(c, on ? 30 : 4);
        knob.setLayoutParams(lp);
    }

    private static boolean isToggle(View v) {
        return v instanceof FrameLayout && v.getTag() instanceof Boolean;
    }

    public static LinearLayout settingRow(Context c, String title, String note, View trailing) {
        LinearLayout row = row(c);
        row.setMinimumHeight(dp(c, 72));
        row.setPadding(dp(c, ROW_PADDING), dp(c, 12), dp(c, 16), dp(c, 12));
        row.setBackground(round(c, SUNKEN, RADIUS));
        boolean leading = isToggle(trailing);
        if (leading) {
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(c, 56), dp(c, 30));
            sl.rightMargin = dp(c, SWITCH_WIDTH - 56 + SWITCH_GAP);
            row.addView(trailing, sl);
            row.setOnClickListener(v -> trailing.performClick());
        } else {
            row.addView(space(c, SWITCH_WIDTH + SWITCH_GAP, 1));
        }
        LinearLayout texts = column(c);
        texts.addView(text(c, title, BODY, TEXT));
        if (note != null && !note.isEmpty()) {
            TextView n = text(c, note, MICRO, TEXT2);
            n.setPadding(0, dp(c, 4), 0, 0);
            texts.addView(n);
        }
        row.addView(texts, weight(1));
        if (trailing != null && !leading) {
            LinearLayout.LayoutParams lp = trailing.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) trailing.getLayoutParams() : wrap();
            lp.leftMargin = dp(c, 20);
            row.addView(trailing, lp);
        }
        return row;
    }

    public static LinearLayout card(Context c) {
        return column(c);
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 8)));
        return v;
    }

    public interface IntCallback {
        void call(int value);
    }

    public interface BoolCallback {
        void call(boolean value);
    }
}
