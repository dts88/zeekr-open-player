package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EmbyItem {
    public static final String MOVIE = "Movie";
    public static final String SERIES = "Series";
    public static final String SEASON = "Season";
    public static final String EPISODE = "Episode";
    public static final String BOX_SET = "BoxSet";
    public static final String FOLDER = "Folder";
    public static final String COLLECTION_FOLDER = "CollectionFolder";

    public final String id;
    public final String name;
    public final String originalTitle;
    public final String type;
    public final String mediaType;
    public final String collectionType;
    public final boolean folder;
    public final String parentId;

    public final String overview;
    public final int year;
    public final String premiereDate;
    public final String dateCreated;
    public final String status;
    public final long runtimeMs;

    public final double communityRating;
    public final double criticRating;
    public final String officialRating;
    public final List<String> genres;

    public final String seriesId;
    public final String seriesName;
    public final String seasonId;
    public final String seasonName;
    public final int indexNumber;
    public final int parentIndexNumber;
    public final int indexNumberEnd;
    public final int childCount;
    public final int recursiveItemCount;

    public final String container;
    public final String path;
    public final String fileName;
    public final long size;
    public final long bitrate;
    public final int width;
    public final int height;

    public final Map<String, String> imageTags;
    public final List<String> backdropTags;
    public final double primaryImageAspectRatio;
    public final String seriesPrimaryImageTag;
    public final String parentBackdropItemId;
    public final List<String> parentBackdropTags;
    public final String parentThumbItemId;
    public final String parentThumbImageTag;

    public final EmbyUserData userData;
    public final List<EmbyMediaSource> mediaSources;
    public final List<EmbyChapter> chapters;

    private EmbyItem(JSONObject o) {
        id = Json.str(o, "Id");
        name = Json.str(o, "Name");
        originalTitle = Json.str(o, "OriginalTitle");
        type = Json.str(o, "Type");
        mediaType = Json.str(o, "MediaType");
        collectionType = Json.str(o, "CollectionType");
        folder = Json.bool(o, "IsFolder");
        parentId = Json.str(o, "ParentId");
        overview = Json.str(o, "Overview");
        year = Json.num(o, "ProductionYear", 0);
        premiereDate = Json.str(o, "PremiereDate");
        dateCreated = Json.str(o, "DateCreated");
        status = Json.str(o, "Status");
        runtimeMs = EmbyTicks.toMs(Json.lng(o, "RunTimeTicks", 0));
        communityRating = Json.dbl(o, "CommunityRating", 0);
        criticRating = Json.dbl(o, "CriticRating", 0);
        officialRating = Json.str(o, "OfficialRating");
        genres = Json.strings(o, "Genres");
        seriesId = Json.str(o, "SeriesId");
        seriesName = Json.str(o, "SeriesName");
        seasonId = Json.str(o, "SeasonId");
        seasonName = Json.str(o, "SeasonName");
        indexNumber = Json.num(o, "IndexNumber", -1);
        parentIndexNumber = Json.num(o, "ParentIndexNumber", -1);
        indexNumberEnd = Json.num(o, "IndexNumberEnd", -1);
        childCount = Json.num(o, "ChildCount", 0);
        recursiveItemCount = Json.num(o, "RecursiveItemCount", 0);
        container = Json.str(o, "Container");
        path = Json.str(o, "Path");
        fileName = Json.str(o, "FileName");
        size = Json.lng(o, "Size", 0);
        bitrate = Json.lng(o, "Bitrate", 0);
        width = Json.num(o, "Width", 0);
        height = Json.num(o, "Height", 0);
        imageTags = Json.stringMap(o, "ImageTags");
        backdropTags = Json.strings(o, "BackdropImageTags");
        primaryImageAspectRatio = Json.dbl(o, "PrimaryImageAspectRatio", 0);
        seriesPrimaryImageTag = Json.str(o, "SeriesPrimaryImageTag");
        parentBackdropItemId = Json.str(o, "ParentBackdropItemId");
        parentBackdropTags = Json.strings(o, "ParentBackdropImageTags");
        parentThumbItemId = Json.str(o, "ParentThumbItemId");
        parentThumbImageTag = Json.str(o, "ParentThumbImageTag");
        userData = EmbyUserData.fromJson(Json.obj(o, "UserData"));
        mediaSources = EmbyMediaSource.list(Json.arr(o, "MediaSources"));
        chapters = EmbyChapter.list(Json.arr(o, "Chapters"));
    }

    public static EmbyItem fromJson(JSONObject o) {
        return new EmbyItem(o);
    }

    public String primaryImageTag() {
        String tag = imageTags.get("Primary");
        return tag == null ? "" : tag;
    }

    public int seasonNumber() {
        if (EPISODE.equals(type)) return parentIndexNumber;
        if (SEASON.equals(type)) return indexNumber;
        return -1;
    }

    public int episodeNumber() {
        return EPISODE.equals(type) ? indexNumber : -1;
    }

    public long premiereMs() {
        return Json.dateMs(premiereDate);
    }

    public long dateCreatedMs() {
        return Json.dateMs(dateCreated);
    }

    static List<EmbyItem> list(JSONArray a) {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<EmbyItem> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new EmbyItem(o));
        }
        return Collections.unmodifiableList(out);
    }
}
