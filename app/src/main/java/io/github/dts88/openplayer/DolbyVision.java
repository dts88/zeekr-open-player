package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class DolbyVision {
    private static volatile Boolean hasDecoder;

    private DolbyVision() {
    }

    static boolean platformHasDecoder() {
        Boolean known = hasDecoder;
        if (known != null) return known;
        boolean found;
        try {
            found = !MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_DOLBY_VISION, false, false).isEmpty();
        } catch (MediaCodecUtil.DecoderQueryException e) {
            found = false;
        }
        hasDecoder = found;
        return found;
    }

    static boolean usesBaseLayer(Format f) {
        return f != null && MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType) && !platformHasDecoder();
    }

    static Format baseLayer(Format f) {
        if (!usesBaseLayer(f)) return f;
        return f.buildUpon().setSampleMimeType(baseMime(f.codecs)).setCodecs(null).build();
    }

    static String baseMime(String codecs) {
        if (codecs != null) {
            String c = codecs.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("dvav") || c.startsWith("dva1")) return MimeTypes.VIDEO_H264;
            if (c.startsWith("dav1")) return MimeTypes.VIDEO_AV1;
        }
        return MimeTypes.VIDEO_H265;
    }

    static int profile(String codecs) {
        if (codecs == null) return -1;
        String[] parts = codecs.trim().split("\\.");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String note(Format f) {
        return note(profile(f.codecs));
    }

    static String note(int profile) {
        switch (profile) {
            case 5: return "没有杜比视界解码器：按 HEVC 放，P5 没有兼容层，颜色会偏绿偏紫";
            case 7: return "没有杜比视界解码器：放 HDR10 基础层（颜色对，少了逐帧映射，增强层不用）";
            case 8: return "没有杜比视界解码器：放基础层（HDR10 / SDR / HLG，看片子）";
            case 4: return "没有杜比视界解码器：放 SDR 基础层（增强层不用）";
            case 9: return "没有杜比视界解码器：放 AVC 的 SDR 基础层";
            case 10: return "没有杜比视界解码器：放 AV1 基础层（车机的 AV1 只有软解，4K 放不动）";
            default: return "没有杜比视界解码器：按基础层放";
        }
    }
}
