package io.github.dts88.openplayer;

import java.util.Locale;

public final class Tx {
    private Tx() {
    }

    public static boolean chinese() {
        return "zh".equals(Locale.getDefault().getLanguage());
    }

    public static String t(String zh, String en) {
        return chinese() ? zh : en;
    }
}
