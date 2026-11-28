package io.github.dts88.openplayer.emby;

import org.json.JSONObject;

public final class EmbyServerInfo {
    public final String name;
    public final String version;
    public final String id;
    public final String localAddress;
    public final String wanAddress;

    private EmbyServerInfo(JSONObject o) {
        name = Json.str(o, "ServerName");
        version = Json.str(o, "Version");
        id = Json.str(o, "Id");
        localAddress = Json.str(o, "LocalAddress");
        wanAddress = Json.str(o, "WanAddress");
    }

    public static EmbyServerInfo fromJson(JSONObject o) {
        return new EmbyServerInfo(o);
    }
}
