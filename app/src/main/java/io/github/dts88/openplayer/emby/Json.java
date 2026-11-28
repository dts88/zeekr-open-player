package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {
    }

    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        Object v = o.opt(key);
        return v == null ? "" : String.valueOf(v);
    }

    static int num(JSONObject o, String key, int fallback) {
        return o == null ? fallback : o.optInt(key, fallback);
    }

    static long lng(JSONObject o, String key, long fallback) {
        return o == null ? fallback : o.optLong(key, fallback);
    }

    static double dbl(JSONObject o, String key, double fallback) {
        if (o == null) return fallback;
        double v = o.optDouble(key, fallback);
        return Double.isNaN(v) || Double.isInfinite(v) ? fallback : v;
    }

    static boolean bool(JSONObject o, String key) {
        return o != null && o.optBoolean(key, false);
    }

    static JSONObject obj(JSONObject o, String key) {
        return o == null ? null : o.optJSONObject(key);
    }

    static JSONArray arr(JSONObject o, String key) {
        return o == null ? null : o.optJSONArray(key);
    }

    static List<String> strings(JSONObject o, String key) {
        JSONArray a = arr(o, key);
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<String> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            if (a.isNull(i)) continue;
            String s = String.valueOf(a.opt(i));
            if (!s.isEmpty()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    static Map<String, String> stringMap(JSONObject o, String key) {
        JSONObject m = obj(o, key);
        if (m == null || m.length() == 0) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (Iterator<String> it = m.keys(); it.hasNext(); ) {
            String k = it.next();
            String v = str(m, k);
            if (!v.isEmpty()) out.put(k, v);
        }
        return Collections.unmodifiableMap(out);
    }

    static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static long dateMs(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (RuntimeException notOffset) {
            try {
                return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (RuntimeException unreadable) {
                return 0;
            }
        }
    }
}
