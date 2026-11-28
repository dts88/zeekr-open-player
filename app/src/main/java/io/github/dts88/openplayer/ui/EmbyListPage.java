package io.github.dts88.openplayer.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.dts88.openplayer.MediaLabels;
import io.github.dts88.openplayer.Tx;
import io.github.dts88.openplayer.emby.EmbyHub;
import io.github.dts88.openplayer.emby.EmbyItem;

import java.util.ArrayList;
import java.util.List;

final class EmbyListPage extends Page {
    private final EmbyHub hub;
    private final EmbyItem parent;
    private final Rows.Adapter rows = new Rows.Adapter();
    private final List<EmbyItem> items = new ArrayList<>();
    private TextView note;
    private int total = -1;
    private boolean loading;
    private String shown = "";

    EmbyListPage(ShellActivity shell, EmbyItem parent) {
        super(shell);
        this.parent = parent;
        hub = EmbyHub.get(shell);
    }

    @Override
    String title() {
        return parent.name;
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
        if (total < 0 && !loading) load();
        else paint();
    }

    @Override
    void onEngineChanged() {
        if (total >= 0) paint();
    }

    private void load() {
        loading = true;
        if (items.isEmpty()) {
            note.setVisibility(View.VISIBLE);
            note.setTextColor(Ui.TEXT2);
            note.setText(Tx.t("正在读取…", "Loading…"));
        }
        hub.children(parent, items.size(), (page, error) -> {
            loading = false;
            if (!built()) return;
            if (error != null || page == null) {
                note.setVisibility(View.VISIBLE);
                note.setTextColor(Ui.BAD);
                note.setText(EmbyHomePage.message(error));
                return;
            }
            items.addAll(page.items);
            total = page.items.isEmpty() ? items.size() : Math.max(page.totalCount, items.size());
            shown = "";
            paint();
        });
    }

    private void paint() {
        String playingId = hub.itemIdOf(shell.engine.current());
        String key = items.size() + "#" + total + "#" + playingId + "#" + loading;
        if (key.equals(shown)) return;
        shown = key;
        List<Rows.Item> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            EmbyItem it = items.get(i);
            int at = i;
            if (EmbyHub.playable(it)) {
                Rows.Item row = new Rows.Item(EmbyHub.titleOf(it))
                        .icon(EmbyHub.audio(it) ? "music" : "film")
                        .current(it.id.equals(playingId))
                        .onClick(() -> play(at));
                if (it.runtimeMs > 0) row.trailing(MediaLabels.clock(it.runtimeMs));
                float progress = EmbyHub.progress(it);
                if (it.userData.played) {
                    row.sub(Tx.t("已看", "Watched"));
                } else if (progress > 0) {
                    row.sub(Tx.t("已播放至 ", "Played to ") + MediaLabels.clock(it.userData.positionMs)).progress(progress);
                } else if (it.year > 0) {
                    row.sub(String.valueOf(it.year));
                }
                list.add(row);
            } else {
                Rows.Item row = new Rows.Item(it.name).icon("folder")
                        .onClick(() -> shell.open(new EmbyListPage(shell, it)));
                if (it.year > 0) row.sub(String.valueOf(it.year));
                list.add(row);
            }
        }
        if (items.size() < total) {
            list.add(new Rows.Item(Tx.t("加载更多", "Load more"))
                    .icon("list")
                    .sub(items.size() + " / " + total)
                    .onClick(() -> {
                        if (!loading) load();
                    }));
        }
        note.setTextColor(Ui.TEXT2);
        note.setText(Tx.t("此处没有内容。", "Nothing here."));
        note.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        rows.set(list);
    }

    private void play(int index) {
        if (hub.play(items, index)) shell.showPlayer();
    }
}
