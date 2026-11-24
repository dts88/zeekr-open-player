package io.github.dts88.openplayer.subs;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.pgs.PgsParser;


@OptIn(markerClass = UnstableApi.class)
public final class PlayerSubtitles implements SubtitleParser.Factory {
    private final SubtitleParser.Factory base = new DefaultSubtitleParserFactory();

    @Override
    public boolean supportsFormat(Format format) {
        return base.supportsFormat(format);
    }

    @Override
    public @Format.CueReplacementBehavior int getCueReplacementBehavior(Format format) {
        return base.getCueReplacementBehavior(format);
    }

    @Override
    public SubtitleParser create(Format format) {
        if (MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType)) {
            return new NormalizedPgs(format.language);
        }
        return base.create(format);
    }

    private static final class NormalizedPgs implements SubtitleParser {
        private static final byte ZLIB = 0x78;
        private static final int LOG_EVERY = 400;

        private final PgsParser inner = new PgsParser();
        private final PgsNormalizer normalizer = new PgsNormalizer();
        private final String language;
        private int loggedAt;
        private boolean failed;

        NormalizedPgs(String language) {
            this.language = language;
        }

        @Override
        public void parse(byte[] data, int offset, int length, OutputOptions outputOptions,
                          Consumer<CuesWithTiming> output) {
            if (length > 0 && data[offset] == ZLIB) {
                inner.parse(data, offset, length, outputOptions, output);
                return;
            }
            try {
                normalizer.feed(data, offset, length,
                        (set, n) -> inner.parse(set, 0, n, outputOptions, output));
            } catch (RuntimeException e) {
                failed = true;
                normalizer.reset();
                inner.parse(data, offset, length, outputOptions, output);
                return;
            }
            int sets = normalizer.sets();
            if (sets > 0 && (loggedAt == 0 || sets - loggedAt >= LOG_EVERY)) {
                loggedAt = sets;
            }
        }

        @Override
        public void reset() {
            normalizer.reset();
            inner.reset();
        }

        @Override
        public @Format.CueReplacementBehavior int getCueReplacementBehavior() {
            return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;
        }
    }
}
