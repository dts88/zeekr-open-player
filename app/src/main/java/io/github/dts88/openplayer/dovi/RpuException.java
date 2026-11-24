package io.github.dts88.openplayer.dovi;

public final class RpuException extends Exception {
    public static final int TRUNCATED = 1;
    public static final int CRC = 2;
    public static final int INVALID = 3;
    public static final int UNSUPPORTED = 4;
    public static final int MISSING_REFERENCE = 5;
    public static final int INTERNAL = 6;

    private static final long serialVersionUID = 1L;

    public final int reason;

    public RpuException(int reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RpuException(int reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
