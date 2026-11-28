package io.github.dts88.openplayer.emby;

import org.json.JSONObject;

public final class EmbyUserData {
    static final EmbyUserData NONE = new EmbyUserData(null);

    public final long positionMs;
    public final boolean played;
    public final boolean favorite;
    public final int playCount;
    public final String lastPlayedDate;
    public final double playedPercentage;
    public final int unplayedItemCount;

    private EmbyUserData(JSONObject o) {
        positionMs = EmbyTicks.toMs(Json.lng(o, "PlaybackPositionTicks", 0));
        played = Json.bool(o, "Played");
        favorite = Json.bool(o, "IsFavorite");
        playCount = Json.num(o, "PlayCount", 0);
        lastPlayedDate = Json.str(o, "LastPlayedDate");
        playedPercentage = Json.dbl(o, "PlayedPercentage", -1);
        unplayedItemCount = Json.num(o, "UnplayedItemCount", -1);
    }

    public static EmbyUserData fromJson(JSONObject o) {
        return o == null ? NONE : new EmbyUserData(o);
    }

    public long lastPlayedMs() {
        return Json.dateMs(lastPlayedDate);
    }
}
