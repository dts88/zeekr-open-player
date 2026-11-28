package io.github.dts88.openplayer.emby;

public final class EmbyException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        NETWORK,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        SERVER,
        BAD_RESPONSE
    }

    public final Kind kind;
    public final int status;
    public final String where;
    public final String serverText;

    EmbyException(Kind kind, int status, String message, String where, String serverText, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
        this.where = where == null ? "" : where;
        this.serverText = serverText == null ? "" : serverText;
    }

    @Override
    public String toString() {
        return "EmbyException[" + kind + (status > 0 ? " " + status : "") + (where.isEmpty() ? "" : " " + where) + "] "
                + getMessage();
    }
}
