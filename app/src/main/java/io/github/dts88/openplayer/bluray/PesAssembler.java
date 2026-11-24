package io.github.dts88.openplayer.bluray;

import java.io.IOException;

final class PesAssembler {
    interface Listener {
        void pesStarted(Header header);

        void pesPayload(byte[] data, int offset, int length) throws IOException;

        void pesFinished(boolean endOfInput);
    }

    static final class Header {
        int streamId;
        int packetLength;
        boolean dataAlignment;
        int flags;
        int headerDataLength;
        boolean hasPts;
        long pts;
        boolean hasDts;
        long dts;
        int streamIdExtension = -1;

        int payloadSize() {
            if (packetLength == 0) return -1;
            int size = packetLength + 6 - FIXED_HEADER - headerDataLength;
            return size < 0 ? -1 : size;
        }
    }

    static final int FIXED_HEADER = 9;
    static final int STREAM_ID_EXTENDED = 0xFD;

    private static final int FINDING_HEADER = 0;
    private static final int READING_HEADER = 1;
    private static final int READING_OPTIONAL = 2;
    private static final int READING_BODY = 3;

    private final Listener listener;
    private final byte[] fixed = new byte[FIXED_HEADER];
    private final byte[] optional = new byte[255];
    private final Header header = new Header();
    private int state = FINDING_HEADER;
    private int bytesRead;
    private int payloadRemaining;

    PesAssembler(Listener listener) {
        this.listener = listener;
    }

    void reset() {
        setState(FINDING_HEADER);
    }

    void consume(byte[] data, int offset, int length, boolean payloadUnitStart) throws IOException {
        if (payloadUnitStart) {
            if (state == READING_BODY) listener.pesFinished(length == 0);
            setState(READING_HEADER);
        }
        int end = offset + length;
        while (offset < end) {
            switch (state) {
                case FINDING_HEADER:
                    offset = end;
                    break;
                case READING_HEADER: {
                    int n = Math.min(end - offset, FIXED_HEADER - bytesRead);
                    System.arraycopy(data, offset, fixed, bytesRead, n);
                    offset += n;
                    bytesRead += n;
                    if (bytesRead == FIXED_HEADER) {
                        if (!parseFixed(fixed, header)) {
                            setState(FINDING_HEADER);
                        } else {
                            setState(READING_OPTIONAL);
                            maybeFinishHeader();
                        }
                    }
                    break;
                }
                case READING_OPTIONAL: {
                    int n = Math.min(end - offset, header.headerDataLength - bytesRead);
                    System.arraycopy(data, offset, optional, bytesRead, n);
                    offset += n;
                    bytesRead += n;
                    maybeFinishHeader();
                    break;
                }
                case READING_BODY: {
                    int n = end - offset;
                    if (payloadRemaining >= 0) n = Math.min(n, payloadRemaining);
                    if (n > 0) listener.pesPayload(data, offset, n);
                    offset += n;
                    if (payloadRemaining >= 0) {
                        payloadRemaining -= n;
                        if (payloadRemaining == 0) {
                            listener.pesFinished(false);
                            setState(READING_HEADER);
                            offset = end;
                        }
                    }
                    break;
                }
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private void maybeFinishHeader() {
        if (bytesRead < header.headerDataLength) return;
        parseOptional(optional, header.headerDataLength, header);
        payloadRemaining = header.payloadSize();
        listener.pesStarted(header);
        setState(READING_BODY);
        if (payloadRemaining == 0) {
            listener.pesFinished(false);
            setState(READING_HEADER);
        }
    }

    private void setState(int s) {
        state = s;
        bytesRead = 0;
    }

    static boolean parseFixed(byte[] h, Header out) {
        if (h[0] != 0 || h[1] != 0 || h[2] != 1) return false;
        if ((h[6] & 0xC0) != 0x80) return false;
        out.streamId = h[3] & 0xFF;
        out.packetLength = ((h[4] & 0xFF) << 8) | (h[5] & 0xFF);
        out.dataAlignment = (h[6] & 0x04) != 0;
        out.flags = h[7] & 0xFF;
        out.hasPts = (out.flags & 0x80) != 0;
        out.hasDts = (out.flags & 0xC0) == 0xC0;
        out.headerDataLength = h[8] & 0xFF;
        out.pts = 0;
        out.dts = 0;
        out.streamIdExtension = -1;
        return true;
    }

    static void parseOptional(byte[] o, int length, Header out) {
        int p = 0;
        int flags = out.flags;
        if (out.hasPts) {
            if (p + 5 > length) {
                out.hasPts = false;
                out.hasDts = false;
                return;
            }
            out.pts = readTimestamp(o, p);
            p += 5;
        }
        if (out.hasDts) {
            if (p + 5 > length) {
                out.hasDts = false;
                return;
            }
            out.dts = readTimestamp(o, p);
            p += 5;
        }
        if ((flags & 0x20) != 0) p += 6;
        if ((flags & 0x10) != 0) p += 3;
        if ((flags & 0x08) != 0) p += 1;
        if ((flags & 0x04) != 0) p += 1;
        if ((flags & 0x02) != 0) p += 2;
        if ((flags & 0x01) == 0 || p >= length) return;
        int ext = o[p++] & 0xFF;
        if ((ext & 0x80) != 0) p += 16;
        if ((ext & 0x40) != 0) {
            if (p >= length) return;
            p += 1 + (o[p] & 0xFF);
        }
        if ((ext & 0x20) != 0) p += 2;
        if ((ext & 0x10) != 0) p += 2;
        if ((ext & 0x01) != 0 && p + 1 < length) {
            int fieldLength = o[p] & 0x7F;
            if (fieldLength > 0 && (o[p + 1] & 0x80) == 0) {
                out.streamIdExtension = o[p + 1] & 0x7F;
            }
        }
    }

    static long readTimestamp(byte[] b, int p) {
        return ((long) (b[p] & 0x0E) << 29)
                | ((b[p + 1] & 0xFFL) << 22)
                | ((long) (b[p + 2] & 0xFE) << 14)
                | ((b[p + 3] & 0xFFL) << 7)
                | ((b[p + 4] & 0xFEL) >> 1);
    }
}
