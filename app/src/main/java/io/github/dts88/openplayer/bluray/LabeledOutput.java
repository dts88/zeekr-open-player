package io.github.dts88.openplayer.bluray;

import androidx.annotation.OptIn;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

import java.io.IOException;

@OptIn(markerClass = UnstableApi.class)
final class LabeledOutput implements ExtractorOutput {
    private final ExtractorOutput delegate;
    private final String label;
    private TrackOutput last;

    LabeledOutput(ExtractorOutput delegate, String label) {
        this.delegate = delegate;
        this.label = label;
    }

    @Override
    public TrackOutput track(int id, int type) {
        last = new Labeled(delegate.track(id, type), label);
        return last;
    }

    @Override
    public void endTracks() {
        delegate.endTracks();
    }

    @Override
    public void seekMap(SeekMap seekMap) {
        delegate.seekMap(seekMap);
    }

    void placeholder(Format format) {
        if (last != null) last.format(format);
    }

    private static final class Labeled implements TrackOutput {
        private final TrackOutput delegate;
        private final String label;

        Labeled(TrackOutput delegate, String label) {
            this.delegate = delegate;
            this.label = label;
        }

        @Override
        public void format(Format format) {
            delegate.format(format.buildUpon().setLabel(label).build());
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
