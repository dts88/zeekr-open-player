package io.github.dts88.openplayer.bluray;

final class Eac3Framer extends AudioFramer {
    static final String MIME_AC3 = "audio/ac3";
    static final String MIME_EAC3 = "audio/eac3";
    static final int SYNC_WORD = 0x0B77;
    static final int SYNC_HEADER = 6;
    static final int FULL_HEADER = 12;

    static final long FL = 1L, FR = 1L << 1, FC = 1L << 2, LFE = 1L << 3, BL = 1L << 4, BR = 1L << 5,
            FLC = 1L << 6, FRC = 1L << 7, BC = 1L << 8, SL = 1L << 9, SR = 1L << 10, TC = 1L << 11,
            TFL = 1L << 12, TFC = 1L << 13, TFR = 1L << 14, TBL = 1L << 15, TBR = 1L << 17,
            WL = 1L << 31, WR = 1L << 32, SDL = 1L << 33, SDR = 1L << 34, LFE2 = 1L << 35;

    private static final long[] LAYOUT_BY_ACMOD = {
            FL | FR, FC, FL | FR, FL | FR | FC, FL | FR | BC, FL | FR | FC | BC, FL | FR | SL | SR,
            FL | FR | FC | SL | SR};
    private static final long[] LAYOUT_BY_CHANMAP_BIT = {
            FL, FC, FR, SL, SR, FLC | FRC, BL | BR, BC, TC, SDL | SDR, WL | WR, TFL | TFR, TFC, TBL | TBR,
            LFE2, LFE};
    private static final int[] SAMPLE_RATE_BY_FSCOD = {48000, 44100, 32000};
    private static final int[] SAMPLE_RATE_BY_FSCOD2 = {24000, 22050, 16000};
    private static final int[] BLOCKS_BY_NUMBLKSCOD = {1, 2, 3, 6};
    private static final int[] AC3_BITRATE_KBPS = {
            32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};

    static final class Header {
        boolean eac3;
        boolean independent;
        int substreamId;
        int frameSize;
        int sampleRate;
        int samples;
        long layout;
    }

    private final Header header = new Header();
    private boolean synced;
    private int program;

    private byte[] pending = new byte[16 * 1024];
    private int pendingLength;
    private long pendingTimeUs = TIME_UNSET;
    private int pendingSamples;
    private int pendingRate;
    private long pendingLayout;
    private boolean pendingEac3;

    private int rate;
    private int channels;
    private String mime;

    Eac3Framer(Sink sink) {
        super(sink);
    }

    @Override
    int frameLength(byte[] b, int start, int end, boolean endOfInput) {
        int available = end - start;
        if (available < SYNC_HEADER) return endOfInput ? -available : 0;
        if (!parse(b, start, available, header) || header.frameSize < FULL_HEADER) {
            synced = false;
            return -skipToSync(b, start, end);
        }
        int size = header.frameSize;
        if (!synced) {
            if (available < size + 2) {
                if (!endOfInput) return 0;
            } else if (readShort(b, start + size) != SYNC_WORD) {
                return -1;
            }
            synced = true;
        }
        if (available < size) return endOfInput ? -available : 0;
        return size;
    }

    @Override
    int continuationSamples(byte[] b, int offset, int length) {
        if (!parse(b, offset, length, header)) return -1;
        if (rate == 0) rate = header.sampleRate;
        if (header.independent && header.substreamId == 0) return -1;
        return header.samples;
    }

    @Override
    int onFrame(byte[] b, int offset, int length, long timeUs) {
        if (!parse(b, offset, length, header)) return 0;
        Header h = header;
        if (h.independent) {
            program = h.substreamId;
            if (h.substreamId != 0) return 0;
            flushPending();
            startPending(b, offset, length, timeUs, h);
            return h.samples;
        }
        if (program == 0 && pendingLength > 0 && h.samples == pendingSamples && h.sampleRate == pendingRate
                && pendingLength + length <= MAX_FRAME) {
            append(b, offset, length);
            pendingLayout |= h.layout;
            pendingEac3 = true;
        }
        return 0;
    }

    @Override
    void flush() {
        flushPending();
    }

    @Override
    void onReset() {
        synced = false;
        program = 0;
        pendingLength = 0;
    }

    @Override
    int sampleRate() {
        return rate;
    }

    @Override
    int channelCount() {
        return channels;
    }

    @Override
    String mimeType() {
        return mime;
    }

    private void startPending(byte[] b, int offset, int length, long timeUs, Header h) {
        pendingLength = 0;
        append(b, offset, length);
        pendingTimeUs = timeUs;
        pendingSamples = h.samples;
        pendingRate = h.sampleRate;
        pendingLayout = h.layout;
        pendingEac3 = h.eac3;
        rate = h.sampleRate;
    }

    private void flushPending() {
        if (pendingLength == 0) return;
        channels = Math.max(1, Long.bitCount(pendingLayout));
        mime = pendingEac3 ? MIME_EAC3 : MIME_AC3;
        int length = pendingLength;
        pendingLength = 0;
        emit(pending, 0, length, pendingTimeUs, true);
    }

    private void append(byte[] b, int offset, int length) {
        if (pendingLength + length > pending.length) {
            byte[] bigger = new byte[Math.max(pending.length * 2, pendingLength + length)];
            System.arraycopy(pending, 0, bigger, 0, pendingLength);
            pending = bigger;
        }
        System.arraycopy(b, offset, pending, pendingLength, length);
        pendingLength += length;
    }

    static boolean parse(byte[] b, int offset, int available, Header h) {
        if (available < SYNC_HEADER || readShort(b, offset) != SYNC_WORD) return false;
        int bsid = bits(b, offset, 40, 5);
        if (bsid > 16) return false;
        boolean full = available >= FULL_HEADER;
        if (bsid <= 10) {
            int fscod = bits(b, offset, 32, 2);
            int frmsizecod = bits(b, offset, 34, 6);
            if (fscod == 3 || frmsizecod > 37) return false;
            h.eac3 = false;
            h.independent = true;
            h.substreamId = 0;
            h.frameSize = ac3FrameSize(fscod, frmsizecod);
            h.sampleRate = SAMPLE_RATE_BY_FSCOD[fscod] >> (Math.max(bsid, 8) - 8);
            h.samples = 1536;
            h.layout = full ? ac3Layout(b, offset) : 0;
            return true;
        }
        int strmtyp = bits(b, offset, 16, 2);
        if (strmtyp == 3) return false;
        int fscod = bits(b, offset, 32, 2);
        int code = bits(b, offset, 34, 2);
        int sampleRate;
        int blocks;
        if (fscod == 3) {
            if (code == 3) return false;
            sampleRate = SAMPLE_RATE_BY_FSCOD2[code];
            blocks = 6;
        } else {
            sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
            blocks = BLOCKS_BY_NUMBLKSCOD[code];
        }
        h.eac3 = true;
        h.independent = strmtyp != 1;
        h.substreamId = bits(b, offset, 18, 3);
        h.frameSize = (bits(b, offset, 21, 11) + 1) * 2;
        h.sampleRate = sampleRate;
        h.samples = blocks * 256;
        h.layout = full ? eac3Layout(b, offset, strmtyp) : 0;
        return true;
    }

    static int ac3FrameSize(int fscod, int frmsizecod) {
        int kbps = AC3_BITRATE_KBPS[frmsizecod / 2];
        int words;
        if (fscod == 0) {
            words = kbps * 2;
        } else if (fscod == 2) {
            words = kbps * 3;
        } else {
            words = kbps * 15360 / 7056 + (frmsizecod & 1);
        }
        return words * 2;
    }

    private static long ac3Layout(byte[] b, int offset) {
        int acmod = bits(b, offset, 48, 3);
        int p = 51;
        if ((acmod & 1) != 0 && acmod != 1) p += 2;
        if ((acmod & 4) != 0) p += 2;
        if (acmod == 2) p += 2;
        return LAYOUT_BY_ACMOD[acmod] | (bits(b, offset, p, 1) == 1 ? LFE : 0);
    }

    private static long eac3Layout(byte[] b, int offset, int strmtyp) {
        int acmod = bits(b, offset, 36, 3);
        long layout = LAYOUT_BY_ACMOD[acmod] | (bits(b, offset, 39, 1) == 1 ? LFE : 0);
        if (strmtyp != 1) return layout;
        int p = 50;
        if (bits(b, offset, p++, 1) == 1) p += 8;
        if (acmod == 0) {
            p += 5;
            if (bits(b, offset, p++, 1) == 1) p += 8;
        }
        if (bits(b, offset, p++, 1) == 0) return layout;
        int chanmap = bits(b, offset, p, 16);
        long mapped = 0;
        for (int i = 0; i < 16; i++) {
            if ((chanmap & (0x8000 >> i)) != 0) mapped |= LAYOUT_BY_CHANMAP_BIT[i];
        }
        return mapped;
    }

    static int frameSize(byte[] b, int offset, int available) {
        Header h = new Header();
        return parse(b, offset, available, h) ? h.frameSize : 0;
    }

    private static int skipToSync(byte[] b, int start, int end) {
        for (int i = start + 1; i + 1 < end; i++) {
            if ((b[i] & 0xFF) == 0x0B && (b[i + 1] & 0xFF) == 0x77) return i - start;
        }
        return Math.max(1, end - start - 1);
    }

    static int readShort(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
    }

    static int bits(byte[] b, int offset, int bitOffset, int n) {
        int value = 0;
        for (int i = 0; i < n; i++) {
            int bit = bitOffset + i;
            value = (value << 1) | ((b[offset + (bit >> 3)] >> (7 - (bit & 7))) & 1);
        }
        return value;
    }
}
