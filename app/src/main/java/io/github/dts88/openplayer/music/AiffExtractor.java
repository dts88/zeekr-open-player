package io.github.dts88.openplayer.music;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;


import java.io.IOException;

@OptIn(markerClass = UnstableApi.class)
public final class AiffExtractor implements Extractor {
    private static final int FORM = 0x464F524D;
    private static final int AIFF = 0x41494646;
    private static final int AIFC = 0x41494643;
    private static final int COMM = 0x434F4D4D;
    private static final int SSND = 0x53534E44;

    private static final int CHUNK_MS = 100;
    private static final int SKIP_BY_READING = 256 * 1024;

    private static final int STATE_HEADER = 0;
    private static final int STATE_CHUNKS = 1;
    private static final int STATE_DATA = 2;

    private final ParsableByteArray scratch = new ParsableByteArray(12);
    private ExtractorOutput output;
    private TrackOutput track;
    private int state = STATE_HEADER;
    private boolean aifc;
    private AiffFormat format;
    private long dataStart = C.INDEX_UNSET;
    private long dataBytes;
    private long totalFrames;
    private long framesRead;
    private int chunkFrames;
    private byte[] in = new byte[0];
    private byte[] out = new byte[0];
    private boolean formatSent;

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        byte[] head = new byte[12];
        if (!input.peekFully(head, 0, 12, true)) return false;
        ParsableByteArray b = new ParsableByteArray(head);
        if (b.readInt() != FORM) return false;
        b.skipBytes(4);
        int type = b.readInt();
        return type == AIFF || type == AIFC;
    }

    @Override
    public void init(ExtractorOutput output) {
        this.output = output;
        track = output.track(0, C.TRACK_TYPE_AUDIO);
        output.endTracks();
    }

    @Override
    public void seek(long position, long timeUs) {
        if (position == 0 || state != STATE_DATA) {
            state = STATE_HEADER;
            format = null;
            dataStart = C.INDEX_UNSET;
            return;
        }
        framesRead = Math.max(0, (position - dataStart) / format.inFrameBytes());
    }

    @Override
    public void release() {
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        switch (state) {
            case STATE_HEADER:
                return readHeader(input, seekPosition);
            case STATE_CHUNKS:
                return readChunk(input, seekPosition);
            default:
                return readData(input);
        }
    }

    private int readHeader(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        if (input.getPosition() != 0) {
            seekPosition.position = 0;
            return RESULT_SEEK;
        }
        scratch.reset(12);
        input.readFully(scratch.getData(), 0, 12);
        if (scratch.readInt() != FORM) throw ParserException.createForMalformedContainer("不是 AIFF：没有 FORM", null);
        scratch.skipBytes(4);
        int type = scratch.readInt();
        if (type != AIFF && type != AIFC) throw ParserException.createForMalformedContainer("不是 AIFF", null);
        aifc = type == AIFC;
        state = STATE_CHUNKS;
        return RESULT_CONTINUE;
    }

    private int readChunk(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        scratch.reset(8);
        if (!input.readFully(scratch.getData(), 0, 8, true)) {
            if (format != null && dataStart != C.INDEX_UNSET) return startData(input, seekPosition);
            throw ParserException.createForMalformedContainer(
                    format == null ? "AIFF 里没有格式块（COMM）" : "AIFF 里没有声音数据（SSND）", null);
        }
        int id = scratch.readInt();
        long size = scratch.readUnsignedInt();
        long bodyStart = input.getPosition();
        long next = bodyStart + size + (size & 1);
        if (id == COMM) {
            int n = (int) Math.min(size, 64);
            byte[] body = new byte[n];
            input.readFully(body, 0, n);
            AiffFormat f = AiffFormat.parseComm(body, 0, n, aifc);
            if (f == null) throw ParserException.createForMalformedContainer("AIFF 的格式块读不懂", null);
            if (!f.supported()) {
                throw ParserException.createForUnsupportedContainerFeature(
                        "AIFF-C 的编码方式「" + f.compression.trim() + "」放不了（只支持没压缩的）");
            }
            format = f;
        } else if (id == SSND) {
            if (size < 8) throw ParserException.createForMalformedContainer("AIFF 的声音数据块太短", null);
            scratch.reset(8);
            input.readFully(scratch.getData(), 0, 8);
            long offset = scratch.readUnsignedInt();
            dataStart = bodyStart + 8 + offset;
            dataBytes = Math.max(0, size - 8 - offset);
        }
        if (format != null && dataStart != C.INDEX_UNSET) return startData(input, seekPosition);
        return skipTo(input, next, seekPosition);
    }

    private int skipTo(ExtractorInput input, long position, PositionHolder seekPosition) throws IOException {
        long gap = position - input.getPosition();
        if (gap < 0 || gap > SKIP_BY_READING) {
            seekPosition.position = position;
            return RESULT_SEEK;
        }
        if (gap > 0 && !input.skipFully((int) gap, true)) return RESULT_END_OF_INPUT;
        return RESULT_CONTINUE;
    }

    private int startData(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        AiffFormat f = format;
        long available = dataBytes;
        long length = input.getLength();
        if (length != C.LENGTH_UNSET) {
            long toEnd = Math.max(0, length - dataStart);
            available = available == 0 ? toEnd : Math.min(available, toEnd);
        }
        totalFrames = Math.min(f.frames > 0 ? f.frames : Long.MAX_VALUE, available / f.inFrameBytes());
        chunkFrames = Math.max(1, f.sampleRate * CHUNK_MS / 1000);
        in = new byte[chunkFrames * f.inFrameBytes()];
        out = new byte[chunkFrames * f.outFrameBytes()];
        framesRead = 0;
        if (!formatSent) {
            formatSent = true;
            int encoding = f.outFloat ? C.ENCODING_PCM_FLOAT
                    : f.outBytesPerSample == 2 ? C.ENCODING_PCM_16BIT
                    : f.outBytesPerSample == 3 ? C.ENCODING_PCM_24BIT : C.ENCODING_PCM_32BIT;
            track.format(new Format.Builder()
                    .setContainerMimeType("audio/aiff")
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(encoding)
                    .setChannelCount(f.channels)
                    .setSampleRate(f.sampleRate)
                    .setAverageBitrate(f.sampleRate * f.outFrameBytes() * 8)
                    .setMaxInputSize(out.length)
                    .build());
            output.seekMap(new PcmSeekMap(f, dataStart, totalFrames));
        }
        state = STATE_DATA;
        if (input.getPosition() != dataStart) return skipTo(input, dataStart, seekPosition);
        return RESULT_CONTINUE;
    }

    private int readData(ExtractorInput input) throws IOException {
        AiffFormat f = format;
        long left = totalFrames - framesRead;
        if (left <= 0) return RESULT_END_OF_INPUT;
        int frames = (int) Math.min(left, chunkFrames);
        if (!input.readFully(in, 0, frames * f.inFrameBytes(), true)) return RESULT_END_OF_INPUT;
        f.convert(in, 0, frames, out, 0);
        int size = frames * f.outFrameBytes();
        scratch.reset(out, size);
        track.sampleData(scratch, size);
        track.sampleMetadata(framesRead * C.MICROS_PER_SECOND / f.sampleRate, C.BUFFER_FLAG_KEY_FRAME, size, 0, null);
        framesRead += frames;
        return RESULT_CONTINUE;
    }

    private static final class PcmSeekMap implements SeekMap {
        private final AiffFormat format;
        private final long dataStart;
        private final long frames;

        PcmSeekMap(AiffFormat format, long dataStart, long frames) {
            this.format = format;
            this.dataStart = dataStart;
            this.frames = frames;
        }

        @Override
        public boolean isSeekable() {
            return true;
        }

        @Override
        public long getDurationUs() {
            return frames * C.MICROS_PER_SECOND / format.sampleRate;
        }

        @Override
        public SeekPoints getSeekPoints(long timeUs) {
            long frame = Math.max(0, Math.min(frames - 1, timeUs * format.sampleRate / C.MICROS_PER_SECOND));
            return new SeekPoints(new SeekPoint(frame * C.MICROS_PER_SECOND / format.sampleRate,
                    dataStart + frame * format.inFrameBytes()));
        }
    }
}
