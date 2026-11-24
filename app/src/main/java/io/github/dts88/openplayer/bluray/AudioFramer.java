package io.github.dts88.openplayer.bluray;

import java.util.ArrayDeque;

abstract class AudioFramer {
    static final long TIME_UNSET = Long.MIN_VALUE + 1;
    static final int MAX_FRAME = 1 << 20;

    interface Sink {
        void sample(byte[] data, int offset, int length, long timeUs, boolean keyFrame);
    }

    private final Sink sink;
    private byte[] buffer = new byte[64 * 1024];
    private int start;
    private int end;
    private long startOffset;
    private final ArrayDeque<long[]> anchors = new ArrayDeque<>();
    private long anchorTimeUs = TIME_UNSET;
    private long samplesSinceAnchor;

    AudioFramer(Sink sink) {
        this.sink = sink;
    }

    final void pesStarted(long timeUs) {
        if (timeUs != TIME_UNSET) anchors.add(new long[] {startOffset + (end - start), timeUs});
    }

    final void data(byte[] data, int offset, int length) {
        ensureCapacity(length);
        System.arraycopy(data, offset, buffer, end, length);
        end += length;
        parse(false);
    }

    final void endOfInput() {
        parse(true);
        flush();
    }

    final void reset() {
        startOffset += end - start;
        start = 0;
        end = 0;
        anchors.clear();
        anchorTimeUs = TIME_UNSET;
        samplesSinceAnchor = 0;
        onReset();
    }

    private void parse(boolean endOfInput) {
        while (start < end) {
            int r = frameLength(buffer, start, end, endOfInput);
            if (r == 0) break;
            if (r < 0) {
                skip(Math.min(-r, end - start));
                continue;
            }
            if (r > end - start) break;
            long timeUs = timeFor(startOffset, continuationSamples(buffer, start, r));
            samplesSinceAnchor += onFrame(buffer, start, r, timeUs);
            skip(r);
        }
        compact();
    }

    private long timeFor(long offset, int continuation) {
        long[] last = null;
        while (!anchors.isEmpty() && anchors.peekFirst()[0] <= offset) last = anchors.pollFirst();
        if (last != null) {
            anchorTimeUs = last[1];
            samplesSinceAnchor = Math.max(continuation, 0);
        }
        if (anchorTimeUs == TIME_UNSET) return TIME_UNSET;
        int rate = sampleRate();
        return rate > 0 ? anchorTimeUs + samplesSinceAnchor * 1_000_000L / rate : anchorTimeUs;
    }

    private void skip(int n) {
        start += n;
        startOffset += n;
    }

    private void compact() {
        if (start == end) {
            start = 0;
            end = 0;
        } else if (start > buffer.length / 2) {
            System.arraycopy(buffer, start, buffer, 0, end - start);
            end -= start;
            start = 0;
        }
    }

    private void ensureCapacity(int extra) {
        if (end + extra <= buffer.length) return;
        if (start > 0) {
            System.arraycopy(buffer, start, buffer, 0, end - start);
            end -= start;
            start = 0;
        }
        if (end + extra > buffer.length) {
            byte[] bigger = new byte[Math.max(buffer.length * 2, end + extra)];
            System.arraycopy(buffer, 0, bigger, 0, end);
            buffer = bigger;
        }
    }

    final void emit(byte[] data, int offset, int length, long timeUs, boolean keyFrame) {
        if (timeUs != TIME_UNSET && length > 0) sink.sample(data, offset, length, timeUs, keyFrame);
    }

    abstract int frameLength(byte[] b, int start, int end, boolean endOfInput);

    abstract int onFrame(byte[] b, int offset, int length, long timeUs);

    int continuationSamples(byte[] b, int offset, int length) {
        return -1;
    }

    abstract int sampleRate();

    abstract int channelCount();

    abstract String mimeType();

    void flush() {}

    void onReset() {}
}
