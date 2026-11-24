package io.github.dts88.openplayer.ui;

import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.dts88.openplayer.Credits;
import io.github.dts88.openplayer.Tx;

final class AboutPage extends Page {

    AboutPage(ShellActivity shell) {
        super(shell);
    }

    @Override
    String title() {
        return Tx.t("关于", "About");
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(c);
        LinearLayout column = Ui.column(c);
        column.setPadding(dp(16), dp(16), dp(16), dp(24));

        LinearLayout card = Ui.card(c);
        card.addView(Ui.settingRow(c, Tx.t("版本", "Version"), "", Ui.text(c, version(), Ui.BODY, Ui.TEXT2)));
        card.addView(Ui.divider(c));
        card.addView(Ui.settingRow(c, Tx.t("作者", "Author"), "", Ui.text(c, Credits.AUTHOR, Ui.BODY, Ui.TEXT2)));
        card.addView(Ui.divider(c));
        card.addView(Ui.settingRow(c, Tx.t("源代码", "Source code"), Credits.SOURCE, null));
        card.addView(Ui.divider(c));
        card.addView(Ui.settingRow(c, Tx.t("许可证", "License"), Credits.licenseNote(), null));
        card.addView(Ui.divider(c));
        card.addView(Ui.settingRow(c, Tx.t("开源组件", "Open-source components"), Tx.t("本应用使用的组件及其许可证。", "Components used by this app and their licenses."),
                Ui.button(c, Tx.t("查看", "View"), false, () -> shell.open(new ComponentsPage(shell)))));
        column.addView(card, Ui.fillWidth());

        TextView intro = Ui.text(c, Credits.intro(), Ui.BODY, Ui.TEXT2);
        intro.setLineSpacing(0, 1.4f);
        intro.setPadding(dp(4), dp(24), dp(4), 0);
        column.addView(intro);

        scroll.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private String version() {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static final class ComponentsPage extends Page {
        ComponentsPage(ShellActivity shell) {
            super(shell);
        }

        @Override
        String title() {
            return Tx.t("开源组件", "Open-source components");
        }

        @Override
        protected View build() {
            ScrollView scroll = new ScrollView(c);
            TextView t = Ui.text(c, Credits.components(), Ui.BODY, Ui.TEXT2);
            t.setLineSpacing(0, 1.4f);
            t.setPadding(dp(Ui.GUTTER), dp(16), dp(Ui.GUTTER), dp(24));
            scroll.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return scroll;
        }
    }
}
