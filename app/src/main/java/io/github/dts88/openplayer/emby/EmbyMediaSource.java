package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EmbyMediaSource {
    public final String id;
    public final String name;
    public final String container;
    public final String path;
    public final String protocol;
    public final long size;
    public final long bitrate;
    public final long runtimeMs;

    public final boolean supportsDirectPlay;
    public final boolean supportsDirectStream;
    public final boolean supportsTranscoding;
    public final boolean remote;

    public final String directStreamUrl;
    public final boolean addApiKeyToDirectStreamUrl;
    public final String transcodingUrl;
    public final String transcodingSubProtocol;
    public final String transcodingContainer;

    public final String liveStreamId;
    public final int defaultAudioStreamIndex;
    public final int defaultSubtitleStreamIndex;
    public final Map<String, String> requiredHttpHeaders;

    public final List<EmbyStream> streams;
    public final List<EmbyChapter> chapters;

    private EmbyMediaSource(JSONObject o) {
        id = Json.str(o, "Id");
        name = Json.str(o, "Name");
        container = Json.str(o, "Container");
        path = Json.str(o, "Path");
        protocol = Json.str(o, "Protocol");
        size = Json.lng(o, "Size", 0);
        bitrate = Json.lng(o, "Bitrate", 0);
        runtimeMs = EmbyTicks.toMs(Json.lng(o, "RunTimeTicks", 0));
        supportsDirectPlay = Json.bool(o, "SupportsDirectPlay");
        supportsDirectStream = Json.bool(o, "SupportsDirectStream");
        supportsTranscoding = Json.bool(o, "SupportsTranscoding");
        remote = Json.bool(o, "IsRemote");
        directStreamUrl = Json.str(o, "DirectStreamUrl");
        addApiKeyToDirectStreamUrl = Json.bool(o, "AddApiKeyToDirectStreamUrl");
        transcodingUrl = Json.str(o, "TranscodingUrl");
        transcodingSubProtocol = Json.str(o, "TranscodingSubProtocol");
        transcodingContainer = Json.str(o, "TranscodingContainer");
        liveStreamId = Json.str(o, "LiveStreamId");
        defaultAudioStreamIndex = Json.num(o, "DefaultAudioStreamIndex", -1);
        defaultSubtitleStreamIndex = Json.num(o, "DefaultSubtitleStreamIndex", -1);
        requiredHttpHeaders = Json.stringMap(o, "RequiredHttpHeaders");
        streams = EmbyStream.list(Json.arr(o, "MediaStreams"));
        chapters = EmbyChapter.list(Json.arr(o, "Chapters"));
    }

    public static EmbyMediaSource fromJson(JSONObject o) {
        return new EmbyMediaSource(o);
    }

    public EmbyStream videoStream() {
        for (EmbyStream s : streams) {
            if (s.isVideo()) return s;
        }
        return null;
    }

    public List<EmbyStream> audioStreams() {
        return ofType(EmbyStream.AUDIO);
    }

    public List<EmbyStream> subtitleStreams() {
        return ofType(EmbyStream.SUBTITLE);
    }

    public EmbyStream stream(int index) {
        for (EmbyStream s : streams) {
            if (s.index == index) return s;
        }
        return null;
    }

    private List<EmbyStream> ofType(String type) {
        List<EmbyStream> out = new ArrayList<>();
        for (EmbyStream s : streams) {
            if (type.equalsIgnoreCase(s.type)) out.add(s);
        }
        return out;
    }

    static List<EmbyMediaSource> list(JSONArray a) {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<EmbyMediaSource> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new EmbyMediaSource(o));
        }
        return Collections.unmodifiableList(out);
    }
}
