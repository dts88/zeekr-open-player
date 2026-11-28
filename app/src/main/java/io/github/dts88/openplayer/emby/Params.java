package io.github.dts88.openplayer.emby;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

final class Params {
    private final StringBuilder sb = new StringBuilder();

    Params add(String name, String value) {
        if (value == null || value.isEmpty()) return this;
        if (sb.length() > 0) sb.append('&');
        sb.append(encode(name)).append('=').append(encode(value));
        return this;
    }

    Params add(String name, long value) {
        return add(name, String.valueOf(value));
    }

    Params add(String name, boolean value) {
        return add(name, value ? "true" : "false");
    }

    boolean isEmpty() {
        return sb.length() == 0;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    static String encode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
