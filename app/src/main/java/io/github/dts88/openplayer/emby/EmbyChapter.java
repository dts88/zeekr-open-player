package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EmbyChapter {
    public static final String CHAPTER = "Chapter";
    public static final String INTRO_START = "IntroStart";
    public static final String INTRO_END = "IntroEnd";
    public static final String CREDITS_START = "CreditsStart";

    public final long startMs;
    public final String name;
    public final String markerType;
    public final String imageTag;
    public final int index;

    private EmbyChapter(JSONObject o) {
        startMs = EmbyTicks.toMs(Json.lng(o, "StartPositionTicks", 0));
        name = Json.str(o, "Name");
        markerType = Json.str(o, "MarkerType");
        imageTag = Json.str(o, "ImageTag");
        index = Json.num(o, "ChapterIndex", -1);
    }

    public static EmbyChapter fromJson(JSONObject o) {
        return new EmbyChapter(o);
    }

    public boolean isMarker() {
        return !markerType.isEmpty() && !CHAPTER.equals(markerType);
    }

    static List<EmbyChapter> list(JSONArray a) {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<EmbyChapter> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new EmbyChapter(o));
        }
        return Collections.unmodifiableList(out);
    }
}
