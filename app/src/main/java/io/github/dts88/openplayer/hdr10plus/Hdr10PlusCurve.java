package io.github.dts88.openplayer.hdr10plus;

import io.github.dts88.openplayer.Tx;

import java.util.Locale;

public final class Hdr10PlusCurve {
    public static final int NONE = 0;
    public static final int PEAK = 1;
    public static final int BEZIER = 2;

    public static final int DEGREE = 16;

    private static final double[] BINOMIAL = {1, 16, 120, 560, 1820, 4368, 8008, 11440, 12870, 11440, 8008, 4368,
            1820, 560, 120, 16, 1};

    public int mode = NONE;
    public float sceneMaxNits;
    public float sceneAverageNits;
    public float dstPeakNits;
    public float targetNits;
    public float kneeX;
    public float kneeY;
    public final float[] weights = new float[DEGREE + 1];
    double[] control = new double[0];

    public void set(Hdr10PlusMeta m, float dstPeakNits) {
        this.dstPeakNits = dstPeakNits;
        targetNits = 0f;
        control = new double[0];
        float s = m == null ? 0f : m.sceneMaxNits();
        if (!(s > 0f) || !(dstPeakNits > 0f)) {
            mode = NONE;
            sceneMaxNits = 0f;
            sceneAverageNits = 0f;
            return;
        }
        s = Math.min(s, 10000f);
        sceneMaxNits = s;
        sceneAverageNits = m.sceneAverageNits();
        if (s <= dstPeakNits || !m.hasCurve() || s <= m.targetNits) {
            mode = PEAK;
            return;
        }

        int n = m.anchors.length + 1;
        double[] p = new double[n + 1];
        p[0] = 0.0;
        p[n] = 1.0;
        for (int i = 1; i < n; i++) p[i] = clamp01(m.anchors[i - 1] / 1023.0);
        double kx = clamp01(m.kneeX / 4095.0);
        double ky = clamp01(m.kneeY / 4095.0);
        double d = dstPeakNits;
        double t = m.targetNits;
        targetNits = m.targetNits;
        if (d < t) {
            double u = Math.max(0.0, d / t);
            kx *= u;
            ky *= u;
            double beta = n * kx / (1.0 - kx);
            double kxy = Math.min(kx * s / d, beta / (beta + 1.0));
            ky = mix(kxy, ky, u);
            for (int i = 2; i <= n; i++) p[i] = mix(1.0, p[i], u);
            p[1] = mix(intercept(n, kx, ky), p[1], u);
        } else if (d > t) {
            double w = Math.pow(Math.max(0.0, 1.0 - (d - t) / (s - t)), 1.4);
            ky *= t / d;
            double kxy = kx * d / s;
            ky = mix(kxy, ky, w);
            for (int i = 2; i < n; i++) p[i] = mix((double) i / n, p[i], w);
            p[1] = mix(intercept(n, kx, ky), p[1], w);
        }
        kx = clamp01(kx);
        ky = clamp01(ky);
        for (int i = 0; i <= n; i++) p[i] = clamp01(p[i]);
        control = p;
        kneeX = (float) kx;
        kneeY = (float) ky;

        double[] q = p;
        for (int deg = n; deg < DEGREE; deg++) {
            double[] r = new double[deg + 2];
            r[0] = q[0];
            r[deg + 1] = q[deg];
            for (int i = 1; i <= deg; i++) {
                double a = i / (double) (deg + 1);
                r[i] = a * q[i - 1] + (1.0 - a) * q[i];
            }
            q = r;
        }
        for (int i = 0; i <= DEGREE; i++) weights[i] = (float) (BINOMIAL[i] * q[i]);
        mode = BEZIER;
    }

    static double intercept(int n, double kx, double ky) {
        if (kx <= 0.0 || ky >= 1.0) return 1.0 / n;
        double slope = ky / kx * (1.0 - kx) / (1.0 - ky);
        return Math.min(slope / n, 1.0);
    }

    private static double mix(double a, double b, double t) {
        return a * (1.0 - t) + b * t;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
    }

    public float bezier(float t) {
        float[] w = weights;
        float s = 1f - t;
        float acc;
        if (t <= 0.5f) {
            float u = t / s;
            acc = w[DEGREE];
            for (int i = DEGREE - 1; i >= 0; i--) acc = acc * u + w[i];
            float s2 = s * s;
            float s4 = s2 * s2;
            float s8 = s4 * s4;
            return acc * s8 * s8;
        }
        float u = s / t;
        acc = w[0];
        for (int i = 1; i <= DEGREE; i++) acc = acc * u + w[i];
        float t2 = t * t;
        float t4 = t2 * t2;
        float t8 = t4 * t4;
        return acc * t8 * t8;
    }

    public float map(float m) {
        float v = Math.min(m / (sceneMaxNits / 10000f), 1f);
        float y;
        if (v <= kneeX && kneeX > 0f) {
            y = v * kneeY / kneeX;
        } else {
            y = kneeY + (1f - kneeY) * bezier((v - kneeX) / (1f - kneeX));
        }
        return y * (dstPeakNits / 10000f);
    }

    double referenceBezier(double t) {
        int n = control.length - 1;
        double sum = 0;
        for (int p = 0; p <= n; p++) {
            sum += binomial(n, p) * Math.pow(t, p) * Math.pow(1.0 - t, n - p) * control[p];
        }
        return sum;
    }

    double reference(double x) {
        double kx = kneeX;
        double ky = kneeY;
        if (x <= kx && kx > 0) return x * ky / kx;
        return ky + (1.0 - ky) * referenceBezier((x - kx) / (1.0 - kx));
    }

    private static double binomial(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    public String describe() {
        if (mode == NONE) return "";
        String peak = String.format(Locale.ROOT, Tx.t("本场景峰值 %.0f 尼特", "scene peak %.0f nits"), sceneMaxNits);
        if (mode == BEZIER) return peak + Tx.t("，按片源曲线", ", source curve");
        return sceneMaxNits <= dstPeakNits ? peak + Tx.t("，原样显示", ", shown as is") : peak;
    }
}
