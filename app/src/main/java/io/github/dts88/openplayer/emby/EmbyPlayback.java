package io.github.dts88.openplayer.emby;

import org.json.JSONObject;

import java.util.List;

public final class EmbyPlayback {
    public static final String NOT_ALLOWED = "NotAllowed";
    public static final String NO_COMPATIBLE_STREAM = "NoCompatibleStream";
    public static final String RATE_LIMIT_EXCEEDED = "RateLimitExceeded";

    public final String playSessionId;
    public final List<EmbyMediaSource> mediaSources;
    public final String errorCode;

    private EmbyPlayback(JSONObject o) {
        playSessionId = Json.str(o, "PlaySessionId");
        mediaSources = EmbyMediaSource.list(Json.arr(o, "MediaSources"));
        errorCode = Json.str(o, "ErrorCode");
    }

    public static EmbyPlayback fromJson(JSONObject o) {
        return new EmbyPlayback(o);
    }

    public boolean failed() {
        return !errorCode.isEmpty();
    }
}
