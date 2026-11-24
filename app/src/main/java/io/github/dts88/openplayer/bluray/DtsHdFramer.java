package io.github.dts88.openplayer.bluray;

final class DtsHdFramer extends AudioFramer {
    static final String MIME_DTS = "audio/vnd.dts";
    static final String MIME_DTS_HD = "audio/vnd.dts.hd";
    static final int CORE_SYNC = 0x7FFE8001;
    static final int EXSS_SYNC = 0x64582025;
    private static final int CORE_HEADER = 16;
    private static final int EXSS_HEADER = 12;
    private static final int[] CHANNELS_BY_AMODE = {1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 6, 6, 7, 8, 8};
    private static final int[] SAMPLE_RATE_BY_SFREQ = {
            0, 8000, 16000, 32000, 0, 0, 11025, 22050, 44100, 0, 0, 12000, 24000, 48000, 0, 0};
    private static final int[] REFERENCE_CLOCK = {32000, 44100, 48000, 0};

    private int sampleRate;
    private int channels;
    private boolean sawExtension;
    private int lastExtensionOffset = -1;

    DtsHdFramer(Sink sink) {
        super(sink);
    }

    @Override
    int frameLength(byte[] b, int start, int end, boolean endOfInput) {
        int available = end - start;
        if (available < 4) return endOfInput ? -available : 0;
        int sync = readInt(b, start);
        if (sync == CORE_SYNC) {
            if (available < CORE_HEADER) return endOfInput ? -available : 0;
            int core = coreFrameSize(b, start);
            if (core < 96 || core > 16384) return -1;
            if (available < core + 4) {
                if (!endOfInput) return 0;
                return available >= core ? core : -available;
            }
            if (readInt(b, start + core) != EXSS_SYNC) return core;
            if (available < core + EXSS_HEADER) return endOfInput ? core : 0;
            int extension = extensionFrameSize(b, start + core);
            if (extension < EXSS_HEADER || extension > MAX_FRAME) return core;
            if (available < core + extension) return endOfInput ? core : 0;
            return core + extension;
        }
        if (sync == EXSS_SYNC) {
            if (available < EXSS_HEADER) return endOfInput ? -available : 0;
            int extension = extensionFrameSize(b, start);
            if (extension < EXSS_HEADER || extension > MAX_FRAME) return -1;
            if (available < extension) return endOfInput ? -available : 0;
            return extension;
        }
        for (int i = start + 1; i + 4 <= end; i++) {
            int word = readInt(b, i);
            if (word == CORE_SYNC || word == EXSS_SYNC) return -(i - start);
        }
        return -(available - 3);
    }

    @Override
    int onFrame(byte[] b, int offset, int length, long timeUs) {
        int samples;
        if (readInt(b, offset) == CORE_SYNC) {
            int core = coreFrameSize(b, offset);
            int rate = SAMPLE_RATE_BY_SFREQ[bits(b, offset, 66, 4)];
            if (rate == 0) return 0;
            sampleRate = rate;
            int amode = bits(b, offset, 60, 6);
            int coreChannels = amode < CHANNELS_BY_AMODE.length ? CHANNELS_BY_AMODE[amode] : 2;
            if (bits(b, offset, 85, 2) != 0) coreChannels++;
            channels = coreChannels;
            samples = (bits(b, offset, 39, 7) + 1) * 32;
            lastExtensionOffset = length > core ? core : -1;
        } else {
            int clock = extensionReferenceClock(b, offset);
            int duration = extensionDurationSamples(b, offset);
            if (clock == 0 || duration == 0) return 0;
            sampleRate = clock;
            if (channels == 0) channels = 2;
            samples = duration;
            lastExtensionOffset = 0;
        }
        if (lastExtensionOffset >= 0) sawExtension = true;
        emit(b, offset, length, timeUs, true);
        return samples;
    }

    @Override
    void onReset() {
        lastExtensionOffset = -1;
    }

    @Override
    int sampleRate() {
        return sampleRate;
    }

    @Override
    int channelCount() {
        return channels;
    }

    @Override
    String mimeType() {
        if (sampleRate == 0) return null;
        return sawExtension ? MIME_DTS_HD : MIME_DTS;
    }

    int lastExtensionOffset() {
        return lastExtensionOffset;
    }

    static int coreFrameSize(byte[] b, int offset) {
        return bits(b, offset, 46, 14) + 1;
    }

    static int extensionFrameSize(byte[] b, int offset) {
        boolean wide = bits(b, offset, 42, 1) == 1;
        int headerBits = wide ? 12 : 8;
        int sizeBits = wide ? 20 : 16;
        return bits(b, offset, 43 + headerBits, sizeBits) + 1;
    }

    static int extensionReferenceClock(byte[] b, int offset) {
        int p = extensionStaticFieldsBit(b, offset);
        if (p < 0) return 0;
        return REFERENCE_CLOCK[bits(b, offset, p, 2)];
    }

    static int extensionDurationSamples(byte[] b, int offset) {
        int p = extensionStaticFieldsBit(b, offset);
        if (p < 0) return 0;
        return 512 * (bits(b, offset, p + 2, 3) + 1);
    }

    private static int extensionStaticFieldsBit(byte[] b, int offset) {
        boolean wide = bits(b, offset, 42, 1) == 1;
        int p = 43 + (wide ? 12 : 8) + (wide ? 20 : 16);
        return bits(b, offset, p, 1) == 1 ? p + 1 : -1;
    }

    static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
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
