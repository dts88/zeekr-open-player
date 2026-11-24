package io.github.dts88.openplayer.bluray;

import java.io.IOException;
import java.util.Locale;

final class M2tsPackets {
    static final int PACKET = 192;
    static final int TS_PACKET = 188;
    static final int EXTRA = PACKET - TS_PACKET;
    static final int SYNC = 0x47;
    static final int SNIFF_PACKETS = 8;
    static final int SNIFF_BYTES = PACKET * SNIFF_PACKETS;

    private M2tsPackets() {}

    static boolean isM2tsName(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".m2ts") || p.endsWith(".mts");
    }

    static boolean looksLikeM2ts(byte[] head, int length) {
        if (length < PACKET * (SNIFF_PACKETS - 1) + EXTRA + 1) return false;
        for (int k = 0; k < SNIFF_PACKETS; k++) {
            if ((head[k * PACKET + EXTRA] & 0xFF) != SYNC) return false;
        }
        return true;
    }

    static long toFilePosition(long tsPosition) {
        return tsPosition / TS_PACKET * PACKET + EXTRA + tsPosition % TS_PACKET;
    }

    static long toFileStart(long tsPosition) {
        long inPacket = tsPosition % TS_PACKET;
        return tsPosition / TS_PACKET * PACKET + (inPacket == 0 ? 0 : EXTRA + inPacket);
    }

    static long tsBytesBefore(long filePosition) {
        long inPacket = filePosition % PACKET;
        return filePosition / PACKET * TS_PACKET + Math.max(0, inPacket - EXTRA);
    }

    interface Upstream {
        int read(byte[] buffer, int offset, int length) throws IOException;
    }

    static final class Stripper {
        private final byte[] buffer = new byte[PACKET * 256];
        private int position;
        private int limit;
        private long filePosition;
        private long remaining;

        void reset(long filePosition, long tsLength) {
            this.filePosition = filePosition;
            remaining = tsLength;
            position = 0;
            limit = 0;
        }

        int read(Upstream upstream, byte[] target, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int copied = 0;
            while (copied < length) {
                if (position == limit) {
                    if (copied > 0) break;
                    int n = upstream.read(buffer, 0, buffer.length);
                    if (n < 0) break;
                    position = 0;
                    limit = n;
                    continue;
                }
                int inPacket = (int) (filePosition % PACKET);
                if (inPacket < EXTRA) {
                    int skip = Math.min(EXTRA - inPacket, limit - position);
                    position += skip;
                    filePosition += skip;
                    continue;
                }
                int n = Math.min(length - copied, Math.min(PACKET - inPacket, limit - position));
                if (remaining > 0) n = (int) Math.min(n, remaining);
                System.arraycopy(buffer, position, target, offset + copied, n);
                position += n;
                filePosition += n;
                copied += n;
                if (remaining > 0) {
                    remaining -= n;
                    if (remaining == 0) break;
                }
            }
            return copied == 0 ? -1 : copied;
        }
    }
}
