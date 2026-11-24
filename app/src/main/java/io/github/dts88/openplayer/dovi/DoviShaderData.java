package io.github.dts88.openplayer.dovi;

import java.util.Arrays;

public final class DoviShaderData {
    public static final int PIECES = 8;
    public static final int VEC4_PER_PIECE = 6;
    public static final int COEF_VEC4 = 3 * PIECES * VEC4_PER_PIECE;
    public static final int PIVOT_VEC4 = 6;
    public static final int UNIFORM_VECTORS = 1 + 1 + 1 + PIVOT_VEC4 + COEF_VEC4 + 3 + 1 + 3;
    public static final float NO_PIVOT = 1e9f;

    public static final String U_SCALE = "uDvScale";
    public static final String U_LO = "uDvLo";
    public static final String U_HI = "uDvHi";
    public static final String U_PIVOTS = "uDvPivots";
    public static final String U_COEF = "uDvCoef";
    public static final String U_NONLINEAR = "uDvNonlinear";
    public static final String U_OFFSET = "uDvOffset";
    public static final String U_LINEAR = "uDvLinear";

    static final float[] LMS_TO_BT2020 = {
            3.064396767855625f, -0.65613130540261551f, 0.017360633500538487f,
            -2.165961226711146f, 1.7855694129834121f, -0.047244531564539943f,
            0.1015574148869036f, -0.12943951030147041f, 1.0298894699757705f};

    static final float PQ_M1 = 0.1593017578125f;
    static final float PQ_M2 = 78.84375f;
    static final float PQ_C1 = 0.8359375f;
    static final float PQ_C2 = 18.8515625f;
    static final float PQ_C3 = 18.6875f;

    public static final String GLSL = ""
            + "#ifndef GL_FRAGMENT_PRECISION_HIGH\n"
            + "#error Dolby Vision reshaping needs highp float\n"
            + "#endif\n"
            + "uniform highp float uDvScale;\n"
            + "uniform highp vec3 uDvLo;\n"
            + "uniform highp vec3 uDvHi;\n"
            + "uniform highp vec4 uDvPivots[6];\n"
            + "uniform highp vec4 uDvCoef[144];\n"
            + "uniform highp mat3 uDvNonlinear;\n"
            + "uniform highp vec3 uDvOffset;\n"
            + "uniform highp mat3 uDvLinear;\n"
            + "const highp mat3 DV_LMS_TO_BT2020 = mat3(\n"
            + "    3.064396767855625, -0.65613130540261551, 0.017360633500538487,\n"
            + "    -2.165961226711146, 1.7855694129834121, -0.047244531564539943,\n"
            + "    0.1015574148869036, -0.12943951030147041, 1.0298894699757705);\n"
            + "highp float dvCurve(highp vec3 sig, int c) {\n"
            + "    highp float s = sig[c];\n"
            + "    highp vec4 pa = uDvPivots[2 * c];\n"
            + "    highp vec4 pb = uDvPivots[2 * c + 1];\n"
            + "    int piece = int(dot(step(pa, vec4(s)), vec4(1.0)) + dot(step(pb.xyz, vec3(s)), vec3(1.0)));\n"
            + "    int b = (c * 8 + piece) * 6;\n"
            + "    highp vec4 k = uDvCoef[b];\n"
            + "    if (k.w == 0.0) {\n"
            + "        s = (k.z * s + k.y) * s + k.x;\n"
            + "    } else {\n"
            + "        highp vec4 sx = vec4(sig.xxy * sig.yzz, sig.x * sig.y * sig.z);\n"
            + "        highp vec3 sig2 = sig * sig;\n"
            + "        highp vec4 sx2 = sx * sx;\n"
            + "        s = uDvCoef[b + 2].w;\n"
            + "        s += dot(k.xyz, sig);\n"
            + "        s += dot(uDvCoef[b + 1], sx);\n"
            + "        s += dot(uDvCoef[b + 2].xyz, sig2);\n"
            + "        s += dot(uDvCoef[b + 3], sx2);\n"
            + "        s += dot(uDvCoef[b + 4].xyz, sig2 * sig);\n"
            + "        s += dot(uDvCoef[b + 5], sx2 * sx);\n"
            + "    }\n"
            + "    return clamp(s, 0.0, 1.0);\n"
            + "}\n"
            + "highp vec3 doviReshape(highp vec3 c) {\n"
            + "    highp vec3 sig = clamp(c * uDvScale, uDvLo, uDvHi);\n"
            + "    return vec3(dvCurve(sig, 0), dvCurve(sig, 1), dvCurve(sig, 2));\n"
            + "}\n"
            + "highp vec3 dvPqEotf(highp vec3 x) {\n"
            + "    x = pow(clamp(x, 0.0, 1.0), vec3(1.0 / 78.84375));\n"
            + "    x = max(x - 0.8359375, 0.0) / (18.8515625 - 18.6875 * x);\n"
            + "    return pow(x, vec3(1.0 / 0.1593017578125));\n"
            + "}\n"
            + "highp vec3 doviToLinearBt2020(highp vec3 c) {\n"
            + "    highp vec3 pq = uDvNonlinear * (c - uDvOffset);\n"
            + "    highp vec3 lms = uDvLinear * dvPqEotf(pq);\n"
            + "    return DV_LMS_TO_BT2020 * lms;\n"
            + "}\n";

    public float scale;
    public final float[] lo = new float[3];
    public final float[] hi = new float[3];
    public final float[] pivots = new float[PIVOT_VEC4 * 4];
    public final float[] coef = new float[COEF_VEC4 * 4];
    public final float[] nonlinear = new float[9];
    public final float[] offset = new float[3];
    public final float[] linear = new float[9];

    public double inputMaxCode;
    public boolean identity;
    public boolean hasMmr;
    public boolean colorFromRpu;

    private static final int[] DEFAULT_YCC_TO_RGB = {9575, 0, 14742, 9575, -1754, -4383, 9575, 17372, 0};
    private static final double[] DEFAULT_OFFSET = {1 / 16.0, 1 / 2.0, 1 / 2.0};
    private static final int[] DEFAULT_RGB_TO_LMS = {5845, 9702, 837, 2568, 12256, 1561, 0, 679, 15705};

    public static DoviShaderData from(Rpu rpu) {
        return new DoviShaderData().set(rpu, 0);
    }

    public DoviShaderData set(Rpu rpu) {
        return set(rpu, 0);
    }

    public DoviShaderData set(Rpu rpu, int containerProfile) {
        Rpu.Header h = rpu.header;
        int depth = h.blBitDepth;
        double maxCode = inputMaxCode > 0 ? inputMaxCode : (1 << depth) - 1;
        scale = (float) (maxCode / (1 << depth));

        Arrays.fill(pivots, NO_PIVOT);
        Arrays.fill(coef, 0f);
        identity = true;
        hasMmr = false;
        for (int c = 0; c < 3; c++) {
            Rpu.Curve curve = rpu.mapping.curves[c];
            int[] codes = curve.pivotCodes();
            int last = codes.length - 1;
            lo[c] = (float) codes[0] / (1 << depth);
            hi[c] = (float) codes[last] / (1 << depth);
            for (int i = 1; i < last && i <= PIECES - 1; i++) {
                pivots[c * 8 + i - 1] = (float) codes[i] / (1 << depth);
            }
            int count = Math.min(curve.pieces.length, PIECES);
            if (count != 1 || codes[0] != 0 || codes[last] < (1 << depth) - 1) identity = false;
            for (int i = 0; i < count; i++) {
                Rpu.Piece piece = curve.pieces[i];
                int at = (c * PIECES + i) * VEC4_PER_PIECE * 4;
                if (piece.mappingIdc == Rpu.Piece.POLYNOMIAL) {
                    for (int k = 0; k < piece.polyCoef.length && k < 3; k++) {
                        coef[at + k] = (float) h.coefficient(piece.polyCoef[k]);
                    }
                    if (coef[at] != 0f || coef[at + 1] != 1f || coef[at + 2] != 0f) identity = false;
                } else {
                    identity = false;
                    hasMmr = true;
                    int order = Math.min(piece.mmrOrder(), 3);
                    coef[at + 3] = order;
                    coef[at + 11] = (float) h.coefficient(piece.mmrConstant);
                    for (int j = 0; j < order; j++) {
                        long[] m = piece.mmrCoef[j];
                        int o = at + j * 8;
                        coef[o] = (float) h.coefficient(m[0]);
                        coef[o + 1] = (float) h.coefficient(m[1]);
                        coef[o + 2] = (float) h.coefficient(m[2]);
                        coef[o + 4] = (float) h.coefficient(m[3]);
                        coef[o + 5] = (float) h.coefficient(m[4]);
                        coef[o + 6] = (float) h.coefficient(m[5]);
                        coef[o + 7] = (float) h.coefficient(m[6]);
                    }
                }
            }
        }

        Rpu.Color color = rpu.dm == null ? null : rpu.dm.color;
        colorFromRpu = color != null;
        int[] yccToRgb = color != null ? color.yccToRgb : DEFAULT_YCC_TO_RGB;
        int[] rgbToLms = color != null ? color.rgbToLms : DEFAULT_RGB_TO_LMS;
        int profile = containerProfile > 0 ? containerProfile : rpu.profile();
        double offsetDenom = profile == 4 ? (double) (1L << 30) : (double) (1L << 28);
        for (int i = 0; i < 3; i++) {
            offset[i] = (float) (color != null ? color.yccToRgbOffset[i] / offsetDenom : DEFAULT_OFFSET[i]);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                nonlinear[col * 3 + row] = (float) (yccToRgb[row * 3 + col] / 8192.0);
                linear[col * 3 + row] = (float) (rgbToLms[row * 3 + col] / 16384.0);
            }
        }
        return this;
    }

    public boolean sameCurves(DoviShaderData other) {
        return other != null && scale == other.scale && Arrays.equals(lo, other.lo) && Arrays.equals(hi, other.hi)
                && Arrays.equals(pivots, other.pivots) && Arrays.equals(coef, other.coef);
    }

    public boolean sameColor(DoviShaderData other) {
        return other != null && Arrays.equals(nonlinear, other.nonlinear) && Arrays.equals(offset, other.offset)
                && Arrays.equals(linear, other.linear);
    }

    float curve(float[] sig, int c) {
        float s = sig[c];
        float[] pa = vec4(pivots, 2 * c);
        float[] pb = vec4(pivots, 2 * c + 1);
        int piece = (int) (dot(step(pa, vec4(s)), vec4(1f)) + dot(step(xyz(pb), vec3(s)), vec3(1f)));
        int b = (c * 8 + piece) * 6;
        float[] k = vec4(coef, b);
        if (k[3] == 0f) {
            s = (k[2] * s + k[1]) * s + k[0];
        } else {
            float[] sx = {sig[0] * sig[1], sig[0] * sig[2], sig[1] * sig[2], sig[0] * sig[1] * sig[2]};
            float[] sig2 = mul(sig, sig);
            float[] sx2 = mul(sx, sx);
            s = vec4(coef, b + 2)[3];
            s += dot(xyz(k), sig);
            s += dot(vec4(coef, b + 1), sx);
            s += dot(xyz(vec4(coef, b + 2)), sig2);
            s += dot(vec4(coef, b + 3), sx2);
            s += dot(xyz(vec4(coef, b + 4)), mul(sig2, sig));
            s += dot(vec4(coef, b + 5), mul(sx2, sx));
        }
        return clamp(s, 0f, 1f);
    }

    public float[] reshape(float[] c) {
        float[] sig = clamp(mul(c, scale), lo, hi);
        return new float[]{curve(sig, 0), curve(sig, 1), curve(sig, 2)};
    }

    static float[] pqEotf(float[] x) {
        x = pow(clamp(x, 0f, 1f), 1f / PQ_M2);
        x = div(max(sub(x, PQ_C1), 0f), sub(PQ_C2, mul(x, PQ_C3)));
        return pow(x, 1f / PQ_M1);
    }

    public float[] toLinearBt2020(float[] c) {
        float[] pq = mat3(nonlinear, sub(c, offset));
        float[] lms = mat3(linear, pqEotf(pq));
        return mat3(LMS_TO_BT2020, lms);
    }

    public static float pqEotf(float x) {
        return pqEotf(vec3(x))[0];
    }

    private static float[] vec3(float v) {
        return new float[]{v, v, v};
    }

    private static float[] vec4(float v) {
        return new float[]{v, v, v, v};
    }

    private static float[] vec4(float[] array, int index) {
        return new float[]{array[index * 4], array[index * 4 + 1], array[index * 4 + 2], array[index * 4 + 3]};
    }

    private static float[] xyz(float[] v) {
        return new float[]{v[0], v[1], v[2]};
    }

    private static float dot(float[] a, float[] b) {
        float sum = a[0] * b[0];
        for (int i = 1; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private static float[] step(float[] edge, float[] x) {
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = x[i] < edge[i] ? 0f : 1f;
        return out;
    }

    private static float[] mul(float[] a, float[] b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] * b[i];
        return out;
    }

    private static float[] mul(float[] a, float b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] * b;
        return out;
    }

    private static float[] div(float[] a, float[] b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] / b[i];
        return out;
    }

    private static float[] sub(float[] a, float[] b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] - b[i];
        return out;
    }

    private static float[] sub(float[] a, float b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] - b;
        return out;
    }

    private static float[] sub(float a, float[] b) {
        float[] out = new float[b.length];
        for (int i = 0; i < b.length; i++) out[i] = a - b[i];
        return out;
    }

    private static float[] max(float[] a, float b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = Math.max(a[i], b);
        return out;
    }

    private static float clamp(float x, float lo, float hi) {
        return Math.min(Math.max(x, lo), hi);
    }

    private static float[] clamp(float[] x, float lo, float hi) {
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = clamp(x[i], lo, hi);
        return out;
    }

    private static float[] clamp(float[] x, float[] lo, float[] hi) {
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = clamp(x[i], lo[i], hi[i]);
        return out;
    }

    private static float[] pow(float[] x, float y) {
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = (float) Math.pow(x[i], y);
        return out;
    }

    private static float[] mat3(float[] m, float[] v) {
        return new float[]{
                m[0] * v[0] + m[3] * v[1] + m[6] * v[2],
                m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
                m[2] * v[0] + m[5] * v[1] + m[8] * v[2]};
    }
}
