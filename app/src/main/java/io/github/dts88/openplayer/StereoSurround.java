package io.github.dts88.openplayer;

final class StereoSurround {
    static final float CENTER_GAIN = 0.354f;
    static final float SURROUND_GAIN = 0.5f;
    static final float REAR_GAIN = 0.70710678f;
    static final float HIGH_PASS_HZ = 120f;
    static final float LOW_PASS_HZ = 7000f;
    static final float SIDE_DELAY_MS = 12f;
    static final float REAR_DELAY_MS = 22f;

    private int sampleRate = 48000;
    private float hpA;
    private float lpA;
    private float hpPrevIn;
    private float hpPrevOut;
    private float lpPrev;
    private float[] delay = new float[1];
    private int at;
    private int sideBack = 1;
    private int rearBack = 1;

    void configure(int rate) {
        sampleRate = rate > 0 ? rate : 48000;
        reset();
    }

    void reset() {
        double dt = 1.0 / sampleRate;
        double rcHigh = 1.0 / (2 * Math.PI * HIGH_PASS_HZ);
        hpA = (float) (rcHigh / (rcHigh + dt));
        double rcLow = 1.0 / (2 * Math.PI * LOW_PASS_HZ);
        lpA = (float) (dt / (rcLow + dt));
        sideBack = Math.max(1, Math.round(SIDE_DELAY_MS * sampleRate / 1000f));
        rearBack = Math.max(1, Math.round(REAR_DELAY_MS * sampleRate / 1000f));
        delay = new float[rearBack + 1];
        at = 0;
        hpPrevIn = 0;
        hpPrevOut = 0;
        lpPrev = 0;
    }

    void process(float[] out, int frames) {
        final int n = SpeakerMap.OUT_CHANNELS;
        final int len = delay.length;
        for (int f = 0, o = 0; f < frames; f++, o += n) {
            float l = out[o + SpeakerMap.FL];
            float r = out[o + SpeakerMap.FR];
            out[o + SpeakerMap.FC] += (l + r) * CENTER_GAIN;
            float diff = (l - r) * SURROUND_GAIN;
            float hp = hpA * (hpPrevOut + diff - hpPrevIn);
            hpPrevIn = diff;
            hpPrevOut = hp;
            lpPrev += lpA * (hp - lpPrev);
            delay[at] = lpPrev;
            float side = delay[(at - sideBack + len) % len];
            float rear = delay[(at - rearBack + len) % len] * REAR_GAIN;
            if (++at == len) at = 0;
            out[o + SpeakerMap.SL] += side;
            out[o + SpeakerMap.SR] -= side;
            out[o + SpeakerMap.BL] += rear;
            out[o + SpeakerMap.BR] -= rear;
        }
    }
}
