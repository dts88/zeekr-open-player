package io.github.dts88.openplayer;

import android.media.MediaFormat;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class BaseLayerVideoRenderer extends MediaCodecVideoRenderer {
    private final VideoFrameGate gate;
    private final DoviFrames dovi;
    private final Hdr10PlusFrames hdr10p;
    private long outputTimeUs = Long.MIN_VALUE;

    BaseLayerVideoRenderer(MediaCodecVideoRenderer.Builder builder, VideoFrameGate gate, DoviFrames dovi,
                           Hdr10PlusFrames hdr10p) {
        super(builder);
        this.gate = gate;
        this.dovi = dovi;
        this.hdr10p = hdr10p;
    }

    static boolean isHevc(Format f) {
        if (f == null) return false;
        if (MimeTypes.VIDEO_H265.equals(f.sampleMimeType)) return true;
        return isDolbyVision(f) && MimeTypes.VIDEO_H265.equals(DolbyVision.baseMime(f.codecs));
    }

    static int hdr10PlusCodec(Format f) {
        if (isHevc(f)) return Hdr10PlusFrames.HEVC;
        if (f == null) return Hdr10PlusFrames.NONE;
        if (MimeTypes.VIDEO_AV1.equals(f.sampleMimeType)) return Hdr10PlusFrames.AV1;
        if (MimeTypes.VIDEO_VP9.equals(f.sampleMimeType)) return Hdr10PlusFrames.VP9;
        return Hdr10PlusFrames.NONE;
    }

    static boolean isDolbyVision(Format f) {
        if (f == null) return false;
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType)) return true;
        String c = f.codecs == null ? "" : f.codecs.trim().toLowerCase(Locale.ROOT);
        return c.startsWith("dvh") || c.startsWith("dva") || c.startsWith("dav1");
    }

    @Override
    protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
        dovi.setEnabled(isDolbyVision(formatHolder.format));
        hdr10p.setCodec(hdr10PlusCodec(formatHolder.format));
        return super.onInputFormatChanged(formatHolder);
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
        dovi.onInput(buffer.data, buffer.timeUs);
        hdr10p.onInput(buffer.data, buffer.hasSupplementalData() ? buffer.supplementalData : null, buffer.timeUs);
        super.onQueueInputBuffer(buffer);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining, boolean sampleStreamIsResetToKeyFrame)
            throws ExoPlaybackException {
        dovi.reset();
        hdr10p.reset();
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame);
    }

    @Override
    protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
            throws MediaCodecUtil.DecoderQueryException {
        return super.supportsFormat(mediaCodecSelector, DolbyVision.baseLayer(format));
    }

    @Override
    protected List<MediaCodecInfo> getDecoderInfos(MediaCodecSelector mediaCodecSelector, Format format,
                                                   boolean requiresSecureDecoder)
            throws MediaCodecUtil.DecoderQueryException {
        return super.getDecoderInfos(mediaCodecSelector, DolbyVision.baseLayer(format), requiresSecureDecoder);
    }

    @Override
    protected MediaFormat getMediaFormat(Format format, String codecMimeType, CodecMaxValues codecMaxValues,
                                         float codecOperatingRate, boolean deviceNeedsNoPostProcessWorkaround,
                                         int tunnelingAudioSessionId) {
        return super.getMediaFormat(DolbyVision.baseLayer(format), codecMimeType, codecMaxValues,
                codecOperatingRate, deviceNeedsNoPostProcessWorkaround, tunnelingAudioSessionId);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodecAdapter codec,
                                          ByteBuffer buffer, int bufferIndex, int bufferFlags, int sampleCount,
                                          long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer,
                                          boolean isLastBuffer, Format format) throws ExoPlaybackException {
        if (!isDecodeOnlyBuffer && gate.blocking(System.nanoTime())) return false;
        outputTimeUs = bufferPresentationTimeUs;
        return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags,
                sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
    }

    @Override
    protected void renderOutputBufferV21(MediaCodecAdapter codec, int index, long presentationTimeUs,
                                         long releaseTimeNs) {
        gate.onReleased(outputTimeUs != Long.MIN_VALUE ? outputTimeUs : presentationTimeUs, releaseTimeNs,
                System.nanoTime());
        super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
    }
}
