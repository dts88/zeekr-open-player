package io.github.dts88.openplayer.bluray;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ts.ElementaryStreamReader;
import androidx.media3.extractor.ts.TsPayloadReader;

import java.io.IOException;

@OptIn(markerClass = UnstableApi.class)
final class BlurayPesReader implements TsPayloadReader, PesAssembler.Listener {
    static final int EXT_TRUEHD = 0x72;
    static final int EXT_AC3 = 0x76;

    private final ElementaryStreamReader main;
    private final ElementaryStreamReader core;
    private final String coreLabel;
    private final String language;
    private final PesAssembler assembler = new PesAssembler(this);
    private final ParsableByteArray scratch = new ParsableByteArray();

    private TimestampAdjuster timestampAdjuster;
    private boolean seenFirstDts;
    private ElementaryStreamReader target;
    private int tsFlags;

    BlurayPesReader(ElementaryStreamReader main, ElementaryStreamReader core,
                    String coreLabel, String language) {
        this.main = main;
        this.core = core;
        this.coreLabel = coreLabel;
        this.language = language;
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
                     TrackIdGenerator idGenerator) {
        this.timestampAdjuster = timestampAdjuster;
        main.createTracks(extractorOutput, idGenerator);
        if (core != null) {
            LabeledOutput labeled = new LabeledOutput(extractorOutput, coreLabel != null ? coreLabel : "AC-3");
            core.createTracks(labeled, idGenerator);
            labeled.placeholder(new Format.Builder()
                    .setId(idGenerator.getFormatId())
                    .setContainerMimeType(MimeTypes.VIDEO_MP2T)
                    .setSampleMimeType(MimeTypes.AUDIO_AC3)
                    .setChannelCount(6)
                    .setSampleRate(48000)
                    .setLanguage(language)
                    .setRoleFlags(C.ROLE_FLAG_ALTERNATE)
                    .build());
        }
    }

    @Override
    public void seek() {
        assembler.reset();
        seenFirstDts = false;
        target = null;
        main.seek();
        if (core != null) core.seek();
    }

    @Override
    public void consume(ParsableByteArray data, int flags) throws ParserException {
        tsFlags = flags;
        try {
            assembler.consume(data.getData(), data.getPosition(), data.bytesLeft(),
                    (flags & FLAG_PAYLOAD_UNIT_START_INDICATOR) != 0);
        } catch (ParserException e) {
            throw e;
        } catch (IOException e) {
            throw ParserException.createForMalformedContainer(e.getMessage(), e);
        }
        data.skipBytes(data.bytesLeft());
    }

    @Override
    public void pesStarted(PesAssembler.Header header) {
        long timeUs = C.TIME_UNSET;
        TimestampAdjuster adjuster = timestampAdjuster;
        if (header.hasPts && adjuster != null) {
            if (!seenFirstDts && header.hasDts) {
                adjuster.adjustTsTimestamp(header.dts);
                seenFirstDts = true;
            }
            timeUs = adjuster.adjustTsTimestamp(header.pts);
        }
        target = header.streamId == PesAssembler.STREAM_ID_EXTENDED && header.streamIdExtension == EXT_AC3
                ? core : main;
        if (target != null) {
            target.packetStarted(timeUs, (tsFlags & ~FLAG_DATA_ALIGNMENT_INDICATOR)
                    | (header.dataAlignment ? FLAG_DATA_ALIGNMENT_INDICATOR : 0));
        }
    }

    @Override
    public void pesPayload(byte[] data, int offset, int length) throws IOException {
        ElementaryStreamReader t = target;
        if (t == null) return;
        scratch.reset(data, offset + length);
        scratch.setPosition(offset);
        t.consume(scratch);
    }

    @Override
    public void pesFinished(boolean endOfInput) {
        if (target != null) target.packetFinished(endOfInput);
        target = null;
    }
}
