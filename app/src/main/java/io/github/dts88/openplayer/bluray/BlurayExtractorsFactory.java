package io.github.dts88.openplayer.bluray;

import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.TsExtractor;

import io.github.dts88.openplayer.music.AiffExtractor;
import io.github.dts88.openplayer.music.DsdExtractor;
import io.github.dts88.openplayer.subs.PlayerSubtitles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public final class BlurayExtractorsFactory implements ExtractorsFactory {
    public static final int TS_SEARCH_BYTES = 5000 * TsExtractor.TS_PACKET_SIZE;
    public static final int M2TS_SEARCH_BYTES = 8000 * TsExtractor.TS_PACKET_SIZE;

    private final DefaultExtractorsFactory delegate =
            new DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(TS_SEARCH_BYTES);
    private SubtitleParser.Factory subtitleParserFactory = new PlayerSubtitles();
    private boolean textTrackTranscodingEnabled = true;
    private Replacer replacer;

    public interface Replacer {
        Extractor replace(Extractor e, SubtitleParser.Factory subtitles, boolean parseDuringExtraction);
    }

    public BlurayExtractorsFactory() {
        delegate.setSubtitleParserFactory(subtitleParserFactory);
    }

    public synchronized BlurayExtractorsFactory setReplacer(Replacer r) {
        replacer = r;
        return this;
    }

    @Override
    @SuppressWarnings("deprecation")
    public synchronized ExtractorsFactory experimentalSetTextTrackTranscodingEnabled(boolean enabled) {
        textTrackTranscodingEnabled = enabled;
        delegate.experimentalSetTextTrackTranscodingEnabled(enabled);
        return this;
    }

    @Override
    public synchronized ExtractorsFactory setSubtitleParserFactory(SubtitleParser.Factory factory) {
        subtitleParserFactory = factory;
        delegate.setSubtitleParserFactory(factory);
        return this;
    }

    @Override
    public synchronized ExtractorsFactory experimentalSetCodecsToParseWithinGopSampleDependencies(int codecs) {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecs);
        return this;
    }

    @Override
    public synchronized Extractor[] createExtractors() {
        return replaceTs(delegate.createExtractors(), false);
    }

    @Override
    public synchronized Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
        return replaceTs(delegate.createExtractors(uri, responseHeaders), M2tsPackets.isM2tsName(uri.getPath()));
    }

    private Extractor[] replaceTs(Extractor[] defaults, boolean bluray) {
        TsExtractor ts = new TsExtractor(
                TsExtractor.MODE_SINGLE_PMT,
                textTrackTranscodingEnabled ? 0 : TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
                subtitleParserFactory,
                new TimestampAdjuster(0),
                new BlurayPayloadReaderFactory(bluray),
                bluray ? M2TS_SEARCH_BYTES : TS_SEARCH_BYTES);
        List<Extractor> out = new ArrayList<>(defaults.length + 3);
        out.add(new AiffExtractor());
        out.add(new DsdExtractor());
        if (bluray) out.add(ts);
        boolean added = bluray;
        for (Extractor e : defaults) {
            if (e instanceof TsExtractor) {
                if (!added) {
                    out.add(ts);
                    added = true;
                }
            } else {
                out.add(replacer == null ? e : replacer.replace(e, subtitleParserFactory, textTrackTranscodingEnabled));
            }
        }
        if (!added) out.add(ts);
        return out.toArray(new Extractor[0]);
    }
}
