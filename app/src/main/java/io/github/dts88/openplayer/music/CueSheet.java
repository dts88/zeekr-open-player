package io.github.dts88.openplayer.music;

import io.github.dts88.openplayer.Tx;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CueSheet {
    public static final class Track {
        public final int number;
        public final String title;
        public final String performer;
        public final String file;
        public final long startMs;

        Track(int number, String title, String performer, String file, long startMs) {
            this.number = number;
            this.title = title;
            this.performer = performer;
            this.file = file;
            this.startMs = startMs;
        }

        public String label() {
            String name = title.isEmpty() ? Tx.t("第 ", "Track ") + number + Tx.t(" 首", "") : title;
            return String.format(Locale.ROOT, "%02d  %s%s", number, name, performer.isEmpty() ? "" : " — " + performer);
        }
    }

    public final String title;
    public final String performer;
    public final String date;
    public final String genre;
    public final List<Track> tracks;
    public final List<String> files;

    private CueSheet(String title, String performer, String date, String genre, List<Track> tracks,
                     List<String> files) {
        this.title = title;
        this.performer = performer;
        this.date = date;
        this.genre = genre;
        this.tracks = Collections.unmodifiableList(tracks);
        this.files = Collections.unmodifiableList(files);
    }

    public static CueSheet parse(byte[] bytes) {
        return parse(decode(bytes));
    }

    public static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b));
            return chars.toString();
        } catch (CharacterCodingException notUtf8) {
            try {
                return new String(b, Charset.forName("GB18030"));
            } catch (RuntimeException noGb) {
                return new String(b, StandardCharsets.ISO_8859_1);
            }
        }
    }

    public static CueSheet parse(String text) {
        String albumTitle = "";
        String albumPerformer = "";
        String date = "";
        String genre = "";
        List<Track> tracks = new ArrayList<>();
        List<String> files = new ArrayList<>();
        String file = "";
        boolean inTrack = false;
        int number = 0;
        String title = "";
        String performer = "";
        long start = -1;
        String trackFile = "";

        for (String raw : text.split("\\r?\\n|\\r")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int space = firstSpace(line);
            String command = (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.ROOT);
            String rest = space < 0 ? "" : line.substring(space + 1).trim();
            switch (command) {
                case "FILE": {
                    file = fileArgument(rest);
                    if (!file.isEmpty() && !files.contains(file)) files.add(file);
                    break;
                }
                case "TRACK": {
                    if (inTrack && start >= 0) {
                        tracks.add(new Track(number, title, performer.isEmpty() ? albumPerformer : performer,
                                trackFile, start));
                    }
                    inTrack = true;
                    number = leadingNumber(rest, tracks.size() + 1);
                    title = "";
                    performer = "";
                    start = -1;
                    trackFile = file;
                    break;
                }
                case "TITLE":
                    if (inTrack) title = firstArgument(rest);
                    else albumTitle = firstArgument(rest);
                    break;
                case "PERFORMER":
                    if (inTrack) performer = firstArgument(rest);
                    else albumPerformer = firstArgument(rest);
                    break;
                case "INDEX": {
                    int sp = firstSpace(rest);
                    if (sp < 0 || !inTrack) break;
                    int index = leadingNumber(rest, -1);
                    long ms = time(rest.substring(sp + 1).trim());
                    if (ms < 0) break;
                    if (index == 1) {
                        start = ms;
                        trackFile = file;
                    }
                    break;
                }
                case "REM": {
                    int sp = firstSpace(rest);
                    String key = (sp < 0 ? rest : rest.substring(0, sp)).toUpperCase(Locale.ROOT);
                    String value = sp < 0 ? "" : firstArgument(rest.substring(sp + 1).trim());
                    if (!inTrack && key.equals("DATE")) date = value;
                    if (!inTrack && key.equals("GENRE")) genre = value;
                    break;
                }
                default:
                    break;
            }
        }
        if (inTrack && start >= 0) {
            tracks.add(new Track(number, title, performer.isEmpty() ? albumPerformer : performer, trackFile, start));
        }
        return new CueSheet(albumTitle, albumPerformer, date, genre, tracks, files);
    }

    public List<Track> tracksIn(String file) {
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) {
            if (t.file.equalsIgnoreCase(file)) out.add(t);
        }
        return out;
    }

    public static int indexAt(List<Track> list, long positionMs) {
        int at = list.isEmpty() ? -1 : 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startMs <= positionMs) at = i;
        }
        return at;
    }

    static long time(String s) {
        String[] p = s.split(":");
        if (p.length != 3) return -1;
        try {
            long m = Long.parseLong(p[0].trim());
            long sec = Long.parseLong(p[1].trim());
            long frames = Long.parseLong(p[2].trim());
            if (m < 0 || sec < 0 || sec >= 60 || frames < 0 || frames >= 75) return -1;
            return (m * 60 + sec) * 1000 + Math.round(frames * 1000.0 / 75);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String firstArgument(String rest) {
        if (rest.isEmpty()) return "";
        if (rest.charAt(0) == '"') {
            int end = rest.indexOf('"', 1);
            return end < 0 ? rest.substring(1) : rest.substring(1, end);
        }
        return rest;
    }

    static String fileArgument(String rest) {
        if (rest.isEmpty() || rest.charAt(0) == '"') return firstArgument(rest);
        int last = rest.lastIndexOf(' ');
        if (last > 0) {
            String tail = rest.substring(last + 1).toUpperCase(Locale.ROOT);
            if (tail.equals("WAVE") || tail.equals("MP3") || tail.equals("AIFF") || tail.equals("BINARY")
                    || tail.equals("MOTOROLA") || tail.equals("FLAC")) {
                return rest.substring(0, last).trim();
            }
        }
        return rest;
    }

    private static int firstSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static int leadingNumber(String s, int fallback) {
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0) return fallback;
        try {
            return Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
