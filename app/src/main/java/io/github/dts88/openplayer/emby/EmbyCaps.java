package io.github.dts88.openplayer.emby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EmbyCaps {
    public static final String CONTAINERS = "mkv,webm,mp4,m4v,mov,ts,mpegts,m2ts,mts,avi,flv";

    private static final String[][] VIDEO = {
            {"video/hevc", "hevc"}, {"video/avc", "h264"}, {"video/av01", "av1"}, {"video/x-vnd.on2.vp9", "vp9"},
            {"video/x-vnd.on2.vp8", "vp8"}, {"video/mpeg2", "mpeg2video"}, {"video/mp4v-es", "mpeg4"},
            {"video/3gpp", "h263"}, {"video/wvc1", "vc1"}};

    public static final List<String> LIMITED_VIDEO = Collections.unmodifiableList(Arrays.asList("mpeg4", "h263"));
    private static final List<String> LIMITED_CONTAINERS = Collections.unmodifiableList(Arrays.asList("avi", "mp4", "m4v", "mov"));

    public static String limitedContainers(String containers) {
        StringBuilder out = new StringBuilder();
        for (String c : containers.toLowerCase(Locale.ROOT).split(",")) {
            if (LIMITED_CONTAINERS.contains(c.trim())) out.append(out.length() > 0 ? "," : "").append(c.trim());
        }
        return out.toString();
    }

    private static boolean limitedOk(String container) {
        for (String c : container.toLowerCase(Locale.ROOT).split(",")) {
            if (LIMITED_CONTAINERS.contains(c.trim())) return true;
        }
        return false;
    }

    private static final List<String> VIDEO_FALLBACK = Collections.unmodifiableList(Arrays.asList("hevc", "h264"));

    public static final List<String> AUDIO = Collections.unmodifiableList(Arrays.asList("truehd", "dts", "eac3", "ac3",
            "flac", "aac", "opus", "vorbis", "mp3", "alac", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_bluray", "pcm_dvd"));

    private EmbyCaps() {
    }

    public static List<String> videoCodecs(Collection<String> decoderMimes) {
        List<String> out = new ArrayList<>();
        if (decoderMimes != null) {
            List<String> lower = new ArrayList<>();
            for (String m : decoderMimes) {
                if (m != null) lower.add(m.trim().toLowerCase(Locale.ROOT));
            }
            for (String[] v : VIDEO) {
                if (lower.contains(v[0])) out.add(v[1]);
            }
        }
        return out.isEmpty() ? VIDEO_FALLBACK : out;
    }

    public static boolean unprobed(EmbyMediaSource s) {
        return s.streams.isEmpty();
    }

    public static boolean containerOk(String container) {
        if (container == null || container.isEmpty()) return false;
        List<String> ours = Arrays.asList(CONTAINERS.split(","));
        for (String c : container.toLowerCase(Locale.ROOT).split(",")) {
            if (ours.contains(c.trim())) return true;
        }
        return false;
    }

    public static boolean looksPlayable(EmbyMediaSource s, Collection<String> video) {
        if (!containerOk(s.container)) return false;
        if (unprobed(s)) return true;
        EmbyStream v = s.videoStream();
        if (v != null) {
            String codec = v.codec.toLowerCase(Locale.ROOT);
            if (!video.contains(codec)) return false;
            if (LIMITED_VIDEO.contains(codec) && !limitedOk(s.container)) return false;
            if (codec.equals("h264") && v.bitDepth > 8) return false;
            if (codec.equals("hevc") && v.bitDepth > 10) return false;
        }
        List<EmbyStream> audio = s.audioStreams();
        if (audio.isEmpty()) return true;
        for (EmbyStream a : audio) {
            if (AUDIO.contains(a.codec.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
