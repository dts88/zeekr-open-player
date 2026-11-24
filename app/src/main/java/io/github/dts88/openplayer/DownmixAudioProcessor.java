package io.github.dts88.openplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@OptIn(markerClass = UnstableApi.class)
final class DownmixAudioProcessor extends BaseAudioProcessor {
    enum Mode {
        PLATFORM("多声道直出（不下混，各声道对应各扬声器）"),
        LORO("标准下混 Lo/Ro（按标准舍弃 LFE）"),
        LORO_LFE("下混 + 低音声道 LFE");

        static final Mode DEFAULT = PLATFORM;

        final String label;

        Mode(String label) {
            this.label = label;
        }

        static Mode fromName(String name) {
            if (name != null) {
                for (Mode m : values()) {
                    if (m.name().equals(name)) return m;
                }
            }
            return DEFAULT;
        }
    }

    static String idle() {
        return Tx.t("未启用（还没有音源）", "Off (no source yet)");
    }

    private final DownmixCore core = new DownmixCore();
    private volatile Mode mode;
    private volatile String status = idle();

    DownmixAudioProcessor(Mode initial) {
        mode = initial;
    }

    static String passThroughStatus(int channels) {
        if (channels <= 0) return idle();
        return channels <= 2
                ? Tx.t("未启用：音源 ", "Off: ") + channels + Tx.t(" 声道，不用下混", "-channel source, no downmix needed")
                : Tx.t("未启用：", "Off: ") + channels + Tx.t(" 声道原样交给车机", " channels passed through");
    }

    void setMode(Mode m) {
        mode = m;
    }

    Mode getMode() {
        return mode;
    }

    String status() {
        return status;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException {
        Mode m = mode;
        if (input.channelCount <= 2 || m == Mode.PLATFORM) {
            status = passThroughStatus(input.channelCount);
            return AudioFormat.NOT_SET;
        }
        if (input.encoding != C.ENCODING_PCM_16BIT || !DownmixCore.supports(input.channelCount)) {
            status = Tx.t("未启用：不支持 ", "Off: unsupported, ") + input.channelCount + Tx.t(" 声道 / 编码 ", " ch / encoding ") + input.encoding + Tx.t("，交给系统", ", left to the system");
            return AudioFormat.NOT_SET;
        }
        status = DownmixCore.describe(input.channelCount, m == Mode.LORO_LFE);
        return new AudioFormat(input.sampleRate, 2, C.ENCODING_PCM_16BIT);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int position = inputBuffer.position();
        int end = inputBuffer.limit();
        AudioFormat in = inputAudioFormat;
        int frames = (end - position) / in.bytesPerFrame;
        boolean lfe = mode == Mode.LORO_LFE;
        if (core.ensure(in.channelCount, lfe, in.sampleRate)) {
            status = DownmixCore.describe(in.channelCount, lfe);
        }
        ByteBuffer src = inputBuffer.duplicate().order(ByteOrder.nativeOrder());
        ByteBuffer out = replaceOutputBuffer(frames * 4);
        core.process(src, position, frames, out);
        inputBuffer.position(end);
        out.flip();
    }

    @Override
    protected void onFlush() {
        core.resetLimiter();
    }

    @Override
    protected void onReset() {
        core.invalidate();
        status = idle();
    }
}
