package io.github.dts88.openplayer.emby;

import java.util.Locale;

public final class EmbyQuality {
    public static final long[] BITRATES = {0, 20_000_000L, 10_000_000L, 6_000_000L, 3_000_000L};

    private EmbyQuality() {
    }

    public static int indexOf(long bitrate) {
        for (int i = 0; i < BITRATES.length; i++) {
            if (BITRATES[i] == bitrate) return i;
        }
        return 0;
    }

    public static long bitrateAt(int index) {
        return index < 0 || index >= BITRATES.length ? 0 : BITRATES[index];
    }

    public static boolean fits(EmbyMediaSource s, long limit) {
        return limit <= 0 || (s.bitrate > 0 && s.bitrate <= limit);
    }

    public static String mbps(long bitrate) {
        if (bitrate <= 0) return "";
        double m = bitrate / 1_000_000.0;
        return String.format(Locale.ROOT, m >= 10 ? "%.0f Mbps" : "%.1f Mbps", m);
    }
}
