package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.view.View;

abstract class Page {
    protected final ShellActivity shell;
    protected final Context c;
    private View view;

    Page(ShellActivity shell) {
        this.shell = shell;
        this.c = shell;
    }

    abstract String title();

    protected abstract View build();

    final View view() {
        if (view == null) view = build();
        return view;
    }

    final boolean built() {
        return view != null;
    }

    void onShow() {
    }

    void onHide() {
    }

    void onEngineChanged() {
    }

    void onTick() {
    }

    protected int dp(float v) {
        return Ui.dp(c, v);
    }
}
