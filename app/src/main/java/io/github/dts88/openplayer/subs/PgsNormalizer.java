package io.github.dts88.openplayer.subs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PgsNormalizer {
    public interface Sink {
        void displaySet(byte[] data, int length);
    }

    static final int SEGMENT_PALETTE = 0x14;
    static final int SEGMENT_OBJECT = 0x15;
    static final int SEGMENT_COMPOSITION = 0x16;
    static final int SEGMENT_WINDOW = 0x17;
    static final int SEGMENT_END = 0x80;

    private static final int MAX_OBJECT_BYTES = 8 << 20;
    private static final int MAX_SIDE = 8192;
    private static final int MAX_SEGMENT = 0xFFFF;

    private static final class PgsObject {
        int width;
        int height;
        byte[] rle;
        int filled;
        boolean complete;
    }

    private static final class Placement {
        int objectId;
        int windowId;
        boolean forced;
        boolean cropped;
        int x;
        int y;
        int cropX;
        int cropY;
        int cropW;
        int cropH;
    }

    private final Map<Integer, int[]> palettes = new HashMap<>();
    private final Map<Integer, PgsObject> objects = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private boolean haveComposition;
    private int videoWidth;
    private int videoHeight;
    private int compositionNumber;
    private int paletteId;
    private int assemblingId = -1;
    private boolean objectInThisSet;

    private byte[] out = new byte[8192];
    private int outSize;

    private int sets;
    private int shown;
    private int cleared;
    private int multiObject;
    private int reused;
    private int croppedObjects;
    private int missingObjects;

    public void reset() {
        palettes.clear();
        objects.clear();
        placements.clear();
        haveComposition = false;
        assemblingId = -1;
        objectInThisSet = false;
    }

    public void feed(byte[] data, int offset, int length, Sink sink) {
        int p = offset;
        int end = offset + length;
        while (p + 3 <= end) {
            int type = data[p] & 0xFF;
            int size = u16(data, p + 1);
            int body = p + 3;
            if (body + size > end) break;
            switch (type) {
                case SEGMENT_COMPOSITION:
                    composition(data, body, size);
                    break;
                case SEGMENT_PALETTE:
                    palette(data, body, size);
                    break;
                case SEGMENT_OBJECT:
                    object(data, body, size);
                    break;
                case SEGMENT_END:
                    end(sink);
                    break;
                default:
                    break;
            }
            p = body + size;
        }
    }

    private void composition(byte[] d, int p, int size) {
        haveComposition = false;
        placements.clear();
        assemblingId = -1;
        objectInThisSet = false;
        if (size < 11) return;
        videoWidth = u16(d, p);
        videoHeight = u16(d, p + 2);
        compositionNumber = u16(d, p + 5);
        int state = d[p + 7] & 0xC0;
        paletteId = d[p + 9] & 0xFF;
        int count = d[p + 10] & 0xFF;
        if (state != 0) {
            palettes.clear();
            objects.clear();
        }
        int q = p + 11;
        int end = p + size;
        for (int i = 0; i < count && q + 8 <= end; i++) {
            Placement pl = new Placement();
            pl.objectId = u16(d, q);
            pl.windowId = d[q + 2] & 0xFF;
            int flags = d[q + 3] & 0xFF;
            pl.cropped = (flags & 0x80) != 0;
            pl.forced = (flags & 0x40) != 0;
            pl.x = u16(d, q + 4);
            pl.y = u16(d, q + 6);
            q += 8;
            if (pl.cropped) {
                if (q + 8 > end) {
                    pl.cropped = false;
                } else {
                    pl.cropX = u16(d, q);
                    pl.cropY = u16(d, q + 2);
                    pl.cropW = u16(d, q + 4);
                    pl.cropH = u16(d, q + 6);
                    q += 8;
                }
            }
            placements.add(pl);
        }
        haveComposition = videoWidth > 0 && videoHeight > 0;
    }

    private void palette(byte[] d, int p, int size) {
        if (size < 2) return;
        int id = d[p] & 0xFF;
        int[] pal = palettes.get(id);
        if (pal == null) {
            pal = new int[256];
            palettes.put(id, pal);
        }
        for (int q = p + 2; q + 5 <= p + size; q += 5) {
            pal[d[q] & 0xFF] = ((d[q + 1] & 0xFF) << 24) | ((d[q + 2] & 0xFF) << 16)
                    | ((d[q + 3] & 0xFF) << 8) | (d[q + 4] & 0xFF);
        }
    }

    private void object(byte[] d, int p, int size) {
        if (size < 4) return;
        int id = u16(d, p);
        int flags = d[p + 3] & 0xFF;
        int q = p + 4;
        int end = p + size;
        PgsObject o;
        if ((flags & 0x80) != 0) {
            assemblingId = -1;
            objects.remove(id);
            if (size < 11) return;
            int total = u24(d, q) - 4;
            int w = u16(d, q + 3);
            int h = u16(d, q + 5);
            q += 7;
            if (total <= 0 || total > MAX_OBJECT_BYTES || w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE) return;
            o = new PgsObject();
            o.width = w;
            o.height = h;
            o.rle = new byte[total];
            objects.put(id, o);
            assemblingId = id;
        } else {
            if (assemblingId != id) return;
            o = objects.get(id);
            if (o == null) return;
        }
        int n = Math.min(end - q, o.rle.length - o.filled);
        System.arraycopy(d, q, o.rle, o.filled, n);
        o.filled += n;
        if (o.filled == o.rle.length) {
            o.complete = true;
            objectInThisSet = true;
            assemblingId = -1;
        } else if ((flags & 0x40) != 0) {
            objects.remove(id);
            assemblingId = -1;
        }
    }

    private void end(Sink sink) {
        if (!haveComposition) return;
        haveComposition = false;
        outSize = 0;
        int drawn = 0;
        int[] pal = palettes.get(paletteId);
        if (pal != null) {
            byte[] paletteSegment = null;
            for (Placement pl : placements) {
                PgsObject o = objects.get(pl.objectId);
                if (o == null || !o.complete) {
                    missingObjects++;
                    continue;
                }
                byte[] rle = o.rle;
                int w = o.width;
                int h = o.height;
                if (pl.cropped && pl.cropW > 0 && pl.cropH > 0
                        && pl.cropX + pl.cropW <= w && pl.cropY + pl.cropH <= h
                        && (pl.cropW != w || pl.cropH != h)) {
                    byte[] px = PgsRle.decode(rle, 0, rle.length, w, h);
                    if (px != null) {
                        rle = PgsRle.encode(PgsRle.crop(px, w, pl.cropX, pl.cropY, pl.cropW, pl.cropH),
                                pl.cropW, pl.cropH);
                        w = pl.cropW;
                        h = pl.cropH;
                        croppedObjects++;
                    }
                }
                if (paletteSegment == null) paletteSegment = paletteSegment(pal, paletteId, videoHeight > 576);
                writeComposition(pl);
                write(paletteSegment, 0, paletteSegment.length);
                writeObject(pl.objectId, rle, w, h);
                writeEnd();
                drawn++;
            }
        }
        if (drawn == 0) writeEnd();
        sets++;
        if (drawn == 0) {
            cleared++;
        } else {
            shown++;
            if (drawn > 1) multiObject++;
            if (!objectInThisSet) reused++;
        }
        objectInThisSet = false;
        sink.displaySet(out, outSize);
    }

    private void writeComposition(Placement pl) {
        ensure(3 + 19);
        out[outSize++] = (byte) SEGMENT_COMPOSITION;
        put16(19);
        put16(videoWidth);
        put16(videoHeight);
        out[outSize++] = 0x10;
        put16(compositionNumber);
        out[outSize++] = (byte) 0x80;
        out[outSize++] = 0;
        out[outSize++] = (byte) paletteId;
        out[outSize++] = 1;
        put16(pl.objectId);
        out[outSize++] = (byte) pl.windowId;
        out[outSize++] = (byte) (pl.forced ? 0x40 : 0);
        put16(pl.x);
        put16(pl.y);
    }

    private void writeObject(int id, byte[] rle, int w, int h) {
        int at = 0;
        boolean first = true;
        do {
            int room = MAX_SEGMENT - (first ? 11 : 4);
            int n = Math.min(room, rle.length - at);
            boolean last = at + n == rle.length;
            int size = (first ? 11 : 4) + n;
            ensure(3 + size);
            out[outSize++] = (byte) SEGMENT_OBJECT;
            put16(size);
            put16(id);
            out[outSize++] = 0;
            out[outSize++] = (byte) ((first ? 0x80 : 0) | (last ? 0x40 : 0));
            if (first) {
                int total = rle.length + 4;
                out[outSize++] = (byte) (total >> 16);
                out[outSize++] = (byte) (total >> 8);
                out[outSize++] = (byte) total;
                put16(w);
                put16(h);
            }
            System.arraycopy(rle, at, out, outSize, n);
            outSize += n;
            at += n;
            first = false;
        } while (at < rle.length);
    }

    private void writeEnd() {
        ensure(3);
        out[outSize++] = (byte) SEGMENT_END;
        out[outSize++] = 0;
        out[outSize++] = 0;
    }

    private void write(byte[] src, int offset, int length) {
        ensure(length);
        System.arraycopy(src, offset, out, outSize, length);
        outSize += length;
    }

    private void put16(int v) {
        out[outSize++] = (byte) (v >> 8);
        out[outSize++] = (byte) v;
    }

    private void ensure(int more) {
        if (outSize + more <= out.length) return;
        byte[] bigger = new byte[Math.max(out.length * 2, outSize + more)];
        System.arraycopy(out, 0, bigger, 0, outSize);
        out = bigger;
    }

    static byte[] paletteSegment(int[] pal, int id, boolean hd) {
        int n = 0;
        for (int e : pal) {
            if ((e & 0xFF) != 0) n++;
        }
        byte[] seg = new byte[3 + 2 + 5 * n];
        int size = 2 + 5 * n;
        seg[0] = (byte) SEGMENT_PALETTE;
        seg[1] = (byte) (size >> 8);
        seg[2] = (byte) size;
        seg[3] = (byte) id;
        seg[4] = 0;
        int o = 5;
        for (int i = 0; i < 256; i++) {
            int e = pal[i];
            if ((e & 0xFF) == 0) continue;
            int ycc = forParser(toRgb((e >>> 24) & 0xFF, (e >> 16) & 0xFF, (e >> 8) & 0xFF, hd));
            seg[o++] = (byte) i;
            seg[o++] = (byte) (ycc >> 16);
            seg[o++] = (byte) (ycc >> 8);
            seg[o++] = (byte) ycc;
            seg[o++] = (byte) e;
        }
        return seg;
    }

    static int toRgb(int y, int cr, int cb, boolean hd) {
        double yn = (y - 16) / 219.0;
        double pb = (cb - 128) / 224.0;
        double pr = (cr - 128) / 224.0;
        double r;
        double g;
        double b;
        if (hd) {
            r = yn + 1.5748 * pr;
            g = yn - 0.1873 * pb - 0.4681 * pr;
            b = yn + 1.8556 * pb;
        } else {
            r = yn + 1.402 * pr;
            g = yn - 0.344136 * pb - 0.714136 * pr;
            b = yn + 1.772 * pb;
        }
        return (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    static int parserRgb(int y, int cr, int cb) {
        int r = (int) (y + (1.40200 * (cr - 128)));
        int g = (int) (y - (0.34414 * (cb - 128)) - (0.71414 * (cr - 128)));
        int b = (int) (y + (1.77200 * (cb - 128)));
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int forParser(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double y0 = 0.299 * r + 0.587 * g + 0.114 * b;
        int yc = (int) Math.round(y0);
        int crc = (int) Math.round(128 + (r - y0) / 1.402);
        int cbc = (int) Math.round(128 + (b - y0) / 1.772);
        int best = 0;
        int bestError = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dcr = -1; dcr <= 1; dcr++) {
                for (int dcb = -1; dcb <= 1; dcb++) {
                    int y = clamp(yc + dy);
                    int cr = clamp(crc + dcr);
                    int cb = clamp(cbc + dcb);
                    int got = parserRgb(y, cr, cb);
                    int er = Math.abs(((got >> 16) & 0xFF) - r);
                    int eg = Math.abs(((got >> 8) & 0xFF) - g);
                    int eb = Math.abs((got & 0xFF) - b);
                    int error = Math.max(er, Math.max(eg, eb)) * 1000 + er + eg + eb;
                    if (error < bestError) {
                        bestError = error;
                        best = (y << 16) | (cr << 8) | cb;
                    }
                }
            }
        }
        return best;
    }

    private static int to255(double v) {
        return clamp((int) Math.round(v * 255.0));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int u16(byte[] d, int p) {
        return ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
    }

    private static int u24(byte[] d, int p) {
        return ((d[p] & 0xFF) << 16) | ((d[p + 1] & 0xFF) << 8) | (d[p + 2] & 0xFF);
    }

    public int sets() {
        return sets;
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "显示集 %d 个（出字幕 %d，清空 %d）%s%s%s%s",
                sets, shown, cleared,
                multiObject > 0 ? "，一屏两块的 " + multiObject + " 个" : "",
                reused > 0 ? "，只换调色板或位置、沿用位图的 " + reused + " 个" : "",
                croppedObjects > 0 ? "，裁切过的对象 " + croppedObjects + " 个" : "",
                missingObjects > 0 ? "，缺位图画不出的对象 " + missingObjects + " 个（拖进度落在中间会有）" : "");
    }
}
