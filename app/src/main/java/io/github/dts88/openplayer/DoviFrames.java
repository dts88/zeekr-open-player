package io.github.dts88.openplayer;

import io.github.dts88.openplayer.dovi.Rpu;
import io.github.dts88.openplayer.dovi.RpuException;
import io.github.dts88.openplayer.dovi.RpuNal;
import io.github.dts88.openplayer.dovi.RpuParser;

import java.nio.ByteBuffer;
import java.util.Locale;

final class DoviFrames {
    static final int RING = 64;

    private final RpuParser parser = new RpuParser();
    private final RpuNal.Result found = new RpuNal.Result();
    private final long[] times = new long[RING];
    private final Rpu[] rpus = new Rpu[RING];
    private int next;
    private Rpu latest;

    private volatile boolean enabled;
    private volatile long frames;
    private volatile long parsed;
    private volatile long missing;
    private volatile long broken;
    private volatile String problem = "";

    void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        reset();
    }

    boolean enabled() {
        return enabled;
    }

    void reset() {
        parser.reset();
        synchronized (this) {
            for (int i = 0; i < RING; i++) rpus[i] = null;
            latest = null;
            next = 0;
        }
    }

    void resetCounts() {
        frames = 0;
        parsed = 0;
        missing = 0;
        broken = 0;
        problem = "";
    }

    void onInput(ByteBuffer data, long timeUs) {
        if (!enabled || data == null || data.remaining() <= 0) return;
        frames++;
        Rpu rpu = null;
        try {
            if (RpuNal.findAnnexB(data, false, found)) {
                rpu = parser.parse(found);
                parsed++;
            } else {
                missing++;
            }
        } catch (RpuException e) {
            broken++;
            problem = e.getMessage() == null ? e.toString() : e.getMessage();
        } catch (RuntimeException e) {
            broken++;
            problem = e.toString();
        }
        if (rpu != null) put(timeUs, rpu);
    }

    synchronized void put(long timeUs, Rpu rpu) {
        times[next] = timeUs;
        rpus[next] = rpu;
        next = (next + 1) % RING;
        latest = rpu;
    }

    synchronized Rpu find(long timeUs) {
        for (int i = 0; i < RING; i++) {
            if (rpus[i] != null && times[i] == timeUs) return rpus[i];
        }
        return null;
    }

    synchronized Rpu latest() {
        return latest;
    }

    long parsed() {
        return parsed;
    }

    long frames() {
        return frames;
    }
}
