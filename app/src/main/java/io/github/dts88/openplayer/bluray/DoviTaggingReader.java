package io.github.dts88.openplayer.bluray;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.TsPayloadReader;

import java.io.IOException;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class DoviTaggingReader implements TsPayloadReader {
    private final TsPayloadReader inner;
    private final String codecs;

    DoviTaggingReader(TsPayloadReader inner, int profile, int level) {
        this.inner = inner;
        this.codecs = codecs(profile, level);
    }

    static boolean wanted(int profile) {
        return profile == 5 || profile == 8;
    }

    static String codecs(int profile, int level) {
        return String.format(Locale.ROOT, "dvhe.%02d.%02d", profile, level);
    }

    static Format tag(Format format, String codecs) {
        if (!MimeTypes.VIDEO_H265.equals(format.sampleMimeType)) return format;
        return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs(codecs).build();
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
                     TrackIdGenerator idGenerator) {
        inner.init(timestampAdjuster, new Output(extractorOutput), idGenerator);
    }

    @Override
    public void seek() {
        inner.seek();
    }

    @Override
    public void consume(ParsableByteArray data, int flags) throws ParserException {
        inner.consume(data, flags);
    }

    private final class Output implements ExtractorOutput {
        private final ExtractorOutput delegate;

        Output(ExtractorOutput delegate) {
            this.delegate = delegate;
        }

        @Override
        public TrackOutput track(int id, int type) {
            TrackOutput t = delegate.track(id, type);
            return type == C.TRACK_TYPE_VIDEO ? new Track(t) : t;
        }

        @Override
        public void endTracks() {
            delegate.endTracks();
        }

        @Override
        public void seekMap(SeekMap seekMap) {
            delegate.seekMap(seekMap);
        }
    }

    private final class Track implements TrackOutput {
        private final TrackOutput delegate;

        Track(TrackOutput delegate) {
            this.delegate = delegate;
        }

        @Override
        public void format(Format format) {
            delegate.format(tag(format, codecs));
        }

        @Override
        public void durationUs(long durationUs) {
            delegate.durationUs(durationUs);
        }

        @Override
        public int sampleData(DataReader input, int length, boolean allowEndOfInput, int sampleDataPart)
                throws IOException {
            return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart);
        }

        @Override
        public void sampleData(ParsableByteArray data, int length, int sampleDataPart) {
            delegate.sampleData(data, length, sampleDataPart);
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, CryptoData cryptoData) {
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData);
        }
    }
}
