package io.github.dts88.openplayer.bluray;

final class TrueHdFramer extends AudioFramer {
    static final String MIME = "audio/true-hd";
    static final int GROUP_AUS = 16;
    static final int MAX_AU_BYTES = 0xFFF * 2;
    static final int MAX_AUS_WITHOUT_MAJOR_SYNC = 1200;
    private static final int MAJOR_SYNC_PREFIX = 12;
    private static final int[] CHANNELS_PER_BIT = {2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 1, 1};

    private boolean synced;
    private int ausSinceMajorSync;
    private int sampleRate;
    private int channels;
    private int samplesPerAu;
    private int lostSyncCount;

    private byte[] group = new byte[GROUP_AUS * 2048];
    private int groupLength;
    private int groupAus;
    private long groupTimeUs = TIME_UNSET;
    private boolean groupKey;

    TrueHdFramer(Sink sink) {
        super(sink);
    }

    @Override
    int frameLength(byte[] b, int start, int end, boolean endOfInput) {
        if (!synced) {
            int at = findMajorSync(b, start, end);
            if (at < 0) {
                int skip = (end - start) - (MAJOR_SYNC_PREFIX - 1);
                if (endOfInput) return -(end - start);
                return skip > 0 ? -skip : 0;
            }
            if (at > start) return -(at - start);
        }
        if (end - start < 4) return endOfInput ? -(end - start) : 0;
        int length = auLength(b, start);
        if (length < 4 || (!synced && length < MAJOR_SYNC_PREFIX)) {
            loseSync();
            return -1;
        }
        if (end - start < length) return endOfInput ? -(end - start) : 0;
        return length;
    }

    @Override
    int onFrame(byte[] b, int offset, int length, long timeUs) {
        boolean major = length >= MAJOR_SYNC_PREFIX && isMajorSync(b, offset);
        if (major) {
            int rateBits = (b[offset + 8] & 0xF0) >> 4;
            int rate = sampleRateOf(rateBits);
            if (rate <= 0) {
                loseSync();
                return 0;
            }
            sampleRate = rate;
            samplesPerAu = 40 << (rateBits & 0x07);
            channels = channelCountOf(b, offset);
            synced = true;
            ausSinceMajorSync = 0;
        } else if (!synced) {
            return 0;
        } else if (++ausSinceMajorSync > MAX_AUS_WITHOUT_MAJOR_SYNC) {
            loseSync();
            return 0;
        }
        if (major || groupAus == GROUP_AUS) flushGroup();
        if (groupAus == 0) {
            groupTimeUs = timeUs;
            groupKey = major;
        }
        append(b, offset, length);
        groupAus++;
        return samplesPerAu;
    }

    @Override
    void flush() {
        flushGroup();
    }

    @Override
    void onReset() {
        synced = false;
        ausSinceMajorSync = 0;
        groupAus = 0;
        groupLength = 0;
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
        return sampleRate > 0 ? MIME : null;
    }

    int lostSyncCount() {
        return lostSyncCount;
    }

    private void loseSync() {
        if (synced) lostSyncCount++;
        synced = false;
        ausSinceMajorSync = 0;
        groupAus = 0;
        groupLength = 0;
    }

    private void flushGroup() {
        if (groupAus > 0) emit(group, 0, groupLength, groupTimeUs, groupKey);
        groupAus = 0;
        groupLength = 0;
    }

    private void append(byte[] b, int offset, int length) {
        if (groupLength + length > group.length) {
            byte[] bigger = new byte[Math.max(group.length * 2, groupLength + length)];
            System.arraycopy(group, 0, bigger, 0, groupLength);
            group = bigger;
        }
        System.arraycopy(b, offset, group, groupLength, length);
        groupLength += length;
    }

    static int auLength(byte[] b, int offset) {
        return (((b[offset] & 0x0F) << 8) | (b[offset + 1] & 0xFF)) * 2;
    }

    static boolean isMajorSync(byte[] b, int offset) {
        return (b[offset + 4] & 0xFF) == 0xF8
                && (b[offset + 5] & 0xFF) == 0x72
                && (b[offset + 6] & 0xFF) == 0x6F
                && (b[offset + 7] & 0xFF) == 0xBA;
    }

    static int findMajorSync(byte[] b, int start, int end) {
        for (int i = start; i + MAJOR_SYNC_PREFIX <= end; i++) {
            if (isMajorSync(b, i)) return i;
        }
        return -1;
    }

    static int sampleRateOf(int rateBits) {
        if (rateBits == 0xF || (rateBits & 0x07) > 2) return 0;
        return ((rateBits & 0x08) != 0 ? 44100 : 48000) << (rateBits & 0x07);
    }

    static int channelCountOf(byte[] b, int offset) {
        int six = ((b[offset + 9] & 0x0F) << 1) | ((b[offset + 10] & 0x80) >> 7);
        int eight = ((b[offset + 10] & 0x1F) << 8) | (b[offset + 11] & 0xFF);
        int n = countChannels(eight);
        if (n == 0) n = countChannels(six);
        return n == 0 ? 2 : n;
    }

    static int countChannels(int assignment) {
        int n = 0;
        for (int i = 0; i < CHANNELS_PER_BIT.length; i++) {
            if (((assignment >> i) & 1) != 0) n += CHANNELS_PER_BIT[i];
        }
        return n;
    }
}
