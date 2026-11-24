package io.github.dts88.openplayer.dovi;

import java.nio.ByteBuffer;

public final class RpuNal {
    public static final int NAL_RPU = 62;
    public static final int NAL_EL = 63;

    static final int MAX_RPU_BYTES = 1 << 16;

    private RpuNal() {
    }

    public static final class Result {
        public byte[] rpu = new byte[512];
        public int rpuLength;
        public int nalOffset = -1;
        public int nalLength;
        public boolean enhancementLayer;
        public boolean complete;

        void clear() {
            rpuLength = 0;
            nalOffset = -1;
            nalLength = 0;
            enhancementLayer = false;
            complete = false;
        }
    }

    public static boolean findLengthPrefixed(ByteBuffer sample, int nalLengthSize, Result out) {
        return findLengthPrefixed(sample, sample.position(), sample.remaining(), nalLengthSize, out);
    }

    public static boolean findLengthPrefixed(byte[] data, int offset, int length, int nalLengthSize, Result out) {
        return findLengthPrefixed(ByteBuffer.wrap(data), offset, length, nalLengthSize, out);
    }

    public static boolean findLengthPrefixed(ByteBuffer sample, int offset, int length, int nalLengthSize,
                                             Result out) {
        out.clear();
        if (nalLengthSize < 1 || nalLengthSize > 4 || !inside(sample, offset, length)) return false;
        int end = offset + length;
        int pos = offset;
        int rpuAt = -1;
        int rpuLength = 0;
        boolean broken = false;
        while (end - pos >= nalLengthSize) {
            long size = 0;
            for (int i = 0; i < nalLengthSize; i++) {
                size = (size << 8) | (sample.get(pos + i) & 0xFF);
            }
            pos += nalLengthSize;
            if (size > end - pos) {
                broken = true;
                break;
            }
            int n = (int) size;
            if (n >= 2) {
                int type = (sample.get(pos) >> 1) & 0x3F;
                if (type == NAL_RPU) {
                    rpuAt = pos;
                    rpuLength = n;
                } else if (type == NAL_EL) {
                    out.enhancementLayer = true;
                }
            }
            pos += n;
        }
        out.complete = !broken && pos == end;
        return take(sample, rpuAt, rpuLength, out);
    }

    public static boolean findAnnexB(ByteBuffer sample, boolean wholeSample, Result out) {
        return findAnnexB(sample, sample.position(), sample.remaining(), wholeSample, out);
    }

    public static boolean findAnnexB(byte[] data, int offset, int length, boolean wholeSample, Result out) {
        return findAnnexB(ByteBuffer.wrap(data), offset, length, wholeSample, out);
    }

    public static boolean findAnnexB(ByteBuffer sample, int offset, int length, boolean wholeSample, Result out) {
        out.clear();
        if (!inside(sample, offset, length)) return false;
        int end = offset + length;
        int nalEnd = end;
        int rpuAt = -1;
        int rpuLength = 0;
        boolean stopped = false;
        int i = end - 3;
        while (i >= offset) {
            int b = sample.get(i);
            if (b == 0) {
                if (sample.get(i + 1) != 0 || sample.get(i + 2) != 1) {
                    i--;
                    continue;
                }
                int nal = i + 3;
                if (nalEnd - nal >= 2) {
                    int type = (sample.get(nal) >> 1) & 0x3F;
                    if (type == NAL_RPU) {
                        if (rpuAt < 0) {
                            int last = nalEnd;
                            while (last > nal + 2 && sample.get(last - 1) == 0) last--;
                            rpuAt = nal;
                            rpuLength = last - nal;
                        }
                        if (!wholeSample) {
                            stopped = i - offset >= 3;
                            break;
                        }
                    } else if (type == NAL_EL) {
                        out.enhancementLayer = true;
                    }
                }
                nalEnd = i;
                i -= 3;
            } else if (b == 1) {
                i -= 2;
            } else {
                i -= 3;
            }
        }
        out.complete = !stopped;
        return take(sample, rpuAt, rpuLength, out);
    }

    public static int unescape(ByteBuffer src, int from, int to, byte[] dst) {
        int zeros = 0;
        int n = 0;
        for (int i = from; i < to; i++) {
            byte b = src.get(i);
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            dst[n++] = b;
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return n;
    }

    private static boolean take(ByteBuffer sample, int nalAt, int nalLength, Result out) {
        if (nalAt < 0 || nalLength <= 2 || nalLength - 2 > MAX_RPU_BYTES) return false;
        int size = nalLength - 2;
        if (out.rpu == null || out.rpu.length < size) {
            out.rpu = new byte[Math.max(size, 512)];
        }
        out.rpuLength = unescape(sample, nalAt + 2, nalAt + nalLength, out.rpu);
        out.nalOffset = nalAt;
        out.nalLength = nalLength;
        return out.rpuLength > 0;
    }

    private static boolean inside(ByteBuffer sample, int offset, int length) {
        return sample != null && offset >= 0 && length >= 0 && offset <= sample.limit() - length;
    }
}
