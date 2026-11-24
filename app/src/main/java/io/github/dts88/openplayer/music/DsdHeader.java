package io.github.dts88.openplayer.music;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DsdHeader {
    public final boolean dsf;
    public final int channels;
    public final int dsdRate;
    public final boolean lsbFirst;
    public final long samplesPerChannel;
    public final int blockSize;
    public final long dataOffset;
    public final long dataBytes;

    DsdHeader(boolean dsf, int channels, int dsdRate, boolean lsbFirst, long samplesPerChannel, int blockSize,
              long dataOffset, long dataBytes) {
        this.dsf = dsf;
        this.channels = channels;
        this.dsdRate = dsdRate;
        this.lsbFirst = lsbFirst;
        this.samplesPerChannel = samplesPerChannel;
        this.blockSize = blockSize;
        this.dataOffset = dataOffset;
        this.dataBytes = dataBytes;
    }

    public static int dsfHeaderLength(byte[] h, int length) {
        if (length < 28 || !tag(h, 0, "DSD ")) return -1;
        long dsdSize = le64(h, 4);
        if (dsdSize < 28 || dsdSize > 4096) return -1;
        if (length < dsdSize + 12) return (int) dsdSize + 52 + 12;
        if (!tag(h, (int) dsdSize, "fmt ")) return -1;
        long fmtSize = le64(h, (int) dsdSize + 4);
        if (fmtSize < 52 || fmtSize > 4096) return -1;
        return (int) (dsdSize + fmtSize + 12);
    }

    public static DsdHeader parseDsf(byte[] h, int length) {
        int need = dsfHeaderLength(h, length);
        if (need < 0 || length < need) return null;
        int fmt = (int) le64(h, 4);
        int fmtSize = (int) le64(h, fmt + 4);
        int formatId = le32(h, fmt + 16);
        int channels = le32(h, fmt + 24);
        int rate = le32(h, fmt + 28);
        int bits = le32(h, fmt + 32);
        long samples = le64(h, fmt + 36);
        int block = le32(h, fmt + 44);
        int data = fmt + fmtSize;
        if (formatId != 0 || channels < 1 || channels > 8 || rate <= 0 || block <= 0 || block > (1 << 20)
                || (bits != 1 && bits != 8) || samples < 0 || !tag(h, data, "data")) {
            return null;
        }
        long dataBytes = Math.max(0, le64(h, data + 4) - 12);
        return new DsdHeader(true, channels, rate, bits == 1, samples, block, data + 12L, dataBytes);
    }

    public static final class DffProperties {
        public int sampleRate;
        public int channels;
        public String compression = "DSD ";
        public String channelIds = "";
    }

    public static DffProperties parseDffProperties(byte[] d, int length) {
        if (length < 4 || !tag(d, 0, "SND ")) return null;
        DffProperties p = new DffProperties();
        int at = 4;
        while (at + 12 <= length) {
            long size = be64(d, at + 4);
            int body = at + 12;
            if (size < 0 || body + size > length) break;
            if (tag(d, at, "FS  ") && size >= 4) {
                p.sampleRate = be32(d, body);
            } else if (tag(d, at, "CHNL") && size >= 2) {
                p.channels = ((d[body] & 0xFF) << 8) | (d[body + 1] & 0xFF);
                StringBuilder ids = new StringBuilder();
                for (int i = 0; i < p.channels && body + 2 + 4 * i + 4 <= body + size; i++) {
                    if (ids.length() > 0) ids.append(' ');
                    ids.append(new String(d, body + 2 + 4 * i, 4, StandardCharsets.ISO_8859_1).trim());
                }
                p.channelIds = ids.toString();
            } else if (tag(d, at, "CMPR") && size >= 4) {
                p.compression = new String(d, body, 4, StandardCharsets.ISO_8859_1);
            }
            at = body + (int) size + (int) (size & 1);
        }
        return p;
    }

    public static DsdHeader forDff(DffProperties p, long dataOffset, long dataBytes) {
        if (p == null || p.channels < 1 || p.channels > 8 || p.sampleRate <= 0) return null;
        return new DsdHeader(false, p.channels, p.sampleRate, false, dataBytes / p.channels * 8, 0,
                dataOffset, dataBytes);
    }

    public double seconds() {
        return samplesPerChannel / (double) dsdRate;
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s，DSD%d（%d Hz），%d 声道，%.1f 秒",
                dsf ? "DSF" : "DFF", dsdRate / 44100, dsdRate, channels, seconds());
    }

    static boolean tag(byte[] b, int at, String t) {
        if (at < 0 || at + 4 > b.length) return false;
        for (int i = 0; i < 4; i++) {
            if (b[at + i] != (byte) t.charAt(i)) return false;
        }
        return true;
    }

    static int le32(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }

    static long le64(byte[] b, int at) {
        return (le32(b, at) & 0xFFFFFFFFL) | ((long) le32(b, at + 4) << 32);
    }

    static int be32(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    static long be64(byte[] b, int at) {
        return ((long) be32(b, at) << 32) | (be32(b, at + 4) & 0xFFFFFFFFL);
    }
}
