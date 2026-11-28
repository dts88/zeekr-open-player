package io.github.dts88.openplayer.emby;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import io.github.dts88.openplayer.PlayerEngine;

import java.util.ArrayList;
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

    private static final String PREFS = "op-emby";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_SESSION = "session";
    private static final String KEY_DEVICE = "device";
    private static final String CLIENT_NAME = "Open Player";
    private static final int DEFAULT_PORT = 8096;
    private static final long POLL_MS = 970;
    private static final int REPORT_EVERY_POLLS = 11;
    private static final long RESUME_MIN_MS = 9000;
    private static final float RESUME_TAIL = 0.97f;

    private static EmbyHub instance;

    public static synchronized EmbyHub get(Context context) {
        if (instance == null) instance = new EmbyHub(context.getApplicationContext());
        return instance;
    }

    private static final class Playing {
        final String itemId;
        final String playSessionId;
        long positionMs;
        boolean paused;
        int polls;

        Playing(String itemId, String playSessionId) {
            this.itemId = itemId;
            this.playSessionId = playSessionId;
        }

        EmbyProgress progress() {
            return new EmbyProgress(itemId, "", playSessionId, EmbyProgress.DIRECT_PLAY).at(positionMs, paused);
        }
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

    public String itemIdOf(PlayerEngine.Item item) {
        Playing p = item == null ? null : queued.get(item.uri.toString());
        return p == null ? "" : p.itemId;
    }

    public boolean play(List<EmbyItem> siblings, int index) {
        EmbyClient c = client;
        if (c == null || index < 0 || index >= siblings.size()) return false;
        EmbyItem picked = siblings.get(index);
        boolean audio = audio(picked);
        boolean alone = !audio && !EmbyItem.EPISODE.equals(picked.type);
        List<PlayerEngine.Item> items = new ArrayList<>();
        Map<String, Playing> map = new HashMap<>();
        int at = 0;
        for (int i = 0; i < siblings.size(); i++) {
            EmbyItem it = siblings.get(i);
            if (!playable(it) || audio(it) != audio || (alone && i != index)) continue;
            String session = sessionTag(it.id);
            String url = streamUrl(c, it, session);
            if (url.isEmpty()) continue;
            Uri uri = Uri.parse(url);
            PlayerEngine.Item item = PlayerEngine.Item.remote(uri, titleOf(it), audio)
                    .titled(titleOf(it), it.seriesName);
            if (i == index) {
                at = items.size();
                long resume = it.userData.positionMs;
                if (resume >= RESUME_MIN_MS && it.runtimeMs > 0 && resume < it.runtimeMs * RESUME_TAIL) {
                    item = item.startAt(resume);
                }
            }
            items.add(item);
            map.put(uri.toString(), new Playing(it.id, session));
        }
        if (items.isEmpty()) return false;
        queued.clear();
        queued.putAll(map);
        engine.playQueue(items, at);
        return true;
    }

    private static String sessionTag(String itemId) {
        long h = 0x7A4C13E5L ^ System.nanoTime();
        for (int i = 0; i < itemId.length(); i++) h = Long.rotateLeft(h ^ itemId.charAt(i), 7) * 0x100000001B3L;
        return Long.toHexString(h) + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xFFFFFFFFL);
    }

    private static String streamUrl(EmbyClient c, EmbyItem it, String session) {
        if (audio(it)) {
            Params p = new Params().add("Static", true).add("PlaySessionId", session);
            return c.withApiKey(c.absoluteUrl("/Audio/" + Params.encode(it.id) + "/stream?" + p));
        }
        EmbyMediaSource source = it.mediaSources.isEmpty() ? null : it.mediaSources.get(0);
        return c.withApiKey(c.directStreamUrl(it.id, source, session));
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
                if (phase == 0) c.reportStart(body);
                else if (phase == 1) c.reportProgress(body);
                else c.reportStopped(body);
            } catch (EmbyException ignored) {
            }
        });
    }
}
