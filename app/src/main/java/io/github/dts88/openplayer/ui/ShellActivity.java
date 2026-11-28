package io.github.dts88.openplayer.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import io.github.dts88.openplayer.AppExit;
import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.Tx;
import io.github.dts88.openplayer.music.CueSheet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public final class ShellActivity extends Activity {
    private final String[][] nav = {{Tx.t("文件", "Files"), "folder"}, {"Emby", "server"}, {Tx.t("设置", "Settings"), "settings"}, {Tx.t("关于", "About"), "info"}};
    static final int TAB_FILES = 0;
    static final int TAB_EMBY = 1;
    static final int TAB_SETTINGS = 2;
    private static final long QUIT_CONFIRM_MS = 4000;
    private static final long TICK_MS = 500;

    PlayerEngine engine;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout rail;
    private TextView titleView;
    private FrameLayout tabHost;
    private FrameLayout subHost;
    private FrameLayout playerHost;
    private MiniBar mini;
    private final Page[] tabs = new Page[nav.length];
    private int tab = -1;
    private final List<Page> stack = new ArrayList<>();
    private final List<View> stackViews = new ArrayList<>();
    private PlayerPage player;
    private boolean playerShown;
    private boolean started;
    private long quitAskedAt = -QUIT_CONFIRM_MS;

    private final PlayerEngine.Listener engineListener = new PlayerEngine.Listener() {
        @Override
        public void onPlayerReplaced(ExoPlayer p) {
            if (player != null) player.onPlayerReplaced(p);
        }

        @Override
        public void onChanged() {
            Page top = top();
            if (top != null) top.onEngineChanged();
            if (player != null) player.onEngineChanged();
            mini.refresh();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (playerShown && player != null) {
                player.onTick();
            } else {
                Page top = top();
                if (top != null) top.onTick();
                mini.tick();
            }
            ui.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        engine = PlayerEngine.get(this);
        setContentView(buildUi());
        engine.addListener(engineListener);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        showTab(TAB_FILES);
        openFromIntent(getIntent());
        if (!playerShown && engine.current() != null && engine.hasVideo() && engine.player().isPlaying()) showPlayer();
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout frame = Ui.column(this);
        FrameLayout bar = new FrameLayout(this);
        titleView = Ui.bold(this, "", Ui.TITLE, Ui.TEXT);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        tl.leftMargin = dp(Ui.GUTTER);
        bar.addView(titleView, tl);
        frame.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Ui.TITLE_BAR)));

        LinearLayout main = Ui.row(this);
        main.setGravity(Gravity.TOP);
        main.setPadding(dp(Ui.GAP), 0, dp(Ui.GAP), dp(Ui.GAP));
        rail = Ui.column(this);
        for (int i = 0; i < nav.length; i++) {
            int index = i;
            LinearLayout item = Ui.row(this);
            item.setMinimumHeight(dp(Ui.TOUCH));
            item.setPadding(dp(20), 0, dp(12), 0);
            item.addView(Ui.icon(this, nav[i][1], 24, Ui.TEXT2));
            TextView label = Ui.text(this, nav[i][0], Ui.BODY, Ui.TEXT);
            label.setPadding(dp(16), 0, 0, 0);
            item.addView(label);
            item.setOnClickListener(v -> showTab(index));
            LinearLayout.LayoutParams lp = Ui.fillWidth();
            lp.bottomMargin = dp(8);
            rail.addView(item, lp);
        }
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout quit = Ui.row(this);
        quit.setMinimumHeight(dp(Ui.TOUCH));
        quit.setPadding(dp(20), 0, dp(12), 0);
        quit.setBackground(Ui.pressable(this, 0x00000000, Ui.RADIUS));
        quit.addView(Ui.icon(this, "power", 24, Ui.TEXT2));
        TextView quitLabel = Ui.text(this, Tx.t("退出", "Quit"), Ui.BODY, Ui.TEXT);
        quitLabel.setPadding(dp(16), 0, 0, 0);
        quit.addView(quitLabel);
        quit.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - quitAskedAt < QUIT_CONFIRM_MS) {
                quitLabel.setText(Tx.t("正在退出…", "Quitting…"));
                AppExit.run(this);
                return;
            }
            quitAskedAt = now;
            quitLabel.setText(Tx.t("确认退出", "Tap again to quit"));
            quitLabel.setTextColor(Ui.ENERGY_TEXT);
            quit.postDelayed(() -> {
                if (SystemClock.elapsedRealtime() - quitAskedAt >= QUIT_CONFIRM_MS && !AppExit.leaving()) {
                    quitLabel.setText(Tx.t("退出", "Quit"));
                    quitLabel.setTextColor(Ui.TEXT);
                }
            }, QUIT_CONFIRM_MS + 100);
        });
        rail.addView(quit, Ui.fillWidth());
        main.addView(rail, new LinearLayout.LayoutParams(dp(Ui.LIST_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        tabHost = new FrameLayout(this);
        tabHost.setBackground(Ui.round(this, Ui.SURFACE, Ui.RADIUS));
        tabHost.setClipToOutline(true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        hl.leftMargin = dp(Ui.GAP);
        main.addView(tabHost, hl);
        frame.addView(main, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = Ui.column(this);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(frame, Ui.fill());
        subHost = new FrameLayout(this);
        subHost.setVisibility(View.GONE);
        stage.addView(subHost, Ui.fill());
        body.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mini = new MiniBar();
        body.addView(mini.view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
        root.addView(body, Ui.fill());

        playerHost = new FrameLayout(this);
        playerHost.setBackgroundColor(0xFF000000);
        playerHost.setVisibility(View.GONE);
        root.addView(playerHost, Ui.fill());
        mini.refresh();
        return root;
    }

    void showTab(int index) {
        while (!stack.isEmpty()) popSub();
        if (tabs[index] == null) tabs[index] = newTab(index);
        if (tab == index) {
            tabs[index].onShow();
            return;
        }
        if (tab >= 0 && tabs[tab] != null) {
            tabs[tab].onHide();
            tabs[tab].view().setVisibility(View.GONE);
        }
        tab = index;
        Page p = tabs[index];
        if (p.view().getParent() == null) tabHost.addView(p.view(), Ui.fill());
        p.view().setVisibility(View.VISIBLE);
        titleView.setText(p.title());
        for (int i = 0; i < nav.length; i++) {
            LinearLayout item = (LinearLayout) rail.getChildAt(i);
            boolean on = i == index;
            item.setBackground(on ? Ui.round(this, Ui.SUNKEN, Ui.RADIUS) : Ui.pressable(this, 0x00000000, Ui.RADIUS));
            ((Icons) ((ImageView) item.getChildAt(0)).getDrawable()).setColor(on ? Ui.TEXT : Ui.TEXT2);
        }
        if (started) p.onShow();
    }

    private Page newTab(int index) {
        switch (index) {
            case TAB_FILES:
                return new FilesPage(this, null, Tx.t("文件", "Files"));
            case TAB_EMBY:
                return new EmbyHomePage(this);
            case TAB_SETTINGS:
                return new SettingsPage(this);
            default:
                return new AboutPage(this);
        }
    }

    void open(Page page) {
        Page under = top();
        if (under != null) under.onHide();
        LinearLayout wrap = Ui.column(this);
        wrap.setBackgroundColor(Ui.BG);
        wrap.setClickable(true);
        FrameLayout bar = new FrameLayout(this);
        LinearLayout left = Ui.row(this);
        left.addView(Ui.iconButton(this, "back", Tx.t("返回", "Back"), false, this::handleBack));
        TextView t = Ui.oneLine(Ui.bold(this, page.title(), Ui.TITLE, Ui.TEXT));
        t.setPadding(dp(16), 0, 0, 0);
        left.addView(t);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        ll.leftMargin = dp(10);
        bar.addView(left, ll);
        wrap.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Ui.TITLE_BAR)));
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.round(this, Ui.SURFACE, Ui.RADIUS));
        card.setClipToOutline(true);
        card.addView(page.view(), Ui.fill());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cl.setMargins(dp(Ui.GAP), 0, dp(Ui.GAP), dp(Ui.GAP));
        wrap.addView(card, cl);
        subHost.addView(wrap, Ui.fill());
        subHost.setVisibility(View.VISIBLE);
        stack.add(page);
        stackViews.add(wrap);
        page.onShow();
    }

    private void popSub() {
        int last = stack.size() - 1;
        Page p = stack.remove(last);
        p.onHide();
        subHost.removeView(stackViews.remove(last));
        if (stack.isEmpty()) subHost.setVisibility(View.GONE);
    }

    private Page top() {
        if (!stack.isEmpty()) return stack.get(stack.size() - 1);
        return tab >= 0 ? tabs[tab] : null;
    }

    void handleBack() {
        if (playerShown) {
            if (player.handleBack()) return;
            hidePlayer();
        } else if (!stack.isEmpty()) {
            popSub();
            Page top = top();
            if (top != null) top.onShow();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        handleBack();
    }

    void showPlayer() {
        if (player == null) {
            player = new PlayerPage(this);
            playerHost.addView(player.view(), Ui.fill());
        }
        if (playerShown) return;
        playerShown = true;
        playerHost.setVisibility(View.VISIBLE);
        Page top = top();
        if (top != null) top.onHide();
        if (started) player.onShow();
    }

    void hidePlayer() {
        if (!playerShown) return;
        playerShown = false;
        player.onHide();
        playerHost.setVisibility(View.GONE);
        mini.refresh();
        Page top = top();
        if (top != null) top.onShow();
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        engine.setInterfaceShown(true);
        if (playerShown) {
            player.onShow();
        } else {
            Page top = top();
            if (top != null) top.onShow();
        }
        mini.refresh();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onStop() {
        started = false;
        engine.setInterfaceShown(false);
        ui.removeCallbacks(ticker);
        engine.savePosition();
        if (playerShown) {
            player.onHide();
        } else {
            Page top = top();
            if (top != null) top.onHide();
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromIntent(intent);
    }

    private void openFromIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri u = intent.getData();
        if (u == null || !"file".equals(u.getScheme()) || u.getPath() == null) return;
        engine.open(new File(u.getPath()));
        showPlayer();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(ticker);
        engine.removeListener(engineListener);
        if (player != null) player.release();
        super.onDestroy();
    }

    int dp(float v) {
        return Ui.dp(this, v);
    }

    private final class MiniBar {
        final FrameLayout view;
        private final TextView title;
        private final TextView sub;
        private final ImageView playPause;
        private final View progress;
        private boolean playingIcon;

        MiniBar() {
            ShellActivity c = ShellActivity.this;
            view = new FrameLayout(c);
            view.setBackgroundColor(Ui.SURFACE);
            View line = new View(c);
            line.setBackgroundColor(Ui.LINE);
            view.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
            progress = new View(c);
            progress.setBackgroundColor(Ui.ENERGY);
            view.addView(progress, new FrameLayout.LayoutParams(0, dp(3), Gravity.TOP | Gravity.START));

            LinearLayout row = Ui.row(c);
            row.setPadding(dp(Ui.GUTTER), dp(3), dp(16), 0);
            FrameLayout art = new FrameLayout(c);
            art.setBackground(Ui.round(c, Ui.SUNKEN, Ui.RADIUS));
            art.addView(Ui.icon(c, "music", 28, Ui.TEXT3), new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            row.addView(art, Ui.sized(c, 56, 56));
            LinearLayout texts = Ui.column(c);
            texts.setPadding(dp(18), 0, dp(18), 0);
            title = Ui.oneLine(Ui.text(c, "", Ui.BODY, Ui.TEXT));
            sub = Ui.oneLine(Ui.text(c, "", Ui.MICRO, Ui.TEXT3));
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(title);
            texts.addView(sub);
            row.addView(texts, Ui.weight(1));
            row.addView(Ui.roundIcon(c, "previous", 60, 28, 0x00000000, Ui.TEXT, () -> engine.previous()));
            playPause = Ui.roundIcon(c, "play", 60, 30, Ui.SUNKEN, Ui.TEXT, () -> {
                Player p = engine.player();
                if (p.isPlaying()) {
                    p.pause();
                } else {
                    if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0);
                    if (p.getPlaybackState() == Player.STATE_IDLE) engine.retry();
                    else p.play();
                }
            });
            LinearLayout.LayoutParams pl = (LinearLayout.LayoutParams) playPause.getLayoutParams();
            pl.leftMargin = dp(8);
            pl.rightMargin = dp(8);
            row.addView(playPause);
            row.addView(Ui.roundIcon(c, "next", 60, 28, 0x00000000, Ui.TEXT, () -> engine.next()));
            row.addView(Ui.roundIcon(c, "stop", 60, 26, 0x00000000, Ui.TEXT2, () -> engine.stopAll()));
            view.addView(row, Ui.fill());
            view.setOnClickListener(v -> showPlayer());
        }

        void refresh() {
            PlayerEngine.Item it = engine.current();
            Player p = engine.player();
            boolean show = it != null && p.getMediaItemCount() > 0 && !playerShown;
            view.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) return;
            List<CueSheet.Track> chapters = engine.chapters();
            int ch = engine.chapterIndex();
            title.setText(ch >= 0 ? chapters.get(ch).label() : it.title);
            sub.setText(ch >= 0 ? it.title : it.subtitle);
            sub.setVisibility(sub.getText().length() == 0 ? View.GONE : View.VISIBLE);
            boolean playing = p.isPlaying();
            if (playing != playingIcon) {
                playingIcon = playing;
                playPause.setImageDrawable(new Icons(playing ? "pause" : "play", Ui.TEXT, ShellActivity.this));
            }
            tick();
        }

        void tick() {
            if (view.getVisibility() != View.VISIBLE) return;
            Player p = engine.player();
            long d = p.getDuration();
            float f = d > 0 ? Math.min(1f, p.getCurrentPosition() / (float) d) : 0f;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progress.getLayoutParams();
            int w = Math.round(view.getWidth() * f);
            if (lp.width != w) {
                lp.width = w;
                progress.setLayoutParams(lp);
            }
            if (!engine.chapters().isEmpty()) {
                int ch = engine.chapterIndex();
                CharSequence want = ch >= 0 ? engine.chapters().get(ch).label() : title.getText();
                if (!want.toString().contentEquals(title.getText())) title.setText(want);
            }
        }
    }
}
