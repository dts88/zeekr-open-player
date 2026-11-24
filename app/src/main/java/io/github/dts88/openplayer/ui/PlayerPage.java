package io.github.dts88.openplayer.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.MediaLabels;
import io.github.dts88.openplayer.Tx;
import io.github.dts88.openplayer.music.CueSheet;
import io.github.dts88.openplayer.subs.Libass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class PlayerPage extends Page {
    private static final long HIDE_AFTER_MS = 4700;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private final String[] resizeLabels = {Tx.t("适应", "Fit"), Tx.t("铺满", "Fill"), Tx.t("拉伸", "Stretch")};
    private static final int[] RESIZE_MODES = {AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL};
    private static final String[] PANEL_KINDS = {"audio", "subtitle", "sound", "picture", "speed", "info"};
    private final String[] panelNames = {Tx.t("音轨", "Audio"), Tx.t("字幕", "Subs"), Tx.t("声音", "Sound"), Tx.t("画面", "Picture"), Tx.t("倍速", "Speed"), Tx.t("信息", "Info")};
    private static final String[] PANEL_ICONS = {"audio", "subtitle", "speaker", "picture", "speed", "info"};
    private static final int PANEL_DP = 520;

    private final PlayerEngine engine;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExoPlayer bound;

    private FrameLayout root;
    private AspectRatioFrameLayout aspect;
    private SubtitleView subtitles;
    private PlayerEngine.VideoViews videoViews;
    private LinearLayout musicBox;
    private TextView musicTitle;
    private TextView musicSub;
    private final Rows.Adapter musicRows = new Rows.Adapter();
    private FrameLayout controls;
    private TextView titleView;
    private TextView subView;
    private LinearLayout chips;
    private SeekLine seek;
    private TextView timeView;
    private TextView durationView;
    private TextView markView;
    private ImageView playPause;
    private TextView message;
    private LinearLayout tools;
    private FrameLayout panel;
    private final TextView[] panelTabs = new TextView[PANEL_KINDS.length];
    private LinearLayout panelBody;
    private String panelKind = "";
    private MeterView meters;
    private TextView panelInfo;
    private boolean visible;
    private LinearLayout statusBox;
    private TextView statusText;
    private View statusRetry;
    private boolean controlsShown = true;
    private long lastTouch;
    private boolean playingIcon;
    private int resize;
    private String shownKey = "";

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onCues(CueGroup cueGroup) {
            subtitles.setCues(cueGroup.cues);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            refreshPlayIcon();
            if (isPlaying) lastTouch = SystemClock.elapsedRealtime();
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            refreshHeader();
            if (panelKind.equals("audio") || panelKind.equals("subtitle")) openPanel(panelKind);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            refreshStatus();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            refreshStatus();
        }
    };

    private static final long METER_MS = 120;
    private final Runnable meterTick = new Runnable() {
        @Override
        public void run() {
            if (!visible || meters == null) return;
            float[] db = engine.takeMeterDb();
            meters.set(db, PlayerEngine.meterNames(db.length));
            ui.postDelayed(this, METER_MS);
        }
    };

    private void restartMeters() {
        ui.removeCallbacks(meterTick);
        ui.post(meterTick);
    }

    PlayerPage(ShellActivity shell) {
        super(shell);
        engine = shell.engine;
    }

    @Override
    String title() {
        return Tx.t("播放", "Playback");
    }

    @Override
    protected View build() {
        root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        root.setOnClickListener(v -> toggleControls());

        aspect = new AspectRatioFrameLayout(c);
        aspect.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        SurfaceView system = new SurfaceView(c);
        SurfaceView gl = new SurfaceView(c);
        gl.setVisibility(View.GONE);
        aspect.addView(system, Ui.fill());
        aspect.addView(gl, Ui.fill());
        subtitles = new SubtitleView(c);
        SubtitleLook.apply(c, subtitles);
        aspect.addView(subtitles, Ui.fill());
        root.addView(aspect, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        videoViews = new PlayerEngine.VideoViews(system, gl, aspect);

        root.addView(buildMusic(), Ui.fill());
        root.addView(buildControls(), Ui.fill());

        message = Ui.text(c, "", Ui.BODY, Ui.TEXT);
        message.setBackground(Ui.round(c, Ui.OVERLAY, Ui.RADIUS));
        message.setPadding(dp(24), dp(14), dp(24), dp(14));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        ml.topMargin = dp(120);
        root.addView(message, ml);
        message.setVisibility(View.GONE);

        statusBox = Ui.column(c);
        statusBox.setGravity(Gravity.CENTER_HORIZONTAL);
        statusBox.setBackground(Ui.round(c, Ui.OVERLAY, Ui.RADIUS));
        statusBox.setPadding(dp(36), dp(26), dp(36), dp(26));
        statusText = Ui.text(c, "", Ui.BODY, Ui.TEXT);
        statusText.setGravity(Gravity.CENTER);
        statusText.setMaxWidth(dp(760));
        statusBox.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        statusRetry = Ui.button(c, Tx.t("重试", "Retry"), true, () -> act(engine::retry));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(22);
        statusBox.addView(statusRetry, rl);
        statusBox.setVisibility(View.GONE);
        root.addView(statusBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        root.addView(buildPanel(), new FrameLayout.LayoutParams(dp(PANEL_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        return root;
    }

    private View buildMusic() {
        musicBox = Ui.row(c);
        musicBox.setGravity(Gravity.TOP);
        musicBox.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A1D24, 0xFF0B0C0F}));
        musicBox.setPadding(dp(64), dp(128), dp(48), dp(230));
        FrameLayout art = new FrameLayout(c);
        art.setBackground(Ui.round(c, Ui.SURFACE_HI, 18));
        art.addView(Ui.icon(c, "music", 120, 0x33FFFFFF), new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        musicBox.addView(art, new LinearLayout.LayoutParams(dp(400), dp(400)));

        LinearLayout right = Ui.column(c);
        right.setPadding(dp(56), 0, 0, 0);
        musicTitle = Ui.bold(c, "", 40, Ui.TEXT);
        musicTitle.setMaxLines(2);
        right.addView(musicTitle, Ui.fillWidth());
        musicSub = Ui.oneLine(Ui.text(c, "", Ui.BODY, Ui.TEXT2));
        musicSub.setPadding(0, dp(10), 0, dp(20));
        right.addView(musicSub, Ui.fillWidth());
        RecyclerView list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(musicRows);
        right.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        musicBox.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        musicBox.setVisibility(View.GONE);
        return musicBox;
    }

    private View buildControls() {
        controls = new FrameLayout(c);

        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.TOP);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD9000000, 0x00000000}));
        top.setPadding(dp(24), dp(20), dp(32), dp(56));
        top.addView(Ui.iconButton(c, "back", Tx.t("返回", "Back"), false, shell::handleBack));
        LinearLayout names = Ui.column(c);
        names.setPadding(dp(24), dp(6), dp(24), 0);
        titleView = Ui.oneLine(Ui.bold(c, "", Ui.ROW, Ui.TEXT));
        subView = Ui.oneLine(Ui.text(c, "", Ui.MICRO, Ui.TEXT2));
        subView.setPadding(0, dp(6), 0, 0);
        names.addView(titleView);
        names.addView(subView);
        top.addView(names, Ui.weight(1));
        chips = Ui.row(c);
        chips.setPadding(0, dp(14), 0, 0);
        top.addView(chips);
        controls.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout bottom = Ui.column(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x00000000}));
        bottom.setPadding(dp(32), dp(72), dp(32), dp(22));
        LinearLayout line = Ui.row(c);
        timeView = Ui.bold(c, "0:00", Ui.CAPTION, Ui.TEXT);
        timeView.setMinWidth(dp(92));
        line.addView(timeView);
        seek = new SeekLine(c);
        seek.setListener(new SeekLine.Listener() {
            @Override
            public void onScrub(long positionMs) {
                lastTouch = SystemClock.elapsedRealtime();
                timeView.setText(MediaLabels.clock(positionMs));
            }

            @Override
            public void onSeek(long positionMs) {
                engine.player().seekTo(positionMs);
            }
        });
        line.addView(seek, Ui.weight(1));
        durationView = Ui.bold(c, "0:00", Ui.CAPTION, Ui.TEXT2);
        durationView.setMinWidth(dp(92));
        durationView.setGravity(Gravity.END);
        line.addView(durationView);
        bottom.addView(line, Ui.fillWidth());

        FrameLayout buttons = new FrameLayout(c);
        markView = Ui.oneLine(Ui.text(c, "", Ui.CAPTION, Ui.TEXT2));
        markView.setMaxWidth(dp(380));
        buttons.addView(markView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout transport = Ui.row(c);
        transport.addView(Ui.roundIcon(c, "previous", 64, 30, 0x00000000, Ui.TEXT, () -> act(engine::previous)));
        transport.addView(jump("rewind", "10", () -> seekBy(-10_000)));
        playPause = Ui.roundIcon(c, "play", 84, 40, Ui.ACCENT, Ui.ON_ACCENT, () -> act(this::togglePlay));
        LinearLayout.LayoutParams pl = (LinearLayout.LayoutParams) playPause.getLayoutParams();
        pl.leftMargin = dp(14);
        pl.rightMargin = dp(14);
        transport.addView(playPause);
        transport.addView(jump("forward", "30", () -> seekBy(30_000)));
        transport.addView(Ui.roundIcon(c, "next", 64, 30, 0x00000000, Ui.TEXT, () -> act(engine::next)));
        ImageView stop = Ui.roundIcon(c, "stop", 64, 28, 0x00000000, Ui.TEXT, this::stopPlayback);
        ((LinearLayout.LayoutParams) stop.getLayoutParams()).leftMargin = dp(10);
        transport.addView(stop);
        buttons.addView(transport, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        tools = Ui.row(c);
        for (int i = 0; i < PANEL_KINDS.length; i++) {
            String kind = PANEL_KINDS[i];
            LinearLayout b = tool(PANEL_ICONS[i], panelNames[i], () -> openPanel(kind));
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.leftMargin = dp(8);
            tools.addView(b, lp);
        }
        buttons.addView(tools, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.END));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        bl.topMargin = dp(4);
        bottom.addView(buttons, bl);
        controls.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        return controls;
    }

    private View jump(String icon, String seconds, Runnable action) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.pressable(c, 0x00000000, 32));
        f.addView(Ui.icon(c, icon, 40, Ui.TEXT), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        TextView n = Ui.bold(c, seconds, 13, Ui.TEXT);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        nl.topMargin = dp(2);
        f.addView(n, nl);
        f.setOnClickListener(v -> act(action));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(64)));
        return f;
    }

    private LinearLayout tool(String icon, String label, Runnable action) {
        LinearLayout b = Ui.column(c);
        b.setGravity(Gravity.CENTER);
        b.setMinimumWidth(dp(84));
        b.setMinimumHeight(dp(80));
        b.setBackground(Ui.pressable(c, 0x00000000, 14));
        b.addView(Ui.icon(c, icon, 30, Ui.TEXT));
        TextView t = Ui.text(c, label, Ui.MICRO, Ui.TEXT2);
        t.setPadding(0, dp(6), 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> act(action));
        return b;
    }

    private View buildPanel() {
        panel = new FrameLayout(c);
        panel.setBackgroundColor(Ui.OVERLAY);
        panel.setClickable(true);
        LinearLayout col = Ui.column(c);
        LinearLayout head = Ui.row(c);
        head.setPadding(dp(10), dp(16), dp(12), dp(10));
        for (int i = 0; i < PANEL_KINDS.length; i++) {
            String kind = PANEL_KINDS[i];
            TextView tab = Ui.bold(c, panelNames[i], 20, Ui.TEXT2);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(Ui.TOUCH));
            tab.setMinWidth(dp(68));
            tab.setOnClickListener(v -> act(() -> openPanel(kind)));
            panelTabs[i] = tab;
            head.addView(tab, Ui.wrap());
        }
        head.addView(Ui.spring(c));
        head.addView(Ui.roundIcon(c, "close", 60, 26, Ui.SURFACE_HI, Ui.TEXT, this::closePanel));
        col.addView(head, Ui.fillWidth());
        ScrollView scroll = new ScrollView(c);
        panelBody = Ui.column(c);
        panelBody.setPadding(0, 0, 0, dp(24));
        scroll.addView(panelBody);
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(col, Ui.fill());
        panel.setVisibility(View.GONE);
        return panel;
    }

    @Override
    void onShow() {
        visible = true;
        bind(engine.player());
        engine.attachVideo(videoViews);
        lastTouch = SystemClock.elapsedRealtime();
        showControls(true);
        refreshAll();
    }

    @Override
    void onHide() {
        visible = false;
        ui.removeCallbacks(meterTick);
        engine.detachVideo(videoViews);
        closePanel();
    }

    void onPlayerReplaced(ExoPlayer p) {
        if (visible) bind(p);
    }

    private void bind(ExoPlayer p) {
        if (bound == p) return;
        if (bound != null) bound.removeListener(playerListener);
        bound = p;
        p.addListener(playerListener);
        subtitles.setCues(p.getCurrentCues().cues);
        Libass.Session ass = engine.assSession();
        if (ass != null) ass.attach(subtitles);
        else Libass.detach(subtitles);
    }

    void release() {
        if (bound != null) bound.removeListener(playerListener);
        bound = null;
        ui.removeCallbacks(meterTick);
    }

    boolean handleBack() {
        if (!panelKind.isEmpty()) {
            closePanel();
            return true;
        }
        return false;
    }

    private void toggleControls() {
        if (!panelKind.isEmpty()) {
            closePanel();
            return;
        }
        lastTouch = SystemClock.elapsedRealtime();
        showControls(!controlsShown || !engine.hasVideo());
    }

    private void showControls(boolean show) {
        controlsShown = show;
        controls.animate().cancel();
        if (show) {
            controls.setVisibility(View.VISIBLE);
            controls.setAlpha(1f);
        } else {
            controls.animate().alpha(0f).setDuration(180).withEndAction(() -> controls.setVisibility(View.GONE)).start();
        }
    }

    private void act(Runnable r) {
        lastTouch = SystemClock.elapsedRealtime();
        r.run();
    }

    private void stopPlayback() {
        engine.stopAll();
        shell.hidePlayer();
    }

    private void togglePlay() {
        Player p = engine.player();
        if (p.isPlaying()) {
            p.pause();
        } else {
            if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0);
            if (p.getPlaybackState() == Player.STATE_IDLE) {
                engine.retry();
                return;
            }
            p.play();
        }
    }

    private void seekBy(long deltaMs) {
        Player p = engine.player();
        long d = p.getDuration();
        long to = Math.max(0, p.getCurrentPosition() + deltaMs);
        if (d > 0) to = Math.min(to, Math.max(0, d - 1000));
        p.seekTo(to);
    }

    private void refreshStatus() {
        if (statusBox == null) return;
        String error = engine.friendlyError();
        String text = error.isEmpty() ? engine.waitingText() : error;
        if (text.isEmpty()) {
            statusBox.setVisibility(View.GONE);
            return;
        }
        if (!text.contentEquals(statusText.getText())) statusText.setText(text);
        statusRetry.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
        if (statusBox.getVisibility() != View.VISIBLE) {
            statusBox.setVisibility(View.VISIBLE);
            if (!error.isEmpty()) showControls(true);
        }
    }

    private final Runnable hideMessage = () -> message.setVisibility(View.GONE);

    private void say(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideMessage);
        ui.postDelayed(hideMessage, Math.max(5200, Math.min(15000, text.length() * 140L)));
    }

    @Override
    void onEngineChanged() {
        if (visible) refreshAll();
    }

    @Override
    void onTick() {
        if (!visible) return;
        Player p = engine.player();
        long d = p.getDuration();
        long pos = p.getCurrentPosition();
        if (d != C.TIME_UNSET && d > 0) pos = Math.min(pos, d);
        if (!seek.dragging()) {
            seek.set(pos, p.getBufferedPosition(), d == C.TIME_UNSET ? 0 : d);
            timeView.setText(MediaLabels.clock(pos));
        }
        durationView.setText(d == C.TIME_UNSET || d <= 0 ? "--:--" : MediaLabels.clock(d));
        refreshMark();
        refreshPlayIcon();
        if (panelKind.equals("info") || panelKind.equals("picture") || panelKind.equals("sound")) fillInfoLines();
        if (controlsShown && engine.hasVideo() && p.isPlaying() && panelKind.isEmpty() && !seek.dragging()
                && SystemClock.elapsedRealtime() - lastTouch > HIDE_AFTER_MS) {
            showControls(false);
        }
        refreshStatus();
        String err = engine.lastError();
        if (err.isEmpty()) {
            message.setTag(null);
        } else if (engine.friendlyError().isEmpty() && !err.equals(message.getTag())) {
            message.setTag(err);
            say(err);
        }
    }

    private void refreshAll() {
        refreshHeader();
        refreshPlayIcon();
        refreshMark();
        boolean video = engine.hasVideo();
        PlayerEngine.Item it = engine.current();
        boolean music = !video && it != null && !engine.audioSummary().isEmpty();
        musicBox.setVisibility(music ? View.VISIBLE : View.GONE);
        if (music) {
            showControls(true);
            fillMusic(it);
        }
        List<CueSheet.Track> chapters = engine.chapters();
        long[] marks = new long[chapters.size()];
        for (int i = 0; i < marks.length; i++) marks[i] = chapters.get(i).startMs;
        seek.setMarks(marks);
    }

    private void refreshHeader() {
        PlayerEngine.Item it = engine.current();
        titleView.setText(it == null ? "" : it.title);
        subView.setText(it == null ? "" : it.subtitle);
        subView.setVisibility(it == null || it.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        chips.removeAllViews();
        Format video = selected(C.TRACK_TYPE_VIDEO);
        Format audio = selected(C.TRACK_TYPE_AUDIO);
        if (video != null) {
            String range = engine.dynamicRange(video);
            addChip(MediaLabels.resolution(video.width, video.height), false);
            if (!range.isEmpty()) addChip(range, true);
            addChip(engine.videoOnGpu() ? Tx.t("已处理", "Processed") : Tx.t("系统直出", "System"), engine.videoOnGpu());
        }
        if (audio != null) addChip(MediaLabels.audioLabel(audio), false);
        String out = engine.outputSummary();
        if (!out.isEmpty() && engine.passThrough() && out.startsWith("12 ")) addChip(Tx.t("→ 车机 7.1.4", "→ Car 7.1.4"), true);
    }

    private void addChip(String text, boolean strong) {
        if (text.isEmpty()) return;
        TextView chip = Ui.chip(c, text, strong);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = dp(10);
        chips.addView(chip, lp);
    }

    private void refreshPlayIcon() {
        boolean playing = engine.player().isPlaying();
        if (playing == playingIcon) return;
        playingIcon = playing;
        playPause.setImageDrawable(new Icons(playing ? "pause" : "play", Ui.ON_ACCENT, c));
    }

    private void refreshMark() {
        List<CueSheet.Track> chapters = engine.chapters();
        String text = "";
        int ch = engine.chapterIndex();
        if (ch >= 0) {
            text = (ch + 1) + " / " + chapters.size() + "  " + (chapters.get(ch).title.isEmpty()
                    ? Tx.t("第 ", "Track ") + chapters.get(ch).number + Tx.t(" 首", "") : chapters.get(ch).title);
        } else if (engine.queue().size() > 1) {
            text = (engine.queueIndex() + 1) + " / " + engine.queue().size();
        }
        if (!engine.resumeText().isEmpty() && engine.player().getCurrentPosition() < 8000 + resumeStart()) {
            text = text.isEmpty() ? Tx.t("从上次位置继续播放", "Resumed from last position") : text;
        }
        if (!text.contentEquals(markView.getText())) {
            markView.setText(text);
            if (musicBox.getVisibility() == View.VISIBLE) fillMusic(engine.current());
        }
    }

    private long resumeStart() {
        PlayerEngine.Item it = engine.current();
        return it == null || it.file() == null ? 0 : Math.max(0, engine.resumePosition(it.file()));
    }

    private void fillMusic(PlayerEngine.Item it) {
        if (it == null) return;
        List<CueSheet.Track> chapters = engine.chapters();
        int ch = engine.chapterIndex();
        String key = it.uri + "#" + ch + "#" + engine.queueIndex() + "#" + engine.queue().size();
        if (key.equals(shownKey)) return;
        shownKey = key;
        musicTitle.setText(ch >= 0 ? (chapters.get(ch).title.isEmpty() ? it.title : chapters.get(ch).title) : it.title);
        String second = ch >= 0 ? (chapters.get(ch).performer.isEmpty() ? it.title : chapters.get(ch).performer + " · " + it.title)
                : it.subtitle;
        musicSub.setText(second);
        List<Rows.Item> rows = new ArrayList<>();
        if (!chapters.isEmpty()) {
            for (int i = 0; i < chapters.size(); i++) {
                CueSheet.Track t = chapters.get(i);
                int index = i;
                rows.add(new Rows.Item(t.title.isEmpty() ? Tx.t("第 ", "Track ") + t.number + Tx.t(" 首", "") : t.title)
                        .number(String.valueOf(t.number)).trailing(MediaLabels.clock(t.startMs)).current(i == ch)
                        .onClick(() -> engine.jumpToChapter(index)));
            }
        } else {
            List<PlayerEngine.Item> queue = engine.queue();
            for (int i = 0; i < queue.size(); i++) {
                int index = i;
                rows.add(new Rows.Item(queue.get(i).title).number(String.valueOf(i + 1)).sub(queue.get(i).subtitle)
                        .current(i == engine.queueIndex())
                        .onClick(() -> engine.jumpToQueue(index)));
            }
        }
        musicRows.set(rows);
    }

    private Format selected(int type) {
        for (Tracks.Group g : engine.player().getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSelected(i)) return g.getTrackFormat(i);
            }
        }
        return null;
    }

    private void closePanel() {
        panelKind = "";
        meters = null;
        panelInfo = null;
        if (panel != null) panel.setVisibility(View.GONE);
        makeRoomForPanel(false);
    }

    private void makeRoomForPanel(boolean open) {
        if (controls == null) return;
        int right = open ? dp(PANEL_DP) : 0;
        if (controls.getPaddingRight() != right) controls.setPadding(0, 0, right, 0);
        tools.setVisibility(open ? View.GONE : View.VISIBLE);
        int musicRight = dp(48) + right;
        if (musicBox.getPaddingRight() != musicRight) {
            musicBox.setPadding(musicBox.getPaddingLeft(), musicBox.getPaddingTop(), musicRight, musicBox.getPaddingBottom());
        }
    }

    private void openPanel(String kind) {
        panelKind = kind;
        meters = null;
        panelInfo = null;
        panelBody.removeAllViews();
        panel.setVisibility(View.VISIBLE);
        makeRoomForPanel(true);
        showControls(true);
        for (int i = 0; i < PANEL_KINDS.length; i++) {
            boolean on = PANEL_KINDS[i].equals(kind);
            panelTabs[i].setTextColor(on ? Ui.TEXT : Ui.TEXT2);
            panelTabs[i].setBackground(on ? Ui.round(c, Ui.SUNKEN, Ui.RADIUS) : Ui.pressable(c, 0x00000000, Ui.RADIUS));
        }
        switch (kind) {
            case "audio":
                fillTracks(C.TRACK_TYPE_AUDIO);
                break;
            case "subtitle":
                fillTracks(C.TRACK_TYPE_TEXT);
                break;
            case "sound":
                fillSound();
                break;
            case "picture":
                fillPicture();
                break;
            case "speed":
                fillSpeed();
                break;
            default:
                panelInfo = infoText();
                panelBody.addView(panelInfo, Ui.fillWidth());
                fillInfoLines();
        }
    }

    private void fillTracks(int type) {
        Player p = engine.player();
        Tracks tracks = p.getCurrentTracks();
        boolean any = false;
        boolean anySelected = false;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) anySelected |= g.isTrackSelected(i);
        }
        if (type == C.TRACK_TYPE_TEXT) {
            panelBody.addView(option(Tx.t("关闭字幕", "Subtitles off"), "", !anySelected, () -> {
                p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
            }));
        }
        int n = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                any = true;
                n++;
                Format f = g.getTrackFormat(i);
                int index = i;
                String name = type == C.TRACK_TYPE_AUDIO ? MediaLabels.audioLabel(f) : subtitleName(f);
                StringBuilder note = new StringBuilder(languageName(f.language));
                if (f.label != null && !f.label.isEmpty()) note.append(note.length() > 0 ? " · " : "").append(f.label);
                if ((f.roleFlags & C.ROLE_FLAG_COMMENTARY) != 0) note.append(note.length() > 0 ? " · " : "").append(Tx.t("评论", "Commentary"));
                if ((f.roleFlags & C.ROLE_FLAG_ALTERNATE) != 0) note.append(note.length() > 0 ? " · " : "").append(Tx.t("备选", "Alternate"));
                if (!g.isTrackSupported(i)) note.append(note.length() > 0 ? " · " : "").append(Tx.t("不支持", "Unsupported"));
                boolean supported = g.isTrackSupported(i);
                panelBody.addView(option(n + ".  " + name, note.toString(), g.isTrackSelected(i), !supported ? null : () -> {
                    TrackSelectionParameters.Builder b = p.getTrackSelectionParameters().buildUpon()
                            .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), index));
                    if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
                    p.setTrackSelectionParameters(b.build());
                }));
            }
        }
        if (!any) {
            TextView none = Ui.text(c, type == C.TRACK_TYPE_AUDIO ? Tx.t("此文件无音轨。", "No audio track in this file.")
                    : Tx.t("此文件无字幕轨。\n将同名字幕文件（如 片名.srt、片名.zh.ass）放在影片所在文件夹后重新打开。", "No subtitle track in this file.\nPut a subtitle file with the same name (e.g. Title.srt, Title.zh.ass) next to the video and open it again."), Ui.BODY, Ui.TEXT2);
            none.setPadding(dp(24), dp(16), dp(24), 0);
            panelBody.addView(none);
        }
        if (type == C.TRACK_TYPE_TEXT) fillSubtitleLook();
    }

    private void fillSubtitleLook() {
        LinearLayout box = Ui.column(c);
        box.setPadding(dp(24), dp(10), dp(20), dp(8));
        View line = Ui.divider(c);
        ((LinearLayout.LayoutParams) line.getLayoutParams()).leftMargin = 0;
        box.addView(line);

        box.addView(panelLabel(Tx.t("字幕延迟", "Subtitle delay"), Tx.t("字幕过早时增加（＋），过晚时减少（−）。仅对当前影片生效。", "Increase (+) if subtitles are early, decrease (−) if late. Applies to this video only.")));
        LinearLayout delay = Ui.row(c);
        TextView value = Ui.bold(c, delayText(engine.subtitleDelayMs()), Ui.BODY, Ui.TEXT);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(104));
        delay.addView(step("−0.5", () -> nudgeDelay(-500, value)));
        delay.addView(step("−0.1", () -> nudgeDelay(-100, value)));
        delay.addView(value);
        delay.addView(step("+0.1", () -> nudgeDelay(100, value)));
        delay.addView(step("+0.5", () -> nudgeDelay(500, value)));
        delay.addView(step(Tx.t("重置", "Reset"), () -> nudgeDelay(-engine.subtitleDelayMs(), value)));
        box.addView(delay, Ui.wrap());

        box.addView(panelLabel(Tx.t("大小", "Size"), ""));
        box.addView(Ui.segmented(c, SubtitleLook.sizeLabels(), SubtitleLook.size(c), i -> {
            SubtitleLook.setSize(c, i);
            SubtitleLook.apply(c, subtitles);
        }), Ui.wrap());
        box.addView(panelLabel(Tx.t("位置", "Position"), Tx.t("自带定位的字幕不受此项影响。", "Subtitles with their own positioning are not affected.")));
        box.addView(Ui.segmented(c, SubtitleLook.positionLabels(), SubtitleLook.position(c), i -> {
            SubtitleLook.setPosition(c, i);
            SubtitleLook.apply(c, subtitles);
        }), Ui.wrap());
        box.addView(panelLabel(Tx.t("样式", "Style"), ""));
        box.addView(Ui.segmented(c, SubtitleLook.styleLabels(), SubtitleLook.style(c), i -> {
            SubtitleLook.setStyle(c, i);
            SubtitleLook.apply(c, subtitles);
        }), Ui.wrap());
        panelBody.addView(box, Ui.fillWidth());
        panelBody.addView(Ui.settingRow(c, Tx.t("使用字幕自带样式", "Use embedded style"), Tx.t("采用 ASS 字幕内置的颜色、粗细与字号。关闭后使用上方设置。", "Use the colours, weight and size defined in ASS subtitles. When off, the settings above apply."),
                Ui.toggle(c, SubtitleLook.embedded(c), on -> {
                    SubtitleLook.setEmbedded(c, on);
                    SubtitleLook.apply(c, subtitles);
                })));
        panelBody.addView(Ui.divider(c));
        panelBody.addView(Ui.settingRow(c, Tx.t("ASS 特效渲染", "ASS effects"), Tx.t("完整呈现 ASS 字幕的旋转、移动、淡入淡出、卡拉 OK 与内嵌字体。", "Render ASS rotation, movement, fades, karaoke and embedded fonts in full. ")
                        + Tx.t("开启后 ASS 字幕不受上方设置影响；显示异常时可关闭。", "When on, ASS subtitles ignore the settings above; turn off if rendering looks wrong."),
                Ui.toggle(c, engine.nativeAss(), engine::setNativeAss)));
        TextView note = Ui.text(c, Tx.t("图形字幕（蓝光、DVD）为预渲染图像，无法调整大小与位置。", "Image subtitles (Blu-ray, DVD) are pre-rendered and cannot be resized or moved."), Ui.MICRO, Ui.TEXT3);
        note.setPadding(dp(24), dp(6), dp(24), 0);
        panelBody.addView(note);
    }

    private View panelLabel(String title, String note) {
        LinearLayout l = Ui.column(c);
        l.setPadding(0, dp(20), 0, dp(12));
        l.addView(Ui.text(c, title, Ui.BODY, Ui.TEXT));
        if (!note.isEmpty()) {
            TextView n = Ui.text(c, note, Ui.MICRO, Ui.TEXT3);
            n.setPadding(0, dp(4), 0, 0);
            l.addView(n);
        }
        return l;
    }

    private View step(String label, Runnable action) {
        TextView b = Ui.bold(c, label, Ui.CAPTION, Ui.TEXT);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(66));
        b.setMinHeight(dp(Ui.TOUCH));
        b.setBackground(Ui.pressable(c, Ui.SURFACE_HI, 14));
        b.setOnClickListener(v -> act(action));
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void nudgeDelay(long deltaMs, TextView value) {
        engine.setSubtitleDelayMs(engine.subtitleDelayMs() + deltaMs);
        value.setText(delayText(engine.subtitleDelayMs()));
    }

    private static String delayText(long ms) {
        if (ms == 0) return Tx.t("0.0 秒", "0.0 s");
        return String.format(Locale.ROOT, Tx.t("%+.1f 秒", "%+.1f s"), ms / 1000.0).replace('-', '−');
    }

    private View option(String title, String note, boolean selected, Runnable action) {
        LinearLayout row = Ui.row(c);
        row.setMinimumHeight(dp(76));
        row.setPadding(dp(24), dp(10), dp(20), dp(10));
        LinearLayout texts = Ui.column(c);
        texts.addView(Ui.text(c, title, Ui.BODY, selected ? Ui.ENERGY_TEXT : action == null ? Ui.TEXT3 : Ui.TEXT));
        if (!note.isEmpty()) {
            TextView n = Ui.text(c, note, Ui.MICRO, Ui.TEXT3);
            n.setPadding(0, dp(4), 0, 0);
            texts.addView(n);
        }
        row.addView(texts, Ui.weight(1));
        if (selected) row.addView(Ui.icon(c, "check", 28, Ui.ACCENT));
        if (action != null) {
            row.setBackground(Ui.pressable(c, 0x00000000, 0));
            row.setOnClickListener(v -> act(action));
        }
        return row;
    }

    private void fillSound() {
        meters = new MeterView(c);
        LinearLayout.LayoutParams ml = Ui.fillWidth();
        ml.leftMargin = dp(20);
        ml.rightMargin = dp(20);
        panelBody.addView(meters, ml);
        panelInfo = infoText();
        panelBody.addView(panelInfo, Ui.fillWidth());
        fillInfoLines();
        SoundControls.addTo(panelBody, shell, true);
        restartMeters();
    }

    private void fillPicture() {
        LinearLayout box = Ui.column(c);
        box.setPadding(dp(24), dp(8), dp(20), dp(8));
        box.addView(Ui.text(c, Tx.t("画面处理", "Processing"), Ui.BODY, Ui.TEXT));
        TextView note = Ui.text(c, SettingsPage.pictureNote(), Ui.MICRO, Ui.TEXT3);
        note.setPadding(0, dp(4), 0, dp(4));
        box.addView(note);
        box.addView(panelLabel(Tx.t("HDR 亮度", "HDR brightness"), SettingsPage.hdrLevelNote()));
        box.addView(Ui.segmented(c, PlayerEngine.hdrLevelLabels(), engine.hdrLevelIndex(), engine::setHdrLevelIndex), Ui.wrap());
        box.addView(panelLabel(Tx.t("画面比例", "Aspect"), ""));
        box.addView(Ui.segmented(c, resizeLabels, resize, i -> {
            resize = i;
            aspect.setResizeMode(RESIZE_MODES[i]);
        }), Ui.wrap());
        panelBody.addView(box, Ui.fillWidth());
        panelInfo = infoText();
        panelBody.addView(panelInfo, Ui.fillWidth());
        fillInfoLines();
    }

    private void fillSpeed() {
        float now = engine.speed();
        for (float s : SPEEDS) {
            String name = s == 1f ? Tx.t("正常", "Normal") : (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + Tx.t(" 倍", "×");
            panelBody.addView(option(name, "", Math.abs(now - s) < 0.01f, () -> {
                engine.setSpeed(s);
                openPanel("speed");
            }));
        }
    }

    private TextView infoText() {
        TextView t = Ui.text(c, "", Ui.CAPTION, Ui.TEXT2);
        t.setLineSpacing(0, 1.35f);
        t.setPadding(dp(24), dp(12), dp(24), dp(12));
        return t;
    }

    private void fillInfoLines() {
        if (panelInfo == null) return;
        StringBuilder s = new StringBuilder();
        boolean sound = panelKind.equals("sound");
        boolean picture = panelKind.equals("picture");
        if (!sound && engine.hasVideo()) {
            line(s, Tx.t("画面", "Picture"), videoLine());
            line(s, Tx.t("画面处理", "Processing"), engine.videoProcessText());
            line(s, Tx.t("杜比视界", "Dolby Vision"), engine.doviMetadataText());
            line(s, "HDR10+", engine.hdr10PlusText());
        }
        if (!picture) {
            line(s, Tx.t("音轨", "Audio"), audioLine());
            line(s, Tx.t("输出", "Output"), engine.outputSummary());
            line(s, Tx.t("声道布局", "Layout"), engine.stageSummary());
        }
        if (panelKind.equals("info")) {
            PlayerEngine.Item it = engine.current();
            if (it != null && it.file() != null) line(s, Tx.t("文件", "File"), it.file().getPath());
            line(s, Tx.t("续播", "Resume"), engine.resumeText());
        }
        String text = s.toString().trim();
        if (!text.contentEquals(panelInfo.getText())) panelInfo.setText(text);
    }

    private String videoLine() {
        Format v = selected(C.TRACK_TYPE_VIDEO);
        if (v == null) return "";
        StringBuilder s = new StringBuilder(MediaLabels.resolution(v.width, v.height));
        if (v.width > 0 && v.height > 0) s.append(Tx.t("（", " (")).append(v.width).append(" × ").append(v.height).append(Tx.t("）", ")"));
        String codec = MediaLabels.videoCodec(v);
        if (!codec.isEmpty()) s.append(" · ").append(codec);
        String range = engine.dynamicRange(v);
        if (!range.isEmpty()) s.append(" · ").append(range);
        if (v.frameRate > 0) {
            String fps = String.format(Locale.ROOT, "%.3f", v.frameRate).replaceAll("0+$", "").replaceAll("[.]$", "");
            s.append(" · ").append(fps).append(" fps");
        }
        return s.toString();
    }

    private String audioLine() {
        Format a = selected(C.TRACK_TYPE_AUDIO);
        if (a == null) return engine.audioSummary();
        StringBuilder s = new StringBuilder(MediaLabels.audioLabel(a));
        if (a.sampleRate > 0 && a.sampleRate <= 48000) {
            s.append(" · ").append(a.sampleRate % 1000 == 0 ? String.valueOf(a.sampleRate / 1000)
                    : String.format(Locale.ROOT, "%.1f", a.sampleRate / 1000.0)).append(" kHz");
        }
        String language = languageName(a.language);
        if (!language.isEmpty()) s.append(" · ").append(language);
        return s.toString();
    }

    private static void line(StringBuilder s, String name, String value) {
        if (value == null || value.isEmpty()) return;
        s.append(name).append(Tx.t("：", ": ")).append(value).append('\n');
    }

    private static String subtitleName(Format f) {
        String mime = MimeTypes.APPLICATION_MEDIA3_CUES.equals(f.sampleMimeType) && f.codecs != null ? f.codecs
                : f.sampleMimeType == null ? "" : f.sampleMimeType;
        switch (mime) {
            case MimeTypes.APPLICATION_PGS:
                return Tx.t("图形字幕（PGS）", "Image subtitles (PGS)");
            case MimeTypes.APPLICATION_SUBRIP:
                return "SRT";
            case MimeTypes.TEXT_SSA:
                return "ASS / SSA";
            case MimeTypes.TEXT_VTT:
                return "WebVTT";
            case MimeTypes.APPLICATION_VOBSUB:
                return Tx.t("DVD 图形字幕", "DVD image subtitles");
            case MimeTypes.APPLICATION_DVBSUBS:
                return Tx.t("DVB 图形字幕", "DVB image subtitles");
            default:
                return mime.isEmpty() ? Tx.t("字幕", "Subs") : mime;
        }
    }

    static String languageName(String code) {
        if (code == null || code.isEmpty() || code.equals("und")) return "";
        String c2 = code.toLowerCase(Locale.ROOT);
        if (c2.startsWith("zh-hant") || c2.equals("zh-tw") || c2.equals("zh-hk")) return Tx.t("繁体中文", "Chinese (Traditional)");
        if (c2.startsWith("zh") || c2.equals("chi") || c2.equals("zho") || c2.equals("cmn")) return Tx.t("中文", "Chinese");
        if (c2.equals("yue")) return Tx.t("粤语", "Cantonese");
        if (c2.startsWith("en")) return Tx.t("英文", "English");
        if (c2.startsWith("ja") || c2.equals("jpn")) return Tx.t("日文", "Japanese");
        if (c2.startsWith("ko") || c2.equals("kor")) return Tx.t("韩文", "Korean");
        if (c2.startsWith("fr")) return Tx.t("法文", "French");
        if (c2.startsWith("de") || c2.equals("ger")) return Tx.t("德文", "German");
        if (c2.startsWith("es") || c2.equals("spa")) return Tx.t("西班牙文", "Spanish");
        if (c2.startsWith("ms") || c2.equals("may")) return Tx.t("马来文", "Malay");
        return code;
    }
}
