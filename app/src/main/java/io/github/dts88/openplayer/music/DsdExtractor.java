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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public final class DsdExtractor implements Extractor {
    private static final int DFF_GROUP = 4096;
    private static final int SKIP_BY_READING = 256 * 1024;
    private static final int MAX_PROPERTIES = 1 << 20;

    private static final int STATE_HEADER = 0;
    private static final int STATE_DFF_CHUNKS = 1;
    private static final int STATE_DATA = 2;

    private ExtractorOutput output;
    private TrackOutput track;
    private int state = STATE_HEADER;
    private boolean formatSent;
    private DsdHeader header;
    private DsdHeader.DffProperties dffProperties;
    private DsdDecimator decimator;
    private int groupBytes;
    private int groupChannelBytes;
    private int groupFrames;
    private long totalFrames;
    private long framesDone;
    private long dataEnd;
    private byte[] in = new byte[0];
    private float[] pcm = new float[0];
    private byte[] out = new byte[0];
    private final ParsableByteArray scratch = new ParsableByteArray();

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        byte[] head = new byte[16];
        if (!input.peekFully(head, 0, 16, true)) return false;
        if (DsdHeader.tag(head, 0, "DSD ")) return DsdHeader.le64(head, 4) == 28;
        return DsdHeader.tag(head, 0, "FRM8") && DsdHeader.tag(head, 12, "DSD ");
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
            header = null;
            dffProperties = null;
            return;
        }
        long group = Math.max(0, (position - header.dataOffset) / groupBytes);
        framesDone = group * groupFrames;
        decimator.reset();
    }

    @Override
    public void release() {
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        switch (state) {
            case STATE_HEADER:
                return readHeader(input, seekPosition);
            case STATE_DFF_CHUNKS:
                return readDffChunk(input, seekPosition);
            default:
                return readGroup(input);
        }
    }

    private int readHeader(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        if (input.getPosition() != 0) {
            seekPosition.position = 0;
            return RESULT_SEEK;
        }
        byte[] head = new byte[28];
        input.peekFully(head, 0, 16);
        if (DsdHeader.tag(head, 0, "FRM8")) {
            input.skipFully(16);
            state = STATE_DFF_CHUNKS;
            return RESULT_CONTINUE;
        }
        input.peekFully(head, 16, 12);
        int need = DsdHeader.dsfHeaderLength(head, 28);
        if (need < 0) throw ParserException.createForMalformedContainer("不是 DSF", null);
        byte[] all = new byte[need];
        input.resetPeekPosition();
        input.peekFully(all, 0, need);
        int exact = DsdHeader.dsfHeaderLength(all, need);
        if (exact > need) {
            all = new byte[exact];
            input.resetPeekPosition();
            input.peekFully(all, 0, exact);
            need = exact;
        }
        DsdHeader h = DsdHeader.parseDsf(all, need);
        if (h == null) throw ParserException.createForMalformedContainer("DSF 的头读不懂", null);
        input.resetPeekPosition();
        return startData(input, h, seekPosition);
    }

    private int readDffChunk(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        byte[] head = new byte[12];
        if (!input.readFully(head, 0, 12, true)) {
            throw ParserException.createForMalformedContainer("DFF 里没有声音数据", null);
        }
        long size = DsdHeader.be64(head, 4);
        if (size < 0) throw ParserException.createForMalformedContainer("DFF 的块长度不对", null);
        long next = input.getPosition() + size + (size & 1);
        if (DsdHeader.tag(head, 0, "PROP") && size <= MAX_PROPERTIES) {
            byte[] body = new byte[(int) size];
            input.readFully(body, 0, body.length);
            DsdHeader.DffProperties p = DsdHeader.parseDffProperties(body, body.length);
            if (p != null) dffProperties = p;
        } else if (DsdHeader.tag(head, 0, "DST ")) {
            throw ParserException.createForUnsupportedContainerFeature("DST 压缩的 DFF 放不了（只支持没压缩的 DSD）");
        } else if (DsdHeader.tag(head, 0, "DSD ")) {
            DsdHeader.DffProperties p = dffProperties;
            if (p != null && !"DSD ".equals(p.compression)) {
                throw ParserException.createForUnsupportedContainerFeature(
                        "DFF 的压缩方式「" + p.compression.trim() + "」放不了（只支持没压缩的 DSD）");
            }
            DsdHeader h = DsdHeader.forDff(p, input.getPosition(), size);
            if (h == null) throw ParserException.createForMalformedContainer("DFF 的声音属性不全", null);
            return startData(input, h, seekPosition);
        }
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

    private int startData(ExtractorInput input, DsdHeader h, PositionHolder seekPosition) throws IOException {
        int outRate = DsdDecimator.outputRate(h.dsdRate);
        if (outRate == 0) {
            throw ParserException.createForUnsupportedContainerFeature("这种 DSD 采样率放不了：" + h.dsdRate + " Hz");
        }
        DsdDecimator d = new DsdDecimator(h.channels, h.dsdRate);
        int perChannel = h.dsf ? h.blockSize : DFF_GROUP;
        if (perChannel % d.bytesPerOutput() != 0) {
            throw ParserException.createForUnsupportedContainerFeature("DSF 的块大小不常见：" + h.blockSize);
        }
        header = h;
        decimator = d;
        groupChannelBytes = perChannel;
        groupBytes = perChannel * h.channels;
        groupFrames = perChannel / d.bytesPerOutput();
        long available = h.dataBytes;
        long length = input.getLength();
        if (length != C.LENGTH_UNSET) available = Math.min(available, Math.max(0, length - h.dataOffset));
        dataEnd = h.dataOffset + available;
        long framesInData = available / h.channels / d.bytesPerOutput();
        long framesInHeader = h.samplesPerChannel / 8 / d.bytesPerOutput();
        totalFrames = framesInHeader > 0 ? Math.min(framesInHeader, framesInData) : framesInData;
        framesDone = 0;
        in = new byte[groupBytes];
        pcm = new float[groupFrames * h.channels];
        out = new byte[groupFrames * h.channels * 4];
        if (!formatSent) {
            formatSent = true;
            track.format(new Format.Builder()
                    .setContainerMimeType(h.dsf ? "audio/x-dsf" : "audio/x-dff")
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                    .setChannelCount(h.channels)
                    .setSampleRate(outRate)
                    .setAverageBitrate(outRate * h.channels * 32)
                    .setMaxInputSize(out.length)
                    .build());
            output.seekMap(new GroupSeekMap(h.dataOffset, groupBytes, groupFrames, totalFrames, outRate));
        }
        state = STATE_DATA;
        if (input.getPosition() != h.dataOffset) return skipTo(input, h.dataOffset, seekPosition);
        return RESULT_CONTINUE;
    }

    private int readGroup(ExtractorInput input) throws IOException {
        DsdHeader h = header;
        DsdDecimator d = decimator;
        long framesLeft = totalFrames - framesDone;
        if (framesLeft <= 0) return RESULT_END_OF_INPUT;
        long bytesLeft = dataEnd - input.getPosition();
        int perChannel = groupChannelBytes;
        int want = groupBytes;
        if (bytesLeft < want) {
            if (h.dsf) return RESULT_END_OF_INPUT;
            perChannel = (int) (bytesLeft / h.channels / d.bytesPerOutput()) * d.bytesPerOutput();
            want = perChannel * h.channels;
            if (want <= 0) return RESULT_END_OF_INPUT;
        }
        if (!input.readFully(in, 0, want, true)) return RESULT_END_OF_INPUT;
        int frames = 0;
        for (int c = 0; c < h.channels; c++) {
            frames = h.dsf
                    ? d.process(c, in, c * perChannel, 1, perChannel, h.lsbFirst, pcm, c, h.channels)
                    : d.process(c, in, c, h.channels, perChannel, false, pcm, c, h.channels);
        }
        frames = (int) Math.min(frames, framesLeft);
        int size = frames * h.channels * 4;
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(pcm, 0, frames * h.channels);
        scratch.reset(out, size);
        track.sampleData(scratch, size);
        track.sampleMetadata(framesDone * C.MICROS_PER_SECOND / d.outputRate(), C.BUFFER_FLAG_KEY_FRAME, size, 0, null);
        framesDone += frames;
        return RESULT_CONTINUE;
    }

    private static final class GroupSeekMap implements SeekMap {
        private final long dataOffset;
        private final int groupBytes;
        private final int groupFrames;
        private final long totalFrames;
        private final int rate;

        GroupSeekMap(long dataOffset, int groupBytes, int groupFrames, long totalFrames, int rate) {
            this.dataOffset = dataOffset;
            this.groupBytes = groupBytes;
            this.groupFrames = groupFrames;
            this.totalFrames = totalFrames;
            this.rate = rate;
        }

        @Override
        public boolean isSeekable() {
            return true;
        }

        @Override
        public long getDurationUs() {
            return totalFrames * C.MICROS_PER_SECOND / rate;
        }

        @Override
        public SeekPoints getSeekPoints(long timeUs) {
            long groups = Math.max(1, (totalFrames + groupFrames - 1) / groupFrames);
            long group = Math.max(0, Math.min(groups - 1, timeUs * rate / C.MICROS_PER_SECOND / groupFrames));
            return new SeekPoints(new SeekPoint(group * groupFrames * C.MICROS_PER_SECOND / rate,
                    dataOffset + group * groupBytes));
        }
    }
}
