package io.github.dts88.openplayer;

final class TopUpmix {
    static final int[] GAIN_CHOICES_DB = {12, 9, 6};
    static final float HIGH_PASS_HZ = 250f;
    static final float FRONT_DELAY_MS = 12f;
    static final float REAR_DELAY_MS = 18f;
    private static final float ALL_PASS_GAIN = 0.5f;
    private static final int[][] ALL_PASS_DELAYS = {{113, 337}, {127, 359}, {149, 389}, {167, 401}};

    private final Path[] paths = new Path[4];
    private float gain = dbToGain(-GAIN_CHOICES_DB[1]);
    private int sampleRate;

    private static final class Path {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;
        private final float[] delay;
        private int delayAt;
        private final float[] ap1;
        private int ap1At;
        private final float[] ap2;
        private int ap2At;

        Path(int sampleRate, float delayMs, int[] allPass) {
            double w = 2 * Math.PI * HIGH_PASS_HZ / sampleRate;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
            double a0 = 1 + alpha;
            b0 = (1 + cos) / 2 / a0;
            b1 = -(1 + cos) / a0;
            b2 = (1 + cos) / 2 / a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
            double scale = sampleRate / 48000.0;
            delay = new float[Math.max(1, (int) Math.round(delayMs / 1000.0 * sampleRate))];
            ap1 = new float[Math.max(1, (int) Math.round(allPass[0] * scale))];
            ap2 = new float[Math.max(1, (int) Math.round(allPass[1] * scale))];
        }

        float step(float in) {
            double hp = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = in;
            y2 = y1;
            y1 = hp;
            float delayed = delay[delayAt];
            delay[delayAt] = (float) hp;
            if (++delayAt == delay.length) delayAt = 0;
            float v = ap1[ap1At];
            float y = -ALL_PASS_GAIN * delayed + v;
            ap1[ap1At] = delayed + ALL_PASS_GAIN * y;
            if (++ap1At == ap1.length) ap1At = 0;
            v = ap2[ap2At];
            float z = -ALL_PASS_GAIN * y + v;
            ap2[ap2At] = y + ALL_PASS_GAIN * z;
            if (++ap2At == ap2.length) ap2At = 0;
            return z;
        }
    }

    void configure(int rate) {
        sampleRate = rate > 0 ? rate : 48000;
        reset();
    }

    void reset() {
        if (sampleRate == 0) sampleRate = 48000;
        for (int i = 0; i < 4; i++) {
            paths[i] = new Path(sampleRate, i < 2 ? FRONT_DELAY_MS : REAR_DELAY_MS, ALL_PASS_DELAYS[i]);
        }
    }

    void setAttenuationDb(int db) {
        gain = dbToGain(-Math.abs(db));
    }

    void process(float[] in, int inChannels, int frames, int frontLeft, int frontRight, int rearLeft, int rearRight,
                 float[] out) {
        if (paths[0] == null) reset();
        final float g = gain;
        final int[] source = {frontLeft, frontRight, rearLeft, rearRight};
        for (int p = 0; p < 4; p++) {
            int s = source[p];
            if (s < 0 || s >= inChannels) continue;
            Path path = paths[p];
            int slot = SpeakerMap.TFL + p;
            for (int f = 0, i = s, o = slot; f < frames; f++, i += inChannels, o += SpeakerMap.OUT_CHANNELS) {
                out[o] += path.step(in[i]) * g;
            }
        }
    }

    private static float dbToGain(float db) {
        return (float) Math.pow(10, db / 20.0);
    }
}
