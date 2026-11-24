package io.github.dts88.openplayer.bluray;

final class HdmvLpcm {
    static final int HEADER = 4;

    static final class Layout {
        final int storedChannels;
        final int channels;
        final int bitsPerSample;
        final int bytesPerSample;
        final int sampleRate;
        final int[] source;
        final boolean[] minus3dB;
        final int assignment;

        Layout(int assignment, int stored, int[] source, boolean[] minus3dB, int bits, int rate) {
            this.assignment = assignment;
            storedChannels = stored;
            channels = source.length;
            this.source = source;
            this.minus3dB = minus3dB;
            bitsPerSample = bits;
            bytesPerSample = bits == 16 ? 2 : 3;
            sampleRate = rate;
        }

        boolean sameAs(Layout o) {
            return o != null && o.assignment == assignment && o.bitsPerSample == bitsPerSample
                    && o.sampleRate == sampleRate;
        }
    }

    private static final int[] STORED = {0, 2, 0, 2, 4, 4, 4, 4, 6, 6, 8, 8, 0, 0, 0, 0};
    private static final int[][] SOURCE = {
            null,
            {0},
            null,
            {0, 1},
            {0, 1, 2},
            {0, 1, 2, 2},
            {0, 1, 2, -1, 3, 3},
            {0, 1, 2, 3},
            {0, 1, 2, 3, 4},
            {0, 1, 2, 5, 3, 4},
            {0, 1, 2, -1, 4, 5, 3, 6},
            {0, 1, 2, 7, 4, 5, 3, 6},
            null, null, null, null
    };

    private HdmvLpcm() {}

    static Layout parse(byte[] h, int offset) {
        int assignment = (h[offset + 2] >> 4) & 0x0F;
        int rateCode = h[offset + 2] & 0x0F;
        int bitsCode = (h[offset + 3] >> 6) & 0x03;
        int rate = rateCode == 1 ? 48000 : rateCode == 4 ? 96000 : rateCode == 5 ? 192000 : 0;
        int bits = bitsCode == 1 ? 16 : bitsCode == 2 ? 20 : bitsCode == 3 ? 24 : 0;
        int[] source = SOURCE[assignment];
        if (rate == 0 || bits == 0 || source == null) return null;
        boolean[] minus3dB = new boolean[source.length];
        if (assignment == 5 || assignment == 6) {
            minus3dB[source.length - 1] = true;
            minus3dB[source.length - 2] = true;
        }
        return new Layout(assignment, STORED[assignment], source, minus3dB, bits, rate);
    }

    static int frames(Layout l, int length) {
        return length / (l.storedChannels * l.bytesPerSample);
    }

    static int convert(Layout l, byte[] in, int offset, int length, byte[] out, int outOffset) {
        int bytes = l.bytesPerSample;
        int inFrame = l.storedChannels * bytes;
        int outFrame = l.channels * bytes;
        int frames = length / inFrame;
        for (int f = 0; f < frames; f++) {
            int src = offset + f * inFrame;
            int dst = outOffset + f * outFrame;
            for (int c = 0; c < l.channels; c++) {
                int d = dst + c * bytes;
                int si = l.source[c];
                if (si < 0) {
                    for (int k = 0; k < bytes; k++) out[d + k] = 0;
                    continue;
                }
                int s = src + si * bytes;
                if (bytes == 2) {
                    int v = (short) (((in[s] & 0xFF) << 8) | (in[s + 1] & 0xFF));
                    if (l.minus3dB[c]) v = (int) Math.round(v * 0.70710678);
                    out[d] = (byte) v;
                    out[d + 1] = (byte) (v >> 8);
                } else {
                    int v = ((in[s] << 24) | ((in[s + 1] & 0xFF) << 16) | ((in[s + 2] & 0xFF) << 8)) >> 8;
                    if (l.minus3dB[c]) v = (int) Math.round(v * 0.70710678);
                    out[d] = (byte) v;
                    out[d + 1] = (byte) (v >> 8);
                    out[d + 2] = (byte) (v >> 16);
                }
            }
        }
        return frames * outFrame;
    }
}
