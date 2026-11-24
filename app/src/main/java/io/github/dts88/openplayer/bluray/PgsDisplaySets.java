package io.github.dts88.openplayer.bluray;

final class PgsDisplaySets {
    interface Sink {
        void displaySet(byte[] data, int length, long timeUs);
    }

    static final int SEGMENT_COMPOSITION = 0x16;
    static final int SEGMENT_END = 0x80;
    static final int MAX_BYTES = 16 << 20;

    private byte[] pending = new byte[16 * 1024];
    private int size;
    private long timeUs;
    private boolean open;

    void reset() {
        size = 0;
        open = false;
    }

    void add(byte[] pes, int length, long pesTimeUs, boolean hasTime, Sink sink) {
        int p = 0;
        while (p + 3 <= length) {
            int type = pes[p] & 0xFF;
            int next = p + 3 + (((pes[p + 1] & 0xFF) << 8) | (pes[p + 2] & 0xFF));
            if (next > length) break;
            if (type == SEGMENT_COMPOSITION) {
                size = 0;
                open = hasTime;
                timeUs = pesTimeUs;
            }
            if (open) {
                int n = next - p;
                if (size + n > MAX_BYTES) {
                    reset();
                } else {
                    if (size + n > pending.length) {
                        byte[] bigger = new byte[Math.max(pending.length * 2, size + n)];
                        System.arraycopy(pending, 0, bigger, 0, size);
                        pending = bigger;
                    }
                    System.arraycopy(pes, p, pending, size, n);
                    size += n;
                    if (type == SEGMENT_END) {
                        sink.displaySet(pending, size, timeUs);
                        reset();
                    }
                }
            }
            p = next;
        }
    }
}
