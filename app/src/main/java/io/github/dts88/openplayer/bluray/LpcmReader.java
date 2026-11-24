package io.github.dts88.openplayer.bluray;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.ElementaryStreamReader;
import androidx.media3.extractor.ts.PesReader;


import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class LpcmReader implements ElementaryStreamReader {
    private final String language;
    private final @C.RoleFlags int roleFlags;
    private final ParsableByteArray scratch = new ParsableByteArray();

    private TrackOutput output;
    private String formatId = "";
    private byte[] pes = new byte[16 * 1024];
    private int pesLength;
    private long timeUs = C.TIME_UNSET;
    private byte[] converted = new byte[0];
    private HdmvLpcm.Layout layout;

    LpcmReader(String language, @C.RoleFlags int roleFlags) {
        this.language = language;
        this.roleFlags = roleFlags;
    }

    @Override
    public void seek() {
        pesLength = 0;
        timeUs = C.TIME_UNSET;
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, PesReader.TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        formatId = idGenerator.getFormatId();
        output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
    }

    @Override
    public void packetStarted(long pesTimeUs, int flags) {
        pesLength = 0;
        timeUs = pesTimeUs;
    }

    @Override
    public void consume(ParsableByteArray data) {
        int n = data.bytesLeft();
        if (pesLength + n > pes.length) {
            byte[] bigger = new byte[Math.max(pes.length * 2, pesLength + n)];
            System.arraycopy(pes, 0, bigger, 0, pesLength);
            pes = bigger;
        }
        data.readBytes(pes, pesLength, n);
        pesLength += n;
    }

    @Override
    public void packetFinished(boolean isEndOfInput) {
        TrackOutput out = output;
        int length = pesLength;
        pesLength = 0;
        if (out == null || timeUs == C.TIME_UNSET || length <= HdmvLpcm.HEADER) return;
        HdmvLpcm.Layout l = HdmvLpcm.parse(pes, 0);
        if (l == null) return;
        if (!l.sameAs(layout)) {
            layout = l;
            out.format(new Format.Builder()
                    .setId(formatId)
                    .setContainerMimeType(MimeTypes.VIDEO_MP2T)
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(l.bitsPerSample == 16 ? C.ENCODING_PCM_16BIT : C.ENCODING_PCM_24BIT)
                    .setChannelCount(l.channels)
                    .setSampleRate(l.sampleRate)
                    .setLanguage(language)
                    .setRoleFlags(roleFlags)
                    .build());
        }
        int frames = HdmvLpcm.frames(l, length - HdmvLpcm.HEADER);
        int size = frames * l.channels * l.bytesPerSample;
        if (size == 0) return;
        if (converted.length < size) converted = new byte[size];
        HdmvLpcm.convert(l, pes, HdmvLpcm.HEADER, length - HdmvLpcm.HEADER, converted, 0);
        scratch.reset(converted, size);
        out.sampleData(scratch, size);
        out.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, 0, null);
    }
}
