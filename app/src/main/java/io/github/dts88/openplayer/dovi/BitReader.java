package io.github.dts88.openplayer.dovi;

final class BitReader {
    private static final int MAX_BYTES = 1 << 24;

    private final byte[] data;
    private final int base;
    private int pos;
    private final int end;

    BitReader(byte[] data, int offset, int length) throws RpuException {
        if (length > MAX_BYTES) {
            throw new RpuException(RpuException.INVALID, "数据太长：" + length + " 字节");
        }
        this.data = data;
        this.base = offset;
        this.end = length * 8;
    }

    int bitsLeft() {
        return end - pos;
    }

    int position() {
        return pos;
    }

    boolean aligned() {
        return (pos & 7) == 0;
    }

    boolean bit() throws RpuException {
        return bits(1) != 0;
    }

    long bits(int n) throws RpuException {
        if (n < 0 || n > 32) {
            throw new RpuException(RpuException.INVALID, "一次读 " + n + " 位");
        }
        if (n > end - pos) {
            throw new RpuException(RpuException.TRUNCATED, "数据不够：还要 " + n + " 位，只剩 " + (end - pos) + " 位");
        }
        long value = 0;
        int left = n;
        while (left > 0) {
            int inByte = 8 - (pos & 7);
            int take = Math.min(inByte, left);
            int chunk = ((data[base + (pos >>> 3)] & 0xFF) >>> (inByte - take)) & ((1 << take) - 1);
            value = (value << take) | chunk;
            pos += take;
            left -= take;
        }
        return value;
    }

    int u(int n) throws RpuException {
        if (n > 31) {
            throw new RpuException(RpuException.INVALID, "一次读 " + n + " 位放不进 int");
        }
        return (int) bits(n);
    }

    int s(int n) throws RpuException {
        if (n < 1) {
            throw new RpuException(RpuException.INVALID, "一次读 " + n + " 位");
        }
        long v = bits(n);
        long sign = 1L << (n - 1);
        return (int) ((v ^ sign) - sign);
    }

    long ue() throws RpuException {
        int zeros = 0;
        while (!bit()) {
            if (++zeros > 31) {
                throw new RpuException(RpuException.INVALID, "指数哥伦布码前面的 0 太多");
            }
        }
        return (1L << zeros) - 1 + bits(zeros);
    }

    long se() throws RpuException {
        long code = ue();
        long half = (code + 1) >>> 1;
        return (code & 1) != 0 ? half : -half;
    }

    int ue(String name, int max) throws RpuException {
        long v = ue();
        if (v > max) {
            throw new RpuException(RpuException.INVALID, name + " = " + v + "，超过 " + max);
        }
        return (int) v;
    }

    void skip(int n) throws RpuException {
        if (n < 0 || n > end - pos) {
            throw new RpuException(RpuException.TRUNCATED, "数据不够：要跳 " + n + " 位，只剩 " + (end - pos) + " 位");
        }
        pos += n;
    }

    void alignZero(String name) throws RpuException {
        while (!aligned()) {
            if (bit()) {
                throw new RpuException(RpuException.INVALID, name + " 不是 0");
            }
        }
    }

    byte[] bytes(int n) throws RpuException {
        if (n < 0 || (long) n * 8 > end - pos) {
            throw new RpuException(RpuException.TRUNCATED, "数据不够：还要 " + n + " 字节，只剩 " + (end - pos) + " 位");
        }
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) bits(8);
        }
        return out;
    }
}
