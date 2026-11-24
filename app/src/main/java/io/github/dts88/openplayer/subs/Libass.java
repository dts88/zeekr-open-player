package io.github.dts88.openplayer.subs;

import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.ui.SubtitleView;

import io.github.peerless2012.ass.media.AssHandler;
import io.github.peerless2012.ass.media.AssHandlerConfig;
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor;
import io.github.peerless2012.ass.media.kt.AssPlayerKt;
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory;
import io.github.peerless2012.ass.media.render.AssRenderer;
import io.github.peerless2012.ass.media.type.AssRenderType;
import io.github.peerless2012.ass.media.widget.AssSubtitleView;

import java.util.concurrent.atomic.AtomicLong;

@OptIn(markerClass = UnstableApi.class)
public final class Libass {
    private static final int MAX_RENDER_PIXELS = 2560 * 1440;
    private static final int BITMAP_CACHE_MB = 64;
    private static final long LIBRARY_ASSUMED_OFFSET_US = 1_000_000_000_000L;

    private static Boolean available;
    private static String problem = "";

    private Libass() {
    }

    public static synchronized boolean available() {
        if (available == null) {
            try {
                System.loadLibrary("asskt");
                available = true;
            } catch (Throwable t) {
                available = false;
                problem = String.valueOf(t);
            }
        }
        return available;
    }

    public static synchronized String problem() {
        return problem;
    }

    public static final class Session {
        private final AssHandler handler;
        private final AssSubtitleParserFactory assParsers;

        private Session() {
            handler = new AssHandler(AssRenderType.OVERLAY_OPEN_GL,
                    new AssHandlerConfig(10000, BITMAP_CACHE_MB, MAX_RENDER_PIXELS));
            assParsers = new AssSubtitleParserFactory(handler);
        }

        public SubtitleParser.Factory parsers(SubtitleParser.Factory plain) {
            return new SubtitleParser.Factory() {
                @Override
                public boolean supportsFormat(Format format) {
                    return isAss(format) || plain.supportsFormat(format);
                }

                @Override
                public int getCueReplacementBehavior(Format format) {
                    return isAss(format) ? assParsers.getCueReplacementBehavior(format)
                            : plain.getCueReplacementBehavior(format);
                }

                @Override
                public SubtitleParser create(Format format) {
                    return isAss(format) ? assParsers.create(format) : plain.create(format);
                }
            };
        }

        public Extractor replace(Extractor e, SubtitleParser.Factory parsers, boolean parseDuringExtraction) {
            if (e.getClass() != MatroskaExtractor.class) return e;
            return new AssMatroskaExtractor(parsers, handler,
                    parseDuringExtraction ? 0 : MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA);
        }

        public RenderersFactory renderers(RenderersFactory base, AtomicLong delayUs) {
            RenderersFactory withAss = AssPlayerKt.withAssSupport(base, handler);
            return (eventHandler, video, audio, text, metadata) -> {
                Renderer[] list = withAss.createRenderers(eventHandler, video, audio, text, metadata);
                for (int i = 0; i < list.length; i++) {
                    if (list[i] instanceof AssRenderer) list[i] = new TimeRenderer(list[i], delayUs);
                }
                return list;
            };
        }

        public void bind(androidx.media3.exoplayer.ExoPlayer player) {
            handler.init(player);
        }

        public void attach(SubtitleView view) {
            detach(view);
            AssPlayerKt.withAssSupport(view, handler);
        }

        public void release() {
            try {
                handler.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static Session newSession() {
        if (!available()) return null;
        try {
            return new Session();
        } catch (Throwable t) {
            synchronized (Libass.class) {
                available = false;
                problem = String.valueOf(t);
            }
            return null;
        }
    }

    public static void detach(SubtitleView view) {
        for (int i = view.getChildCount() - 1; i >= 0; i--) {
            View child = view.getChildAt(i);
            if (child instanceof AssSubtitleView) view.removeViewAt(i);
        }
    }

    static boolean isAss(Format format) {
        return MimeTypes.TEXT_SSA.equals(format.sampleMimeType);
    }

    static final class TimeRenderer extends ForwardingRenderer {
        private final AtomicLong delayUs;
        private long offsetUs = LIBRARY_ASSUMED_OFFSET_US;
        private long nextOffsetUs = C.TIME_UNSET;
        private long nextFromUs;
        private int skipped;

        TimeRenderer(Renderer renderer, AtomicLong delayUs) {
            super(renderer);
            this.delayUs = delayUs;
        }

        @Override
        public void enable(RendererConfiguration configuration, Format[] formats, SampleStream stream, long positionUs,
                           boolean joining, boolean mayRenderStartOfStream, long startPositionUs, long offsetUs,
                           MediaSource.MediaPeriodId mediaPeriodId) throws ExoPlaybackException {
            this.offsetUs = offsetUs;
            nextOffsetUs = C.TIME_UNSET;
            super.enable(configuration, formats, stream, positionUs, joining, mayRenderStartOfStream, startPositionUs,
                    offsetUs, mediaPeriodId);
        }

        @Override
        public void replaceStream(Format[] formats, SampleStream stream, long startPositionUs, long offsetUs,
                                  MediaSource.MediaPeriodId mediaPeriodId) throws ExoPlaybackException {
            nextOffsetUs = offsetUs;
            nextFromUs = offsetUs + startPositionUs;
            super.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId);
        }

        @Override
        public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
            if (nextOffsetUs != C.TIME_UNSET && positionUs >= nextFromUs) {
                offsetUs = nextOffsetUs;
                nextOffsetUs = C.TIME_UNSET;
            }
            try {
                super.render(forLibrary(positionUs, offsetUs, delayUs.get()), elapsedRealtimeUs);
            } catch (RuntimeException e) {
                skipped++;
            }
        }

        static long forLibrary(long positionUs, long offsetUs, long delayUs) {
            return positionUs - offsetUs - delayUs + LIBRARY_ASSUMED_OFFSET_US;
        }
    }
}
