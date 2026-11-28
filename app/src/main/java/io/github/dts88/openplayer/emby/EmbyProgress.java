package io.github.dts88.openplayer.emby;

import org.json.JSONObject;

public final class EmbyProgress {
    public static final String DIRECT_PLAY = "DirectPlay";
    public static final String DIRECT_STREAM = "DirectStream";
    public static final String TRANSCODE = "Transcode";

    public static final int UNSET = Integer.MIN_VALUE;

    public final String itemId;
    public final String mediaSourceId;
    public final String playSessionId;
    public final String playMethod;
    public final long positionMs;
    public final boolean paused;
    public final int audioStreamIndex;
    public final int subtitleStreamIndex;
    public final String liveStreamId;

    public EmbyProgress(String itemId, String mediaSourceId, String playSessionId, String playMethod) {
        this(itemId, mediaSourceId, playSessionId, playMethod, 0, false, UNSET, UNSET, "");
    }

    private EmbyProgress(String itemId, String mediaSourceId, String playSessionId, String playMethod, long positionMs,
                         boolean paused, int audioStreamIndex, int subtitleStreamIndex, String liveStreamId) {
        this.itemId = itemId == null ? "" : itemId;
        this.mediaSourceId = mediaSourceId == null ? "" : mediaSourceId;
        this.playSessionId = playSessionId == null ? "" : playSessionId;
        this.playMethod = playMethod == null ? "" : playMethod;
        this.positionMs = Math.max(0, positionMs);
        this.paused = paused;
        this.audioStreamIndex = audioStreamIndex;
        this.subtitleStreamIndex = subtitleStreamIndex;
        this.liveStreamId = liveStreamId == null ? "" : liveStreamId;
    }

    public EmbyProgress at(long positionMs, boolean paused) {
        return new EmbyProgress(itemId, mediaSourceId, playSessionId, playMethod, positionMs, paused, audioStreamIndex,
                subtitleStreamIndex, liveStreamId);
    }

    public EmbyProgress tracks(int audioStreamIndex, int subtitleStreamIndex) {
        return new EmbyProgress(itemId, mediaSourceId, playSessionId, playMethod, positionMs, paused, audioStreamIndex,
                subtitleStreamIndex, liveStreamId);
    }

    public EmbyProgress liveStream(String liveStreamId) {
        return new EmbyProgress(itemId, mediaSourceId, playSessionId, playMethod, positionMs, paused, audioStreamIndex,
                subtitleStreamIndex, liveStreamId);
    }

    JSONObject playingBody(String eventName) {
        JSONObject o = common();
        Json.put(o, "IsPaused", paused);
        Json.put(o, "CanSeek", true);
        if (!playMethod.isEmpty()) Json.put(o, "PlayMethod", playMethod);
        if (audioStreamIndex != UNSET) Json.put(o, "AudioStreamIndex", audioStreamIndex);
        if (subtitleStreamIndex != UNSET) Json.put(o, "SubtitleStreamIndex", subtitleStreamIndex);
        if (eventName != null) Json.put(o, "EventName", eventName);
        return o;
    }

    JSONObject stoppedBody() {
        return common();
    }

    private JSONObject common() {
        JSONObject o = new JSONObject();
        Json.put(o, "ItemId", itemId);
        if (!mediaSourceId.isEmpty()) Json.put(o, "MediaSourceId", mediaSourceId);
        if (!playSessionId.isEmpty()) Json.put(o, "PlaySessionId", playSessionId);
        if (!liveStreamId.isEmpty()) Json.put(o, "LiveStreamId", liveStreamId);
        Json.put(o, "PositionTicks", EmbyTicks.fromMs(positionMs));
        return o;
    }
}
