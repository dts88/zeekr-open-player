package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class VideoLook {
    static final int[] TARGET_NITS = {203, 300, 500};
    static String[] targetLabels() {
        return new String[]{Tx.t("偏亮", "Brighter"), Tx.t("标准", "Standard"), Tx.t("偏暗", "Darker")};
    }
    static final int DEFAULT_TARGET = 1;

    final int transfer;
    final int standard;
    final boolean fullRange;
    final int bitDepth;
    final float srcPeakNits;
    final float dstPeakNits;
    final boolean hdrOutput;
    final boolean process;
    final int doviProfile;
    final boolean declared;
    final boolean wideGamut;

    private VideoLook(int transfer, int standard, boolean fullRange, int bitDepth, float srcPeakNits,
                      float dstPeakNits, boolean hdrOutput, boolean process, int doviProfile, boolean declared,
                      boolean wideGamut) {
        this.transfer = transfer;
        this.standard = standard;
        this.fullRange = fullRange;
        this.bitDepth = bitDepth;
        this.srcPeakNits = srcPeakNits;
        this.dstPeakNits = dstPeakNits;
        this.hdrOutput = hdrOutput;
        this.process = process;
        this.doviProfile = doviProfile;
        this.declared = declared;
        this.wideGamut = wideGamut;
    }

    static VideoLook plain() {
        return new VideoLook(HdrMath.SDR, YuvMatrix.BT709, false, 8, 1000, TARGET_NITS[DEFAULT_TARGET], false, false,
                -1, false, false);
    }

    static VideoLook of(Format f, int targetIndex, boolean hdrOutput, boolean process, boolean doviReady) {
        return of(f, targetIndex, hdrOutput, process, doviReady, false);
    }

    static VideoLook of(Format f, int targetIndex, boolean hdrOutput, boolean process, boolean doviReady,
                        boolean wideGamut) {
        ColorInfo c = f.colorInfo;
        int bits = bitDepth(f);
        int profile = MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType) || startsWithDv(f.codecs)
                ? Math.max(0, DolbyVision.profile(f.codecs)) : -1;
        boolean declared = c != null && c.colorTransfer != Format.NO_VALUE && c.colorSpace != Format.NO_VALUE;

        int transfer;
        if (profile == 5) {
            transfer = doviReady ? HdrMath.DOVI : HdrMath.SDR;
        } else if (c != null && c.colorTransfer == C.COLOR_TRANSFER_ST2084) {
            transfer = HdrMath.PQ;
        } else if (c != null && c.colorTransfer == C.COLOR_TRANSFER_HLG) {
            transfer = HdrMath.HLG;
        } else if (profile == 7 || (profile == 8 && (c == null || c.colorTransfer == Format.NO_VALUE))) {
            transfer = HdrMath.PQ;
        } else {
            transfer = HdrMath.SDR;
        }

        int standard;
        if (c != null && c.colorSpace == C.COLOR_SPACE_BT2020) {
            standard = YuvMatrix.BT2020;
        } else if (c != null && c.colorSpace == C.COLOR_SPACE_BT601) {
            standard = YuvMatrix.BT601;
        } else if (c != null && c.colorSpace == C.COLOR_SPACE_BT709) {
            standard = YuvMatrix.BT709;
        } else if (transfer != HdrMath.SDR || bits >= 10) {
            standard = YuvMatrix.BT2020;
        } else {
            standard = f.height > 0 && f.height <= 576 ? YuvMatrix.BT601 : YuvMatrix.BT709;
        }
        boolean full = (c != null && c.colorRange == C.COLOR_RANGE_FULL) || transfer == HdrMath.DOVI;

        int[] peaks = HdrMath.staticPeaks(c == null ? null : c.hdrStaticInfo);
        float srcPeak = transfer == HdrMath.HLG ? HdrMath.HLG_PEAK_NITS : HdrMath.sourcePeakNits(peaks[0], peaks[1]);
        int target = TARGET_NITS[Math.max(0, Math.min(TARGET_NITS.length - 1, targetIndex))];
        return new VideoLook(transfer, standard, full, bits, srcPeak, target,
                hdrOutput && transfer != HdrMath.SDR, process, profile, declared, wideGamut);
    }

    private static boolean startsWithDv(String codecs) {
        if (codecs == null) return false;
        String c = codecs.trim().toLowerCase(Locale.ROOT);
        return c.startsWith("dvh") || c.startsWith("dva") || c.startsWith("dav1");
    }

    static int bitDepth(Format f) {
        ColorInfo c = f.colorInfo;
        if (c != null && c.lumaBitdepth > 0) return c.lumaBitdepth;
        String codecs = f.codecs == null ? "" : f.codecs;
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType) || codecs.startsWith("dv")) return 10;
        if (codecs.startsWith("hvc1.2") || codecs.startsWith("hev1.2")) return 10;
        return 8;
    }

    boolean hdr() {
        return transfer != HdrMath.SDR || doviProfile == 5;
    }

    boolean same(VideoLook o) {
        return o != null && transfer == o.transfer && standard == o.standard && fullRange == o.fullRange
                && bitDepth == o.bitDepth && srcPeakNits == o.srcPeakNits && dstPeakNits == o.dstPeakNits
                && hdrOutput == o.hdrOutput && process == o.process && doviProfile == o.doviProfile
                && wideGamut == o.wideGamut;
    }

    float[] gamut() {
        if (transfer != HdrMath.SDR || standard == YuvMatrix.BT2020) {
            return wideGamut ? HdrMath.BT2020_TO_PANEL : HdrMath.BT2020_TO_BT709;
        }
        return wideGamut ? HdrMath.BT709_TO_PANEL : HdrMath.IDENTITY;
    }

    float[] lumaIn() {
        return transfer != HdrMath.SDR || standard == YuvMatrix.BT2020 ? HdrMath.LUMA_2020 : HdrMath.LUMA_709;
    }

    boolean sdrGamut() {
        return wideGamut && process && transfer == HdrMath.SDR && doviProfile != 5;
    }

    String sourceText() {
        String peak = String.format(Locale.ROOT, Tx.t("（峰值 %.0f 尼特）", " (peak %.0f nits)"), srcPeakNits);
        if (doviProfile == 5) return Tx.t("杜比视界 P5", "Dolby Vision P5");
        String dv = doviProfile > 0 ? Tx.t("杜比视界 P", "Dolby Vision P") + doviProfile + Tx.t(" 基础层，", " base layer, ") : "";
        switch (transfer) {
            case HdrMath.PQ:
                return dv + "HDR10" + peak;
            case HdrMath.HLG:
                return dv + "HLG";
            default:
                return dv + "SDR";
        }
    }

    String describe() {
        if (!process) return sourceText() + Tx.t("，不处理（直通）", ", unprocessed (passthrough)");
        if (doviProfile == 5 && transfer != HdrMath.DOVI) {
            return Tx.t("杜比视界 P5：无法转换色彩，按原样播放（色彩会偏绿偏紫）", "Dolby Vision P5: colours cannot be converted, shown as is (green and purple tint)");
        }
        if (transfer == HdrMath.SDR) return sourceText() + (sdrGamut() ? Tx.t("，按广色域屏换算", ", converted for the wide-gamut display") : Tx.t("，直接显示", ", shown directly"));
        if (hdrOutput) return sourceText() + Tx.t(" → HDR10 直出", " → HDR10 output");
        return sourceText() + String.format(Locale.ROOT, Tx.t(" → 映射为 SDR（目标 %.0f 尼特%s）", " → mapped to SDR (target %.0f nits%s)"), dstPeakNits,
                wideGamut ? Tx.t("，按广色域屏", ", wide-gamut display") : "");
    }
}
