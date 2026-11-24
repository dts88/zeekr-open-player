package io.github.dts88.openplayer;

import io.github.dts88.openplayer.hdr10plus.Hdr10PlusException;
import io.github.dts88.openplayer.hdr10plus.Hdr10PlusMeta;
import io.github.dts88.openplayer.hdr10plus.Hdr10PlusSei;

import java.nio.ByteBuffer;
import java.util.Locale;

final class Hdr10PlusFrames {
    static final int RING = 64;

    private final Hdr10PlusSei.Scratch scratch = new Hdr10PlusSei.Scratch();
    private final long[] times = new long[RING];
    private final Hdr10PlusMeta[] metas = new Hdr10PlusMeta[RING];
    private int next;
    private Hdr10PlusMeta latest;

    static final int NONE = 0;
    static final int HEVC = 1;
    static final int AV1 = 2;
    static final int VP9 = 3;

    private volatile int codec;
    private volatile boolean enabled;
    private volatile long frames;
    private volatile long found;
    private volatile long broken;
    private volatile long changes;
    private volatile String problem = "";
    private volatile String firstText = "";

    void setEnabled(boolean on) {
        setCodec(on ? HEVC : NONE);
    }

    void setCodec(int which) {
        if (codec == which) return;
        codec = which;
        enabled = which != NONE;
        reset();
    }

    boolean enabled() {
        return enabled;
    }

    void reset() {
        scratch.reset();
        synchronized (this) {
            for (int i = 0; i < RING; i++) metas[i] = null;
            latest = null;
            next = 0;
        }
    }

    void resetCounts() {
        frames = 0;
        found = 0;
        broken = 0;
        changes = 0;
        problem = "";
        firstText = "";
    }

    void onInput(ByteBuffer data, long timeUs) {
        onInput(data, null, timeUs);
    }

    void onInput(ByteBuffer data, ByteBuffer supplemental, long timeUs) {
        int which = codec;
        if (which == NONE || data == null || data.remaining() <= 0) return;
        frames++;
        Hdr10PlusMeta meta = null;
        try {
            if (which == AV1) {
                meta = Hdr10PlusSei.findAv1(data, data.position(), data.remaining(), scratch);
            } else if (which == VP9) {
                if (supplemental != null && supplemental.remaining() > 0) {
                    meta = Hdr10PlusSei.fromT35(supplemental, supplemental.position(), supplemental.remaining(), scratch);
                }
            } else {
                meta = Hdr10PlusSei.findAnnexB(data, scratch);
            }
        } catch (Hdr10PlusException e) {
            broken++;
            problem = e.getMessage() == null ? e.toString() : e.getMessage();
        } catch (RuntimeException e) {
            broken++;
            problem = e.toString();
        }
        if (meta == null) return;
        found++;
        put(timeUs, meta);
    }

    synchronized void put(long timeUs, Hdr10PlusMeta meta) {
        if (meta != latest) {
            changes++;
            if (firstText.isEmpty()) firstText = meta.describe();
        }
        times[next] = timeUs;
        metas[next] = meta;
        next = (next + 1) % RING;
        latest = meta;
    }

    synchronized Hdr10PlusMeta find(long timeUs) {
        for (int i = 0; i < RING; i++) {
            if (metas[i] != null && times[i] == timeUs) return metas[i];
        }
        return null;
    }

    synchronized Hdr10PlusMeta latest() {
        return latest;
    }

    boolean seen() {
        return found > 0;
    }

    long found() {
        return found;
    }

    long frames() {
        return frames;
    }
}
