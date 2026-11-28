package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.dts88.openplayer.MediaLabels;
import io.github.dts88.openplayer.Tx;
import io.github.dts88.openplayer.emby.EmbyHub;
import io.github.dts88.openplayer.emby.EmbyItem;
import io.github.dts88.openplayer.emby.EmbyPage;

import java.util.ArrayList;
import java.util.List;

final class EmbyListPage extends Page {
    private static final int[][] ART_DP = {{62, 93}, {132, 74}, {78, 78}};
    private static final int PROGRESS_DP = 340;

    private final EmbyHub hub;
    private final EmbyItem parent;
    private final String term;
    private final String heading;
    private final List<EmbyItem> items = new ArrayList<>();
    private final Cards cards = new Cards();
    private TextView note;
    private int total = -1;
    private boolean loading;
    private boolean starting;
    private String playingId = "";

    EmbyListPage(ShellActivity shell, EmbyItem parent) {
        super(shell);
        this.parent = parent;
        this.term = "";
        this.heading = parent.name;
        hub = EmbyHub.get(shell);
    }

    EmbyListPage(ShellActivity shell, String term) {
        super(shell);
        this.parent = null;
        this.term = term;
        this.heading = Tx.t("搜索：", "Search: ") + term;
        hub = EmbyHub.get(shell);
    }

    @Override
    String title() {
        return heading;
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
        list.setAdapter(cards);
        column.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return column;
    }

    @Override
    void onShow() {
        if (total < 0 && !loading) load();
        else mark();
    }

    @Override
    void onEngineChanged() {
        if (total >= 0) mark();
    }

    private void say(String text, int color) {
        note.setVisibility(View.VISIBLE);
        note.setTextColor(color);
        note.setText(text);
    }

    private void load() {
        loading = true;
        if (items.isEmpty()) say(Tx.t("正在读取…", "Loading…"), Ui.TEXT2);
        EmbyHub.Done<EmbyPage> done = (page, error) -> {
            loading = false;
            if (!built()) return;
            if (error != null || page == null) {
                say(EmbyHomePage.message(error), Ui.BAD);
                return;
            }
            items.addAll(page.items);
            total = page.items.isEmpty() ? items.size() : Math.max(page.totalCount, items.size());
            note.setTextColor(Ui.TEXT2);
            note.setText(term.isEmpty() ? Tx.t("此处没有内容。", "Nothing here.") : Tx.t("没有找到。", "Nothing found."));
            note.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            playingId = hub.itemIdOf(shell.engine.current());
            cards.notifyDataSetChanged();
        };
        if (parent != null) hub.children(parent, items.size(), done);
        else hub.search(term, items.size(), done);
    }

    private void mark() {
        String now = hub.itemIdOf(shell.engine.current());
        if (now.equals(playingId)) return;
        playingId = now;
        cards.notifyDataSetChanged();
    }

    private void open(int index) {
        EmbyItem it = items.get(index);
        if (!EmbyHub.playable(it)) {
            shell.open(new EmbyListPage(shell, it));
            return;
        }
        if (starting) return;
        starting = true;
        say(Tx.t("正在准备播放…", "Preparing playback…"), Ui.TEXT2);
        hub.play(items, index, (started, error) -> {
            starting = false;
            if (built()) note.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            if (Boolean.TRUE.equals(started)) {
                shell.showPlayer();
            } else {
                Toast.makeText(c, error == null ? Tx.t("无法播放此内容", "This item cannot be played")
                        : EmbyHomePage.message(error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String subOf(EmbyItem it) {
        if (EmbyHub.playable(it)) {
            if (it.userData.played) return Tx.t("已看", "Watched");
            if (EmbyHub.progress(it) > 0) return Tx.t("已播放至 ", "Played to ") + MediaLabels.clock(it.userData.positionMs);
        }
        if (EmbyItem.EPISODE.equals(it.type) && !it.seriesName.isEmpty()) return it.seriesName;
        return it.year > 0 ? String.valueOf(it.year) : "";
    }

    private final class Cards extends RecyclerView.Adapter<Card> {
        @Override
        public int getItemCount() {
            return items.size() + (items.size() < total ? 1 : 0);
        }

        @NonNull
        @Override
        public Card onCreateViewHolder(@NonNull ViewGroup group, int type) {
            return new Card(group.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull Card card, int position) {
            if (position >= items.size()) {
                card.more(items.size() + " / " + total);
                card.itemView.setOnClickListener(v -> {
                    if (!loading) load();
                });
                return;
            }
            EmbyItem it = items.get(position);
            card.bind(it, it.id.equals(playingId));
            card.itemView.setOnClickListener(v -> open(card.getBindingAdapterPosition()));
        }
    }

    private final class Card extends RecyclerView.ViewHolder {
        private final FrameLayout frame;
        private final ImageView art;
        private final ImageView mark;
        private final TextView title;
        private final TextView sub;
        private final TextView trailing;
        private final View progress;

        Card(Context context) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(24), dp(10));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);
            row.setBackground(Ui.pressable(context, Ui.SUNKEN, Ui.RADIUS));

            frame = new FrameLayout(context);
            frame.setBackground(Ui.round(context, Ui.SURFACE, 6));
            frame.setClipToOutline(true);
            mark = Ui.icon(context, "film", 28, Ui.TEXT3);
            frame.addView(mark, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            art = new ImageView(context);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(art, Ui.fill());
            row.addView(frame, new LinearLayout.LayoutParams(dp(ART_DP[0][0]), dp(ART_DP[0][1])));

            LinearLayout texts = Ui.column(context);
            texts.setPadding(dp(18), 0, dp(16), 0);
            title = Ui.oneLine(Ui.text(context, "", Ui.BODY, Ui.TEXT));
            sub = Ui.oneLine(Ui.text(context, "", Ui.MICRO, Ui.TEXT3));
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(title);
            texts.addView(sub);
            progress = new View(context);
            progress.setBackgroundColor(Ui.ACCENT);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(4));
            pl.topMargin = dp(6);
            texts.addView(progress, pl);
            row.addView(texts, Ui.weight(1));
            trailing = Ui.text(context, "", Ui.CAPTION, Ui.TEXT3);
            row.addView(trailing);
        }

        private void size(int shape) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) frame.getLayoutParams();
            int w = dp(ART_DP[shape][0]);
            int h = dp(ART_DP[shape][1]);
            if (lp.width == w && lp.height == h) return;
            lp.width = w;
            lp.height = h;
            frame.setLayoutParams(lp);
        }

        void bind(EmbyItem it, boolean current) {
            int shape = EmbyHub.shape(it);
            size(shape);
            boolean playable = EmbyHub.playable(it);
            mark.setImageDrawable(new Icons(!playable ? "folder" : EmbyHub.audio(it) ? "music" : "film", Ui.TEXT3, c));
            Posters.into(art, hub.imageUrl(it, dp(ART_DP[shape][0]) * 2), dp(ART_DP[shape][0]));
            title.setText(EmbyHub.titleOf(it));
            title.setTextColor(current ? Ui.ENERGY_TEXT : Ui.TEXT);
            String line = subOf(it);
            sub.setText(line);
            sub.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
            trailing.setText(playable && it.runtimeMs > 0 ? MediaLabels.clock(it.runtimeMs) : "");
            float done = playable && !it.userData.played ? EmbyHub.progress(it) : -1;
            progress.setVisibility(done > 0 ? View.VISIBLE : View.GONE);
            if (done > 0) {
                LinearLayout.LayoutParams pl = (LinearLayout.LayoutParams) progress.getLayoutParams();
                pl.width = Math.max(dp(6), Math.round(dp(PROGRESS_DP) * done));
                progress.setLayoutParams(pl);
            }
        }

        void more(String count) {
            size(EmbyHub.SHAPE_SQUARE);
            mark.setImageDrawable(new Icons("list", Ui.TEXT3, c));
            Posters.into(art, "", 0);
            title.setText(Tx.t("加载更多", "Load more"));
            title.setTextColor(Ui.TEXT);
            sub.setText(count);
            sub.setVisibility(View.VISIBLE);
            trailing.setText("");
            progress.setVisibility(View.GONE);
        }
    }
}
