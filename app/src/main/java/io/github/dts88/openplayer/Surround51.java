package io.github.dts88.openplayer;

import java.util.Locale;

final class Surround51 {
    enum Placement {
        SIDE("侧面"),
        BACK("后面"),
        BOTH("侧面 + 后面");

        final String label;

        Placement(String label) {
            this.label = label;
        }

        static Placement fromName(String name) {
            if (name != null) {
                for (Placement p : values()) {
                    if (p.name().equals(name)) return p;
                }
            }
            return SIDE;
        }
    }

    static final float BOTH_SIDE = 0.777f;
    static final float BOTH_BACK = 0.629f;

    private Surround51() {}

    static void expand(float[] in, int frames, float[] out, Placement p) {
        float side = p == Placement.BOTH ? BOTH_SIDE : 1f;
        float back = p == Placement.BOTH ? BOTH_BACK : p == Placement.BACK ? 1f : 0f;
        if (p == Placement.BACK) side = 0f;
        for (int f = 0, i = 0, o = 0; f < frames; f++, i += 6, o += 8) {
            out[o] = in[i];
            out[o + 1] = in[i + 1];
            out[o + 2] = in[i + 2];
            out[o + 3] = in[i + 3];
            out[o + 4] = in[i + 4] * back;
            out[o + 5] = in[i + 5] * back;
            out[o + 6] = in[i + 4] * side;
            out[o + 7] = in[i + 5] * side;
        }
    }

    static void expand(short[] in, int frames, short[] out, Placement p) {
        float side = p == Placement.BOTH ? BOTH_SIDE : 1f;
        float back = p == Placement.BOTH ? BOTH_BACK : p == Placement.BACK ? 1f : 0f;
        if (p == Placement.BACK) side = 0f;
        for (int f = 0, i = 0, o = 0; f < frames; f++, i += 6, o += 8) {
            out[o] = in[i];
            out[o + 1] = in[i + 1];
            out[o + 2] = in[i + 2];
            out[o + 3] = in[i + 3];
            out[o + 4] = scale(in[i + 4], back);
            out[o + 5] = scale(in[i + 5], back);
            out[o + 6] = scale(in[i + 4], side);
            out[o + 7] = scale(in[i + 5], side);
        }
    }

    private static short scale(short v, float g) {
        if (g == 1f) return v;
        if (g == 0f) return 0;
        return (short) Math.round(v * g);
    }

    static String describe(Placement p, boolean expanded) {
        String how = expanded ? "（6 声道扩展为 7.1 输出）" : "";
        if (p == Placement.BOTH) {
            return String.format(Locale.ROOT, "侧面 + 后面，侧面 %.1f dB、后面 %.1f dB%s",
                    20 * Math.log10(BOTH_SIDE), 20 * Math.log10(BOTH_BACK), how);
        }
        return p.label + how;
    }
}
