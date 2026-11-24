package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;

import java.util.concurrent.atomic.AtomicLong;

@OptIn(markerClass = UnstableApi.class)
final class DelayedTextRenderer extends ForwardingRenderer {
    static final long MAX_DELAY_MS = 30_000;

    private final AtomicLong delayUs;

    DelayedTextRenderer(Renderer text, AtomicLong delayUs) {
        super(text);
        this.delayUs = delayUs;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        super.render(shifted(positionUs, delayUs.get()), elapsedRealtimeUs);
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() || (delayUs.get() > 0 && isCurrentStreamFinal() && hasReadStreamToEnd());
    }

    static long shifted(long positionUs, long delayUs) {
        return positionUs - delayUs;
    }

    static long clampMs(long ms) {
        return Math.max(-MAX_DELAY_MS, Math.min(MAX_DELAY_MS, ms));
    }
}
