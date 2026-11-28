package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EmbyStream {
    public static final String VIDEO = "Video";
    public static final String AUDIO = "Audio";
    public static final String SUBTITLE = "Subtitle";

    public static final String DELIVERY_ENCODE = "Encode";
    public static final String DELIVERY_EMBED = "Embed";
    public static final String DELIVERY_EXTERNAL = "External";
    public static final String DELIVERY_HLS = "Hls";

    public final String type;
    public final int index;
    public final String codec;
    public final String profile;
    public final double level;
    public final String language;
    public final String title;
    public final String displayTitle;

    public final int channels;
    public final String channelLayout;
    public final int sampleRate;
    public final int bitDepth;
    public final int bitRate;

    public final int width;
    public final int height;
    public final double frameRate;
    public final boolean interlaced;
    public final String videoRange;
    public final String colorTransfer;
    public final String colorPrimaries;
    public final String colorSpace;
    public final String extendedVideoType;
    public final String extendedVideoSubType;
    public final String extendedVideoSubTypeDescription;

    public final boolean defaultStream;
    public final boolean forced;
    public final boolean external;
    public final boolean hearingImpaired;
    public final boolean textSubtitle;

    public final String deliveryMethod;
    public final String deliveryUrl;

    private EmbyStream(JSONObject o) {
        type = Json.str(o, "Type");
        index = Json.num(o, "Index", -1);
        codec = Json.str(o, "Codec");
        profile = Json.str(o, "Profile");
        level = Json.dbl(o, "Level", -1);
        language = Json.str(o, "Language");
        title = Json.str(o, "Title");
        displayTitle = Json.str(o, "DisplayTitle");
        channels = Json.num(o, "Channels", 0);
        channelLayout = Json.str(o, "ChannelLayout");
        sampleRate = Json.num(o, "SampleRate", 0);
        bitDepth = Json.num(o, "BitDepth", 0);
        bitRate = Json.num(o, "BitRate", 0);
        width = Json.num(o, "Width", 0);
        height = Json.num(o, "Height", 0);
        double average = Json.dbl(o, "AverageFrameRate", 0);
        frameRate = average > 0 ? average : Json.dbl(o, "RealFrameRate", 0);
        interlaced = Json.bool(o, "IsInterlaced");
        videoRange = Json.str(o, "VideoRange");
        colorTransfer = Json.str(o, "ColorTransfer");
        colorPrimaries = Json.str(o, "ColorPrimaries");
        colorSpace = Json.str(o, "ColorSpace");
        extendedVideoType = Json.str(o, "ExtendedVideoType");
        extendedVideoSubType = Json.str(o, "ExtendedVideoSubType");
        extendedVideoSubTypeDescription = Json.str(o, "ExtendedVideoSubTypeDescription");
        defaultStream = Json.bool(o, "IsDefault");
        forced = Json.bool(o, "IsForced");
        external = Json.bool(o, "IsExternal");
        hearingImpaired = Json.bool(o, "IsHearingImpaired");
        textSubtitle = Json.bool(o, "IsTextSubtitleStream");
        deliveryMethod = Json.str(o, "DeliveryMethod");
        deliveryUrl = Json.str(o, "DeliveryUrl");
    }

    public static EmbyStream fromJson(JSONObject o) {
        return new EmbyStream(o);
    }

    public boolean isVideo() {
        return VIDEO.equalsIgnoreCase(type);
    }

    public boolean isAudio() {
        return AUDIO.equalsIgnoreCase(type);
    }

    public boolean isSubtitle() {
        return SUBTITLE.equalsIgnoreCase(type);
    }

    public boolean isDolbyVision() {
        return "DolbyVision".equalsIgnoreCase(extendedVideoType) || dovi() != null;
    }

    public int dolbyVisionProfile() {
        int[] p = dovi();
        return p == null ? -1 : p[0];
    }

    public int dolbyVisionCompatibility() {
        int[] p = dovi();
        return p == null ? -1 : p[1];
    }

    private int[] dovi() {
        String prefix = "DoviProfile";
        String s = extendedVideoSubType;
        if (!s.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        String digits = s.substring(prefix.length());
        if (digits.isEmpty() || digits.length() > 4) return null;
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') return null;
        }
        if (digits.length() == 1) return new int[]{digits.charAt(0) - '0', -1};
        int last = digits.length() - 1;
        return new int[]{Integer.parseInt(digits.substring(0, last)), digits.charAt(last) - '0'};
    }

    static List<EmbyStream> list(JSONArray a) {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<EmbyStream> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new EmbyStream(o));
        }
        return Collections.unmodifiableList(out);
    }
}
