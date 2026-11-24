package io.github.dts88.openplayer;

final class ChannelMeter {
    static final int MAX_CHANNELS = 24;
    static final float SILENT_DB = -90f;

    private static final String[] ZH = {"前左", "前右", "中置", "低音", "后左", "后右", "侧左", "侧右",
            "顶前左", "顶前右", "顶后左", "顶后右"};
    private static final String[] EN = {"FL", "FR", "C", "LFE", "RL", "RR", "SL", "SR", "TFL", "TFR", "TRL", "TRR"};

    private volatile int channels;
    private final float[] live = new float[MAX_CHANNELS];

    void configure(int channelCount) {
        channels = Math.max(0, Math.min(channelCount, MAX_CHANNELS));
    }

    void addFloat(float[] buf, int frameCount) {
        int c = channels;
        if (c == 0) return;
        for (int f = 0, i = 0; f < frameCount; f++) {
            for (int ch = 0; ch < c; ch++, i++) {
                float v = Math.abs(buf[i]);
                if (v > live[ch]) live[ch] = v;
            }
        }
    }

    float[] takeLiveDb() {
        int c = channels;
        float[] out = new float[c];
        for (int ch = 0; ch < c; ch++) {
            out[ch] = toDb(live[ch]);
            live[ch] = 0f;
        }
        return out;
    }

    static float toDb(float v) {
        if (v <= 0f) return SILENT_DB;
        return (float) Math.max(SILENT_DB, 20 * Math.log10(v));
    }

    static String[] names(int channels) {
        String[] all = Tx.chinese() ? ZH : EN;
        String[] n = new String[Math.max(0, channels)];
        switch (channels) {
            case 1:
                n[0] = Tx.t("单声道", "Mono");
                return n;
            case 2:
                n[0] = Tx.t("左", "L");
                n[1] = Tx.t("右", "R");
                return n;
            case 3:
            case 6:
            case 8:
            case 12:
                System.arraycopy(all, 0, n, 0, channels);
                return n;
            case 10:
                System.arraycopy(all, 0, n, 0, 6);
                System.arraycopy(all, 8, n, 6, 4);
                return n;
            default:
                for (int i = 0; i < n.length; i++) n[i] = String.valueOf(i + 1);
                return n;
        }
    }
}
