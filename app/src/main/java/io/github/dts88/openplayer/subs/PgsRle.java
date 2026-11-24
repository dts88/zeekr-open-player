package io.github.dts88.openplayer.subs;

import java.util.Arrays;

final class PgsRle {
    static final int MAX_PIXELS = 4096 * 2304;

    private PgsRle() {
    }

    static byte[] decode(byte[] rle, int offset, int length, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return null;
        int total = width * height;
        byte[] px = new byte[total];
        int p = offset;
        int end = offset + length;
        int i = 0;
        while (p < end && i < total) {
            int c = rle[p++] & 0xFF;
            if (c != 0) {
                px[i++] = (byte) c;
                continue;
            }
            if (p >= end) return null;
            int s = rle[p++] & 0xFF;
            if (s == 0) {
                int rest = i % width;
                if (rest != 0) i += width - rest;
                continue;
            }
            int run = s & 0x3F;
            if ((s & 0x40) != 0) {
                if (p >= end) return null;
                run = (run << 8) | (rle[p++] & 0xFF);
            }
            int color = 0;
            if ((s & 0x80) != 0) {
                if (p >= end) return null;
                color = rle[p++] & 0xFF;
            }
            int n = Math.min(run, total - i);
            if (color != 0) Arrays.fill(px, i, i + n, (byte) color);
            i += n;
        }
        return px;
    }

    static byte[] crop(byte[] px, int width, int x, int y, int w, int h) {
        byte[] out = new byte[w * h];
        for (int row = 0; row < h; row++) {
            System.arraycopy(px, (y + row) * width + x, out, row * w, w);
        }
        return out;
    }

    static byte[] encode(byte[] px, int width, int height) {
        byte[] out = new byte[2 * width * height + 2 * height];
        int o = 0;
        for (int row = 0; row < height; row++) {
            int x = 0;
            int base = row * width;
            while (x < width) {
                int c = px[base + x] & 0xFF;
                int run = 1;
                while (x + run < width && run < 0x3FFF && (px[base + x + run] & 0xFF) == c) run++;
                if (c != 0 && run <= 2) {
                    out[o++] = (byte) c;
                    if (run == 2) out[o++] = (byte) c;
                } else {
                    out[o++] = 0;
                    int flags = c != 0 ? 0x80 : 0;
                    if (run <= 0x3F) {
                        out[o++] = (byte) (flags | run);
                    } else {
                        out[o++] = (byte) (flags | 0x40 | (run >> 8));
                        out[o++] = (byte) run;
                    }
                    if (c != 0) out[o++] = (byte) c;
                }
                x += run;
            }
            out[o++] = 0;
            out[o++] = 0;
        }
        return Arrays.copyOf(out, o);
    }
}
