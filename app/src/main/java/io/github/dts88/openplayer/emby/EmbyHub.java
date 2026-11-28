package io.github.dts88.openplayer.emby;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.Tx;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmbyHub implements PlayerEngine.Listener {
    public interface Done<T> {
        void done(T value, EmbyException error);
    }

    private interface Call<T> {
        T run(EmbyClient client) throws EmbyException;
    }

    public static final int PAGE_SIZE = 96;
    public static final int SHAPE_PORTRAIT = 0;
    public static final int SHAPE_WIDE = 1;
    public static final int SHAPE_SQUARE = 2;

    private static final String PREFS = "op-emby";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_SESSION = "session";
    private static final String KEY_DEVICE = "device";
    private static final String KEY_QUALITY = "quality_bps";
    private static final String CLIENT_NAME = "Open Player";
    private static final int DEFAULT_PORT = 8096;
    private static final long POLL_MS = 970;
    private static final int REPORT_EVERY_POLLS = 11;
    private static final long RESUME_MIN_MS = 9000;
    private static final float RESUME_TAIL = 0.97f;
    private static final int QUEUE_AHEAD = 13;
    private static final String[] SEARCH_TYPES = {EmbyItem.MOVIE, EmbyItem.SERIES, EmbyItem.EPISODE, "Video", "MusicAlbum",
            "Audio", EmbyItem.BOX_SET};

    private static EmbyHub instance;

    public static synchronized EmbyHub get(Context context) {
        if (instance == null) instance = new EmbyHub(context.getApplicationContext());
        return instance;
    }

    private static final class Playing {
        final String itemId;
        final String sourceId;
        final String playSessionId;
        final String method;
        long positionMs;
        boolean paused;
        int polls;

        Playing(String itemId, String sourceId, String playSessionId, String method) {
            this.itemId = itemId;
            this.sourceId = sourceId;
            this.playSessionId = playSessionId;
            this.method = method;
        }

        EmbyProgress progress() {
            return new EmbyProgress(itemId, sourceId, playSessionId, method).at(positionMs, paused);
        }
    }

    private static final class Resolved {
        final PlayerEngine.Item item;
        final Playing playing;

        Resolved(PlayerEngine.Item item, Playing playing) {
            this.item = item;
            this.playing = playing;
        }
    }

    private static final class Prepared {
        final List<PlayerEngine.Item> items = new ArrayList<>();
        final Map<String, Playing> map = new HashMap<>();
        int at;
    }

    private final Context app;
    private final PlayerEngine engine;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService reporter = Executors.newSingleThreadExecutor();
    private final Map<String, Playing> queued = new HashMap<>();
    private final String deviceId;
    private String address;
    private EmbyClient client;
    private Playing playing;
    private int revision;
    private long quality;
    private volatile List<String> videoCodecs;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            Playing p = playing;
            if (p == null) return;
            ExoPlayer player = engine.player();
            if (player.getPlaybackState() != Player.STATE_IDLE) {
                p.positionMs = Math.max(0, player.getCurrentPosition());
                p.paused = !player.getPlayWhenReady();
            }
            if (++p.polls % REPORT_EVERY_POLLS == 0) report(p, 1);
            ui.postDelayed(this, POLL_MS);
        }
    };

    private EmbyHub(Context app) {
        this.app = app;
        SharedPreferences prefs = prefs();
        String id = prefs.getString(KEY_DEVICE, "");
        if (id.isEmpty()) {
            id = "op7x-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            prefs.edit().putString(KEY_DEVICE, id).apply();
        }
        deviceId = id;
        address = prefs.getString(KEY_ADDRESS, "");
        quality = EmbyQuality.bitrateAt(EmbyQuality.indexOf(prefs.getLong(KEY_QUALITY, 0)));
        EmbySession saved = EmbySession.fromJson(prefs.getString(KEY_SESSION, ""));
        if (!address.isEmpty() && saved != null) {
            client = newClient(address);
            client.resume(saved);
        }
        engine = PlayerEngine.get(app);
        engine.addListener(this);
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private EmbyClient newClient(String url) {
        String version = "";
        try {
            version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return new EmbyClient(url, new EmbyDevice(CLIENT_NAME, Build.MODEL == null ? "Zeekr" : Build.MODEL, deviceId,
                version));
    }

    public boolean signedIn() {
        return client != null && client.session() != null;
    }

    public String userName() {
        EmbySession s = client == null ? null : client.session();
        return s == null ? "" : s.userName;
    }

    public String address() {
        return address;
    }

    public int revision() {
        return revision;
    }

    public static String[] qualityLabels() {
        String[] labels = new String[EmbyQuality.BITRATES.length];
        labels[0] = Tx.t("原画", "Original");
        for (int i = 1; i < labels.length; i++) labels[i] = EmbyQuality.mbps(EmbyQuality.BITRATES[i]);
        return labels;
    }

    public int qualityIndex() {
        return EmbyQuality.indexOf(quality);
    }

    public void setQualityIndex(int index) {
        quality = EmbyQuality.bitrateAt(index);
        prefs().edit().putLong(KEY_QUALITY, quality).apply();
    }

    static String normalize(String typed) {
        String a = typed == null ? "" : typed.trim();
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (a.isEmpty()) return "";
        String lower = a.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return a;
        int slash = a.indexOf('/');
        String host = slash < 0 ? a : a.substring(0, slash);
        String rest = slash < 0 ? "" : a.substring(slash);
        boolean hasPort = host.startsWith("[") ? host.contains("]:") : host.indexOf(':') >= 0;
        return "http://" + host + (hasPort ? "" : ":" + DEFAULT_PORT) + rest;
    }

    public void connect(String typedAddress, String user, String password, Done<String> done) {
        String url = normalize(typedAddress);
        EmbyClient fresh = newClient(url);
        worker.execute(() -> {
            String version = "";
            EmbyException error = null;
            try {
                version = fresh.publicInfo().version;
                fresh.login(user, password);
            } catch (EmbyException e) {
                error = e;
            }
            String v = version;
            EmbyException err = error;
            ui.post(() -> {
                if (err == null) {
                    address = url;
                    client = fresh;
                    prefs().edit().putString(KEY_ADDRESS, url).putString(KEY_SESSION, fresh.session().toJson()).apply();
                    revision++;
                }
                done.done(v, err);
            });
        });
    }

    public void signOut() {
        EmbyClient old = client;
        client = null;
        queued.clear();
        prefs().edit().remove(KEY_SESSION).apply();
        revision++;
        if (old == null) return;
        worker.execute(() -> {
            try {
                old.logout();
            } catch (EmbyException ignored) {
            }
        });
    }

    private <T> void ask(Call<T> call, Done<T> done) {
        EmbyClient c = client;
        if (c == null) {
            done.done(null, null);
            return;
        }
        worker.execute(() -> {
            T value = null;
            EmbyException error = null;
            try {
                value = call.run(c);
            } catch (EmbyException e) {
                error = e;
            }
            T v = value;
            EmbyException err = error;
            ui.post(() -> {
                if (err != null && err.kind == EmbyException.Kind.UNAUTHORIZED && client == c) {
                    client = null;
                    prefs().edit().remove(KEY_SESSION).apply();
                    revision++;
                }
                done.done(v, err);
            });
        });
    }

    public void views(Done<List<EmbyItem>> done) {
        ask(EmbyClient::views, done);
    }

    public void children(EmbyItem parent, int start, Done<EmbyPage> done) {
        ask(c -> {
            if (EmbyItem.SERIES.equals(parent.type)) {
                List<EmbyItem> seasons = c.seasons(parent.id);
                return new EmbyPage(seasons, seasons.size());
            }
            if (EmbyItem.SEASON.equals(parent.type) && !parent.seriesId.isEmpty()) {
                List<EmbyItem> episodes = c.episodes(parent.seriesId, parent.id);
                return new EmbyPage(episodes, episodes.size());
            }
            return c.items(new EmbyQuery().parent(parent.id).sort(EmbyQuery.SORT_NAME, false).page(start, PAGE_SIZE));
        }, done);
    }

    public void search(String term, int start, Done<EmbyPage> done) {
        ask(c -> c.items(new EmbyQuery().search(term).recursive(true).types(SEARCH_TYPES)
                .sort(EmbyQuery.SORT_NAME, false).page(start, PAGE_SIZE)), done);
    }

    public static boolean playable(EmbyItem it) {
        return !it.folder && ("Video".equals(it.mediaType) || "Audio".equals(it.mediaType));
    }

    public static boolean audio(EmbyItem it) {
        return "Audio".equals(it.mediaType);
    }

    public static String episodeLabel(EmbyItem it) {
        StringBuilder sb = new StringBuilder();
        if (it.parentIndexNumber >= 0) sb.append('S').append(it.parentIndexNumber);
        if (it.indexNumber >= 0) sb.append('E').append(it.indexNumber);
        return sb.toString();
    }

    public static String titleOf(EmbyItem it) {
        if (!EmbyItem.EPISODE.equals(it.type)) return it.name;
        String label = episodeLabel(it);
        return label.isEmpty() ? it.name : label + "  " + it.name;
    }

    public static float progress(EmbyItem it) {
        if (it.userData.positionMs <= 0 || it.runtimeMs <= 0) return -1;
        return Math.min(1f, it.userData.positionMs / (float) it.runtimeMs);
    }

    public static int shape(EmbyItem it) {
        if (EmbyItem.EPISODE.equals(it.type)) return SHAPE_WIDE;
        if (audio(it) || "MusicAlbum".equals(it.type) || "MusicArtist".equals(it.type)) return SHAPE_SQUARE;
        return SHAPE_PORTRAIT;
    }

    public String imageUrl(EmbyItem it, int width) {
        EmbyClient c = client;
        if (c == null) return "";
        String tag = it.primaryImageTag();
        if (!tag.isEmpty()) return c.imageUrl(it.id, "Primary", tag, width);
        if (!it.seriesPrimaryImageTag.isEmpty() && !it.seriesId.isEmpty()) {
            return c.imageUrl(it.seriesId, "Primary", it.seriesPrimaryImageTag, width);
        }
        return "";
    }

    public String itemIdOf(PlayerEngine.Item item) {
        Playing p = item == null ? null : queued.get(item.uri.toString());
        return p == null ? "" : p.itemId;
    }

    public void play(List<EmbyItem> siblings, int index, Done<Boolean> done) {
        if (index < 0 || index >= siblings.size()) {
            done.done(false, null);
            return;
        }
        List<EmbyItem> list = new ArrayList<>(siblings);
        ask(c -> prepare(c, list, index), (prepared, error) -> {
            if (error != null || prepared == null || prepared.items.isEmpty()) {
                done.done(false, error);
                return;
            }
            queued.clear();
            queued.putAll(prepared.map);
            engine.playQueue(prepared.items, prepared.at);
            done.done(true, null);
        });
    }

    private Prepared prepare(EmbyClient c, List<EmbyItem> siblings, int index) throws EmbyException {
        EmbyItem picked = siblings.get(index);
        boolean audio = audio(picked);
        boolean alone = !audio && !EmbyItem.EPISODE.equals(picked.type);
        List<String> video = videoCodecs();
        Prepared out = new Prepared();
        for (int i = 0; i < siblings.size(); i++) {
            EmbyItem it = siblings.get(i);
            if (!playable(it) || audio(it) != audio || (alone && i != index)) continue;
            long start = i == index ? resumeAt(it) : -1;
            Resolved r;
            if (audio) {
                r = audioItem(c, it);
            } else if (i == index) {
                r = resolve(c, it);
            } else {
                EmbyMediaSource src = it.mediaSources.isEmpty() ? null : it.mediaSources.get(0);
                if (src != null && EmbyCaps.looksPlayable(src, video) && EmbyQuality.fits(src, quality)) {
                    String session = sessionTag(it.id);
                    r = toItem(c, it, src, c.directStreamUrl(it.id, src, session), session, EmbyProgress.DIRECT_STREAM);
                } else if (i > index && i < index + QUEUE_AHEAD) {
                    try {
                        r = resolve(c, it);
                    } catch (EmbyException skipped) {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            PlayerEngine.Item item = start > 0 ? r.item.startAt(start) : r.item;
            if (i == index) out.at = out.items.size();
            out.items.add(item);
            out.map.put(item.uri.toString(), r.playing);
        }
        return out;
    }

    private static long resumeAt(EmbyItem it) {
        long resume = it.userData.positionMs;
        return resume >= RESUME_MIN_MS && it.runtimeMs > 0 && resume < it.runtimeMs * RESUME_TAIL ? resume : -1;
    }

    private Resolved audioItem(EmbyClient c, EmbyItem it) {
        String session = sessionTag(it.id);
        Params p = new Params().add("Static", true).add("PlaySessionId", session);
        Uri uri = Uri.parse(c.withApiKey(c.absoluteUrl("/Audio/" + Params.encode(it.id) + "/stream?" + p)));
        PlayerEngine.Item item = PlayerEngine.Item.remote(uri, titleOf(it), true).titled(titleOf(it), it.seriesName);
        return new Resolved(item, new Playing(it.id, "", session, EmbyProgress.DIRECT_PLAY));
    }

    private Resolved resolve(EmbyClient c, EmbyItem it) throws EmbyException {
        EmbyPlayback pb = c.playbackInfo(it.id, null, quality, profile(), 0, EmbyProgress.UNSET, EmbyProgress.UNSET, true,
                true, true);
        EmbyMediaSource direct = null;
        EmbyMediaSource untested = null;
        EmbyMediaSource converted = null;
        for (EmbyMediaSource s : pb.mediaSources) {
            if (direct == null && (s.supportsDirectPlay || s.supportsDirectStream)) direct = s;
            if (untested == null && EmbyCaps.unprobed(s) && EmbyCaps.containerOk(s.container)) untested = s;
            if (converted == null && s.supportsTranscoding && !s.transcodingUrl.isEmpty()) converted = s;
        }
        String session = pb.playSessionId.isEmpty() ? sessionTag(it.id) : pb.playSessionId;
        if (direct == null) direct = untested;
        if (direct != null) {
            return toItem(c, it, direct, c.directStreamUrl(it.id, direct, session), session, EmbyProgress.DIRECT_STREAM);
        }
        if (converted != null) {
            return toItem(c, it, converted, c.absoluteUrl(converted.transcodingUrl), session, EmbyProgress.TRANSCODE);
        }
        throw new EmbyException(EmbyException.Kind.SERVER, 0,
                Tx.t("服务器判定无法播放此内容，且未提供转码版本", "The server cannot provide a playable version of this item")
                        + (pb.errorCode.isEmpty() ? "" : " (" + pb.errorCode + ")"), "", "", null);
    }

    private Resolved toItem(EmbyClient c, EmbyItem it, EmbyMediaSource src, String url, String session, String method) {
        boolean converted = EmbyProgress.TRANSCODE.equals(method);
        List<PlayerEngine.ExternalSubtitle> subs = new ArrayList<>();
        for (EmbyStream t : src.subtitleStreams()) {
            String mime = subtitleMime(t.codec);
            boolean separate = t.external
                    || (converted && EmbyStream.DELIVERY_EXTERNAL.equalsIgnoreCase(t.deliveryMethod) && !t.deliveryUrl.isEmpty());
            if (!separate || mime.isEmpty()) continue;
            String label = !t.displayTitle.isEmpty() ? t.displayTitle : !t.title.isEmpty() ? t.title
                    : Tx.t("外挂字幕", "External subtitle");
            subs.add(new PlayerEngine.ExternalSubtitle(Uri.parse(c.withApiKey(subtitleUrl(c, it.id, src, t))), mime,
                    t.language.isEmpty() ? null : t.language, label, !t.external && t.defaultStream));
        }
        PlayerEngine.Item item = PlayerEngine.Item.remote(Uri.parse(c.withApiKey(url)), titleOf(it), false)
                .titled(titleOf(it), it.seriesName).subtitles(subs);
        return new Resolved(item, new Playing(it.id, src.id, session, method));
    }

    private static String subtitleMime(String codec) {
        switch (codec.toLowerCase(Locale.ROOT)) {
            case "srt":
            case "subrip":
                return MimeTypes.APPLICATION_SUBRIP;
            case "ass":
            case "ssa":
                return MimeTypes.TEXT_SSA;
            case "vtt":
            case "webvtt":
                return MimeTypes.TEXT_VTT;
            default:
                return "";
        }
    }

    private static String subtitleExtension(String codec) {
        String lower = codec.toLowerCase(Locale.ROOT);
        return lower.equals("subrip") ? "srt" : lower.equals("webvtt") ? "vtt" : lower;
    }

    private static String subtitleUrl(EmbyClient c, String itemId, EmbyMediaSource src, EmbyStream t) {
        if (!t.deliveryUrl.isEmpty()) return c.absoluteUrl(t.deliveryUrl);
        return c.absoluteUrl("/Videos/" + Params.encode(itemId) + "/" + Params.encode(src.id) + "/Subtitles/" + t.index
                + "/0/Stream." + subtitleExtension(t.codec));
    }

    private List<String> videoCodecs() {
        List<String> v = videoCodecs;
        if (v != null) return v;
        List<String> mimes = new ArrayList<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.isEncoder()) mimes.addAll(Arrays.asList(info.getSupportedTypes()));
            }
        } catch (RuntimeException ignored) {
        }
        v = EmbyCaps.videoCodecs(mimes);
        videoCodecs = v;
        return v;
    }

    private JSONObject profile() {
        return EmbyDeviceProfile.build(videoCodecs(), EmbyCaps.AUDIO, quality, EmbyCaps.CONTAINERS);
    }

    private static String sessionTag(String itemId) {
        long h = 0x7A4C13E5L ^ System.nanoTime();
        for (int i = 0; i < itemId.length(); i++) h = Long.rotateLeft(h ^ itemId.charAt(i), 7) * 0x100000001B3L;
        return Long.toHexString(h) + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xFFFFFFFFL);
    }

    @Override
    public void onChanged() {
        PlayerEngine.Item current = engine.current();
        Playing now = current == null ? null : queued.get(current.uri.toString());
        if (now == playing) return;
        Playing before = playing;
        playing = now;
        ui.removeCallbacks(poll);
        if (before != null) report(before, 2);
        if (now != null) {
            now.polls = 0;
            now.positionMs = Math.max(0, engine.player().getCurrentPosition());
            now.paused = false;
            report(now, 0);
            ui.postDelayed(poll, POLL_MS);
        }
    }

    private void report(Playing p, int phase) {
        EmbyClient c = client;
        if (c == null) return;
        EmbyProgress body = p.progress();
        reporter.execute(() -> {
            try {
                if (phase == 0) {
                    c.reportStart(body);
                } else if (phase == 1) {
                    c.reportProgress(body);
                } else {
                    c.reportStopped(body);
                    if (EmbyProgress.TRANSCODE.equals(p.method)) c.stopEncoding(p.playSessionId);
                }
            } catch (EmbyException ignored) {
            }
        });
    }
}
