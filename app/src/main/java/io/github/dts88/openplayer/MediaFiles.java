package io.github.dts88.openplayer;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MediaFiles {
    private static final Set<String> VIDEO_EXT = new HashSet<>(Arrays.asList(
            "mkv", "mk3d", "mp4", "m4v", "mov", "webm", "ts", "m2ts", "mts", "mpg", "mpeg", "avi"));
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "mka", "m4a", "wav", "flac", "ogg", "oga", "opus", "aac", "ac3", "eac3", "ec3", "dts", "thd", "mlp",
            "mp3", "aif", "aiff", "aifc", "dsf", "dff"));
    private static final String CUE_EXT = "cue";

    private MediaFiles() {
    }

    public static String extension(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isVideo(File f) {
        return VIDEO_EXT.contains(extension(f));
    }

    public static boolean isAudio(File f) {
        return AUDIO_EXT.contains(extension(f));
    }

    public static boolean isCue(File f) {
        return CUE_EXT.equals(extension(f));
    }

    public static boolean isPlayable(File f) {
        return isVideo(f) || isAudio(f);
    }
}
