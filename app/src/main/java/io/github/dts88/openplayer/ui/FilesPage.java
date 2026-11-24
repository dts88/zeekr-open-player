package io.github.dts88.openplayer.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.dts88.openplayer.MediaFiles;
import io.github.dts88.openplayer.MediaLabels;
import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.Roots;
import io.github.dts88.openplayer.Tx;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class FilesPage extends Page {
    private final File dir;
    private final String label;
    private final Rows.Adapter rows = new Rows.Adapter();
    private TextView note;
    private String shown = "";

    FilesPage(ShellActivity shell, File dir, String label) {
        super(shell);
        this.dir = dir;
        this.label = label;
    }

    @Override
    String title() {
        return label;
    }

    @Override
    protected View build() {
        LinearLayout column = Ui.column(c);
        column.setPadding(dp(16), dp(16), dp(16), 0);
        note = Ui.text(c, "", Ui.BODY, Ui.TEXT2);
        note.setPadding(dp(4), 0, 0, dp(12));
        column.addView(note);
        RecyclerView list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(rows);
        column.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return column;
    }

    @Override
    void onShow() {
        fill();
    }

    @Override
    void onEngineChanged() {
        if (dir != null) fill();
    }

    private void fill() {
        if (dir == null) {
            fillRoots();
        } else {
            fillFolder();
        }
    }

    private void fillRoots() {
        List<Rows.Item> items = new ArrayList<>();
        for (File r : Roots.list(c)) {
            String name = Roots.isUsb(r) ? Tx.t("U 盘", "USB drive") : Tx.t("车机存储", "Car storage");
            items.add(new Rows.Item(name).icon("folder").sub(r.getPath())
                    .onClick(() -> shell.open(new FilesPage(shell, r, name))));
        }
        note.setText(Tx.t("选择存储设备。", "Choose a storage device."));
        rows.set(items);
    }

    private void fillFolder() {
        File[] all = dir.listFiles();
        List<File> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) folders.add(f);
                else if (MediaFiles.isPlayable(f) || MediaFiles.isCue(f)) files.add(f);
            }
        }
        Collator collator = Collator.getInstance(Locale.CHINA);
        Comparator<File> byName = (x, y) -> collator.compare(x.getName(), y.getName());
        folders.sort(byName);
        files.sort(byName);

        PlayerEngine.Item playing = shell.engine.current();
        File playingFile = playing == null ? null : playing.file();
        String key = folders.size() + "#" + files.size() + "#" + playingFile + "#" + shell.engine.revision();
        if (key.equals(shown)) return;
        shown = key;

        List<File> playable = new ArrayList<>();
        for (File f : files) {
            if (!MediaFiles.isCue(f)) playable.add(f);
        }
        List<Rows.Item> items = new ArrayList<>();
        for (File f : folders) {
            items.add(new Rows.Item(f.getName()).icon("folder")
                    .onClick(() -> shell.open(new FilesPage(shell, f, f.getName()))));
        }
        for (File f : files) {
            boolean cue = MediaFiles.isCue(f);
            Rows.Item row = new Rows.Item(f.getName())
                    .icon(cue ? "disc" : MediaFiles.isAudio(f) ? "music" : "film")
                    .trailing(MediaLabels.size(f.length()))
                    .current(f.equals(playingFile))
                    .onClick(() -> play(f, playable));
            if (cue) {
                row.sub(Tx.t("分轨表", "Cue sheet"));
            } else {
                long at = shell.engine.resumePosition(f);
                long total = shell.engine.resumeDuration(f);
                if (at > 0 && total > 0) {
                    row.sub(Tx.t("已播放至 ", "Played to ") + MediaLabels.clock(at)).progress(at / (float) total);
                }
            }
            items.add(row);
        }
        if (all == null) {
            note.setText(Tx.t("无法读取此文件夹。", "This folder cannot be read."));
        } else if (items.isEmpty()) {
            note.setText(Tx.t("此文件夹中无可播放的文件。", "No playable files in this folder."));
        }
        note.setVisibility(all == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        rows.set(items);
    }

    private void play(File picked, List<File> siblings) {
        if (MediaFiles.isCue(picked)) {
            shell.engine.open(picked);
            shell.showPlayer();
            return;
        }
        boolean audio = MediaFiles.isAudio(picked);
        List<PlayerEngine.Item> queue = new ArrayList<>();
        int index = 0;
        for (File f : siblings) {
            if (MediaFiles.isAudio(f) != audio) continue;
            if (f.equals(picked)) index = queue.size();
            queue.add(shell.engine.withSiblingCue(f));
        }
        shell.engine.playQueue(queue, index);
        shell.showPlayer();
    }
}
