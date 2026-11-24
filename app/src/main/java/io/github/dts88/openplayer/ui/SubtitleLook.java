package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;

import io.github.dts88.openplayer.Tx;

@OptIn(markerClass = UnstableApi.class)
final class SubtitleLook {
    static String[] sizeLabels() {
        return new String[]{Tx.t("小", "Small"), Tx.t("中", "Medium"), Tx.t("大", "Large"), Tx.t("特大", "XL")};
    }
    private static final float[] SIZE_FRACTIONS = {0.042f, 0.0533f, 0.066f, 0.082f};

    static String[] positionLabels() {
        return new String[]{Tx.t("低", "Low"), Tx.t("中", "Middle"), Tx.t("高", "High")};
    }
    private static final float[] BOTTOM_FRACTIONS = {0.06f, 0.13f, 0.21f};

    static String[] styleLabels() {
        return new String[]{Tx.t("白字描边", "White, outlined"), Tx.t("白字黑底", "White on black"), Tx.t("黄字描边", "Yellow, outlined")};
    }

    private static final String PREFS = "subtitle_look";
    private static final String KEY_SIZE = "size";
    private static final String KEY_POSITION = "position";
    private static final String KEY_STYLE = "style";
    private static final String KEY_EMBEDDED = "embedded";

    private SubtitleLook() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int size(Context c) {
        return clamp(prefs(c).getInt(KEY_SIZE, 1), sizeLabels().length);
    }

    static int position(Context c) {
        return clamp(prefs(c).getInt(KEY_POSITION, 0), positionLabels().length);
    }

    static int style(Context c) {
        return clamp(prefs(c).getInt(KEY_STYLE, 0), styleLabels().length);
    }

    static boolean embedded(Context c) {
        return prefs(c).getBoolean(KEY_EMBEDDED, true);
    }

    static void setSize(Context c, int index) {
        prefs(c).edit().putInt(KEY_SIZE, index).apply();
    }

    static void setPosition(Context c, int index) {
        prefs(c).edit().putInt(KEY_POSITION, index).apply();
    }

    static void setStyle(Context c, int index) {
        prefs(c).edit().putInt(KEY_STYLE, index).apply();
    }

    static void setEmbedded(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_EMBEDDED, on).apply();
    }

    private static int clamp(int index, int count) {
        return Math.max(0, Math.min(count - 1, index));
    }

    static float sizeFraction(int index) {
        return SIZE_FRACTIONS[clamp(index, SIZE_FRACTIONS.length)];
    }

    static float bottomFraction(int index) {
        return BOTTOM_FRACTIONS[clamp(index, BOTTOM_FRACTIONS.length)];
    }

    static CaptionStyleCompat captionStyle(int index) {
        switch (clamp(index, styleLabels().length)) {
            case 1:
                return new CaptionStyleCompat(Color.WHITE, 0xB3000000, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null);
            case 2:
                return new CaptionStyleCompat(0xFFFFE14A, Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
            default:
                return new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
        }
    }

    static void apply(Context c, SubtitleView view) {
        view.setFractionalTextSize(sizeFraction(size(c)));
        view.setBottomPaddingFraction(bottomFraction(position(c)));
        view.setStyle(captionStyle(style(c)));
        boolean embedded = embedded(c);
        view.setApplyEmbeddedStyles(embedded);
        view.setApplyEmbeddedFontSizes(embedded);
    }
}
