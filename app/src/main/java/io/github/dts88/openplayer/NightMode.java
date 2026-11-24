package io.github.dts88.openplayer;

final class NightMode {
    static final float THRESHOLD_DB = -30f;
    static final float RATIO = 3f;
    static final float KNEE_DB = 8f;
    static final float MAKEUP_DB = 8f;
    static final double ATTACK_SEC = 0.005;
    static final double RELEASE_SEC = 0.30;

    private int channels = 2;
    private double attackK;
    private double releaseK;
    private double reductionDb;
    private volatile float lastReductionDb;

    void configure(int channelCount, int sampleRate) {
        channels = Math.max(1, channelCount);
        int rate = sampleRate > 0 ? sampleRate : 48000;
        attackK = 1 - Math.exp(-1.0 / (ATTACK_SEC * rate));
        releaseK = 1 - Math.exp(-1.0 / (RELEASE_SEC * rate));
        reductionDb = 0;
    }

    void reset() {
        reductionDb = 0;
        lastReductionDb = 0;
    }

    static float staticReductionDb(float levelDb) {
        float over = levelDb - THRESHOLD_DB;
        float slope = 1f / RATIO - 1f;
        if (2 * over <= -KNEE_DB) return 0f;
        if (2 * over < KNEE_DB) {
            float x = over + KNEE_DB / 2;
            return slope * x * x / (2 * KNEE_DB);
        }
        return slope * over;
    }

    void process(float[] x, int frames) {
        final int ch = channels;
        if (attackK == 0) configure(ch, 48000);
        double gr = reductionDb;
        for (int f = 0, i = 0; f < frames; f++) {
            float peak = 0f;
            for (int c = 0; c < ch; c++) {
                float a = x[i + c];
                if (a < 0) a = -a;
                if (a > peak) peak = a;
            }
            float levelDb = peak > 1e-6f ? (float) (20 * Math.log10(peak)) : -120f;
            double target = staticReductionDb(levelDb);
            gr += (target - gr) * (target < gr ? attackK : releaseK);
            float gain = (float) Math.pow(10, (gr + MAKEUP_DB) / 20.0);
            for (int c = 0; c < ch; c++, i++) x[i] *= gain;
        }
        reductionDb = gr;
        lastReductionDb = (float) gr;
    }

    float reductionDb() {
        return lastReductionDb;
    }
}
