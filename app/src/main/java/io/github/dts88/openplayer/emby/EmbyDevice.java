package io.github.dts88.openplayer.emby;

import java.nio.charset.StandardCharsets;

public final class EmbyDevice {
    public final String client;
    public final String deviceName;
    public final String deviceId;
    public final String version;

    public EmbyDevice(String client, String deviceName, String deviceId, String version) {
        this.client = client == null ? "" : client;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.deviceId = deviceId == null ? "" : deviceId;
        this.version = version == null ? "" : version;
    }

    String authorization(String userId) {
        StringBuilder sb = new StringBuilder("Emby ");
        if (userId != null && !userId.isEmpty()) sb.append("UserId=\"").append(headerSafe(userId)).append("\", ");
        sb.append("Client=\"").append(headerSafe(client)).append("\", ");
        sb.append("Device=\"").append(headerSafe(deviceName)).append("\", ");
        sb.append("DeviceId=\"").append(headerSafe(deviceId)).append("\", ");
        sb.append("Version=\"").append(headerSafe(version)).append('"');
        return sb.toString();
    }

    static String headerSafe(String s) {
        if (s == null) return "";
        StringBuilder sb = null;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            boolean plain = cp >= 0x20 && cp < 0x7F && cp != '"' && cp != ',' && cp != '%';
            if (!plain && sb == null) sb = new StringBuilder(s.length() + 16).append(s, 0, i);
            if (sb != null) {
                if (plain) {
                    sb.append((char) cp);
                } else {
                    for (byte b : s.substring(i, i + n).getBytes(StandardCharsets.UTF_8)) {
                        sb.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
                    }
                }
            }
            i += n;
        }
        return sb == null ? s : sb.toString();
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
}
