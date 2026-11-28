package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.dts88.openplayer.Tx;
import io.github.dts88.openplayer.emby.EmbyException;
import io.github.dts88.openplayer.emby.EmbyHub;
import io.github.dts88.openplayer.emby.EmbyItem;

import java.util.List;

final class EmbyHomePage extends Page {
    private static final int FORM_WIDTH = 920;
    private static final int LIBRARY_ART_DP = 118;

    private final EmbyHub hub;
    private LinearLayout column;
    private boolean busy;
    private int shown = -1;

    EmbyHomePage(ShellActivity shell) {
        super(shell);
        hub = EmbyHub.get(shell);
    }

    @Override
    String title() {
        return "Emby";
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(c);
        column = Ui.column(c);
        column.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    @Override
    void onShow() {
        if (busy || shown == hub.revision()) return;
        fill("");
    }

    private void fill(String result) {
        shown = hub.revision();
        column.removeAllViews();
        if (hub.signedIn()) {
            fillLibraries();
        } else {
            fillForm(result);
        }
    }

    private void fillLibraries() {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.settingRow(c, Tx.t("已登录", "Signed in"), hub.userName() + "  ·  " + hub.address(),
                Ui.button(c, Tx.t("退出登录", "Sign out"), false, () -> {
                    hub.signOut();
                    Posters.clear(c);
                    fill("");
                })));
        card.addView(Ui.divider(c));
        card.addView(Ui.settingRow(c, Tx.t("画质", "Quality"),
                Tx.t("原画：播放服务器上的原文件。选择码率后，高于该码率的影片由服务器转码后播放，起播与跳转较慢。",
                        "Original plays the file as it is on the server. With a bitrate chosen, videos above it are transcoded by the server, which makes starting and seeking slower."),
                null));
        LinearLayout.LayoutParams steps = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        steps.leftMargin = dp(20);
        steps.bottomMargin = dp(8);
        card.addView(Ui.segmented(c, EmbyHub.qualityLabels(), hub.qualityIndex(), hub::setQualityIndex), steps);
        column.addView(card, Ui.fillWidth());

        LinearLayout find = Ui.row(c);
        EditText term = input(Tx.t("搜索影片、剧集、音乐", "Search films, series and music"), "", false);
        term.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        Runnable go = () -> {
            String text = term.getText().toString().trim();
            if (text.isEmpty()) return;
            hideKeyboard(term);
            shell.open(new EmbyListPage(shell, text));
        };
        term.setOnEditorActionListener((v, action, event) -> {
            go.run();
            return true;
        });
        find.addView(term, Ui.weight(1));
        find.addView(Ui.space(c, 12, 1));
        find.addView(Ui.button(c, Tx.t("搜索", "Search"), false, go));
        LinearLayout.LayoutParams fl = Ui.fillWidth();
        fl.topMargin = dp(14);
        column.addView(find, fl);

        TextView heading = Ui.section(c, Tx.t("媒体库", "Libraries"));
        heading.setPadding(dp(4), dp(18), 0, dp(10));
        column.addView(heading);
        TextView note = Ui.text(c, Tx.t("正在读取…", "Loading…"), Ui.BODY, Ui.TEXT2);
        note.setPadding(dp(4), 0, 0, dp(12));
        column.addView(note);
        LinearLayout list = Ui.column(c);
        column.addView(list, Ui.fillWidth());

        int asked = shown;
        hub.views((views, error) -> {
            if (asked != shown || !built()) return;
            if (error != null || views == null) {
                if (!hub.signedIn()) {
                    fill(message(error));
                    return;
                }
                note.setTextColor(Ui.BAD);
                note.setText(message(error));
                return;
            }
            note.setVisibility(views.isEmpty() ? View.VISIBLE : View.GONE);
            note.setText(Tx.t("此账号下没有媒体库。", "This account has no libraries."));
            addLibraries(list, views);
        });
    }

    private void addLibraries(LinearLayout list, List<EmbyItem> views) {
        for (EmbyItem view : views) {
            LinearLayout row = Ui.row(c);
            row.setPadding(dp(12), dp(10), dp(20), dp(10));
            row.setBackground(Ui.pressable(c, Ui.SUNKEN, Ui.RADIUS));
            FrameLayout frame = new FrameLayout(c);
            frame.setBackground(Ui.round(c, Ui.SURFACE, 6));
            frame.setClipToOutline(true);
            frame.addView(Ui.icon(c, "folder", 28, Ui.TEXT3), new FrameLayout.LayoutParams(dp(28), dp(28),
                    android.view.Gravity.CENTER));
            ImageView art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(art, Ui.fill());
            Posters.into(art, hub.imageUrl(view, dp(LIBRARY_ART_DP) * 2), dp(LIBRARY_ART_DP));
            row.addView(frame, new LinearLayout.LayoutParams(dp(LIBRARY_ART_DP), dp(LIBRARY_ART_DP * 9 / 16)));
            TextView name = Ui.oneLine(Ui.text(c, view.name, Ui.ROW, Ui.TEXT));
            name.setPadding(dp(18), 0, 0, 0);
            row.addView(name, Ui.weight(1));
            row.addView(Ui.icon(c, "chevron", 22, Ui.TEXT3));
            row.setOnClickListener(v -> shell.open(new EmbyListPage(shell, view)));
            LinearLayout.LayoutParams lp = Ui.fillWidth();
            lp.bottomMargin = dp(8);
            list.addView(row, lp);
        }
    }

    private void fillForm(String result) {
        LinearLayout form = Ui.column(c);
        column.addView(form, new LinearLayout.LayoutParams(dp(FORM_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT));

        form.addView(label(Tx.t("服务器地址", "Server address"),
                Tx.t("例如 192.168.1.10:8096 或 https://emby.example.com；未写端口时使用 8096。",
                        "For example 192.168.1.10:8096 or https://emby.example.com; port 8096 is used when none is given.")));
        EditText address = input("192.168.1.10:8096", hub.address(), false);
        form.addView(address, Ui.fillWidth());
        form.addView(label(Tx.t("用户名", "User name"), ""));
        EditText user = input("", "", false);
        form.addView(user, Ui.fillWidth());
        form.addView(label(Tx.t("密码", "Password"), Tx.t("仅用于本次登录，不会保存。", "Used for this sign-in only; it is not stored.")));
        EditText password = input("", "", true);
        form.addView(password, Ui.fillWidth());

        LinearLayout row = Ui.row(c);
        row.setPadding(0, dp(26), 0, 0);
        TextView status = Ui.text(c, result, Ui.BODY, result.isEmpty() ? Ui.TEXT2 : Ui.BAD);
        row.addView(Ui.button(c, Tx.t("连接", "Connect"), true, () -> {
            if (busy) return;
            if (address.getText().toString().trim().isEmpty()) {
                status.setTextColor(Ui.BAD);
                status.setText(Tx.t("请填写服务器地址。", "Enter the server address."));
                return;
            }
            busy = true;
            status.setTextColor(Ui.TEXT2);
            status.setText(Tx.t("正在连接…", "Connecting…"));
            hideKeyboard(password);
            hub.connect(address.getText().toString(), user.getText().toString(), password.getText().toString(),
                    (version, error) -> {
                        busy = false;
                        if (!built()) return;
                        if (error == null) {
                            fill("");
                        } else {
                            status.setTextColor(Ui.BAD);
                            status.setText(message(error));
                        }
                    });
        }));
        status.setPadding(dp(24), 0, 0, 0);
        row.addView(status, Ui.weight(1));
        form.addView(row, Ui.fillWidth());
    }

    private View label(String title, String note) {
        LinearLayout l = Ui.column(c);
        l.setPadding(dp(4), dp(20), 0, dp(10));
        l.addView(Ui.text(c, title, Ui.BODY, Ui.TEXT));
        if (!note.isEmpty()) {
            TextView n = Ui.text(c, note, Ui.MICRO, Ui.TEXT3);
            n.setPadding(0, dp(4), 0, 0);
            l.addView(n);
        }
        return l;
    }

    private EditText input(String hint, String value, boolean password) {
        EditText e = new EditText(c);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.BODY);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.TEXT3);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setBackground(Ui.round(c, Ui.SUNKEN, Ui.RADIUS));
        e.setPadding(dp(20), 0, dp(20), 0);
        e.setMinHeight(dp(Ui.TOUCH));
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    static String message(EmbyException e) {
        String m = e == null ? "" : e.getMessage();
        return m == null || m.isEmpty() ? Tx.t("服务器出错", "The server reported an error") : m;
    }
}
