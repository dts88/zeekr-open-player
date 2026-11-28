package io.github.dts88.openplayer.emby;

public final class EmbyTicks {
    public static final long PER_MS = 10_000L;

    private EmbyTicks() {
    }

    public static long toMs(long ticks) {
        return ticks / PER_MS;
    }

    public static long fromMs(long ms) {
        return ms * PER_MS;
    }
}
