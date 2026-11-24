package io.github.dts88.openplayer.hdr10plus;

import java.util.Arrays;
import java.util.Locale;

public final class Hdr10PlusMeta {
    public static final int HEADER_BYTES = 6;
    public static final int MAX_ANCHORS = 15;
    public static final int MAX_PERCENTILES = 15;
    public static final float NITS_PER_UNIT = 0.1f;

    public final int applicationVersion;
    public final int windows;
    public final int targetNits;
    public final int[] maxScl;
    public final int averageMaxRgb;
    public final int[] percentages;
    public final int[] percentiles;
    public final int fractionBright;
    public final boolean toneMapping;
    public final int kneeX;
    public final int kneeY;
    public final int[] anchors;
    public final boolean saturationMapping;
    public final int saturationWeight;
    public final int trailingBits;

    private Hdr10PlusMeta(int applicationVersion, int windows, int targetNits, int[] maxScl, int averageMaxRgb,
                          int[] percentages, int[] percentiles, int fractionBright, boolean toneMapping, int kneeX,
                          int kneeY, int[] anchors, boolean saturationMapping, int saturationWeight,
                          int trailingBits) {
        this.applicationVersion = applicationVersion;
        this.windows = windows;
        this.targetNits = targetNits;
        this.maxScl = maxScl;
        this.averageMaxRgb = averageMaxRgb;
        this.percentages = percentages;
        this.percentiles = percentiles;
        this.fractionBright = fractionBright;
        this.toneMapping = toneMapping;
        this.kneeX = kneeX;
        this.kneeY = kneeY;
        this.anchors = anchors;
        this.saturationMapping = saturationMapping;
        this.saturationWeight = saturationWeight;
        this.trailingBits = trailingBits;
    }

    public static boolean isHdr10Plus(byte[] d, int offset, int length) {
        return length > HEADER_BYTES && offset >= 0 && offset + length <= d.length
                && (d[offset] & 0xFF) == 0xB5 && d[offset + 1] == 0x00 && d[offset + 2] == 0x3C
                && d[offset + 3] == 0x00 && d[offset + 4] == 0x01 && d[offset + 5] == 0x04;
    }

    public static Hdr10PlusMeta parse(byte[] data, int offset, int length) throws Hdr10PlusException {
        if (!isHdr10Plus(data, offset, length)) return null;
        Bits b = new Bits(data, offset + HEADER_BYTES, length - HEADER_BYTES);
        int version = b.u(8);
        if (version > 1) throw new Hdr10PlusException("不认识的版本 " + version);
        int windows = b.u(2);
        if (windows < 1 || windows > 3) throw new Hdr10PlusException("区域个数 " + windows);
        for (int w = 1; w < windows; w++) b.skip(153);
        int target = b.u(27);
        if (target > 10000) throw new Hdr10PlusException("目标屏亮度 " + target + " 尼特");
        if (b.u(1) != 0) skipGrid(b);

        int[] maxScl = new int[3];
        int average = 0;
        int[] percentages = new int[0];
        int[] percentiles = new int[0];
        int fraction = 0;
        for (int w = 0; w < windows; w++) {
            int[] scl = {b.u(17), b.u(17), b.u(17)};
            int avg = b.u(17);
            int n = b.u(4);
            if (n > MAX_PERCENTILES) throw new Hdr10PlusException("分位点个数 " + n);
            int[] pa = new int[n];
            int[] pv = new int[n];
            for (int i = 0; i < n; i++) {
                pa[i] = b.u(7);
                pv[i] = b.u(17);
            }
            int frac = b.u(10);
            if (w == 0) {
                maxScl = scl;
                average = avg;
                percentages = pa;
                percentiles = pv;
                fraction = frac;
            }
        }
        for (int v : maxScl) {
            if (v > 100000) throw new Hdr10PlusException("场景最亮值 " + v);
        }
        if (average > 100000) throw new Hdr10PlusException("场景平均亮度 " + average);
        if (b.u(1) != 0) skipGrid(b);

        boolean tone = false;
        int kneeX = 0;
        int kneeY = 0;
        int[] anchors = new int[0];
        boolean saturation = false;
        int weight = 8;
        for (int w = 0; w < windows; w++) {
            boolean t = b.u(1) != 0;
            int kx = 0;
            int ky = 0;
            int[] a = new int[0];
            if (t) {
                kx = b.u(12);
                ky = b.u(12);
                int n = b.u(4);
                if (n > MAX_ANCHORS) throw new Hdr10PlusException("控制点个数 " + n);
                a = new int[n];
                for (int i = 0; i < n; i++) a[i] = b.u(10);
            }
            boolean s = b.u(1) != 0;
            int sw = s ? b.u(6) : 8;
            if (w == 0) {
                tone = t;
                kneeX = kx;
                kneeY = ky;
                anchors = a;
                saturation = s;
                weight = sw;
            }
        }
        return new Hdr10PlusMeta(version, windows, target, maxScl, average, percentages, percentiles, fraction, tone,
                kneeX, kneeY, anchors, saturation, weight, b.left());
    }

    private static void skipGrid(Bits b) throws Hdr10PlusException {
        int rows = b.u(5);
        int cols = b.u(5);
        if (rows < 2 || rows > 25 || cols < 2 || cols > 25) {
            throw new Hdr10PlusException("亮度表 " + rows + " × " + cols);
        }
        b.skip(rows * cols * 4);
    }

    public float maxSclNits() {
        return Math.max(maxScl[0], Math.max(maxScl[1], maxScl[2])) * NITS_PER_UNIT;
    }

    public float topPercentileNits() {
        int best = -1;
        int value = 0;
        for (int i = 0; i < percentages.length; i++) {
            if (percentages[i] >= 90 && percentages[i] >= best) {
                best = percentages[i];
                value = percentiles[i];
            }
        }
        return value * NITS_PER_UNIT;
    }

    public float sceneMaxNits() {
        float top = topPercentileNits();
        return top > 0f ? top : maxSclNits();
    }

    public float sceneAverageNits() {
        return averageMaxRgb * NITS_PER_UNIT;
    }

    public boolean hasCurve() {
        return toneMapping && anchors.length > 0 && targetNits > 0;
    }

    public boolean same(Hdr10PlusMeta o) {
        return o != null && applicationVersion == o.applicationVersion && windows == o.windows
                && targetNits == o.targetNits && averageMaxRgb == o.averageMaxRgb
                && fractionBright == o.fractionBright && toneMapping == o.toneMapping && kneeX == o.kneeX
                && kneeY == o.kneeY && saturationMapping == o.saturationMapping
                && saturationWeight == o.saturationWeight && Arrays.equals(maxScl, o.maxScl)
                && Arrays.equals(percentages, o.percentages) && Arrays.equals(percentiles, o.percentiles)
                && Arrays.equals(anchors, o.anchors);
    }

    public String describe() {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "场景最亮按 %.0f 尼特算（红、绿、蓝各自最亮 %.0f / %.0f / %.0f），平均 %.1f 尼特",
                sceneMaxNits(), maxScl[0] * NITS_PER_UNIT, maxScl[1] * NITS_PER_UNIT, maxScl[2] * NITS_PER_UNIT,
                sceneAverageNits()));
        if (toneMapping) {
            s.append(String.format(Locale.ROOT, "；参考曲线按 %d 尼特的屏，拐点 %.3f / %.3f，控制点 %d 个", targetNits,
                    kneeX / 4095f, kneeY / 4095f, anchors.length));
        } else {
            s.append("；没带参考曲线");
        }
        if (windows > 1) s.append("；分了 ").append(windows).append(" 个区域（只用第一个）");
        return s.toString();
    }

    private static final class Bits {
        private final byte[] data;
        private final int base;
        private final int end;
        private int pos;

        Bits(byte[] data, int offset, int length) {
            this.data = data;
            this.base = offset;
            this.end = length * 8;
        }

        int left() {
            return end - pos;
        }

        int u(int n) throws Hdr10PlusException {
            if (n > end - pos) {
                throw new Hdr10PlusException("数据不够：还要 " + n + " 位，只剩 " + (end - pos) + " 位");
            }
            int value = 0;
            int need = n;
            while (need > 0) {
                int inByte = 8 - (pos & 7);
                int take = Math.min(inByte, need);
                int chunk = ((data[base + (pos >>> 3)] & 0xFF) >>> (inByte - take)) & ((1 << take) - 1);
                value = (value << take) | chunk;
                pos += take;
                need -= take;
            }
            return value;
        }

        void skip(int n) throws Hdr10PlusException {
            if (n > end - pos) {
                throw new Hdr10PlusException("数据不够：要跳 " + n + " 位，只剩 " + (end - pos) + " 位");
            }
            pos += n;
        }
    }
}
