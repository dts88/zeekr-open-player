package io.github.dts88.openplayer.emby;

import org.json.JSONException;
import org.json.JSONObject;

public final class EmbySession {
    public final String serverId;
    public final String userId;
    public final String userName;
    public final String token;

    public EmbySession(String serverId, String userId, String userName, String token) {
        this.serverId = serverId == null ? "" : serverId;
        this.userId = userId == null ? "" : userId;
        this.userName = userName == null ? "" : userName;
        this.token = token == null ? "" : token;
    }

    public String toJson() {
        JSONObject o = new JSONObject();
        Json.put(o, "serverId", serverId);
        Json.put(o, "userId", userId);
        Json.put(o, "userName", userName);
        Json.put(o, "token", token);
        return o.toString();
    }

    public static EmbySession fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            EmbySession s = new EmbySession(Json.str(o, "serverId"), Json.str(o, "userId"), Json.str(o, "userName"),
                    Json.str(o, "token"));
            return s.userId.isEmpty() || s.token.isEmpty() ? null : s;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "EmbySession[userId=" + userId + ", serverId=" + serverId + "]";
    }
}
