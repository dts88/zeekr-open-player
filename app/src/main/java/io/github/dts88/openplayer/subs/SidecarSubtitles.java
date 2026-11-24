package io.github.dts88.openplayer.subs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SidecarSubtitles {
    public static final class Found {
        public final File file;
        public final String mimeType;
        public final String label;
        public final String language;

        Found(File file, String mimeType, String label, String language) {
            this.file = file;
            this.mimeType = mimeType;
            this.label = label;
            this.language = language;
        }
    }

    public static final String MIME_SRT = "application/x-subrip";
    public static final String MIME_SSA = "text/x-ssa";
    public static final String MIME_VTT = "text/vtt";

    static final int MAX_CONVERT_BYTES = 16 << 20;

    private SidecarSubtitles() {
    }

    public static List<Found> find(File video) {
        List<Found> out = new ArrayList<>();
        File dir = video.getParentFile();
        File[] all = dir == null ? null : dir.listFiles();
        if (all == null) return out;
        Arrays.sort(all, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        for (File f : all) {
            if (!f.isFile()) continue;
            String[] m = match(video.getName(), f.getName());
            if (m != null) out.add(new Found(f, m[0], m[1], m[2]));
        }
        return out;
    }

    static String[] match(String videoName, String candidate) {
        int dot = candidate.lastIndexOf('.');
        if (dot <= 0) return null;
        String ext = candidate.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime;
        switch (ext) {
            case "srt":
                mime = MIME_SRT;
                break;
            case "ass":
            case "ssa":
                mime = MIME_SSA;
                break;
            case "vtt":
                mime = MIME_VTT;
                break;
            default:
                return null;
        }
        int videoDot = videoName.lastIndexOf('.');
        String stem = videoDot <= 0 ? videoName : videoName.substring(0, videoDot);
        String rest = candidate.substring(0, dot);
        if (rest.length() < stem.length() || !rest.regionMatches(true, 0, stem, 0, stem.length())) return null;
        String tag = rest.substring(stem.length());
        if (tag.isEmpty()) return new String[]{mime, ext, null};
        if (tag.charAt(0) != '.' || tag.length() == 1) return null;
        tag = tag.substring(1);
        return new String[]{mime, tag, languageOf(tag)};
    }

    static String languageOf(String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        String first = t.split("[&+_,\\s.\\-]")[0];
        if (t.startsWith("zh-hant") || t.startsWith("zh-tw") || t.startsWith("zh-hk")) return "zh-Hant";
        if (t.startsWith("zh")) return "zh";
        switch (first) {
            case "chs":
            case "sc":
            case "gb":
            case "chi":
            case "zho":
            case "cn":
            case "简体":
            case "简中":
            case "简":
            case "中文":
                return "zh";
            case "cht":
            case "tc":
            case "big5":
            case "繁体":
            case "繁中":
            case "繁":
                return "zh-Hant";
            case "en":
            case "eng":
            case "英文":
                return "en";
            case "ja":
            case "jp":
            case "jpn":
            case "日文":
                return "ja";
            case "ko":
            case "kor":
                return "ko";
            case "ms":
            case "may":
            case "msa":
                return "ms";
            default:
                return null;
        }
    }

    static boolean readableAsIs(byte[] b) {
        if (b.length >= 2 && ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE
                || (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF)) {
            return true;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    static byte[] toUtf8(byte[] b) {
        Charset gb;
        try {
            gb = Charset.forName("GB18030");
        } catch (RuntimeException e) {
            return b;
        }
        return new String(b, gb).getBytes(StandardCharsets.UTF_8);
    }

    public static File asUtf8(File src, File cacheDir) throws IOException {
        long size = src.length();
        if (size > MAX_CONVERT_BYTES) return src;
        byte[] data = new byte[(int) size];
        int n = 0;
        try (FileInputStream in = new FileInputStream(src)) {
            while (n < data.length) {
                int r = in.read(data, n, data.length - n);
                if (r < 0) break;
                n += r;
            }
        }
        if (n != data.length) data = Arrays.copyOf(data, n);
        if (readableAsIs(data)) return src;
        String name = src.getName();
        int dot = name.lastIndexOf('.');
        File out = new File(cacheDir, pathTag(src.getAbsolutePath()) + "-" + size + "-"
                + src.lastModified() + (dot < 0 ? "" : name.substring(dot)));
        if (out.isFile() && out.length() > 0) return out;
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) throw new IOException("建不了目录 " + cacheDir);
        try (FileOutputStream o = new FileOutputStream(out)) {
            o.write(toUtf8(data));
        }
        return out;
    }

    static String pathTag(String path) {
        int h = 0x5D7A1E3B;
        for (int i = 0; i < path.length(); i++) {
            h = Integer.rotateLeft((h ^ path.charAt(i)) * 0x01000193, 5);
        }
        return Integer.toHexString(h);
    }
}
