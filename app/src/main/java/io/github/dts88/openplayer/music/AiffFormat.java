package io.github.dts88.openplayer.music;

import java.util.Locale;

public final class AiffFormat {
    enum Coding {
        PCM_BE, PCM_LE, UNSIGNED_8, FLOAT32_BE, FLOAT64_BE
    }

    public final int channels;
    public final long frames;
    public final int bits;
    public final int sampleRate;
    public final String compression;
    final Coding coding;
    public final int inBytesPerSample;
    public final int outBytesPerSample;
    public final boolean outFloat;

    private AiffFormat(int channels, long frames, int bits, int sampleRate, String compression, Coding coding) {
        this.channels = channels;
        this.frames = frames;
        this.bits = bits;
        this.sampleRate = sampleRate;
        this.compression = compression;
        this.coding = coding;
        if (coding == Coding.FLOAT32_BE) {
            inBytesPerSample = 4;
            outBytesPerSample = 4;
            outFloat = true;
        } else if (coding == Coding.FLOAT64_BE) {
            inBytesPerSample = 8;
            outBytesPerSample = 4;
            outFloat = true;
        } else {
            inBytesPerSample = (bits + 7) / 8;
            outBytesPerSample = Math.max(2, inBytesPerSample);
            outFloat = false;
        }
    }

    public boolean supported() {
        return coding != null;
    }

    public int inFrameBytes() {
        return inBytesPerSample * channels;
    }

    public int outFrameBytes() {
        return outBytesPerSample * channels;
    }

    public static AiffFormat parseComm(byte[] d, int offset, int length, boolean aifc) {
        if (length < 18) return null;
        int channels = u16(d, offset);
        long frames = u32(d, offset + 2);
        int bits = u16(d, offset + 6);
        double rate = extended80(d, offset + 8);
        if (channels < 1 || channels > 24 || bits < 1 || bits > 64 || !(rate >= 1000 && rate <= 768000)) return null;
        String compression = "NONE";
        if (aifc && length >= 22) {
            compression = new String(d, offset + 18, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        Coding coding;
        switch (compression) {
            case "NONE":
            case "twos":
            case "in24":
            case "in32":
                coding = bits <= 32 ? Coding.PCM_BE : null;
                break;
            case "sowt":
                coding = bits <= 32 ? Coding.PCM_LE : null;
                break;
            case "raw ":
                coding = bits <= 8 ? Coding.UNSIGNED_8 : null;
                break;
            case "fl32":
            case "FL32":
                coding = Coding.FLOAT32_BE;
                break;
            case "fl64":
            case "FL64":
                coding = Coding.FLOAT64_BE;
                break;
            default:
                coding = null;
        }
        return new AiffFormat(channels, frames, bits, (int) Math.round(rate), compression, coding);
    }

    public void convert(byte[] in, int inOffset, int frames, byte[] out, int outOffset) {
        int samples = frames * channels;
        int i = inOffset;
        int o = outOffset;
        switch (coding) {
            case PCM_LE:
                if (inBytesPerSample == 1) {
                    for (int s = 0; s < samples; s++) {
                        out[o++] = 0;
                        out[o++] = in[i++];
                    }
                } else {
                    System.arraycopy(in, i, out, o, samples * inBytesPerSample);
                }
                break;
            case UNSIGNED_8:
                for (int s = 0; s < samples; s++) {
                    out[o++] = 0;
                    out[o++] = (byte) ((in[i++] & 0xFF) - 128);
                }
                break;
            case PCM_BE:
                if (inBytesPerSample == 1) {
                    for (int s = 0; s < samples; s++) {
                        out[o++] = 0;
                        out[o++] = in[i++];
                    }
                } else {
                    int w = inBytesPerSample;
                    for (int s = 0; s < samples; s++) {
                        for (int b = 0; b < w; b++) out[o + b] = in[i + w - 1 - b];
                        i += w;
                        o += w;
                    }
                }
                break;
            case FLOAT32_BE:
                for (int s = 0; s < samples; s++) {
                    out[o] = in[i + 3];
                    out[o + 1] = in[i + 2];
                    out[o + 2] = in[i + 1];
                    out[o + 3] = in[i];
                    i += 4;
                    o += 4;
                }
                break;
            case FLOAT64_BE:
                for (int s = 0; s < samples; s++) {
                    long bitsIn = 0;
                    for (int b = 0; b < 8; b++) bitsIn = (bitsIn << 8) | (in[i + b] & 0xFF);
                    int f = Float.floatToRawIntBits((float) Double.longBitsToDouble(bitsIn));
                    out[o] = (byte) f;
                    out[o + 1] = (byte) (f >> 8);
                    out[o + 2] = (byte) (f >> 16);
                    out[o + 3] = (byte) (f >> 24);
                    i += 8;
                    o += 4;
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    static double extended80(byte[] d, int p) {
        int exponent = ((d[p] & 0x7F) << 8) | (d[p + 1] & 0xFF);
        long mantissa = 0;
        for (int i = 0; i < 8; i++) mantissa = (mantissa << 8) | (d[p + 2 + i] & 0xFF);
        if (exponent == 0 && mantissa == 0) return 0;
        if (exponent == 0x7FFF) return Double.NaN;
        double m = (mantissa >>> 32) * 4294967296.0 + (mantissa & 0xFFFFFFFFL);
        double v = Math.scalb(m, exponent - 16383 - 63);
        return (d[p] & 0x80) != 0 ? -v : v;
    }

    public String describe() {
        return String.format(Locale.ROOT, "%d 声道，%d Hz，%d 位%s，%d 帧（%.1f 秒）%s",
                channels, sampleRate, bits,
                outFloat ? "浮点" : "",
                frames, frames / (double) sampleRate,
                "NONE".equals(compression) ? "" : "，编码方式 " + compression.trim());
    }

    private static int u16(byte[] d, int p) {
        return ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
    }

    private static long u32(byte[] d, int p) {
        return ((d[p] & 0xFFL) << 24) | ((d[p + 1] & 0xFF) << 16) | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }
}
