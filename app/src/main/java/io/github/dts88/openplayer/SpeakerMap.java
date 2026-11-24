package io.github.dts88.openplayer;

import java.util.Arrays;

final class SpeakerMap {
    static final int OUT_CHANNELS = 12;
    static final int FL = 0;
    static final int FR = 1;
    static final int FC = 2;
    static final int LFE = 3;
    static final int BL = 4;
    static final int BR = 5;
    static final int SL = 6;
    static final int SR = 7;
    static final int TFL = 8;
    static final int TFR = 9;
    static final int TBL = 10;
    static final int TBR = 11;

    enum Role { FL, FR, FC, LFE, LS, RS, BL, BR, SL, SR, BC, TFL, TFR, TBL, TBR }

    private static final float HALF_POWER = 0.70710678f;

    final int channels;
    final Surround51.Placement placement;
    private final int[] slotA;
    private final int[] slotB;
    private final float[] gainA;
    private final float[] gainB;
    final boolean hasTops;
    final int frontLeft;
    final int frontRight;
    final int rearLeft;
    final int rearRight;
    private final Role[] roles;

    private SpeakerMap(int channels, Surround51.Placement placement, Role[] roles) {
        this.channels = channels;
        this.placement = placement;
        this.roles = roles;
        slotA = new int[channels];
        slotB = new int[channels];
        gainA = new float[channels];
        gainB = new float[channels];
        Arrays.fill(slotB, -1);
        boolean tops = false;
        int fl = -1;
        int fr = -1;
        int ls = -1;
        int rs = -1;
        int bl = -1;
        int br = -1;
        for (int c = 0; c < channels; c++) {
            Role r = c < roles.length ? roles[c] : null;
            if (r == null) {
                slotA[c] = c < OUT_CHANNELS ? c : -1;
                gainA[c] = 1f;
                if (c >= TFL && c < OUT_CHANNELS) tops = true;
                continue;
            }
            gainA[c] = 1f;
            switch (r) {
                case FL: slotA[c] = FL; fl = c; break;
                case FR: slotA[c] = FR; fr = c; break;
                case FC: slotA[c] = FC; break;
                case LFE: slotA[c] = LFE; break;
                case BL: slotA[c] = BL; bl = c; break;
                case BR: slotA[c] = BR; br = c; break;
                case SL: slotA[c] = SL; break;
                case SR: slotA[c] = SR; break;
                case TFL: slotA[c] = TFL; tops = true; break;
                case TFR: slotA[c] = TFR; tops = true; break;
                case TBL: slotA[c] = TBL; tops = true; break;
                case TBR: slotA[c] = TBR; tops = true; break;
                case BC:
                    slotA[c] = BL;
                    slotB[c] = BR;
                    gainA[c] = HALF_POWER;
                    gainB[c] = HALF_POWER;
                    break;
                case LS:
                case RS:
                    boolean left = r == Role.LS;
                    if (left) ls = c; else rs = c;
                    if (placement == Surround51.Placement.BACK) {
                        slotA[c] = left ? BL : BR;
                    } else if (placement == Surround51.Placement.BOTH) {
                        slotA[c] = left ? SL : SR;
                        gainA[c] = Surround51.BOTH_SIDE;
                        slotB[c] = left ? BL : BR;
                        gainB[c] = Surround51.BOTH_BACK;
                    } else {
                        slotA[c] = left ? SL : SR;
                    }
                    break;
                default:
                    slotA[c] = -1;
            }
        }
        hasTops = tops;
        frontLeft = fl;
        frontRight = fr;
        rearLeft = bl >= 0 ? bl : ls;
        rearRight = br >= 0 ? br : rs;
    }

    static SpeakerMap of(int channels, Surround51.Placement placement) {
        return new SpeakerMap(channels, placement, rolesFor(channels));
    }

    static Role[] rolesFor(int channels) {
        switch (channels) {
            case 2: return new Role[]{Role.FL, Role.FR};
            case 3: return new Role[]{Role.FL, Role.FR, Role.FC};
            case 4: return new Role[]{Role.FL, Role.FR, Role.LS, Role.RS};
            case 5: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LS, Role.RS};
            case 6: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LFE, Role.LS, Role.RS};
            case 7: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LFE, Role.BC, Role.LS, Role.RS};
            case 8: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LFE, Role.BL, Role.BR, Role.SL, Role.SR};
            case 10: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LFE, Role.LS, Role.RS,
                    Role.TFL, Role.TFR, Role.TBL, Role.TBR};
            case 12: return new Role[]{Role.FL, Role.FR, Role.FC, Role.LFE, Role.BL, Role.BR, Role.SL, Role.SR,
                    Role.TFL, Role.TFR, Role.TBL, Role.TBR};
            default: return new Role[0];
        }
    }

    static String layoutName(int channels) {
        switch (channels) {
            case 1: return Tx.t("单声道", "Mono");
            case 2: return Tx.t("立体声", "Stereo");
            case 3: return "3.0";
            case 4: return "4.0";
            case 5: return "5.0";
            case 6: return "5.1";
            case 7: return "6.1";
            case 8: return "7.1";
            case 10: return "5.1.4";
            case 12: return "7.1.4";
            default: return channels + Tx.t(" 声道", " ch");
        }
    }

    void map(float[] in, int frames, float[] out) {
        final int ch = channels;
        Arrays.fill(out, 0, frames * OUT_CHANNELS, 0f);
        for (int f = 0, i = 0, o = 0; f < frames; f++, o += OUT_CHANNELS) {
            for (int c = 0; c < ch; c++, i++) {
                float v = in[i];
                if (v == 0f) continue;
                int a = slotA[c];
                if (a >= 0) out[o + a] += v * gainA[c];
                int b = slotB[c];
                if (b >= 0) out[o + b] += v * gainB[c];
            }
        }
    }

    int slotA(int c) {
        return slotA[c];
    }

    int slotB(int c) {
        return slotB[c];
    }

    float gainA(int c) {
        return gainA[c];
    }

    float gainB(int c) {
        return gainB[c];
    }

    String describe() {
        StringBuilder s = new StringBuilder();
        s.append(layoutName(channels)).append(Tx.t(" → 7.1.4（12 声道）", " → 7.1.4 (12 ch)"));
        boolean pair = false;
        boolean backCenter = false;
        for (Role r : roles) {
            if (r == Role.LS) pair = true;
            if (r == Role.BC) backCenter = true;
        }
        if (roles.length == 0) s.append(Tx.t("（未知布局，按顺序映射至前 ", " (unknown layout, mapped in order to the first ")).append(Math.min(channels, OUT_CHANNELS)).append(Tx.t(" 声道）", " ch)"));
        if (pair) {
            s.append(Tx.t("，环绕声：", ", surrounds: ")).append(placement == Surround51.Placement.BACK ? Tx.t("后面", "rear")
                    : placement == Surround51.Placement.BOTH ? Tx.t("侧面 + 后面", "side + rear") : Tx.t("侧面", "side"));
        }
        if (backCenter) s.append(Tx.t("，后中分配至后左、后右（各 −3 dB）", ", back centre split to rear left and right (−3 dB each)"));
        return s.toString();
    }
}
