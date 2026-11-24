package io.github.dts88.openplayer;

final class OutputGuard {
    static final float CEILING = 0.9988f;
    static final double ATTACK_SEC = 0.001;
    static final double RELEASE_SEC = 0.08;
    static final int[] HEADROOM_CHOICES_DB = {0, 3, 6};

    private volatile int headroomDb;
    private volatile float headroomGain = 1f;

    private int channels = 1;
    private int attackFrames = 48;
    private double releaseK = 1 - Math.exp(-1.0 / (RELEASE_SEC * 48000));
    private double gain = 1;
    private float[] envelope = new float[0];

    private volatile long overFrames;
    private volatile float maxPeak;
    private volatile float minGain = 1f;

    void configure(int channelCount, int sampleRate) {
        channels = Math.max(1, channelCount);
        int rate = sampleRate > 0 ? sampleRate : 48000;
        attackFrames = Math.max(1, (int) Math.round(ATTACK_SEC * rate));
        releaseK = 1 - Math.exp(-1.0 / (RELEASE_SEC * rate));
        gain = 1;
    }

    void resetState() {
        gain = 1;
    }

    void setHeadroomDb(int db) {
        headroomDb = Math.max(0, db);
        headroomGain = (float) Math.pow(10, -headroomDb / 20.0);
    }

    int headroomDb() {
        return headroomDb;
    }

    boolean altersShorts() {
        return headroomDb != 0;
    }

    boolean processFloat(float[] x, int frames) {
        final int ch = channels;
        final float hg = headroomGain;
        boolean changed = hg != 1f;
        if (envelope.length < frames) envelope = new float[frames];
        final float[] env = envelope;
        boolean anyOver = false;
        float peakAll = maxPeak;
        long over = 0;
        for (int f = 0, i = 0; f < frames; f++) {
            float pk = 0f;
            for (int c = 0; c < ch; c++, i++) {
                float v = x[i];
                if (hg != 1f) {
                    v *= hg;
                    x[i] = v;
                }
                float a = v < 0 ? -v : v;
                if (a > pk) pk = a;
            }
            if (pk > peakAll) peakAll = pk;
            if (pk > CEILING) {
                env[f] = CEILING / pk;
                anyOver = true;
                over++;
            } else {
                env[f] = 1f;
            }
        }
        maxPeak = peakAll;
        if (over > 0) overFrames += over;
        if (!anyOver && gain >= 1) return changed;

        final float step = 1f / attackFrames;
        float next = 1f;
        for (int f = frames - 1; f >= 0; f--) {
            float a = env[f];
            float ramp = next + step;
            if (ramp < a) a = ramp;
            env[f] = a;
            next = a;
        }
        double g = gain;
        float least = minGain;
        for (int f = 0, i = 0; f < frames; f++) {
            float target = env[f];
            g = target < g ? target : g + (target - g) * releaseK;
            if (g > 0.99995 && target >= 1f) g = 1;
            if (g < 1) {
                float gf = (float) g;
                for (int c = 0; c < ch; c++, i++) x[i] *= gf;
                changed = true;
                if (gf < least) least = gf;
            } else {
                i += ch;
            }
        }
        gain = g;
        minGain = least;
        return changed;
    }

    void processShorts(short[] x, int samples) {
        final float hg = headroomGain;
        for (int i = 0; i < samples; i++) x[i] = (short) Math.round(x[i] * hg);
    }
}
