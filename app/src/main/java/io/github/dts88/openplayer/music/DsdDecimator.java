package io.github.dts88.openplayer.music;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class DsdDecimator {
    static final int SPAN = 24;
    static final double CUTOFF = 0.3628;
    static final double ATTENUATION_DB = 100;
    public static final float GAIN = 1.4f;
    public static final byte SILENCE = 0x69;

    private static final byte[] REVERSE = new byte[256];
    private static final Map<Integer, float[][]> TABLES = new HashMap<>();

    static {
        for (int v = 0; v < 256; v++) REVERSE[v] = (byte) (Integer.reverse(v) >>> 24);
    }

    private final int channels;
    private final int decimation;
    private final int bytesPerOutput;
    private final int outputRate;
    private final float[][] tables;
    private final byte[][] history;
    private final int[] position;
    private final int[] fadeDone;
    private final int fadeLength;

    public DsdDecimator(int channels, int dsdRate) {
        int out = outputRate(dsdRate);
        if (out == 0 || channels < 1) throw new IllegalArgumentException("DSD " + dsdRate + " Hz");
        this.channels = channels;
        this.outputRate = out;
        this.decimation = dsdRate / out;
        this.bytesPerOutput = decimation / 8;
        this.tables = tables(decimation);
        this.history = new byte[channels][tables.length * 2];
        this.position = new int[channels];
        this.fadeDone = new int[channels];
        this.fadeLength = out / 200;
        reset();
    }

    public static int outputRate(int dsdRate) {
        for (int base : new int[]{88200, 96000}) {
            if (dsdRate >= base * 32 && dsdRate % base == 0) {
                int d = dsdRate / base;
                if (d <= 512 && Integer.bitCount(d) == 1) return base;
            }
        }
        return 0;
    }

    public int outputRate() {
        return outputRate;
    }

    public int bytesPerOutput() {
        return bytesPerOutput;
    }

    public void reset() {
        for (int c = 0; c < channels; c++) {
            Arrays.fill(history[c], SILENCE);
            position[c] = 0;
            fadeDone[c] = 0;
        }
    }

    public int process(int channel, byte[] dsd, int offset, int stride, int count, boolean lsbFirst,
                       float[] out, int outOffset, int outStride) {
        byte[] h = history[channel];
        float[][] t = tables;
        int length = t.length;
        int p = position[channel];
        int faded = fadeDone[channel];
        int produced = count / bytesPerOutput;
        int at = offset;
        for (int n = 0; n < produced; n++) {
            for (int b = 0; b < bytesPerOutput; b++) {
                byte v = dsd[at];
                at += stride;
                if (lsbFirst) v = REVERSE[v & 0xFF];
                if (++p == length) p = 0;
                h[p] = v;
                h[p + length] = v;
            }
            int newest = p + length;
            float sum = 0f;
            for (int j = 0; j < length; j++) sum += t[j][h[newest - j] & 0xFF];
            sum *= GAIN;
            if (faded < fadeLength) sum *= (float) ++faded / fadeLength;
            out[outOffset + n * outStride] = sum;
        }
        position[channel] = p;
        fadeDone[channel] = faded;
        return produced;
    }

    private static synchronized float[][] tables(int decimation) {
        float[][] t = TABLES.get(decimation);
        if (t == null) {
            t = buildTables(design(decimation));
            TABLES.put(decimation, t);
        }
        return t;
    }

    static double[] design(int decimation) {
        int n = SPAN * decimation;
        double fc = CUTOFF / decimation;
        double beta = 0.1102 * (ATTENUATION_DB - 8.7);
        double i0Beta = besselI0(beta);
        double[] h = new double[n];
        double mid = (n - 1) / 2.0;
        double sum = 0;
        for (int k = 0; k < n; k++) {
            double x = k - mid;
            double sinc = x == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * x) / (Math.PI * x);
            double r = x / mid;
            double window = besselI0(beta * Math.sqrt(Math.max(0, 1 - r * r))) / i0Beta;
            h[k] = sinc * window;
            sum += h[k];
        }
        for (int k = 0; k < n; k++) h[k] /= sum;
        return h;
    }

    static float[][] buildTables(double[] h) {
        int length = h.length / 8;
        float[][] t = new float[length][256];
        for (int j = 0; j < length; j++) {
            for (int v = 0; v < 256; v++) {
                double sum = 0;
                for (int bit = 0; bit < 8; bit++) {
                    sum += ((v >> bit) & 1) != 0 ? h[8 * j + bit] : -h[8 * j + bit];
                }
                t[j][v] = (float) sum;
            }
        }
        return t;
    }

    private static double besselI0(double x) {
        double sum = 1;
        double term = 1;
        double half = x / 2;
        for (int k = 1; k < 60; k++) {
            term *= (half / k) * (half / k);
            sum += term;
            if (term < sum * 1e-17) break;
        }
        return sum;
    }
}
