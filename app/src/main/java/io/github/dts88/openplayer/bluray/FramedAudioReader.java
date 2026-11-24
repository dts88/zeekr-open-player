package io.github.dts88.openplayer.bluray;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Ac3Util;
import androidx.media3.extractor.DtsUtil;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.ElementaryStreamReader;
import androidx.media3.extractor.ts.PesReader;


import java.util.Arrays;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class FramedAudioReader implements ElementaryStreamReader, AudioFramer.Sink {
    enum Kind {
        TRUEHD("TrueHD"),
        DTS("DTS"),
        EAC3("杜比数字+");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static final int MAX_INPUT_SIZE = 64 * 1024;

    private final Kind kind;
    private final AudioFramer framer;
    private final String language;
    private final @C.RoleFlags int roleFlags;
    private final ParsableByteArray scratch = new ParsableByteArray();

    private TrackOutput output;
    private String formatId = "";
    private Format format;
    private int extensionChannels;
    private int extensionSampleRate;
    private int loggedLostSync;
    private boolean joc;
    private int jocChecks;

    FramedAudioReader(Kind kind, String language, @C.RoleFlags int roleFlags) {
        this.kind = kind;
        this.language = language;
        this.roleFlags = roleFlags;
        switch (kind) {
            case TRUEHD:
                framer = new TrueHdFramer(this);
                break;
            case EAC3:
                framer = new Eac3Framer(this);
                break;
            default:
                framer = new DtsHdFramer(this);
                break;
        }
    }

    @Override
    public void seek() {
        framer.reset();
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, PesReader.TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        formatId = idGenerator.getFormatId();
        output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
    }

    @Override
    public void packetStarted(long pesTimeUs, int flags) {
        framer.pesStarted(pesTimeUs == C.TIME_UNSET ? AudioFramer.TIME_UNSET : pesTimeUs);
    }

    @Override
    public void consume(ParsableByteArray data) {
        int n = data.bytesLeft();
        framer.data(data.getData(), data.getPosition(), n);
        data.skipBytes(n);
    }

    @Override
    public void packetFinished(boolean isEndOfInput) {
        if (isEndOfInput) framer.endOfInput();
    }

    @Override
    public void sample(byte[] data, int offset, int length, long timeUs, boolean keyFrame) {
        TrackOutput out = output;
        if (out == null) return;
        updateFormat(out, data, offset, length);
        if (format == null) return;
        logLostSync();
        scratch.reset(data, offset + length);
        scratch.setPosition(offset);
        out.sampleData(scratch, length);
        out.sampleMetadata(timeUs, keyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0, length, 0, null);
    }

    private void updateFormat(TrackOutput out, byte[] data, int offset, int length) {
        String mime = framer.mimeType();
        int channels = framer.channelCount();
        int rate = framer.sampleRate();
        if (mime == null || channels <= 0 || rate <= 0) return;
        if (framer instanceof DtsHdFramer) {
            int ext = ((DtsHdFramer) framer).lastExtensionOffset();
            if (ext >= 0 && extensionChannels == 0) readExtension(data, offset + ext, length - ext);
            channels = Math.max(channels, extensionChannels);
            rate = Math.max(rate, extensionSampleRate);
        }
        if (Eac3Framer.MIME_EAC3.equals(mime)) {
            if (!joc && jocChecks < 64) {
                jocChecks++;
                joc = containsJoc(data, offset, length);
            }
            if (joc) mime = MimeTypes.AUDIO_E_AC3_JOC;
        }
        Format current = format;
        if (current != null && mime.equals(current.sampleMimeType)
                && channels == current.channelCount && rate == current.sampleRate) {
            return;
        }
        format = new Format.Builder()
                .setId(formatId)
                .setContainerMimeType(MimeTypes.VIDEO_MP2T)
                .setSampleMimeType(mime)
                .setChannelCount(channels)
                .setSampleRate(rate)
                .setMaxInputSize(MAX_INPUT_SIZE)
                .setLanguage(language)
                .setRoleFlags(roleFlags)
                .build();
        out.format(format);
    }

    private static boolean containsJoc(byte[] data, int offset, int length) {
        int end = offset + length;
        ParsableBitArray bits = new ParsableBitArray(data, end);
        int p = offset;
        try {
            while (end - p >= Eac3Framer.FULL_HEADER) {
                int size = Eac3Framer.frameSize(data, p, end - p);
                if (size <= 0 || size > end - p) break;
                bits.setPosition(p * 8);
                if (MimeTypes.AUDIO_E_AC3_JOC.equals(Ac3Util.parseAc3SyncframeInfo(bits).mimeType)) return true;
                p += size;
            }
        } catch (RuntimeException e) {
        }
        return false;
    }

    private void readExtension(byte[] data, int offset, int length) {
        try {
            if (length < 16) return;
            int headerSize = DtsUtil.parseDtsHdHeaderSize(Arrays.copyOfRange(data, offset, offset + 16));
            if (headerSize <= 0 || headerSize > length) return;
            DtsUtil.DtsHeader h = DtsUtil.parseDtsHdHeader(Arrays.copyOfRange(data, offset, offset + headerSize));
            if (h.channelCount > 0) extensionChannels = h.channelCount;
            if (h.sampleRate > 0) extensionSampleRate = h.sampleRate;
        } catch (Exception e) {
            extensionChannels = -1;
        }
    }

    private void logLostSync() {
        if (!(framer instanceof TrueHdFramer)) return;
        int lost = ((TrueHdFramer) framer).lostSyncCount();
        if (lost > loggedLostSync && loggedLostSync < 5) {
        }
        loggedLostSync = lost;
    }

    private String describe(String mime) {
        if (DtsHdFramer.MIME_DTS_HD.equals(mime)) return "DTS-HD（核心加扩展，FFmpeg 解完整声道）";
        if (DtsHdFramer.MIME_DTS.equals(mime)) return "DTS";
        if (TrueHdFramer.MIME.equals(mime)) return "TrueHD";
        if (MimeTypes.AUDIO_E_AC3_JOC.equals(mime)) return "杜比数字+ 全景声（JOC；FFmpeg 解出底层声道）";
        if (Eac3Framer.MIME_EAC3.equals(mime)) return "杜比数字+（核心和从属子流拼成一帧）";
        if (Eac3Framer.MIME_AC3.equals(mime)) return "杜比数字（AC-3）";
        return kind.label;
    }
}
