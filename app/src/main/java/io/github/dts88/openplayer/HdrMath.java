package io.github.dts88.openplayer;

import io.github.dts88.openplayer.hdr10plus.Hdr10PlusCurve;

final class HdrMath {
    static final int SDR = 0;
    static final int PQ = 1;
    static final int HLG = 2;
    static final int DOVI = 3;

    static final float REFERENCE_WHITE_NITS = 203f;
    static final float HLG_PEAK_NITS = 1000f;
    static final float SDR_GAMMA = 2.2f;

    private static final double M1 = 2610.0 / 16384.0;
    private static final double M2 = 2523.0 / 4096.0 * 128.0;
    private static final double C1 = 3424.0 / 4096.0;
    private static final double C2 = 2413.0 / 4096.0 * 32.0;
    private static final double C3 = 2392.0 / 4096.0 * 32.0;

    private static final double HLG_A = 0.17883277;
    private static final double HLG_B = 0.28466892;
    private static final double HLG_C = 0.55991073;

    static final float[] LUMA_2020 = {0.2627f, 0.6780f, 0.0593f};
    static final float[] LUMA_709 = {0.2126f, 0.7152f, 0.0722f};

    static final float[] BT2020_TO_BT709 = {
            1.660491f, -0.124550f, -0.018151f,
            -0.587641f, 1.132900f, -0.100579f,
            -0.072850f, -0.008349f, 1.118730f};
    static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    static final double[] BT709_XY = {0.640, 0.330, 0.300, 0.600, 0.150, 0.060, 0.3127, 0.3290};
    static final double[] BT2020_XY = {0.708, 0.292, 0.170, 0.797, 0.131, 0.046, 0.3127, 0.3290};
    static final double[] PANEL_XY = {0.680, 0.320, 0.265, 0.690, 0.150, 0.060, 0.3127, 0.3290};

    static final float[] BT2020_TO_PANEL = conversion(BT2020_XY, PANEL_XY);
    static final float[] BT709_TO_PANEL = conversion(BT709_XY, PANEL_XY);

    private HdrMath() {
    }

    static double pqToLinear(double code) {
        double p = Math.pow(Math.max(code, 0.0), 1.0 / M2);
        return Math.pow(Math.max(p - C1, 0.0) / (C2 - C3 * p), 1.0 / M1);
    }

    static double linearToPq(double linear) {
        double p = Math.pow(Math.max(linear, 0.0), M1);
        return Math.pow((C1 + C2 * p) / (1.0 + C3 * p), M2);
    }

    static double nitsToPq(double nits) {
        return linearToPq(nits / 10000.0);
    }

    static double hlgToScene(double code) {
        double e = Math.max(code, 0.0);
        return e <= 0.5 ? e * e / 3.0 : (Math.exp((e - HLG_C) / HLG_A) + HLG_B) / 12.0;
    }

    static double eetf(double code, double srcMax, double dstMax) {
        if (dstMax >= srcMax) return Math.min(code, dstMax);
        double e1 = Math.min(code / srcMax, 1.0);
        double maxLum = dstMax / srcMax;
        double knee = 1.5 * maxLum - 0.5;
        if (e1 < knee) return e1 * srcMax;
        double t = (e1 - knee) / (1.0 - knee);
        double t2 = t * t;
        double t3 = t2 * t;
        double e2 = (2.0 * t3 - 3.0 * t2 + 1.0) * knee + (t3 - 2.0 * t2 + t) * (1.0 - knee)
                + (-2.0 * t3 + 3.0 * t2) * maxLum;
        return e2 * srcMax;
    }

    static double[] toLinear(double r, double g, double b, int transfer) {
        if (transfer == HLG) {
            double sr = hlgToScene(r);
            double sg = hlgToScene(g);
            double sb = hlgToScene(b);
            double ys = LUMA_2020[0] * sr + LUMA_2020[1] * sg + LUMA_2020[2] * sb;
            double gain = HLG_PEAK_NITS / 10000.0 * Math.pow(Math.max(ys, 1e-6), 0.2);
            return new double[]{sr * gain, sg * gain, sb * gain};
        }
        return new double[]{pqToLinear(r), pqToLinear(g), pqToLinear(b)};
    }

    static float[] linearToSdr(double r, double g, double b, float srcPeakNits, float dstPeakNits) {
        return linearToSdr(r, g, b, srcPeakNits, dstPeakNits, null);
    }

    static float[] linearToSdr(double r, double g, double b, float srcPeakNits, float dstPeakNits,
                               Hdr10PlusCurve curve) {
        return linearToSdr(r, g, b, srcPeakNits, dstPeakNits, curve, BT2020_TO_BT709);
    }

    static float[] linearToSdr(double r, double g, double b, float srcPeakNits, float dstPeakNits,
                               Hdr10PlusCurve curve, float[] gamut) {
        double peak = dstPeakNits / 10000.0;
        double m = Math.max(r, Math.max(g, b));
        if (m > 0) {
            double scale = curve != null && curve.mode == Hdr10PlusCurve.BEZIER
                    ? curve.map((float) m) / m
                    : pqToLinear(eetf(linearToPq(m), nitsToPq(srcPeakNits), nitsToPq(dstPeakNits))) / m;
            r *= scale;
            g *= scale;
            b *= scale;
        }
        double y = LUMA_2020[0] * r + LUMA_2020[1] * g + LUMA_2020[2] * b;
        return fit(gamut, r, g, b, y, peak);
    }

    static float[] fit(float[] k, double r, double g, double b, double y, double peak) {
        double or = k[0] * r + k[3] * g + k[6] * b;
        double og = k[1] * r + k[4] * g + k[7] * b;
        double ob = k[2] * r + k[5] * g + k[8] * b;
        double lo = Math.min(or, Math.min(og, ob));
        if (lo < 0) {
            double t = y / (y - lo);
            or = y + (or - y) * t;
            og = y + (og - y) * t;
            ob = y + (ob - y) * t;
        }
        double hi = Math.max(or, Math.max(og, ob));
        if (hi > peak && hi > y) {
            double t = Math.max(peak - y, 0.0) / (hi - y);
            or = y + (or - y) * t;
            og = y + (og - y) * t;
            ob = y + (ob - y) * t;
        }
        return new float[]{encode(or / peak), encode(og / peak), encode(ob / peak)};
    }

    static float[] toSdr(double r, double g, double b, int transfer, float srcPeakNits, float dstPeakNits) {
        return toSdr(r, g, b, transfer, srcPeakNits, dstPeakNits, null);
    }

    static float[] toSdr(double r, double g, double b, int transfer, float srcPeakNits, float dstPeakNits,
                         Hdr10PlusCurve curve) {
        return toSdr(r, g, b, transfer, srcPeakNits, dstPeakNits, curve, BT2020_TO_BT709);
    }

    static float[] toSdr(double r, double g, double b, int transfer, float srcPeakNits, float dstPeakNits,
                         Hdr10PlusCurve curve, float[] gamut) {
        double[] lin = toLinear(r, g, b, transfer);
        return linearToSdr(lin[0], lin[1], lin[2], srcPeakNits, dstPeakNits, curve, gamut);
    }

    private static float encode(double v) {
        return (float) Math.pow(Math.max(0.0, Math.min(1.0, v)), 1.0 / SDR_GAMMA);
    }

    static float[] sdrToGamut(double r, double g, double b, float[] gamut) {
        double lr = Math.pow(Math.max(0.0, Math.min(1.0, r)), SDR_GAMMA);
        double lg = Math.pow(Math.max(0.0, Math.min(1.0, g)), SDR_GAMMA);
        double lb = Math.pow(Math.max(0.0, Math.min(1.0, b)), SDR_GAMMA);
        return new float[]{
                encode(gamut[0] * lr + gamut[3] * lg + gamut[6] * lb),
                encode(gamut[1] * lr + gamut[4] * lg + gamut[7] * lb),
                encode(gamut[2] * lr + gamut[5] * lg + gamut[8] * lb)};
    }

    static double[] rgbToXyz(double[] xy) {
        double[] p = new double[9];
        for (int i = 0; i < 3; i++) {
            double x = xy[2 * i];
            double y = xy[2 * i + 1];
            p[i] = x / y;
            p[3 + i] = 1.0;
            p[6 + i] = (1.0 - x - y) / y;
        }
        double wx = xy[6];
        double wy = xy[7];
        double[] s = times(invert(p), new double[]{wx / wy, 1.0, (1.0 - wx - wy) / wy});
        double[] m = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) m[3 * r + c] = p[3 * r + c] * s[c];
        }
        return m;
    }

    static float[] conversion(double[] fromXy, double[] toXy) {
        double[] a = invert(rgbToXyz(toXy));
        double[] b = rgbToXyz(fromXy);
        float[] k = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double v = a[3 * r] * b[c] + a[3 * r + 1] * b[3 + c] + a[3 * r + 2] * b[6 + c];
                k[3 * c + r] = (float) v;
            }
        }
        return k;
    }

    static double[] invert(double[] a) {
        double det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6])
                + a[2] * (a[3] * a[7] - a[4] * a[6]);
        double[] r = {
                a[4] * a[8] - a[5] * a[7], a[2] * a[7] - a[1] * a[8], a[1] * a[5] - a[2] * a[4],
                a[5] * a[6] - a[3] * a[8], a[0] * a[8] - a[2] * a[6], a[2] * a[3] - a[0] * a[5],
                a[3] * a[7] - a[4] * a[6], a[1] * a[6] - a[0] * a[7], a[0] * a[4] - a[1] * a[3]};
        for (int i = 0; i < 9; i++) r[i] /= det;
        return r;
    }

    private static double[] times(double[] m, double[] v) {
        return new double[]{
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    static int[] staticPeaks(byte[] info) {
        if (info == null || info.length < 25) return new int[]{0, 0};
        return new int[]{u16(info, 17), u16(info, 21)};
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
    }

    static float sourcePeakNits(int masteringMax, int maxCll) {
        if (maxCll >= 100) return Math.min(maxCll, 4000);
        if (masteringMax >= 100) return Math.min(masteringMax, 1000);
        return 1000f;
    }
}
