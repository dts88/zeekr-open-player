package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class EmbyDeviceProfile {
    public static final long DEFAULT_MAX_BITRATE = 200_000_000L;

    private static final String CONTAINERS = "mkv,webm,mp4,m4v,mov,ts,mpegts,m2ts,mts";
    private static final String[] TRANSCODE_VIDEO = {"h264", "hevc"};
    private static final String[] TRANSCODE_AUDIO = {"ac3", "eac3", "aac"};

    private static final String[][] SUBTITLES = {
            {"subrip", "Embed"}, {"srt", "Embed"}, {"ass", "Embed"}, {"ssa", "Embed"}, {"webvtt", "Embed"},
            {"mov_text", "Embed"}, {"pgssub", "Embed"}, {"dvdsub", "Embed"}, {"dvbsub", "Embed"},
            {"srt", "External"}, {"ass", "External"}, {"ssa", "External"}, {"vtt", "External"}, {"vtt", "Hls"}};

    private EmbyDeviceProfile() {
    }

    public static JSONObject build(Collection<String> videoCodecs, Collection<String> audioCodecs, long maxBitrate) {
        return build(videoCodecs, audioCodecs, maxBitrate, CONTAINERS);
    }

    public static JSONObject build(Collection<String> videoCodecs, Collection<String> audioCodecs, long maxBitrate,
                                   String containers) {
        List<String> video = clean(videoCodecs);
        List<String> audio = clean(audioCodecs);

        JSONObject profile = new JSONObject();
        Json.put(profile, "Name", "Open Player 7x4");
        Json.put(profile, "MaxStreamingBitrate", maxBitrate > 0 ? maxBitrate : DEFAULT_MAX_BITRATE);

        JSONArray direct = new JSONArray();
        if (!video.isEmpty() && !audio.isEmpty()) {
            List<String> anywhere = new ArrayList<>(video);
            anywhere.removeAll(EmbyCaps.LIMITED_VIDEO);
            List<String> limited = new ArrayList<>(video);
            limited.retainAll(EmbyCaps.LIMITED_VIDEO);
            if (!anywhere.isEmpty()) direct.put(directProfile(containers, anywhere, audio));
            String few = EmbyCaps.limitedContainers(containers);
            if (!limited.isEmpty() && !few.isEmpty()) direct.put(directProfile(few, limited, audio));
        }
        Json.put(profile, "DirectPlayProfiles", direct);

        JSONObject t = new JSONObject();
        Json.put(t, "Type", "Video");
        Json.put(t, "Context", "Streaming");
        Json.put(t, "Protocol", "hls");
        Json.put(t, "Container", "ts");
        Json.put(t, "VideoCodec", join(pick(TRANSCODE_VIDEO, video, "h264")));
        Json.put(t, "AudioCodec", join(pick(TRANSCODE_AUDIO, audio, "aac")));
        Json.put(t, "MaxAudioChannels", "6");
        Json.put(t, "MinSegments", 1);
        Json.put(t, "SegmentLength", 3);
        Json.put(t, "BreakOnNonKeyFrames", true);
        Json.put(t, "ManifestSubtitles", "vtt");
        Json.put(profile, "TranscodingProfiles", new JSONArray().put(t));

        Json.put(profile, "ContainerProfiles", new JSONArray());

        JSONArray codecs = new JSONArray();
        if (video.contains("h264")) {
            codecs.put(codecProfile("Video", "h264", atMost("VideoBitDepth", "8"), atMost("VideoLevel", "52")));
        }
        if (video.contains("hevc")) {
            codecs.put(codecProfile("Video", "hevc", atMost("VideoBitDepth", "10"), atMost("VideoLevel", "153")));
        }
        codecs.put(codecProfile("VideoAudio", null, atMost("AudioChannels", "8")));
        Json.put(profile, "CodecProfiles", codecs);

        JSONArray subtitles = new JSONArray();
        for (String[] s : SUBTITLES) {
            JSONObject o = new JSONObject();
            Json.put(o, "Format", s[0]);
            Json.put(o, "Method", s[1]);
            subtitles.put(o);
        }
        Json.put(profile, "SubtitleProfiles", subtitles);

        Json.put(profile, "ResponseProfiles", new JSONArray());
        return profile;
    }

    private static JSONObject directProfile(String containers, List<String> video, List<String> audio) {
        JSONObject d = new JSONObject();
        Json.put(d, "Type", "Video");
        Json.put(d, "Container", containers);
        Json.put(d, "VideoCodec", join(video));
        Json.put(d, "AudioCodec", join(audio));
        return d;
    }

    private static JSONObject codecProfile(String type, String codec, JSONObject... conditions) {
        JSONObject o = new JSONObject();
        Json.put(o, "Type", type);
        if (codec != null) Json.put(o, "Codec", codec);
        JSONArray a = new JSONArray();
        for (JSONObject c : conditions) a.put(c);
        Json.put(o, "Conditions", a);
        return o;
    }

    private static JSONObject atMost(String property, String value) {
        JSONObject o = new JSONObject();
        Json.put(o, "Condition", "LessThanEqual");
        Json.put(o, "Property", property);
        Json.put(o, "Value", value);
        Json.put(o, "IsRequired", false);
        return o;
    }

    private static List<String> pick(String[] wanted, List<String> supported, String fallback) {
        List<String> out = new ArrayList<>();
        for (String w : wanted) {
            if (supported.contains(w)) out.add(w);
        }
        if (out.isEmpty()) out.add(fallback);
        return out;
    }

    private static List<String> clean(Collection<String> codecs) {
        List<String> out = new ArrayList<>();
        if (codecs == null) return out;
        for (String c : codecs) {
            if (c == null) continue;
            String s = c.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }
}
