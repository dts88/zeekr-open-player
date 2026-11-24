package io.github.dts88.openplayer;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Roots {
    private static final Pattern VOLUME = Pattern.compile("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");

    private Roots() {
    }

    public static List<File> list(Context context) {
        List<File> found = new ArrayList<>();
        for (String mount : mountPoints()) addIfReadable(found, new File(mount));
        File[] own = context.getExternalFilesDirs(null);
        if (own != null) {
            for (int i = 1; i < own.length; i++) {
                if (own[i] == null) continue;
                String root = volumeRootOf(own[i].getAbsolutePath());
                if (root != null) addIfReadable(found, new File(root));
            }
        }
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && !found.contains(internal)) found.add(internal);
        return found;
    }

    public static boolean isUsb(File root) {
        return isVolumeRoot(root.getAbsolutePath());
    }

    private static void addIfReadable(List<File> list, File f) {
        if (f.isDirectory() && f.canRead() && !list.contains(f)) list.add(f);
    }

    private static String volumeRootOf(String path) {
        int index = path.indexOf("/Android/data/");
        String root = index > 0 ? path.substring(0, index) : path;
        return isVolumeRoot(root) ? root : null;
    }

    private static boolean isVolumeRoot(String path) {
        return path != null && VOLUME.matcher(path).matches();
    }

    private static List<String> mountPoints() {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && isVolumeRoot(parts[1]) && !result.contains(parts[1])) {
                    result.add(parts[1]);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return result;
    }
}
