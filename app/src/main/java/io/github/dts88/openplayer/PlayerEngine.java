package io.github.dts88.openplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.github.dts88.openplayer.bluray.BlurayExtractorsFactory;
import io.github.dts88.openplayer.bluray.M2tsDataSource;
import io.github.dts88.openplayer.music.CueSheet;
import io.github.dts88.openplayer.subs.Libass;
import io.github.dts88.openplayer.subs.PlayerSubtitles;
import io.github.dts88.openplayer.subs.SidecarSubtitles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@OptIn(markerClass = UnstableApi.class)
public final class PlayerEngine {
    public interface Listener {
        default void onPlayerReplaced(ExoPlayer player) {
        }

        default void onChanged() {
        }
    }

    public static final class VideoViews {
        final SurfaceView system;
        final SurfaceView gl;
        final AspectRatioFrameLayout aspect;

        public VideoViews(SurfaceView system, SurfaceView gl, AspectRatioFrameLayout aspect) {
            this.system = system;
            this.gl = gl;
            this.aspect = aspect;
        }
    }

    public static final class Item {
        public final Uri uri;
        public final String title;
        public final String subtitle;
        public final List<CueSheet.Track> chapters;
        public final String chapterSource;
        public final long startMs;
        private final int remote;

        public Item(Uri uri, String title) {
            this(uri, title, "", Collections.emptyList(), "", -1, 0);
        }

        private Item(Uri uri, String title, String subtitle, List<CueSheet.Track> chapters, String chapterSource,
                     long startMs, int remote) {
            this.uri = uri;
            this.title = title;
            this.subtitle = subtitle;
            this.chapters = chapters;
            this.chapterSource = chapterSource;
            this.startMs = startMs;
            this.remote = remote;
        }

        public static Item file(File f) {
            return new Item(Uri.fromFile(f), f.getName());
        }

        public static Item remote(Uri uri, String title, boolean audio) {
            return new Item(uri, title, "", Collections.emptyList(), "", -1, audio ? 2 : 1);
        }

        public Item titled(String title, String subtitle) {
            return new Item(uri, title, subtitle == null ? "" : subtitle, chapters, chapterSource, startMs, remote);
        }

        public Item chapters(List<CueSheet.Track> list, String source) {
            return new Item(uri, title, subtitle, list, source, startMs, remote);
        }

        public Item startAt(long ms) {
            return new Item(uri, title, subtitle, chapters, chapterSource, ms, remote);
        }

        public boolean audioOnly() {
            if (remote != 0) return remote == 2;
            File f = file();
            return f != null && MediaFiles.isAudio(f);
        }

        public File file() {
            return "file".equals(uri.getScheme()) && uri.getPath() != null ? new File(uri.getPath()) : null;
        }
    }

    private static final String PREFS = "player";
    private static final String KEY_MODE = "downmix_mode";
    private static final String KEY_HEADROOM = "headroom_db";
    private static final String KEY_SURROUND = "surround_51";
    private static final String KEY_UPMIX = "top_upmix_db";
    private static final String KEY_NIGHT = "night_mode";
    private static final String KEY_STEREO_WIDE = "stereo_wide";
    private static final String KEY_STEREO_SURROUND = "stereo_surround";
    private static final String KEY_HDR_LEVEL = "hdr_level";
    private static final String KEY_AUDIO_LANGUAGE = "audio_language";
    private static final String KEY_TEXT_LANGUAGE = "text_language";
    private static final String KEY_NATIVE_ASS = "native_ass";

    public static String[] modeLabels() {
        return new String[]{Tx.t("多声道直出", "Direct"), Tx.t("标准下混", "Downmix"), Tx.t("下混 + 低音", "Downmix + LFE")};
    }
    public static String[] placementLabels() {
        return new String[]{Tx.t("侧面", "Side"), Tx.t("后面", "Rear"), Tx.t("侧面 + 后面", "Side + rear")};
    }

    static final String[] AUDIO_QUALITY_ORDER = {
            MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_AC3};

    private static final int MAX_CUE_BYTES = 1052672;
    private static final long PREVIOUS_RESTARTS_AFTER_MS = 3200;
    private static final long VIDEO_OFF_DELAY_MS = 3100;
    private static final long TICK_MS = 250;
    private static final int KEEP_EVERY_TICKS = 37;
    private static final int SIDECAR_ID_BASE = 128;

    private static final int WHY_SETTING = 0x2B1;
    private static final int WHY_QUEUE = 0x2B3;
    private static final int WHY_STATE = 0x2B9;
    private static final int WHY_TRACKS = 0x2C5;
    private static final int WHY_PICTURE = 0x2CF;

    private static final boolean HDR_OUTPUT = false;
    private static final boolean WIDE_GAMUT = true;

    private static PlayerEngine instance;

    public static synchronized PlayerEngine get(Context context) {
        if (instance == null) instance = new PlayerEngine(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final OutputGuard guard = new OutputGuard();
    private final ChannelMeter meter = new ChannelMeter();
    private final OutputStage stage = new OutputStage(guard, meter);
    private final VideoFrameGate gate = new VideoFrameGate();
    private final DoviFrames doviFrames = new DoviFrames();
    private final Hdr10PlusFrames hdr10pFrames = new Hdr10PlusFrames();
    private final AtomicLong textDelayUs = new AtomicLong();
    private final PlaybackPositions positions;

    private boolean hdr10pShown;
    private boolean gpuFallback;
    private int hdrLevel = VideoLook.DEFAULT_TARGET;
    private VideoLook look;
    private VideoViews views;
    private GlVideoRenderer glRenderer;
    private Surface glInput;
    private String glError = "";
    private Format videoInputFormat;
    private Format videoTrackFormat;
    private DownmixAudioProcessor downmix;
    private boolean floatOutput;
    private boolean floatBroken;
    private float speed = 1f;
    private Libass.Session ass;
    private boolean oneAtATime;
    private ExoPlayer player;
    private int revision;

    private String title = "";
    private String inputFormat = "";
    private String trackConfig = "";
    private String lastError = "";
    private String friendlyError = "";
    private long waitSinceMs;
    private int ticks;
    private boolean itemOpen;
    private boolean itemReady;

    private String positionKey;
    private String resumeText = "";
    private List<CueSheet.Track> chapters = Collections.emptyList();
    private List<Item> queue = Collections.emptyList();
    private int queueIndex = -1;
    private Item current;
    private long lastPositionMs;
    private long lastDurationMs;
    private boolean hasVideo;
    private ListenableFuture<MediaController> serviceLink;

    private int shownActivities;
    private boolean videoOff;
    private final Runnable videoOffLater = () -> setVideoOff(true);

    private PlayerEngine(Context app) {
        this.app = app;
        positions = new PlaybackPositions(new File(app.getFilesDir(), "resume.tsv"));
        SharedPreferences prefs = prefs();
        DownmixAudioProcessor.Mode mode = DownmixAudioProcessor.Mode.fromName(prefs.getString(KEY_MODE, null));
        guard.setHeadroomDb(prefs.getInt(KEY_HEADROOM, 0));
        stage.setPlacement(Surround51.Placement.fromName(prefs.getString(KEY_SURROUND, null)));
        stage.setUpmixDb(prefs.getInt(KEY_UPMIX, 0));
        stage.setNight(prefs.getBoolean(KEY_NIGHT, false));
        stage.setStereoWide(prefs.getBoolean(KEY_STEREO_WIDE, false));
        stage.setStereoSurround(prefs.getBoolean(KEY_STEREO_SURROUND, false));
        hdrLevel = within(prefs.getInt(KEY_HDR_LEVEL, VideoLook.DEFAULT_TARGET), VideoLook.TARGET_NITS.length);
        downmix = new DownmixAudioProcessor(mode);
        buildPlayer();
        ui.post(ticker);
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int within(int index, int count) {
        return Math.max(0, Math.min(count - 1, index));
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed(int why) {
        revision = revision * 131 + why;
        for (Listener l : new ArrayList<>(listeners)) l.onChanged();
    }

    public int revision() {
        return revision;
    }

    public ExoPlayer player() {
        return player;
    }

    public boolean passThrough() {
        return downmix.getMode() == DownmixAudioProcessor.Mode.PLATFORM;
    }

    public int headroomDb() {
        return guard.headroomDb();
    }

    public int upmixDb() {
        return stage.upmixDb();
    }

    public boolean night() {
        return stage.night();
    }

    public boolean stereoWide() {
        return stage.stereoWide();
    }

    public boolean stereoSurround() {
        return stage.stereoSurround();
    }

    public int modeIndex() {
        return downmix.getMode().ordinal();
    }

    public int placementIndex() {
        return stage.placement().ordinal();
    }

    public float[] takeMeterDb() {
        return passThrough() ? meter.takeLiveDb() : new float[0];
    }

    public static String[] meterNames(int channels) {
        return ChannelMeter.names(channels);
    }

    public static int[] headroomChoices() {
        return OutputGuard.HEADROOM_CHOICES_DB;
    }

    public static int[] upmixChoices() {
        return TopUpmix.GAIN_CHOICES_DB;
    }

    public static String[] hdrLevelLabels() {
        String[] labels = VideoLook.targetLabels();
        labels[VideoLook.DEFAULT_TARGET] += Tx.t("（推荐）", " (recommended)");
        return labels;
    }

    public int hdrLevelIndex() {
        return hdrLevel;
    }

    public void setModeIndex(int index) {
        DownmixAudioProcessor.Mode m = DownmixAudioProcessor.Mode.values()[index];
        DownmixAudioProcessor.Mode old = downmix.getMode();
        if (old == m) return;
        prefs().edit().putString(KEY_MODE, m.name()).apply();
        boolean rebuild = (old == DownmixAudioProcessor.Mode.PLATFORM) != (m == DownmixAudioProcessor.Mode.PLATFORM);
        if (rebuild) {
            rebuildPlayer(m, false);
        } else {
            downmix.setMode(m);
        }
        changed(WHY_SETTING);
    }

    public void setPlacementIndex(int index) {
        Surround51.Placement p = Surround51.Placement.values()[index];
        if (p == stage.placement()) return;
        stage.setPlacement(p);
        prefs().edit().putString(KEY_SURROUND, p.name()).apply();
    }

    public void setUpmix(int db) {
        if (stage.upmixDb() == db) return;
        stage.setUpmixDb(db);
        prefs().edit().putInt(KEY_UPMIX, db).apply();
    }

    public void setNight(boolean on) {
        if (stage.night() == on) return;
        stage.setNight(on);
        prefs().edit().putBoolean(KEY_NIGHT, on).apply();
    }

    public void setHeadroom(int db) {
        if (guard.headroomDb() == db) return;
        prefs().edit().putInt(KEY_HEADROOM, db).apply();
        guard.setHeadroomDb(db);
    }

    public void setStereoWide(boolean on) {
        if (stage.stereoWide() == on) return;
        stage.setStereoWide(on);
        prefs().edit().putBoolean(KEY_STEREO_WIDE, on).apply();
        if (passThrough()) rebuildPlayer(downmix.getMode(), false);
    }

    public void setStereoSurround(boolean on) {
        if (stage.stereoSurround() == on) return;
        stage.setStereoSurround(on);
        prefs().edit().putBoolean(KEY_STEREO_SURROUND, on).apply();
        if (!stage.stereoWide() && passThrough()) rebuildPlayer(downmix.getMode(), false);
        changed(WHY_SETTING);
    }

    public void setHdrLevelIndex(int index) {
        int v = within(index, VideoLook.TARGET_NITS.length);
        if (v == hdrLevel) return;
        hdrLevel = v;
        prefs().edit().putInt(KEY_HDR_LEVEL, v).apply();
        pushLook();
        changed(WHY_PICTURE);
    }

    public void setSpeed(float value) {
        speed = value;
        boolean wantFloat = passThrough() && !floatBroken && value == 1f;
        if (passThrough() && wantFloat != floatOutput) {
            rebuildPlayer(downmix.getMode(), false);
        } else {
            player.setPlaybackSpeed(value);
        }
        changed(WHY_SETTING);
    }

    public float speed() {
        return speed;
    }

    public boolean nativeAss() {
        return prefs().getBoolean(KEY_NATIVE_ASS, true);
    }

    public void setNativeAss(boolean on) {
        if (nativeAss() == on) return;
        prefs().edit().putBoolean(KEY_NATIVE_ASS, on).apply();
        rebuildPlayer(downmix.getMode(), false);
        changed(WHY_SETTING);
    }

    public Libass.Session assSession() {
        return ass;
    }

    public long subtitleDelayMs() {
        return textDelayUs.get() / 1000;
    }

    public void setSubtitleDelayMs(long ms) {
        long value = DelayedTextRenderer.clampMs(ms);
        long old = textDelayUs.getAndSet(value * 1000) / 1000;
        if (old == value) return;
        if (player.getMediaItemCount() > 0 && player.getPlaybackState() != Player.STATE_IDLE) {
            player.seekTo(player.getCurrentPosition());
        }
        changed(WHY_SETTING);
    }

    public String audioLanguage() {
        return prefs().getString(KEY_AUDIO_LANGUAGE, "");
    }

    public void setAudioLanguage(String language) {
        prefs().edit().putString(KEY_AUDIO_LANGUAGE, language).apply();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage(language.isEmpty() ? null : language).build());
    }

    public String textLanguage() {
        return prefs().getString(KEY_TEXT_LANGUAGE, "");
    }

    public void setTextLanguage(String language) {
        prefs().edit().putString(KEY_TEXT_LANGUAGE, language).apply();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredTextLanguage(language.isEmpty() ? null : language).build());
    }

    public List<CueSheet.Track> chapters() {
        return chapters;
    }

    public int chapterIndex() {
        return chapters.isEmpty() ? -1 : CueSheet.indexAt(chapters, player.getCurrentPosition());
    }

    public String resumeText() {
        return resumeText;
    }

    public String lastError() {
        return lastError;
    }

    public String friendlyError() {
        return friendlyError;
    }

    public Item current() {
        return current;
    }

    public List<Item> queue() {
        return queue;
    }

    public int queueIndex() {
        return queueIndex;
    }

    public boolean hasVideo() {
        return hasVideo;
    }

    public String audioSummary() {
        return inputFormat;
    }

    public String outputSummary() {
        return trackConfig;
    }

    public String stageSummary() {
        if (!passThrough()) return downmix.status();
        return stage.description();
    }

    public boolean videoOnGpu() {
        return glRenderer != null;
    }

    public String videoProcessText() {
        if (!hasVideo) return "";
        GlVideoRenderer r = glRenderer;
        if (r == null) {
            return Tx.t("系统直接显示", "Shown by the system") + (look != null && look.hdr() ? Tx.t("（", " (") + look.sourceText() + Tx.t("，由车机处理）", ", handled by the head unit)") : "")
                    + (glError.isEmpty() ? "" : Tx.t("；画面处理出错：", "; processing error: ") + glError);
        }
        String text = r.lookText();
        return text.isEmpty() ? Tx.t("正在启动…", "Starting…") : text;
    }

    public String doviMetadataText() {
        if (!doviFrames.enabled()) return "";
        long seen = doviFrames.frames();
        VideoLook now = look;
        boolean p5 = now != null && now.doviProfile == 5;
        if (doviFrames.parsed() > 0) {
            return p5 ? Tx.t("逐帧元数据，据此转换色彩", "Per-frame metadata, used to convert colours") : Tx.t("含元数据，色调映射时采用各场景的峰值亮度", "Metadata present; tone mapping uses each scene's peak");
        }
        if (seen < 48) return "";
        return Tx.t("标记为杜比视界，但未检出元数据：", "Tagged Dolby Vision, but no metadata found: ") + (p5 ? Tx.t("无法转换色彩", "colours cannot be converted") : Tx.t("按 HDR10 播放", "played as HDR10"));
    }

    public String hdr10PlusText() {
        if (!hdr10pFrames.seen()) return "";
        if (glRenderer == null) return Tx.t("含动态元数据；系统直接显示时由车机处理", "Dynamic metadata present; handled by the head unit when shown by the system");
        VideoLook now = look;
        if (now != null && now.doviProfile > 0 && doviFrames.parsed() > 0) return Tx.t("含动态元数据；本片采用杜比视界元数据", "Dynamic metadata present; this video uses its Dolby Vision metadata");
        return Tx.t("含动态元数据，按场景映射", "Dynamic metadata present, mapped per scene");
    }

    public String dynamicRange(Format f) {
        String range = MediaLabels.dynamicRange(f);
        if (!hdr10pFrames.seen()) return range;
        if (range.isEmpty() || range.equals("HDR10")) return "HDR10+";
        return range.startsWith(Tx.t("杜比视界", "Dolby Vision")) ? range + " · HDR10+" : range;
    }

    public String waitingText() {
        if (player.getPlaybackState() != Player.STATE_BUFFERING) return "";
        if (waitSinceMs == 0) waitSinceMs = SystemClock.elapsedRealtime();
        long waited = (SystemClock.elapsedRealtime() - waitSinceMs) / 1000;
        if (!itemReady) return waited < 3 ? "" : Tx.t("正在读取文件，已等待 ", "Reading the file, waited ") + waited + Tx.t(" 秒", " s");
        if (waited < 2) return "";
        return waited < 4 ? Tx.t("正在读取…", "Reading…") : Tx.t("读取较慢，已等待 ", "Slow to read, waited ") + waited + Tx.t(" 秒", " s");
    }

    public long resumePosition(File f) {
        PlaybackPositions.Entry e = positions.peek(PlaybackPositions.key(f.getAbsolutePath(), f.length()));
        return e == null ? -1 : e.positionMs;
    }

    public long resumeDuration(File f) {
        PlaybackPositions.Entry e = positions.peek(PlaybackPositions.key(f.getAbsolutePath(), f.length()));
        return e == null ? -1 : e.durationMs;
    }

    public void stopAll() {
        if (player.getMediaItemCount() == 0 && current == null) return;
        savePosition();
        itemOpen = false;
        player.stop();
        player.clearMediaItems();
        queue = new ArrayList<>();
        queueIndex = -1;
        current = null;
        positionKey = null;
        chapters = Collections.emptyList();
        title = "";
        resumeText = "";
        lastError = "";
        friendlyError = "";
        hasVideo = false;
        changed(WHY_QUEUE);
    }

    public void retry() {
        if (player.getMediaItemCount() == 0) return;
        lastError = "";
        friendlyError = "";
        if (!itemOpen) openItem();
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        player.play();
        changed(WHY_STATE);
    }

    private static String friendly(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
                return Tx.t("未找到文件：U 盘已拔出，或文件已被删除。", "File not found: the USB drive was removed, or the file was deleted.");
            case PlaybackException.ERROR_CODE_IO_NO_PERMISSION:
                return Tx.t("无权读取此文件。", "No permission to read this file.");
            case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED:
                return Tx.t("读取文件出错，请检查 U 盘连接。", "Error reading the file. Check the USB drive.");
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return Tx.t("不支持此文件格式。", "This file format is not supported.");
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                return Tx.t("文件解析中断：文件可能已损坏或不完整。", "Parsing stopped: the file may be damaged or incomplete.");
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
                return Tx.t("解码器初始化失败。", "The decoder could not start.");
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                return Tx.t("车机不支持此影片的编码格式。", "The head unit does not support this video's codec.");
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return Tx.t("解码出错。", "Decoding error.");
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED:
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED:
                return Tx.t("音频输出失败。", "Audio output failed.");
            default:
                return Tx.t("无法播放此文件。", "This file cannot be played.");
        }
    }

    public void attachVideo(VideoViews v) {
        if (views == v) return;
        if (views != null) detachVideo(views);
        views = v;
        v.gl.getHolder().addCallback(glSurfaceCallback);
        VideoSize size = player.getVideoSize();
        if (size.width > 0 && size.height > 0) {
            v.aspect.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height);
        }
        applyVideoPath();
    }

    public void detachVideo(VideoViews v) {
        if (views != v) return;
        v.gl.getHolder().removeCallback(glSurfaceCallback);
        views = null;
        applyVideoPath();
    }

    private boolean wantsGpu() {
        return !gpuFallback && look != null;
    }

    private void applyVideoPath() {
        VideoViews v = views;
        if (v == null) {
            gate.setEnabled(false);
            player.clearVideoSurface();
            releaseGl();
            return;
        }
        if (!wantsGpu()) {
            gate.setEnabled(false);
            v.gl.setVisibility(View.GONE);
            v.system.setVisibility(View.VISIBLE);
            player.setVideoSurfaceView(v.system);
            releaseGl();
            return;
        }
        v.system.setVisibility(View.GONE);
        v.gl.setVisibility(View.VISIBLE);
        if (glRenderer == null) {
            player.clearVideoSurface();
            GlVideoRenderer r = new GlVideoRenderer(gate, doviFrames, hdr10pFrames, GlVideoRenderer.Mode.YUV,
                    HDR_OUTPUT, ui, glListener);
            glRenderer = r;
            r.start();
            pushLook();
            Surface out = v.gl.getHolder().getSurface();
            if (out != null && out.isValid() && v.gl.getWidth() > 0 && v.gl.getHeight() > 0) {
                r.setOutputSurface(out, v.gl.getWidth(), v.gl.getHeight());
            }
        } else if (glInput != null) {
            gate.setEnabled(true);
            player.setVideoSurface(glInput);
        }
    }

    private void releaseGl() {
        GlVideoRenderer r = glRenderer;
        glRenderer = null;
        glInput = null;
        if (r != null) r.release();
    }

    private final GlVideoRenderer.Listener glListener = new GlVideoRenderer.Listener() {
        @Override
        public void onGlReady(GlVideoRenderer renderer, Surface input) {
            if (renderer != glRenderer || !wantsGpu()) return;
            glInput = input;
            gate.setEnabled(true);
            player.setVideoSurface(input);
            pushLook();
        }

        @Override
        public void onGlError(GlVideoRenderer renderer, String what) {
            if (renderer != glRenderer) return;
            glError = what;
            Toast.makeText(app, Tx.t("画面处理出错，本片改由系统直接显示，颜色可能偏艳。", "Picture processing failed; this video is shown by the system and colours may look oversaturated."), Toast.LENGTH_LONG).show();
            gpuFallback = true;
            applyVideoPath();
            changed(WHY_PICTURE);
        }
    };

    private final SurfaceHolder.Callback glSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (glRenderer != null) glRenderer.setOutputSurface(holder.getSurface(), width, height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (glRenderer != null) glRenderer.setOutputSurface(null, 0, 0);
        }
    };

    private void pushLook() {
        Format f = videoInputFormat != null ? videoInputFormat : videoTrackFormat;
        if (f == null) return;
        ColorInfo written = f.colorInfo;
        if (hdr10pFrames.seen() && (written == null || written.colorTransfer == Format.NO_VALUE)
                && MediaLabels.dolbyVisionProfile(f) < 0) {
            f = f.buildUpon().setColorInfo(new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build()).build();
        }
        VideoLook now = VideoLook.of(f, hdrLevel, HDR_OUTPUT, true, true, WIDE_GAMUT);
        boolean differs = !now.same(look);
        look = now;
        if (wantsGpu() != (glRenderer != null) && views != null) applyVideoPath();
        GlVideoRenderer r = glRenderer;
        if (r != null) r.setLook(now);
        if (differs) changed(WHY_PICTURE);
    }

    private void buildPlayer() {
        boolean passThrough = passThrough();
        boolean useFloat = passThrough && !floatBroken && speed == 1f;
        floatOutput = useFloat;
        DownmixAudioProcessor processor = downmix;
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app) {
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                               boolean enableAudioTrackPlaybackParams) {
                AudioSink sink = new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(useFloat)
                        .setAudioProcessors(new AudioProcessor[]{processor})
                        .build();
                return new GuardedAudioSink(sink, stage, passThrough);
            }

            @Override
            protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper,
                                              int extensionRendererMode, ArrayList<Renderer> out) {
                int first = out.size();
                super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out);
                for (int i = first; i < out.size(); i++) out.set(i, new DelayedTextRenderer(out.get(i), textDelayUs));
            }

            @Override
            protected void buildVideoRenderers(Context context, int extensionRendererMode,
                                               MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
                                               Handler eventHandler, VideoRendererEventListener eventListener,
                                               long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
                int first = out.size();
                super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                        eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
                for (int i = first; i < out.size(); i++) {
                    if (out.get(i).getClass() != MediaCodecVideoRenderer.class) continue;
                    out.set(i, new BaseLayerVideoRenderer(new MediaCodecVideoRenderer.Builder(context)
                            .setCodecAdapterFactory(getCodecAdapterFactory())
                            .setMediaCodecSelector(mediaCodecSelector)
                            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                            .setEnableDecoderFallback(enableDecoderFallback)
                            .setEventHandler(eventHandler)
                            .setEventListener(eventListener)
                            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY), gate, doviFrames,
                            hdr10pFrames));
                    break;
                }
            }
        };
        renderers.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);

        DefaultTrackSelector selector = new DefaultTrackSelector(app);
        String audioLanguage = audioLanguage();
        String textLanguage = textLanguage();
        selector.setParameters(selector.buildUponParameters()
                .setConstrainAudioChannelCountToDeviceCapabilities(false)
                .setPreferredAudioMimeTypes(AUDIO_QUALITY_ORDER)
                .setPreferredAudioLanguage(audioLanguage.isEmpty() ? null : audioLanguage)
                .setPreferredTextLanguage(textLanguage.isEmpty() ? null : textLanguage));

        ass = nativeAss() ? Libass.newSession() : null;
        RenderersFactory factory = ass == null ? renderers : ass.renderers(renderers, textDelayUs);
        player = new ExoPlayer.Builder(app, factory)
                .setMediaSourceFactory(newSources(app, ass))
                .setTrackSelector(selector)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true)
                .build();
        player.addAnalyticsListener(analytics);
        player.addListener(listener);
        if (ass != null) ass.bind(player);
        if (speed != 1f) player.setPlaybackSpeed(speed);
    }

    private static DefaultMediaSourceFactory newSources(Context context, Libass.Session ass) {
        BlurayExtractorsFactory extractors = new BlurayExtractorsFactory();
        SubtitleParser.Factory parsers = new PlayerSubtitles();
        if (ass != null) {
            parsers = ass.parsers(parsers);
            extractors.setReplacer(ass::replace);
        }
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(
                new M2tsDataSource.Factory(new DefaultDataSource.Factory(context)), extractors);
        sources.setSubtitleParserFactory(parsers);
        return sources;
    }

    public void playQueue(List<Item> items, int index) {
        if (items.isEmpty() || index < 0 || index >= items.size()) return;
        savePosition();
        itemOpen = false;
        queue = new ArrayList<>(items);
        oneAtATime = false;
        for (Item it : queue) oneAtATime |= !it.audioOnly();
        startAt(index, true);
        startService();
        changed(WHY_QUEUE);
    }

    private void startAt(int index, boolean explicit) {
        long startMs = beginItem(index, explicit);
        if (oneAtATime) {
            player.setMediaItem(buildItem(queue.get(index)), startMs);
        } else {
            List<MediaItem> media = new ArrayList<>(queue.size());
            for (Item it : queue) media.add(buildItem(it));
            player.setMediaItems(media, index, startMs);
        }
        player.prepare();
        player.play();
    }

    public void jumpToQueue(int index) {
        if (index < 0 || index >= queue.size() || index == queueIndex) return;
        if (oneAtATime) {
            savePosition();
            itemOpen = false;
            startAt(index, false);
            changed(WHY_QUEUE);
        } else {
            player.seekTo(index, 0);
        }
    }

    private void startService() {
        if (serviceLink != null) return;
        try {
            SessionToken token = new SessionToken(app, new ComponentName(app, PlaybackService.class));
            serviceLink = new MediaController.Builder(app, token).buildAsync();
        } catch (RuntimeException e) {
            serviceLink = Futures.immediateFailedFuture(e);
        }
    }

    private long beginItem(int index, boolean explicit) {
        Item it = queue.get(index);
        queueIndex = index;
        current = it;
        chapters = it.chapters;
        positionKey = null;
        resumeText = "";
        lastPositionMs = 0;
        lastDurationMs = 0;
        long resumeMs = -1;
        File f = it.file();
        if (f != null) positionKey = PlaybackPositions.key(f.getAbsolutePath(), f.length());
        if (it.startMs >= 0) resumeMs = it.startMs;
        else if (explicit && positionKey != null) resumeMs = positions.resumeAt(positionKey);
        title = it.title;
        inputFormat = "";
        trackConfig = "";
        lastError = "";
        friendlyError = "";
        waitSinceMs = 0;
        hasVideo = false;
        videoInputFormat = null;
        videoTrackFormat = null;
        look = null;
        textDelayUs.set(0);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build());
        openItem();
        if (resumeMs > 0 && it.startMs < 0) resumeText = Tx.t("从上次的 ", "Resumed from ") + MediaLabels.clock(resumeMs) + Tx.t(" 继续播放", "");
        return Math.max(0, resumeMs);
    }

    private MediaItem buildItem(Item it) {
        MediaMetadata.Builder meta = new MediaMetadata.Builder().setTitle(it.title);
        if (!it.subtitle.isEmpty()) meta.setArtist(it.subtitle);
        MediaItem.Builder b = new MediaItem.Builder().setUri(it.uri).setMediaMetadata(meta.build());
        File f = it.file();
        if (f == null) return b.build();
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        File cache = new File(app.getCacheDir(), "subs");
        for (SidecarSubtitles.Found sub : SidecarSubtitles.find(f)) {
            try {
                File utf8 = SidecarSubtitles.asUtf8(sub.file, cache);
                configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(utf8))
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setId(String.valueOf(SIDECAR_ID_BASE + configs.size()))
                        .setSelectionFlags(configs.isEmpty() ? C.SELECTION_FLAG_DEFAULT : 0)
                        .build());
            } catch (IOException ignored) {
            }
        }
        if (!configs.isEmpty()) b.setSubtitleConfigurations(configs);
        return b.build();
    }

    public void next() {
        if (!chapters.isEmpty() && chapterIndex() < chapters.size() - 1) {
            seekChapter(1);
        } else if (oneAtATime) {
            jumpToQueue(queueIndex + 1);
        } else if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        }
    }

    public void previous() {
        if (!chapters.isEmpty()) {
            if (chapterIndex() > 0 || player.getCurrentPosition() > PREVIOUS_RESTARTS_AFTER_MS || !hasPreviousItem()) {
                seekChapter(-1);
                return;
            }
        }
        if (player.getCurrentPosition() > PREVIOUS_RESTARTS_AFTER_MS || !hasPreviousItem()) {
            player.seekTo(0);
        } else if (oneAtATime) {
            jumpToQueue(queueIndex - 1);
        } else {
            player.seekToPreviousMediaItem();
        }
    }

    private boolean hasPreviousItem() {
        return oneAtATime ? queueIndex > 0 : player.hasPreviousMediaItem();
    }

    private boolean hasNextItem() {
        return oneAtATime ? queueIndex < queue.size() - 1 : player.hasNextMediaItem();
    }

    public boolean hasNext() {
        return (!chapters.isEmpty() && chapterIndex() < chapters.size() - 1) || hasNextItem();
    }

    public void open(File f) {
        if (MediaFiles.isCue(f)) openCue(f);
        else playQueue(Collections.singletonList(withSiblingCue(f)), 0);
    }

    public Item withSiblingCue(File f) {
        Item it = Item.file(f);
        File cue = siblingCue(f);
        if (cue == null) return it;
        CueSheet sheet = readCue(cue);
        if (sheet == null) return it;
        List<CueSheet.Track> list = sheet.tracksIn(f.getName());
        if (list.isEmpty() && sheet.files.size() <= 1) list = sheet.tracks;
        return list.size() > 1 ? it.chapters(list, cue.getName()) : it;
    }

    private void openCue(File cue) {
        CueSheet sheet = readCue(cue);
        if (sheet == null) return;
        if (sheet.tracks.isEmpty()) {
            fail(Tx.t("此 .cue 中未读到分轨：", "No tracks found in this .cue: ") + cue.getName());
            return;
        }
        File dir = cue.getParentFile();
        String listed = sheet.files.isEmpty() ? "" : lastPathPart(sheet.files.get(0));
        File audio = null;
        File[] all = dir == null ? null : dir.listFiles();
        if (all != null) {
            String[] wants = {listed, listed.isEmpty() ? "" : stem(listed), stem(cue.getName())};
            for (int pass = 0; pass < wants.length && audio == null; pass++) {
                String want = wants[pass];
                if (want.isEmpty()) continue;
                for (File f : all) {
                    if (!f.isFile() || MediaFiles.isCue(f) || !MediaFiles.isPlayable(f)) continue;
                    boolean same = pass == 0 ? f.getName().equalsIgnoreCase(want)
                            : stem(f.getName()).equalsIgnoreCase(want) || f.getName().equalsIgnoreCase(want);
                    if (same) {
                        audio = f;
                        break;
                    }
                }
            }
        }
        if (audio == null) {
            fail(Tx.t("未找到 ", "The audio file for ") + cue.getName() + Tx.t(" 指向的音频文件", " was not found") + (listed.isEmpty() ? "" : Tx.t("（", " (") + listed + Tx.t("）", ")")));
            return;
        }
        List<CueSheet.Track> list = sheet.files.size() <= 1 ? sheet.tracks : sheet.tracksIn(sheet.files.get(0));
        String name = (sheet.title.isEmpty() ? "" : sheet.title + " · ") + audio.getName();
        playQueue(Collections.singletonList(new Item(Uri.fromFile(audio), name).chapters(list, cue.getName())), 0);
    }

    private void fail(String what) {
        lastError = what;
        Toast.makeText(app, what, Toast.LENGTH_LONG).show();
    }

    private static File siblingCue(File f) {
        File dir = f.getParentFile();
        if (dir == null) return null;
        for (String name : new String[]{stem(f.getName()) + ".cue", f.getName() + ".cue"}) {
            File c = new File(dir, name);
            if (c.isFile()) return c;
        }
        File[] all = dir.listFiles();
        if (all == null) return null;
        for (File c : all) {
            if (c.isFile() && MediaFiles.isCue(c) && stem(c.getName()).equalsIgnoreCase(stem(f.getName()))) return c;
        }
        return null;
    }

    private CueSheet readCue(File cue) {
        try (FileInputStream in = new FileInputStream(cue)) {
            byte[] buf = new byte[(int) Math.min(cue.length(), MAX_CUE_BYTES)];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            return CueSheet.parse(n == buf.length ? buf : Arrays.copyOf(buf, n));
        } catch (IOException e) {
            lastError = Tx.t("分轨表读取失败。", "The cue sheet could not be read.");
            return null;
        }
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String lastPathPart(String path) {
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return cut < 0 ? path : path.substring(cut + 1);
    }

    @SuppressWarnings("deprecation")
    private void adoptEmbeddedCue(Tracks tracks) {
        if (!chapters.isEmpty()) return;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                Metadata m = g.getTrackFormat(i).metadata;
                if (m == null) continue;
                for (int k = 0; k < m.length(); k++) {
                    Metadata.Entry e = m.get(k);
                    if (!(e instanceof androidx.media3.extractor.metadata.flac.VorbisComment)) continue;
                    androidx.media3.extractor.metadata.flac.VorbisComment c =
                            (androidx.media3.extractor.metadata.flac.VorbisComment) e;
                    if (!"CUESHEET".equalsIgnoreCase(c.key)) continue;
                    CueSheet sheet = CueSheet.parse(c.value);
                    if (sheet.tracks.size() > 1) chapters = sheet.tracks;
                    return;
                }
            }
        }
    }

    public void seekChapter(int direction) {
        if (chapters.isEmpty()) return;
        long pos = player.getCurrentPosition();
        int now = CueSheet.indexAt(chapters, pos);
        int target;
        if (direction > 0) {
            target = now + 1;
            if (target >= chapters.size()) return;
        } else {
            target = pos - chapters.get(now).startMs > PREVIOUS_RESTARTS_AFTER_MS || now == 0 ? now : now - 1;
        }
        jumpToChapter(target);
    }

    public void jumpToChapter(int index) {
        player.seekTo(chapters.get(index).startMs);
        player.play();
    }

    public void savePosition() {
        if (positionKey == null || player == null || player.getMediaItemCount() == 0) return;
        long d = player.getDuration();
        if (d == C.TIME_UNSET || d <= 0) return;
        long pos = player.getPlaybackState() == Player.STATE_ENDED ? d : player.getCurrentPosition();
        positions.update(positionKey, pos, d, System.currentTimeMillis());
    }

    public void setInterfaceShown(boolean shown) {
        shownActivities = Math.max(0, shownActivities + (shown ? 1 : -1));
        ui.removeCallbacks(videoOffLater);
        if (shownActivities > 0) {
            setVideoOff(false);
        } else {
            ui.postDelayed(videoOffLater, VIDEO_OFF_DELAY_MS);
        }
    }

    private void setVideoOff(boolean off) {
        if (off == videoOff) return;
        videoOff = off;
        applyVideoOff();
    }

    private void applyVideoOff() {
        if (player == null) return;
        boolean now = player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO);
        if (now == videoOff) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, videoOff).build());
    }

    private void openItem() {
        itemOpen = true;
        itemReady = false;
        applyVideoOff();
        if (gpuFallback) {
            gpuFallback = false;
            glError = "";
        }
        doviFrames.resetCounts();
        hdr10pFrames.resetCounts();
        hdr10pShown = false;
        if (glRenderer != null) glRenderer.resetScene();
    }

    private void rebuildPlayer(DownmixAudioProcessor.Mode m, boolean resume) {
        MediaItem item = player.getCurrentMediaItem();
        waitSinceMs = 0;
        long pos = player.getCurrentPosition();
        boolean playWhenReady = resume || player.getPlayWhenReady();
        int state = player.getPlaybackState();
        boolean wasActive = state == Player.STATE_READY || state == Player.STATE_BUFFERING;
        player.removeListener(listener);
        player.removeAnalyticsListener(analytics);
        player.release();
        if (ass != null) {
            ass.release();
            ass = null;
        }
        downmix = new DownmixAudioProcessor(m);
        trackConfig = "";
        buildPlayer();
        for (Listener l : new ArrayList<>(listeners)) l.onPlayerReplaced(player);
        applyVideoPath();
        if (item == null) return;
        if (oneAtATime || queue.isEmpty()) {
            player.setMediaItem(item, pos);
        } else {
            List<MediaItem> media = new ArrayList<>(queue.size());
            for (Item it : queue) media.add(buildItem(it));
            player.setMediaItems(media, queueIndex, pos);
        }
        if (resume || wasActive) {
            player.prepare();
            player.setPlayWhenReady(playWhenReady);
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ticks++;
            if (!hdr10pShown && hdr10pFrames.seen()) {
                hdr10pShown = true;
                pushLook();
                changed(WHY_PICTURE);
            }
            if (player.isPlaying() && ticks % KEEP_EVERY_TICKS == 0) savePosition();
            if (player.getMediaItemCount() > 0 && player.getCurrentMediaItemIndex() == queueIndex) {
                long d = player.getDuration();
                if (d != C.TIME_UNSET && d > 0) {
                    lastDurationMs = d;
                    lastPositionMs = player.getCurrentPosition();
                }
            }
            ui.postDelayed(this, TICK_MS);
        }
    };

    private final AnalyticsListener analytics = new AnalyticsListener() {
        @Override
        public void onAudioInputFormatChanged(EventTime eventTime, Format format,
                                              DecoderReuseEvaluation decoderReuseEvaluation) {
            inputFormat = MediaLabels.audioLabel(format);
        }

        @Override
        public void onVideoInputFormatChanged(EventTime eventTime, Format format,
                                              DecoderReuseEvaluation decoderReuseEvaluation) {
            videoInputFormat = format;
            pushLook();
        }

        @Override
        public void onAudioTrackInitialized(EventTime eventTime, AudioSink.AudioTrackConfig config) {
            trackConfig = Integer.bitCount(config.channelConfig) + Tx.t(" 声道，", " ch, ") + encodingName(config.encoding) + Tx.t("，", ", ")
                    + config.sampleRate + " Hz";
            changed(WHY_TRACKS);
        }
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlayerError(PlaybackException error) {
            if (floatOutput && error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) {
                floatBroken = true;
                DownmixAudioProcessor.Mode m = downmix.getMode();
                ui.post(() -> rebuildPlayer(m, true));
                return;
            }
            friendlyError = friendly(error);
            savePosition();
            itemOpen = false;
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (!isPlaying) savePosition();
            changed(WHY_STATE);
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            int i = player.getCurrentMediaItemIndex();
            if (oneAtATime || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || i == queueIndex
                    || i < 0 || i >= queue.size()) {
                return;
            }
            boolean finished = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
            if (positionKey != null && lastDurationMs > 0) {
                positions.update(positionKey, finished ? lastDurationMs : lastPositionMs, lastDurationMs,
                        System.currentTimeMillis());
            }
            beginItem(i, false);
            changed(WHY_QUEUE);
        }

        @Override
        public void onVideoSizeChanged(VideoSize size) {
            VideoViews v = views;
            if (v != null && size.width > 0 && size.height > 0) {
                v.aspect.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height);
            }
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO);
            videoTrackFormat = null;
            for (Tracks.Group g : tracks.getGroups()) {
                if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < g.length; i++) {
                    if (g.isTrackSelected(i)) videoTrackFormat = g.getTrackFormat(i);
                }
            }
            if (videoTrackFormat != null) pushLook();
            adoptEmbeddedCue(tracks);
            changed(WHY_TRACKS);
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition,
                                            int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !itemOpen) openItem();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                if (waitSinceMs == 0) waitSinceMs = SystemClock.elapsedRealtime();
                if (!friendlyError.isEmpty()) {
                    friendlyError = "";
                    lastError = "";
                    if (!itemOpen && player.getMediaItemCount() > 0) openItem();
                }
            } else {
                waitSinceMs = 0;
            }
            if (state == Player.STATE_READY) {
                itemReady = true;
            } else if (state == Player.STATE_ENDED) {
                savePosition();
                resumeText = "";
                itemOpen = false;
                if (oneAtATime && queueIndex < queue.size() - 1) {
                    int nextIndex = queueIndex + 1;
                    List<Item> same = queue;
                    ui.post(() -> {
                        if (queue == same && queueIndex == nextIndex - 1 && !itemOpen) {
                            startAt(nextIndex, false);
                            changed(WHY_QUEUE);
                        }
                    });
                }
            }
        }
    };

    private static String encodingName(int e) {
        if (e == C.ENCODING_PCM_16BIT) return Tx.t("PCM 16 位", "PCM 16-bit");
        if (e == C.ENCODING_PCM_FLOAT) return Tx.t("PCM 浮点", "PCM float");
        if (e == C.ENCODING_PCM_24BIT) return Tx.t("PCM 24 位", "PCM 24-bit");
        if (e == C.ENCODING_PCM_32BIT) return Tx.t("PCM 32 位", "PCM 32-bit");
        return String.format(Locale.ROOT, Tx.t("编码 #%d", "encoding #%d"), e);
    }
}
