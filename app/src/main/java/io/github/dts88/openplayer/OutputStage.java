package io.github.dts88.openplayer;

import java.util.Locale;

final class OutputStage {
    private final OutputGuard guard;
    private final ChannelMeter meter;
    private final TopUpmix upmix = new TopUpmix();
    private final NightMode night = new NightMode();
    private final StereoSurround surround = new StereoSurround();

    private volatile Surround51.Placement placement = Surround51.Placement.SIDE;
    private volatile int upmixDb;
    private volatile boolean nightOn;
    private volatile boolean stereoWide;
    private volatile boolean stereoSurround;
    private boolean surroundWasOn;

    private int inChannels;
    private int outChannels;
    private int sampleRate = 48000;
    private SpeakerMap map;
    private boolean upmixWasOn;
    private boolean nightWasOn;
    private int activeUpmixDb;
    private int describedState = -1;
    private volatile String description = "";

    OutputStage(OutputGuard guard, ChannelMeter meter) {
        this.guard = guard;
        this.meter = meter;
    }

    void setPlacement(Surround51.Placement p) {
        placement = p;
    }

    Surround51.Placement placement() {
        return placement;
    }

    void setUpmixDb(int db) {
        upmixDb = Math.max(0, db);
    }

    int upmixDb() {
        return upmixDb;
    }

    void setNight(boolean on) {
        nightOn = on;
    }

    boolean night() {
        return nightOn;
    }

    void setStereoWide(boolean on) {
        stereoWide = on;
    }

    boolean stereoWide() {
        return stereoWide;
    }

    void setStereoSurround(boolean on) {
        stereoSurround = on;
    }

    boolean stereoSurround() {
        return stereoSurround;
    }

    boolean wide(int channels) {
        return channels >= 3 || (channels == 2 && (stereoWide || stereoSurround));
    }

    int configure(int channels, int rate) {
        inChannels = Math.max(0, channels);
        sampleRate = rate > 0 ? rate : 48000;
        if (inChannels == 0) {
            map = null;
            outChannels = 0;
            meter.configure(0);
            description = "";
            return 0;
        }
        map = wide(inChannels) ? SpeakerMap.of(inChannels, placement) : null;
        outChannels = map != null ? SpeakerMap.OUT_CHANNELS : inChannels;
        guard.configure(outChannels, sampleRate);
        meter.configure(outChannels);
        upmix.configure(sampleRate);
        night.configure(outChannels, sampleRate);
        surround.configure(sampleRate);
        surroundWasOn = false;
        upmixWasOn = false;
        nightWasOn = false;
        activeUpmixDb = 0;
        describedState = -1;
        describe();
        return outChannels;
    }

    int outChannels() {
        return outChannels;
    }

    void flush() {
        upmix.reset();
        night.reset();
        surround.reset();
        guard.resetState();
        upmixWasOn = false;
        nightWasOn = false;
    }

    void process(float[] in, int frames, float[] out) {
        SpeakerMap m = map;
        int upmixNow = 0;
        boolean surroundNow = false;
        if (m != null) {
            Surround51.Placement p = placement;
            if (m.placement != p) {
                m = SpeakerMap.of(inChannels, p);
                map = m;
                describedState = -1;
            }
            m.map(in, frames, out);
            surroundNow = inChannels == 2 && stereoSurround;
            if (surroundNow) {
                if (!surroundWasOn) surround.reset();
                surround.process(out, frames);
            }
            int db = upmixDb;
            if (db > 0 && !m.hasTops && m.frontLeft >= 0) {
                if (!upmixWasOn) upmix.reset();
                upmix.setAttenuationDb(db);
                upmix.process(in, inChannels, frames, m.frontLeft, m.frontRight, m.rearLeft, m.rearRight, out);
                upmixNow = db;
            }
        } else {
            System.arraycopy(in, 0, out, 0, frames * inChannels);
        }
        upmixWasOn = upmixNow > 0;
        surroundWasOn = surroundNow;
        activeUpmixDb = upmixNow;
        boolean nightNow = nightOn;
        if (nightNow) {
            if (!nightWasOn) night.reset();
            night.process(out, frames);
        }
        nightWasOn = nightNow;
        int state = upmixNow * 4 + (surroundNow ? 2 : 0) + (nightNow ? 1 : 0);
        if (state != describedState) {
            describedState = state;
            describe();
        }
        guard.processFloat(out, frames);
        meter.addFloat(out, frames);
    }

    private void describe() {
        if (inChannels == 0) {
            description = "";
            return;
        }
        StringBuilder s = new StringBuilder();
        SpeakerMap m = map;
        if (m != null) {
            s.append(m.describe());
            if (surroundWasOn) s.append(Tx.t("；立体声转环绕（中置、侧面、后面）", "; stereo to surround (centre, side, rear)"));
            if (m.hasTops) {
                s.append(Tx.t("；影片含顶部声道，不上混", "; source has height channels, no upmix"));
            } else if (activeUpmixDb > 0) {
                s.append(String.format(Locale.ROOT, Tx.t("；顶部上混 −%d dB", "; height upmix −%d dB"), activeUpmixDb));
            } else {
                s.append(Tx.t("；顶部无声（未开启上混）", "; height speakers silent (upmix off)"));
            }
        } else {
            s.append(SpeakerMap.layoutName(inChannels)).append(Tx.t("按 ", " sent as ")).append(inChannels).append(Tx.t(" 声道输出", " ch"));
        }
        if (nightWasOn) s.append(Tx.t("；夜间模式已开启", "; night mode on"));
        description = s.toString();
    }

    String description() {
        return description;
    }

    float nightReductionDb() {
        return nightOn ? night.reductionDb() : 0f;
    }
}
