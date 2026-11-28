package io.github.dts88.openplayer.emby;

import org.json.JSONObject;

import java.util.List;

public final class EmbyPage {
    public final List<EmbyItem> items;
    public final int totalCount;

    EmbyPage(List<EmbyItem> items, int totalCount) {
        this.items = items;
        this.totalCount = totalCount;
    }

    public static EmbyPage fromJson(JSONObject o) {
        List<EmbyItem> items = EmbyItem.list(Json.arr(o, "Items"));
        return new EmbyPage(items, Json.num(o, "TotalRecordCount", items.size()));
    }
}
