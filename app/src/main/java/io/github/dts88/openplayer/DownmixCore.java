package io.github.dts88.openplayer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

final class DownmixCore {
    static final float THRESHOLD = 0.8912509f;
    static final double LOOKAHEAD_SEC = 0.0015;
    static final double RELEASE_SEC = 0.08;
    private static final float M3DB = 0.70710678f;

    private static final int FL = 0, FR = 1, FC = 2, LFE = 3, BL = 4, BR = 5, SL = 6, SR = 7, BC = 8,
            TFL = 9, TFR = 10, TBL = 11, TBR = 12;

    private int channels = -1;
    private boolean lfe;
    private float[] toL = new float[0];
    private float[] toR = new float[0];
    private float gain = 1f;

    private int rate = -1;
    private int n;
    private float[] reqRing;
    private float[] boxRing;
    private float[] delayL;
    private float[] delayR;
    private int pos;
    private double boxSum;
    private float held = 1f;
    private float releaseK;

    boolean ensure(int ch, boolean withLfe, int sampleRate) {
        boolean changed = false;
        if (ch != channels || withLfe != lfe) {
            float[][] m = matrix(ch, withLfe);
            toL = m[0];
            toR = m[1];
            gain = (float) normalization(m[0], m[1]);
            channels = ch;
            lfe = withLfe;
            changed = true;
        }
        if (sampleRate != rate || reqRing == null) {
            rate = sampleRate;
            n = Math.max(8, (int) Math.round(LOOKAHEAD_SEC * sampleRate));
            reqRing = new float[n];
            boxRing = new float[n];
            delayL = new float[n];
            delayR = new float[n];
            releaseK = (float) (1 - Math.exp(-1.0 / (RELEASE_SEC * sampleRate)));
            resetLimiter();
        }
        return changed;
    }

    void resetLimiter() {
        if (reqRing == null) return;
        Arrays.fill(reqRing, 1f);
        Arrays.fill(boxRing, 1f);
        Arrays.fill(delayL, 0f);
        Arrays.fill(delayR, 0f);
        boxSum = n;
        held = 1f;
        pos = 0;
    }

    void invalidate() {
        channels = -1;
        rate = -1;
        reqRing = null;
    }

    int latencyFrames() {
        return n - 1;
    }

    float process(ByteBuffer src, int srcPos, int frames, ByteBuffer out) {
        final int ch = channels;
        final float[] cl = toL;
        final float[] cr = toR;
        final float g0 = gain;
        float minG = 1f;
        int p = srcPos;
        for (int f = 0; f < frames; f++) {
            float l = 0f;
            float r = 0f;
            for (int c = 0; c < ch; c++) {
                float s = src.getShort(p) * (1f / 32768f);
                p += 2;
                l += cl[c] * s;
                r += cr[c] * s;
            }
            l *= g0;
            r *= g0;

            float pk = Math.max(Math.abs(l), Math.abs(r));
            reqRing[pos] = pk > THRESHOLD ? THRESHOLD / pk : 1f;
            float h = 1f;
            for (int i = 0; i < n; i++) {
                float v = reqRing[i];
                if (v < h) h = v;
            }
            held = h < held ? h : held + (h - held) * releaseK;
            boxSum += held - boxRing[pos];
            boxRing[pos] = held;
            delayL[pos] = l;
            delayR[pos] = r;
            int next = pos + 1 == n ? 0 : pos + 1;
            float lg = (float) (boxSum / n);
            if (lg > 1f) lg = 1f;
            out.putShort(pcm16(delayL[next] * lg));
            out.putShort(pcm16(delayR[next] * lg));
            if (lg < minG) minG = lg;
            pos = next;
            if (pos == 0) {
                double s = 0;
                for (int i = 0; i < n; i++) s += boxRing[i];
                boxSum = s;
            }
        }
        return minG;
    }

    static boolean supports(int ch) {
        return roles(ch) != null;
    }

    static String describe(int ch, boolean withLfe) {
        float[][] m = matrix(ch, withLfe);
        double db = 20 * Math.log10(normalization(m[0], m[1]));
        return String.format(Locale.ROOT, Tx.t("%d 声道 → 2 声道，%s，归一 %.1f dB，限幅 −1 dBFS", "%d ch → 2 ch, %s, normalised %.1f dB, limiter −1 dBFS"),
                ch, withLfe ? Tx.t("含 LFE", "with LFE") : Tx.t("不含 LFE", "without LFE"), db);
    }

    static float[][] matrix(int ch, boolean withLfe) {
        float[] l = new float[ch];
        float[] r = new float[ch];
        int[] roles = roles(ch);
        if (roles != null) {
            for (int c = 0; c < ch; c++) {
                switch (roles[c]) {
                    case FL: l[c] = 1f; break;
                    case FR: r[c] = 1f; break;
                    case FC: l[c] = M3DB; r[c] = M3DB; break;
                    case LFE:
                        if (withLfe) {
                            l[c] = M3DB;
                            r[c] = M3DB;
                        }
                        break;
                    case BL:
                    case SL:
                    case TFL:
                    case TBL: l[c] = M3DB; break;
                    case BR:
                    case SR:
                    case TFR:
                    case TBR: r[c] = M3DB; break;
                    case BC: l[c] = 0.5f; r[c] = 0.5f; break;
                    default: break;
                }
            }
        }
        return new float[][]{l, r};
    }

    static double normalization(float[] l, float[] r) {
        double sl = 0;
        double sr = 0;
        for (int c = 0; c < l.length; c++) {
            sl += l[c] * l[c];
            sr += r[c] * r[c];
        }
        double mx = Math.max(sl, sr);
        return mx > 0 ? 1.0 / Math.sqrt(mx) : 1.0;
    }

    private static int[] roles(int ch) {
        switch (ch) {
            case 3: return new int[]{FL, FR, FC};
            case 4: return new int[]{FL, FR, BL, BR};
            case 5: return new int[]{FL, FR, FC, BL, BR};
            case 6: return new int[]{FL, FR, FC, LFE, BL, BR};
            case 7: return new int[]{FL, FR, FC, LFE, BL, BR, BC};
            case 8: return new int[]{FL, FR, FC, LFE, BL, BR, SL, SR};
            case 10: return new int[]{FL, FR, FC, LFE, BL, BR, TFL, TFR, TBL, TBR};
            case 12: return new int[]{FL, FR, FC, LFE, BL, BR, SL, SR, TFL, TFR, TBL, TBR};
            default: return null;
        }
    }

    static short pcm16(float v) {
        int s = Math.round(v * 32767f);
        if (s > 32767) s = 32767;
        else if (s < -32768) s = -32768;
        return (short) s;
    }
}
