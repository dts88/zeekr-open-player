package io.github.dts88.openplayer;

import java.util.concurrent.atomic.AtomicInteger;

final class VideoFrameGate {
    static final long TIMEOUT_NS = 300_000_000L;
    private static final int RING = 32;

    private volatile boolean enabled;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile long pendingSinceNs;

    private final long[] ringReleaseNs = new long[RING];
    private final long[] ringPtsUs = new long[RING];
    private int ringNext;

    void setEnabled(boolean on) {
        enabled = on;
        pending.set(0);
    }

    boolean enabled() {
        return enabled;
    }

    boolean blocking(long nowNs) {
        if (!enabled || pending.get() <= 0) return false;
        if (nowNs - pendingSinceNs > TIMEOUT_NS) {
            pending.set(0);
            return false;
        }
        return true;
    }

    void onReleased(long presentationTimeUs, long releaseTimeNs, long nowNs) {
        if (!enabled) return;
        synchronized (this) {
            ringReleaseNs[ringNext] = releaseTimeNs;
            ringPtsUs[ringNext] = presentationTimeUs;
            ringNext = (ringNext + 1) % RING;
        }
        pendingSinceNs = nowNs;
        pending.incrementAndGet();
    }

    void onConsumed() {
        int v;
        do {
            v = pending.get();
            if (v <= 0) return;
        } while (!pending.compareAndSet(v, v - 1));
    }

    synchronized long presentationTimeFor(long releaseTimeNs) {
        for (int i = 0; i < RING; i++) {
            if (ringReleaseNs[i] == releaseTimeNs && releaseTimeNs != 0) return ringPtsUs[i];
        }
        return Long.MIN_VALUE;
    }
}
