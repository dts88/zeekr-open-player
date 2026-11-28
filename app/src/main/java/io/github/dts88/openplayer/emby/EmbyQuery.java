package io.github.dts88.openplayer.emby;

import java.util.ArrayList;
import java.util.List;

public final class EmbyQuery {
    public static final String SORT_NAME = "SortName";
    public static final String SORT_DATE_CREATED = "DateCreated";
    public static final String SORT_DATE_PLAYED = "DatePlayed";
    public static final String SORT_PREMIERE_DATE = "PremiereDate";
    public static final String SORT_PRODUCTION_YEAR = "ProductionYear";
    public static final String SORT_COMMUNITY_RATING = "CommunityRating";
    public static final String SORT_RUNTIME = "Runtime";
    public static final String SORT_RANDOM = "Random";

    public static final String LIST_FIELDS = "PrimaryImageAspectRatio,ProductionYear,RunTimeTicks,CommunityRating,"
            + "OfficialRating,UserDataPlayCount,UserDataLastPlayedDate";

    private String parentId;
    private String types;
    private boolean recursive;
    private String sortBy;
    private boolean descending;
    private int startIndex;
    private int limit;
    private String searchTerm;
    private boolean favoritesOnly;
    private String fields;
    private final List<String[]> extra = new ArrayList<>();

    public EmbyQuery parent(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public EmbyQuery types(String... types) {
        this.types = join(types);
        return this;
    }

    public EmbyQuery recursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    public EmbyQuery sort(String sortBy, boolean descending) {
        this.sortBy = sortBy;
        this.descending = descending;
        return this;
    }

    public EmbyQuery page(int startIndex, int limit) {
        this.startIndex = Math.max(0, startIndex);
        this.limit = Math.max(0, limit);
        return this;
    }

    public EmbyQuery search(String term) {
        this.searchTerm = term == null ? null : term.trim();
        if (this.searchTerm != null && !this.searchTerm.isEmpty()) this.recursive = true;
        return this;
    }

    public EmbyQuery favorites(boolean only) {
        this.favoritesOnly = only;
        return this;
    }

    public EmbyQuery fields(String... fields) {
        this.fields = join(fields);
        return this;
    }

    public EmbyQuery with(String name, String value) {
        extra.add(new String[]{name, value});
        return this;
    }

    String requestedFields() {
        return fields;
    }

    void writeTo(Params p) {
        p.add("ParentId", parentId);
        p.add("IncludeItemTypes", types);
        if (recursive) p.add("Recursive", true);
        p.add("SearchTerm", searchTerm);
        if (favoritesOnly) p.add("IsFavorite", true);
        if (sortBy != null && !sortBy.isEmpty()) {
            p.add("SortBy", sortBy);
            p.add("SortOrder", descending ? "Descending" : "Ascending");
        }
        if (startIndex > 0) p.add("StartIndex", startIndex);
        if (limit > 0) p.add("Limit", limit);
        for (String[] e : extra) p.add(e[0], e[1]);
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String s : parts) {
                if (s == null || s.trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(s.trim());
            }
        }
        return sb.toString();
    }
}
