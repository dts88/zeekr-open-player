package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public final class MediaLabels {
    private MediaLabels() {
    }

    public static String resolution(int width, int height) {
        if (width <= 0 || height <= 0) return "";
        if (width >= 7000 || height >= 4000) return "8K";
        if (width >= 3200 || height >= 1800) return "4K";
        if (width >= 2400 || height >= 1300) return "1440p";
        if (width >= 1800 || height >= 1000) return "1080p";
        if (width >= 1200 || height >= 700) return "720p";
        if (height >= 560) return "576p";
        if (height >= 400) return "480p";
        return width + "×" + height;
    }

    public static String dynamicRange(Format f) {
        int profile = dolbyVisionProfile(f);
        if (profile >= 0) return Tx.t("杜比视界", "Dolby Vision") + (profile > 0 ? " P" + profile : "");
        ColorInfo c = f.colorInfo;
        if (c == null) return "";
        if (c.colorTransfer == C.COLOR_TRANSFER_ST2084) return "HDR10";
        if (c.colorTransfer == C.COLOR_TRANSFER_HLG) return "HLG";
        return "";
    }

    public static int dolbyVisionProfile(Format f) {
        String codecs = f.codecs == null ? "" : f.codecs.toLowerCase(Locale.ROOT);
        boolean dv = MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType);
        int at = -1;
        for (String tag : new String[]{"dvhe.", "dvh1.", "dvav.", "dva1.", "dav1."}) {
            int i = codecs.indexOf(tag);
            if (i >= 0) at = i + tag.length();
        }
        if (at < 0) return dv ? 0 : -1;
        int end = at;
        while (end < codecs.length() && Character.isDigit(codecs.charAt(end))) end++;
        try {
            return end > at ? Integer.parseInt(codecs.substring(at, end)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String videoCodec(Format f) {
        String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
        String codecs = f.codecs == null ? "" : f.codecs.toLowerCase(Locale.ROOT);
        if (m.equals(MimeTypes.VIDEO_H265) || codecs.startsWith("hvc1") || codecs.startsWith("hev1")
                || codecs.startsWith("dvhe") || codecs.startsWith("dvh1")) {
            return "HEVC";
        }
        if (m.equals(MimeTypes.VIDEO_H264) || codecs.startsWith("avc") || codecs.startsWith("dva")) return "H.264";
        if (m.equals(MimeTypes.VIDEO_AV1) || codecs.startsWith("av01") || codecs.startsWith("dav1")) return "AV1";
        if (m.equals(MimeTypes.VIDEO_VP9)) return "VP9";
        if (m.equals(MimeTypes.VIDEO_MPEG2)) return "MPEG-2";
        if (m.equals(MimeTypes.VIDEO_VC1)) return "VC-1";
        if (m.equals(MimeTypes.VIDEO_DOLBY_VISION)) return "HEVC";
        return m.startsWith("video/") ? m.substring(6) : m;
    }

    public static String videoLabel(Format f) {
        String res = resolution(f.width, f.height);
        String range = dynamicRange(f);
        return range.isEmpty() ? res : res.isEmpty() ? range : res + " · " + range;
    }

    public static String audioCodec(Format f) {
        String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
        switch (m) {
            case MimeTypes.AUDIO_TRUEHD:
                return "TrueHD";
            case MimeTypes.AUDIO_DTS_HD:
                return "DTS-HD";
            case MimeTypes.AUDIO_DTS:
            case MimeTypes.AUDIO_DTS_EXPRESS:
                return "DTS";
            case MimeTypes.AUDIO_E_AC3_JOC:
                return Tx.t("杜比数字+ 全景声", "Dolby Digital Plus Atmos");
            case MimeTypes.AUDIO_E_AC3:
                return Tx.t("杜比数字+", "Dolby Digital Plus");
            case MimeTypes.AUDIO_AC3:
                return Tx.t("杜比数字", "Dolby Digital");
            case MimeTypes.AUDIO_AC4:
                return "AC-4";
            case MimeTypes.AUDIO_FLAC:
                return "FLAC";
            case MimeTypes.AUDIO_ALAC:
                return "ALAC";
            case MimeTypes.AUDIO_AAC:
                return "AAC";
            case MimeTypes.AUDIO_MPEG:
                return "MP3";
            case MimeTypes.AUDIO_OPUS:
                return "Opus";
            case MimeTypes.AUDIO_VORBIS:
                return "Vorbis";
            case MimeTypes.AUDIO_RAW:
                return "PCM";
            default:
                return m.startsWith("audio/") ? m.substring(6) : m;
        }
    }

    public static String channels(int count) {
        switch (count) {
            case 1:
                return Tx.t("单声道", "Mono");
            case 2:
                return "2.0";
            case 3:
                return "3.0";
            case 4:
                return "4.0";
            case 5:
                return "5.0";
            case 6:
                return "5.1";
            case 7:
                return "6.1";
            case 8:
                return "7.1";
            case 10:
                return "5.1.4";
            case 12:
                return "7.1.4";
            default:
                return count > 0 ? count + Tx.t(" 声道", " ch") : "";
        }
    }

    public static String audioLabel(Format f) {
        StringBuilder s = new StringBuilder(audioCodec(f));
        String ch = channels(f.channelCount);
        if (!ch.isEmpty()) s.append(' ').append(ch);
        if (f.sampleRate > 48000) {
            s.append(" · ").append(f.sampleRate % 1000 == 0 ? String.valueOf(f.sampleRate / 1000)
                    : String.format(Locale.ROOT, "%.1f", f.sampleRate / 1000.0)).append(" kHz");
        }
        return s.toString();
    }

    public static int audioRank(Format f) {
        String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
        int base;
        switch (m) {
            case MimeTypes.AUDIO_TRUEHD:
            case MimeTypes.AUDIO_RAW:
            case MimeTypes.AUDIO_FLAC:
            case MimeTypes.AUDIO_ALAC:
            case MimeTypes.AUDIO_DTS_HD:
                base = 300;
                break;
            case MimeTypes.AUDIO_E_AC3_JOC:
                base = 200;
                break;
            case MimeTypes.AUDIO_E_AC3:
            case MimeTypes.AUDIO_DTS:
                base = 150;
                break;
            default:
                base = 100;
        }
        return base + Math.max(0, f.channelCount);
    }

    public static String duration(long ms) {
        if (ms <= 0) return "";
        long s = ms / 1000;
        if (s >= 3600) return (s / 3600) + Tx.t(" 小时 ", " h ") + (s % 3600 / 60) + Tx.t(" 分", " min");
        if (s >= 600) return (s / 60) + Tx.t(" 分钟", " min");
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static String size(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.ROOT, "%.0f MB", bytes / (double) (1L << 20));
        return Math.max(1, bytes >> 10) + " KB";
    }
}
