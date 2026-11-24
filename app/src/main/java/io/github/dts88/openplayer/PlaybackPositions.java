package io.github.dts88.openplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlaybackPositions {
    static final long MIN_DURATION_MS = 5 * 60_000L;
    static final long MIN_POSITION_MS = 30_000L;
    static final long END_MARGIN_MS = 60_000L;
    static final double END_FRACTION = 0.95;
    static final int MAX_ENTRIES = 300;
    static final long MIN_CHANGE_MS = 2_000L;

    static final class Entry {
        final long positionMs;
        final long durationMs;
        final long updatedAt;

        Entry(long positionMs, long durationMs, long updatedAt) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.updatedAt = updatedAt;
        }
    }

    private final File file;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true);
    private int linesOnDisk;
    private String problem = "";

    PlaybackPositions(File file) {
        this.file = file;
        load();
    }

    static String key(String path, long size) {
        return size + "|" + path.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    long resumeAt(String key) {
        Entry e = entries.get(key);
        return e == null ? -1 : e.positionMs;
    }

    static boolean worthKeeping(long positionMs, long durationMs) {
        if (durationMs < MIN_DURATION_MS) return false;
        if (positionMs < MIN_POSITION_MS) return false;
        return positionMs < durationMs * END_FRACTION && durationMs - positionMs >= END_MARGIN_MS;
    }

    boolean update(String key, long positionMs, long durationMs, long now) {
        Entry old = entries.get(key);
        if (!worthKeeping(positionMs, durationMs)) {
            if (old != null) {
                entries.remove(key);
                append(key, -1, durationMs, now);
            }
            return false;
        }
        if (old != null && Math.abs(old.positionMs - positionMs) < MIN_CHANGE_MS && old.durationMs == durationMs) {
            return true;
        }
        entries.put(key, new Entry(positionMs, durationMs, now));
        append(key, positionMs, durationMs, now);
        trim(now);
        return true;
    }

    void clear(String key, long now) {
        if (entries.remove(key) != null) append(key, -1, 0, now);
    }

    int size() {
        return entries.size();
    }

    List<String> keysNewestFirst() {
        List<String> keys = new ArrayList<>(entries.keySet());
        java.util.Collections.reverse(keys);
        return keys;
    }

    Entry peek(String key) {
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (e.getKey().equals(key)) return e.getValue();
        }
        return null;
    }

    String problem() {
        return problem;
    }

    private void trim(long now) {
        while (entries.size() > MAX_ENTRIES) {
            String oldest = entries.keySet().iterator().next();
            entries.remove(oldest);
            append(oldest, -1, 0, now);
        }
    }

    private void load() {
        entries.clear();
        linesOnDisk = 0;
        if (!file.isFile()) return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                linesOnDisk++;
                String[] p = line.split("\t", 4);
                if (p.length != 4 || p[3].isEmpty()) continue;
                try {
                    long position = Long.parseLong(p[0]);
                    long duration = Long.parseLong(p[1]);
                    long at = Long.parseLong(p[2]);
                    if (position < 0) entries.remove(p[3]);
                    else entries.put(p[3], new Entry(position, duration, at));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            problem = "读续播记录失败：" + e;
        }
        if (linesOnDisk > entries.size() * 4 + 200) compact();
    }

    private void compact() {
        File tmp = new File(file.getPath() + ".tmp");
        List<Map.Entry<String, Entry>> all = new ArrayList<>(entries.entrySet());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Entry> e : all) {
                w.write(line(e.getKey(), e.getValue().positionMs, e.getValue().durationMs, e.getValue().updatedAt));
            }
        } catch (IOException e) {
            problem = "整理续播记录失败：" + e;
            return;
        }
        if (file.exists() && !file.delete()) {
            problem = "整理续播记录失败：旧文件删不掉";
            return;
        }
        if (tmp.renameTo(file)) linesOnDisk = all.size();
        else problem = "整理续播记录失败：改名不成";
    }

    private void append(String key, long positionMs, long durationMs, long now) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(line(key, positionMs, durationMs, now));
            linesOnDisk++;
        } catch (IOException e) {
            problem = "写续播记录失败：" + e;
        }
    }

    private static String line(String key, long positionMs, long durationMs, long now) {
        return positionMs + "\t" + durationMs + "\t" + now + "\t" + key + "\n";
    }
}
