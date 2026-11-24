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

@OptIn(markerClass = UnstableApi.class)
final class PgsReader implements ElementaryStreamReader {
    private final String language;
    private final @C.RoleFlags int roleFlags;
    private final PgsDisplaySets sets = new PgsDisplaySets();
    private final ParsableByteArray scratch = new ParsableByteArray();

    private TrackOutput output;
    private byte[] pes = new byte[8 * 1024];
    private int pesLength;
    private long pesTimeUs = C.TIME_UNSET;

    PgsReader(String language, @C.RoleFlags int roleFlags) {
        this.language = language;
        this.roleFlags = roleFlags;
    }

    @Override
    public void seek() {
        pesLength = 0;
        pesTimeUs = C.TIME_UNSET;
        sets.reset();
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, PesReader.TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
        output.format(new Format.Builder()
                .setId(idGenerator.getFormatId())
                .setContainerMimeType(MimeTypes.VIDEO_MP2T)
                .setSampleMimeType(MimeTypes.APPLICATION_PGS)
                .setLanguage(language)
                .setRoleFlags(roleFlags)
                .build());
    }

    @Override
    public void packetStarted(long pesTimeUs, int flags) {
        pesLength = 0;
        this.pesTimeUs = pesTimeUs;
    }

    @Override
    public void consume(ParsableByteArray data) {
        int n = data.bytesLeft();
        if (pesLength + n > PgsDisplaySets.MAX_BYTES) {
            data.skipBytes(n);
            return;
        }
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
        if (out == null || length == 0) return;
        sets.add(pes, length, pesTimeUs, pesTimeUs != C.TIME_UNSET, (data, size, timeUs) -> {
            scratch.reset(data, size);
            out.sampleData(scratch, size);
            out.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, 0, null);
        });
    }
}
