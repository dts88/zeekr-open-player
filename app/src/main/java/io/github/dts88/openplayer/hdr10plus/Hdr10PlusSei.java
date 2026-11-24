package io.github.dts88.openplayer.hdr10plus;

import java.nio.ByteBuffer;

public final class Hdr10PlusSei {
    public static final int NAL_PREFIX_SEI = 39;
    static final int SEI_T35 = 4;
    static final int MAX_SEI_BYTES = 1 << 16;

    private Hdr10PlusSei() {
    }

    public static final class Scratch {
        byte[] rbsp = new byte[1024];
        byte[] payload = new byte[256];
        int payloadLength;
        Hdr10PlusMeta last;

        public void reset() {
            payloadLength = 0;
            last = null;
        }
    }

    public static Hdr10PlusMeta findAnnexB(ByteBuffer sample, int offset, int length, Scratch scratch)
            throws Hdr10PlusException {
        if (sample == null || offset < 0 || length < 0 || offset > sample.limit() - length) return null;
        int end = offset + length;
        int nal = afterStartCode(sample, offset, end);
        while (nal >= 0 && end - nal >= 2) {
            int type = (sample.get(nal) >> 1) & 0x3F;
            if (type < 32) return null;
            int next = afterStartCode(sample, nal, end);
            int nalEnd = next < 0 ? end : next - 3;
            while (nalEnd > nal + 2 && sample.get(nalEnd - 1) == 0) nalEnd--;
            if (type == NAL_PREFIX_SEI) {
                Hdr10PlusMeta m = fromSeiNal(sample, nal, nalEnd - nal, scratch);
                if (m != null) return m;
            }
            nal = next;
        }
        return null;
    }

    public static Hdr10PlusMeta findAnnexB(ByteBuffer sample, Scratch scratch) throws Hdr10PlusException {
        return sample == null ? null : findAnnexB(sample, sample.position(), sample.remaining(), scratch);
    }

    public static Hdr10PlusMeta findLengthPrefixed(ByteBuffer sample, int offset, int length, int nalLengthSize,
                                                   Scratch scratch) throws Hdr10PlusException {
        if (sample == null || nalLengthSize < 1 || nalLengthSize > 4 || offset < 0 || length < 0
                || offset > sample.limit() - length) {
            return null;
        }
        int end = offset + length;
        int pos = offset;
        while (end - pos >= nalLengthSize) {
            long size = 0;
            for (int i = 0; i < nalLengthSize; i++) size = (size << 8) | (sample.get(pos + i) & 0xFF);
            pos += nalLengthSize;
            if (size > end - pos) return null;
            int n = (int) size;
            if (n >= 2) {
                int type = (sample.get(pos) >> 1) & 0x3F;
                if (type < 32) return null;
                if (type == NAL_PREFIX_SEI) {
                    Hdr10PlusMeta m = fromSeiNal(sample, pos, n, scratch);
                    if (m != null) return m;
                }
            }
            pos += n;
        }
        return null;
    }

    public static Hdr10PlusMeta fromSeiNal(ByteBuffer data, int nalOffset, int nalLength, Scratch scratch)
            throws Hdr10PlusException {
        if (nalLength <= 2 || nalLength > MAX_SEI_BYTES) return null;
        if (scratch.rbsp.length < nalLength) scratch.rbsp = new byte[nalLength];
        byte[] b = scratch.rbsp;
        int n = unescape(data, nalOffset + 2, nalOffset + nalLength, b);
        int p = 0;
        while (p < n && !(p == n - 1 && (b[p] & 0xFF) == 0x80)) {
            int type = 0;
            while (p < n && (b[p] & 0xFF) == 0xFF) {
                type += 255;
                p++;
            }
            if (p >= n) return null;
            type += b[p++] & 0xFF;
            int size = 0;
            while (p < n && (b[p] & 0xFF) == 0xFF) {
                size += 255;
                p++;
            }
            if (p >= n) return null;
            size += b[p++] & 0xFF;
            if (size > n - p) return null;
            if (type == SEI_T35 && Hdr10PlusMeta.isHdr10Plus(b, p, size)) return t35(b, p, size, scratch);
            p += size;
        }
        return null;
    }

    private static Hdr10PlusMeta t35(byte[] b, int p, int size, Scratch scratch) throws Hdr10PlusException {
        if (scratch.last != null && scratch.payloadLength == size && sameBytes(b, p, scratch.payload, size)) {
            return scratch.last;
        }
        scratch.reset();
        Hdr10PlusMeta m = Hdr10PlusMeta.parse(b, p, size);
        if (scratch.payload.length < size) scratch.payload = new byte[size];
        System.arraycopy(b, p, scratch.payload, 0, size);
        scratch.payloadLength = size;
        scratch.last = m;
        return m;
    }

    static final int OBU_METADATA = 5;
    static final int METADATA_T35 = 4;

    public static Hdr10PlusMeta findAv1(ByteBuffer sample, int offset, int length, Scratch scratch)
            throws Hdr10PlusException {
        if (sample == null || offset < 0 || length < 0 || offset > sample.limit() - length) return null;
        int end = offset + length;
        int pos = offset;
        while (pos < end) {
            int header = sample.get(pos++) & 0xFF;
            if ((header & 0x80) != 0) return null;
            int type = (header >> 3) & 0x0F;
            boolean extension = (header & 0x04) != 0;
            boolean hasSize = (header & 0x02) != 0;
            if (extension) pos++;
            long size;
            if (hasSize) {
                long[] v = leb128(sample, pos, end);
                if (v == null) return null;
                size = v[0];
                pos = (int) v[1];
            } else {
                size = end - pos;
            }
            if (size < 0 || size > end - pos) return null;
            int obuEnd = pos + (int) size;
            if (type == OBU_METADATA) {
                long[] kind = leb128(sample, pos, obuEnd);
                if (kind != null && kind[0] == METADATA_T35) {
                    Hdr10PlusMeta m = fromT35(sample, (int) kind[1], obuEnd - (int) kind[1], scratch);
                    if (m != null) return m;
                }
            }
            pos = obuEnd;
        }
        return null;
    }

    public static Hdr10PlusMeta fromT35(ByteBuffer data, int offset, int length, Scratch scratch)
            throws Hdr10PlusException {
        if (data == null || length <= Hdr10PlusMeta.HEADER_BYTES || length > MAX_SEI_BYTES || offset < 0
                || offset > data.limit() - length) {
            return null;
        }
        if (scratch.rbsp.length < length) scratch.rbsp = new byte[length];
        byte[] b = scratch.rbsp;
        for (int i = 0; i < length; i++) b[i] = data.get(offset + i);
        return Hdr10PlusMeta.isHdr10Plus(b, 0, length) ? t35(b, 0, length, scratch) : null;
    }

    private static long[] leb128(ByteBuffer d, int pos, int end) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            if (pos >= end) return null;
            int b = d.get(pos++) & 0xFF;
            value |= (long) (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) return new long[]{value, pos};
        }
        return null;
    }

    private static boolean sameBytes(byte[] a, int aOffset, byte[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[aOffset + i] != b[i]) return false;
        }
        return true;
    }

    static int afterStartCode(ByteBuffer b, int from, int end) {
        int i = from;
        while (i + 2 < end) {
            int third = b.get(i + 2);
            if (third > 1 || third < 0) {
                i += 3;
            } else if (third == 1) {
                if (b.get(i) == 0 && b.get(i + 1) == 0) return i + 3;
                i += 3;
            } else {
                i += 1;
            }
        }
        return -1;
    }

    static int unescape(ByteBuffer src, int from, int to, byte[] dst) {
        int zeros = 0;
        int n = 0;
        for (int i = from; i < to; i++) {
            byte v = src.get(i);
            if (zeros >= 2 && v == 3) {
                zeros = 0;
                continue;
            }
            dst[n++] = v;
            zeros = v == 0 ? zeros + 1 : 0;
        }
        return n;
    }
}
