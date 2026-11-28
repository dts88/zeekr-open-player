package io.github.dts88.openplayer.emby;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import io.github.dts88.openplayer.Tx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class EmbyClient {
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 30_000;

    private static final String LIST_IMAGES = "Primary,Backdrop,Thumb";
    private static String badResponseText() {
        return Tx.t("无法识别服务器响应，目标可能不是 Emby 服务器", "The response was not recognised; this may not be an Emby server");
    }

    private static String notFoundText() {
        return Tx.t("服务器上未找到此内容", "Not found on the server");
    }

    private static final Pattern SECRET_PARAM = Pattern.compile(
            "(?i)([?&](?:api_key|apikey|x-emby-token|x-mediabrowser-token|token|accesstoken|access_token)=)[^&#\\s]*");
    private static final Pattern HAS_API_KEY = Pattern.compile("(?i)[?&]api_key=");

    private final String root;
    private final String base;
    private final EmbyDevice device;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private volatile EmbySession session;

    public EmbyClient(String baseUrl, EmbyDevice device) {
        this(baseUrl, device, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    public EmbyClient(String baseUrl, EmbyDevice device, int connectTimeoutMs, int readTimeoutMs) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.toLowerCase(Locale.ROOT).endsWith("/emby")) u = u.substring(0, u.length() - "/emby".length());
        this.root = u;
        this.base = u + "/emby";
        this.device = device == null ? new EmbyDevice("", "", "", "") : device;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public EmbySession login(String user, String password) throws EmbyException {
        JSONObject body = new JSONObject();
        Json.put(body, "Username", user == null ? "" : user);
        Json.put(body, "Pw", password == null ? "" : password);
        Response r = call("POST", "/Users/AuthenticateByName", null, body, null, true);
        JSONObject o = object(r);
        JSONObject u = Json.obj(o, "User");
        String token = Json.str(o, "AccessToken");
        String userId = Json.str(u, "Id");
        if (token.isEmpty() || userId.isEmpty()) {
            throw new EmbyException(EmbyException.Kind.BAD_RESPONSE, r.status, badResponseText(), r.where, "", null);
        }
        String name = Json.str(u, "Name");
        String serverId = Json.str(o, "ServerId");
        EmbySession s = new EmbySession(serverId.isEmpty() ? Json.str(u, "ServerId") : serverId, userId,
                name.isEmpty() && user != null ? user : name, token);
        session = s;
        return s;
    }

    public void resume(EmbySession saved) {
        session = saved;
    }

    public EmbySession session() {
        return session;
    }

    public void logout() throws EmbyException {
        EmbySession s = session;
        if (s == null) return;
        try {
            call("POST", "/Sessions/Logout", null, null, s, false);
        } finally {
            session = null;
        }
    }

    public EmbyServerInfo publicInfo() throws EmbyException {
        return EmbyServerInfo.fromJson(object(call("GET", "/System/Info/Public", null, null, null, false)));
    }

    public List<EmbyItem> views() throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("IncludeExternalContent", false);
        return itemList(call("GET", "/Users/" + seg(s.userId) + "/Views", p, null, s, false));
    }

    public EmbyPage items(EmbyQuery q) throws EmbyException {
        EmbySession s = need();
        EmbyQuery query = q == null ? new EmbyQuery() : q;
        Params p = new Params();
        query.writeTo(p);
        String fields = query.requestedFields();
        listExtras(p, fields != null ? fields : EmbyQuery.LIST_FIELDS);
        return page(call("GET", "/Users/" + seg(s.userId) + "/Items", p, null, s, false));
    }

    public List<EmbyItem> latest(String parentId, int limit) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("ParentId", parentId);
        if (limit > 0) p.add("Limit", limit);
        listExtras(p, EmbyQuery.LIST_FIELDS);
        return itemList(call("GET", "/Users/" + seg(s.userId) + "/Items/Latest", p, null, s, false));
    }

    public List<EmbyItem> resumeItems(int limit) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("Recursive", true).add("MediaTypes", "Video");
        if (limit > 0) p.add("Limit", limit);
        listExtras(p, EmbyQuery.LIST_FIELDS);
        return itemList(call("GET", "/Users/" + seg(s.userId) + "/Items/Resume", p, null, s, false));
    }

    public List<EmbyItem> nextUp(String seriesId, int limit) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("UserId", s.userId).add("LegacyNextUp", true).add("SeriesId", seriesId);
        if (limit > 0) p.add("Limit", limit);
        listExtras(p, EmbyQuery.LIST_FIELDS);
        return itemList(call("GET", "/Shows/NextUp", p, null, s, false));
    }

    public List<EmbyItem> seasons(String seriesId) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("UserId", s.userId);
        listExtras(p, EmbyQuery.LIST_FIELDS);
        return itemList(call("GET", "/Shows/" + seg(seriesId) + "/Seasons", p, null, s, false));
    }

    public List<EmbyItem> episodes(String seriesId, String seasonId) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("UserId", s.userId).add("SeasonId", seasonId);
        listExtras(p, EmbyQuery.LIST_FIELDS + ",Overview,MediaSources");
        return itemList(call("GET", "/Shows/" + seg(seriesId) + "/Episodes", p, null, s, false));
    }

    public EmbyItem item(String id) throws EmbyException {
        EmbySession s = need();
        String path = "/Users/" + seg(s.userId) + "/Items/" + seg(id);
        return EmbyItem.fromJson(object(call("GET", path, null, null, s, false)));
    }

    public EmbyPlayback playbackInfo(String itemId, String mediaSourceId, long maxBitrate, JSONObject deviceProfile)
            throws EmbyException {
        return playbackInfo(itemId, mediaSourceId, maxBitrate, deviceProfile, 0, EmbyProgress.UNSET,
                EmbyProgress.UNSET);
    }

    public EmbyPlayback playbackInfo(String itemId, String mediaSourceId, long maxBitrate, JSONObject deviceProfile,
                                     long startMs, int audioStreamIndex, int subtitleStreamIndex) throws EmbyException {
        return playbackInfo(itemId, mediaSourceId, maxBitrate, deviceProfile, startMs, audioStreamIndex,
                subtitleStreamIndex, true, true, true);
    }

    public EmbyPlayback playbackInfo(String itemId, String mediaSourceId, long maxBitrate, JSONObject deviceProfile,
                                     long startMs, int audioStreamIndex, int subtitleStreamIndex, boolean direct,
                                     boolean videoCopy, boolean audioCopy) throws EmbyException {
        EmbySession s = need();
        JSONObject body = new JSONObject();
        Json.put(body, "UserId", s.userId);
        if (maxBitrate > 0) Json.put(body, "MaxStreamingBitrate", maxBitrate);
        if (mediaSourceId != null && !mediaSourceId.isEmpty()) Json.put(body, "MediaSourceId", mediaSourceId);
        if (startMs > 0) Json.put(body, "StartTimeTicks", EmbyTicks.fromMs(startMs));
        if (audioStreamIndex != EmbyProgress.UNSET) Json.put(body, "AudioStreamIndex", audioStreamIndex);
        if (subtitleStreamIndex != EmbyProgress.UNSET) Json.put(body, "SubtitleStreamIndex", subtitleStreamIndex);
        Json.put(body, "IsPlayback", true);
        Json.put(body, "AutoOpenLiveStream", true);
        if (!direct) {
            Json.put(body, "EnableDirectPlay", false);
            Json.put(body, "EnableDirectStream", false);
        }
        if (!videoCopy) Json.put(body, "AllowVideoStreamCopy", false);
        if (!audioCopy) Json.put(body, "AllowAudioStreamCopy", false);
        if (deviceProfile != null) Json.put(body, "DeviceProfile", deviceProfile);
        String path = "/Items/" + seg(itemId) + "/PlaybackInfo";
        return EmbyPlayback.fromJson(object(call("POST", path, null, body, s, false)));
    }

    public void stopEncoding(String playSessionId) throws EmbyException {
        EmbySession s = need();
        Params p = new Params().add("DeviceId", device.deviceId).add("PlaySessionId", playSessionId);
        call("DELETE", "/Videos/ActiveEncodings", p, null, s, false);
    }

    public String directStreamUrl(String itemId, EmbyMediaSource source) {
        return directStreamUrl(itemId, source, null);
    }

    public String directStreamUrl(String itemId, EmbyMediaSource source, String playSessionId) {
        if (source != null && !source.directStreamUrl.isEmpty()) {
            String url = absoluteUrl(source.directStreamUrl);
            return source.addApiKeyToDirectStreamUrl ? withApiKey(url) : url;
        }
        if (itemId == null || itemId.isEmpty()) return "";
        StringBuilder url = new StringBuilder(base).append("/Videos/").append(Params.encode(itemId)).append("/stream");
        String extension = source == null ? "" : extension(source);
        if (!extension.isEmpty()) url.append('.').append(extension);
        Params p = new Params().add("Static", true);
        if (source != null) p.add("MediaSourceId", source.id);
        p.add("DeviceId", device.deviceId);
        p.add("PlaySessionId", playSessionId);
        return url.append('?').append(p).toString();
    }

    public Map<String, String> authHeaders() {
        return Collections.unmodifiableMap(headers(session));
    }

    public String absoluteUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) return "";
        String lower = serverUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return serverUrl;
        String path = serverUrl.startsWith("/") ? serverUrl : "/" + serverUrl;
        return lower.startsWith("/emby/") || lower.startsWith("emby/") ? root + path : base + path;
    }

    public String relativeUrl(String url) {
        return isOwnUrl(url) ? url.substring(root.length()) : url;
    }

    public boolean isOwnUrl(String url) {
        if (url == null || root.isEmpty() || !url.regionMatches(true, 0, root, 0, root.length())) return false;
        if (url.length() == root.length()) return true;
        char next = url.charAt(root.length());
        return next == '/' || next == '?';
    }

    public String withApiKey(String url) {
        EmbySession s = session;
        if (url == null || s == null || s.token.isEmpty() || !isOwnUrl(url) || HAS_API_KEY.matcher(url).find()) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + "api_key=" + Params.encode(s.token);
    }

    public static String redactUrl(String url) {
        return url == null ? "" : SECRET_PARAM.matcher(url).replaceAll("$1***");
    }

    public String imageUrl(String itemId, String imageType, String tag, int maxWidth) {
        if (itemId == null || itemId.isEmpty()) return "";
        Params p = new Params();
        if (maxWidth > 0) p.add("MaxWidth", maxWidth);
        p.add("Tag", tag);
        String type = imageType == null || imageType.isEmpty() ? "Primary" : imageType;
        String url = base + "/Items/" + Params.encode(itemId) + "/Images/" + Params.encode(type);
        return p.isEmpty() ? url : url + "?" + p;
    }

    public void reportStart(EmbyProgress p) throws EmbyException {
        call("POST", "/Sessions/Playing", null, p.playingBody(null), need(), false);
    }

    public void reportProgress(EmbyProgress p) throws EmbyException {
        call("POST", "/Sessions/Playing/Progress", null, p.playingBody("TimeUpdate"), need(), false);
    }

    public void reportStopped(EmbyProgress p) throws EmbyException {
        call("POST", "/Sessions/Playing/Stopped", null, p.stoppedBody(), need(), false);
    }

    public void setPlayed(String itemId, boolean played) throws EmbyException {
        EmbySession s = need();
        String path = "/Users/" + seg(s.userId) + "/PlayedItems/" + seg(itemId);
        call("POST", played ? path : path + "/Delete", null, null, s, false);
    }

    public void setFavorite(String itemId, boolean favorite) throws EmbyException {
        EmbySession s = need();
        String path = "/Users/" + seg(s.userId) + "/FavoriteItems/" + seg(itemId);
        call("POST", favorite ? path : path + "/Delete", null, null, s, false);
    }

    @Override
    public String toString() {
        EmbySession s = session;
        return "EmbyClient[" + base + (s == null ? Tx.t("，没登录", ", not signed in") : Tx.t("，", ", ") + s) + "]";
    }

    private static final class Response {
        final int status;
        final String text;
        final String where;

        Response(int status, String text, String where) {
            this.status = status;
            this.text = text;
            this.where = where;
        }
    }

    private EmbySession need() throws EmbyException {
        EmbySession s = session;
        if (s == null || s.userId.isEmpty() || s.token.isEmpty()) {
            throw new EmbyException(EmbyException.Kind.UNAUTHORIZED, 0, Tx.t("尚未登录 Emby", "Not signed in to Emby"), "", "", null);
        }
        return s;
    }

    private static void listExtras(Params p, String fields) {
        p.add("Fields", fields);
        p.add("ImageTypeLimit", 1);
        p.add("EnableImageTypes", LIST_IMAGES);
    }

    private Map<String, String> headers(EmbySession s) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("X-Emby-Authorization", device.authorization(s == null ? null : s.userId));
        if (s != null && !s.token.isEmpty()) h.put("X-Emby-Token", EmbyDevice.headerSafe(s.token));
        return h;
    }

    private Response call(String method, String path, Params query, JSONObject body, EmbySession as, boolean login)
            throws EmbyException {
        String where = method + " /emby" + path;
        String url = base + path + (query == null || query.isEmpty() ? "" : "?" + query);
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
            c.setUseCaches(false);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> h : headers(as).entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            if ("POST".equals(method)) {
                byte[] bytes = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                if (body != null) c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = c.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new EmbyException(EmbyException.Kind.SERVER, status,
                        Tx.t("服务器返回重定向。请检查服务器地址，需直接填写 Emby 的地址与端口", "The server answered with a redirect. Check the address: enter the Emby address and port directly"), where, "", null);
            }
            String text = read(status >= 400 ? c.getErrorStream() : c.getInputStream());
            if (status >= 200 && status < 300) return new Response(status, text, where);
            throw failure(status, where, text, as, login);
        } catch (SocketTimeoutException e) {
            throw new EmbyException(EmbyException.Kind.NETWORK, 0, Tx.t("Emby 服务器无响应（超时）", "The Emby server did not respond (timed out)"), where, "", e);
        } catch (IOException e) {
            throw new EmbyException(EmbyException.Kind.NETWORK, 0, Tx.t("无法连接 Emby 服务器", "Cannot reach the Emby server"), where, "", e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = stream.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static EmbyException failure(int status, String where, String text, EmbySession as, boolean login) {
        String server = login ? "" : scrub(text, as);
        switch (status) {
            case 401:
                return new EmbyException(EmbyException.Kind.UNAUTHORIZED, status,
                        login ? Tx.t("用户名或密码错误", "Wrong user name or password") : Tx.t("登录已失效，请重新登录", "The sign-in has expired; please sign in again"), where, server, null);
            case 403:
                return new EmbyException(EmbyException.Kind.FORBIDDEN, status,
                        login ? Tx.t("服务器拒绝此账号登录", "The server refused this account") : Tx.t("此账号无权限", "This account has no permission"), where, server, null);
            case 404:
                return new EmbyException(EmbyException.Kind.NOT_FOUND, status, notFoundText(), where, server, null);
            default:
                return new EmbyException(EmbyException.Kind.SERVER, status,
                        (status >= 500 ? Tx.t("Emby 服务器出错", "The Emby server reported an error") : Tx.t("Emby 服务器拒绝了请求", "The Emby server rejected the request")) + Tx.t("（HTTP ", " (HTTP ") + status + Tx.t("）", ")"),
                        where, server, null);
        }
    }

    private static String scrub(String text, EmbySession as) {
        if (text == null) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        if (as != null && !as.token.isEmpty()) t = t.replace(as.token, "***");
        t = redactUrl(t);
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    private static Object parse(Response r) throws EmbyException {
        try {
            Object v = new JSONTokener(r.text).nextValue();
            if (v instanceof JSONObject || v instanceof JSONArray) return v;
        } catch (JSONException unreadable) {
        }
        throw new EmbyException(EmbyException.Kind.BAD_RESPONSE, r.status, badResponseText(), r.where, "", null);
    }

    private static JSONObject object(Response r) throws EmbyException {
        Object v = parse(r);
        if (v instanceof JSONObject) return (JSONObject) v;
        throw new EmbyException(EmbyException.Kind.BAD_RESPONSE, r.status, badResponseText(), r.where, "", null);
    }

    private static List<EmbyItem> itemList(Response r) throws EmbyException {
        Object v = parse(r);
        if (v instanceof JSONArray) return EmbyItem.list((JSONArray) v);
        return EmbyItem.list(Json.arr((JSONObject) v, "Items"));
    }

    private static EmbyPage page(Response r) throws EmbyException {
        Object v = parse(r);
        if (v instanceof JSONObject) return EmbyPage.fromJson((JSONObject) v);
        List<EmbyItem> items = EmbyItem.list((JSONArray) v);
        return new EmbyPage(items, items.size());
    }

    private static String seg(String id) throws EmbyException {
        if (id == null || id.isEmpty()) {
            throw new EmbyException(EmbyException.Kind.NOT_FOUND, 0, notFoundText(), "", "", null);
        }
        return Params.encode(id);
    }

    private static String extension(EmbyMediaSource source) {
        String path = source.path;
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fromPath = dot > slash ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (fromPath.matches("[a-z0-9]{1,8}")) return fromPath;
        String container = source.container.trim().toLowerCase(Locale.ROOT);
        return container.matches("[a-z0-9]{1,8}") ? container : "";
    }
}
