package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@OptIn(markerClass = UnstableApi.class)
final class GuardedAudioSink extends ForwardingAudioSink {
    private static final int MAX_CHANNELS = 24;

    private final OutputStage stage;
    private final boolean passThrough;
    private boolean active;
    private int encoding = C.ENCODING_INVALID;
    private int sourceChannels;
    private int[] remap;
    private int inChannels;
    private int outChannels;

    private ByteBuffer source;
    private ByteBuffer handed;
    private ByteBuffer copy = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    private float[] in = new float[0];
    private float[] out = new float[0];

    GuardedAudioSink(AudioSink sink, OutputStage stage, boolean passThrough) {
        super(sink);
        this.stage = stage;
        this.passThrough = passThrough;
    }

    boolean wide() {
        return active && outChannels == SpeakerMap.OUT_CHANNELS && inChannels != outChannels;
    }

    @Override
    public void configure(Format inputFormat, int specifiedBufferSize, int[] outputChannels)
            throws ConfigurationException {
        boolean pcm = MimeTypes.AUDIO_RAW.equals(inputFormat.sampleMimeType);
        encoding = pcm ? inputFormat.pcmEncoding : C.ENCODING_INVALID;
        sourceChannels = inputFormat.channelCount;
        remap = outputChannels != null && outputChannels.length > 0 ? outputChannels.clone() : null;
        inChannels = remap != null ? remap.length : sourceChannels;
        active = passThrough && sourceChannels > 0 && inChannels <= MAX_CHANNELS && bytesPerSample(encoding) > 0
                && remapValid();
        clearPending();
        if (!active) {
            stage.configure(0, inputFormat.sampleRate);
            super.configure(inputFormat, specifiedBufferSize, outputChannels);
            return;
        }
        outChannels = stage.configure(inChannels, inputFormat.sampleRate);
        super.configure(inputFormat.buildUpon()
                .setChannelCount(outChannels)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build(), specifiedBufferSize, null);
    }

    private boolean remapValid() {
        if (remap == null) return true;
        for (int c : remap) {
            if (c < 0 || c >= sourceChannels) return false;
        }
        return true;
    }

    static int bytesPerSample(int encoding) {
        switch (encoding) {
            case C.ENCODING_PCM_16BIT: return 2;
            case C.ENCODING_PCM_24BIT: return 3;
            case C.ENCODING_PCM_32BIT:
            case C.ENCODING_PCM_FLOAT: return 4;
            default: return 0;
        }
    }

    @Override
    public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
            throws InitializationException, WriteException {
        if (!active) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount);
        if (buffer != source || handed == null) {
            source = buffer;
            handed = prepare(buffer);
        }
        boolean done = super.handleBuffer(handed, presentationTimeUs, encodedAccessUnitCount);
        if (done) {
            buffer.position(buffer.limit());
            clearPending();
        }
        return done;
    }

    @Override
    public void flush() {
        clearPending();
        if (active) stage.flush();
        super.flush();
    }

    @Override
    public void reset() {
        clearPending();
        if (active) stage.flush();
        super.reset();
    }

    private void clearPending() {
        source = null;
        handed = null;
    }

    private ByteBuffer prepare(ByteBuffer buffer) {
        ByteBuffer src = buffer.duplicate().order(ByteOrder.nativeOrder());
        int frames = src.remaining() / (bytesPerSample(encoding) * sourceChannels);
        if (in.length < frames * inChannels) in = new float[frames * inChannels];
        if (out.length < frames * outChannels) out = new float[frames * outChannels];
        read(src, encoding, sourceChannels, remap, frames, in);
        stage.process(in, frames, out);
        int bytes = frames * outChannels * 4;
        if (copy.capacity() < bytes) {
            copy = ByteBuffer.allocateDirect(Math.max(bytes, copy.capacity() * 2)).order(ByteOrder.nativeOrder());
        }
        copy.clear();
        copy.limit(bytes);
        copy.asFloatBuffer().put(out, 0, frames * outChannels);
        return copy;
    }

    static void read(ByteBuffer src, int encoding, int channels, int[] remap, int frames, float[] dst) {
        int outChannels = remap != null ? remap.length : channels;
        int base = src.position();
        int width = bytesPerSample(encoding);
        for (int f = 0; f < frames; f++) {
            int frameAt = base + f * channels * width;
            for (int c = 0; c < outChannels; c++) {
                int at = frameAt + (remap != null ? remap[c] : c) * width;
                float v;
                switch (encoding) {
                    case C.ENCODING_PCM_16BIT:
                        v = src.getShort(at) / 32768f;
                        break;
                    case C.ENCODING_PCM_24BIT:
                        v = ((src.get(at) & 0xFF) | ((src.get(at + 1) & 0xFF) << 8) | (src.get(at + 2) << 16))
                                / 8388608f;
                        break;
                    case C.ENCODING_PCM_32BIT:
                        v = src.getInt(at) / 2147483648f;
                        break;
                    default:
                        v = src.getFloat(at);
                }
                dst[f * outChannels + c] = v;
            }
        }
    }
}
