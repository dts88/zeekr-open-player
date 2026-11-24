package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.Tx;

final class SoundControls {
    private SoundControls() {
    }

    static void addTo(LinearLayout parent, ShellActivity shell, boolean stacked) {
        Context c = shell;
        PlayerEngine e = shell.engine;

        add(parent, c, stacked, Tx.t("输出方式", "Output mode"), Tx.t("多声道直出：各声道分别送至对应扬声器。下混：混为立体声后输出。", "Direct: each channel goes to its own speakers. Downmix: mixed to stereo."),
                Ui.segmented(c, PlayerEngine.modeLabels(), e.modeIndex(), e::setModeIndex), false);

        add(parent, c, stacked, Tx.t("5.1 环绕声位置", "5.1 surround placement"), Tx.t("5.0 / 5.1 / 6.1 影片仅有一对环绕声道，可分配至侧面、后面或两组扬声器。", "5.0 / 5.1 / 6.1 sources have one surround pair; send it to the side, the rear, or both speaker groups."),
                Ui.segmented(c, PlayerEngine.placementLabels(), e.placementIndex(), e::setPlacementIndex), true);

        int[] upmix = PlayerEngine.upmixChoices();
        String[] upmixLabels = new String[upmix.length + 1];
        upmixLabels[0] = Tx.t("关", "Off");
        int upmixSelected = 0;
        for (int i = 0; i < upmix.length; i++) {
            upmixLabels[i + 1] = "−" + upmix[i] + " dB";
            if (upmix[i] == e.upmixDb()) upmixSelected = i + 1;
        }
        add(parent, c, stacked, Tx.t("顶部上混", "Height upmix"), Tx.t("影片无顶部声道时，将部分声音分配至顶部扬声器。", "Sends part of the sound to the height speakers when the source has no height channels."),
                Ui.segmented(c, upmixLabels, upmixSelected, i -> e.setUpmix(i == 0 ? 0 : upmix[i - 1])), true);

        add(parent, c, stacked, Tx.t("夜间模式", "Night mode"), Tx.t("压缩动态范围：降低大音量，提升小音量。", "Compresses dynamic range: loud parts quieter, quiet parts louder."),
                Ui.toggle(c, e.night(), e::setNight), true);

        int[] room = PlayerEngine.headroomChoices();
        String[] roomLabels = new String[room.length];
        int roomSelected = 0;
        for (int i = 0; i < room.length; i++) {
            roomLabels[i] = room[i] == 0 ? "0" : "−" + room[i] + " dB";
            if (room[i] == e.headroomDb()) roomSelected = i;
        }
        add(parent, c, stacked, Tx.t("输出余量", "Headroom"), Tx.t("整体降低输出电平。多声道影片大音量失真时，可选 −3 dB 或 −6 dB。", "Lowers the overall output level. Try −3 dB or −6 dB if loud multichannel passages distort."),
                Ui.segmented(c, roomLabels, roomSelected, i -> e.setHeadroom(room[i])), true);

        add(parent, c, stacked, Tx.t("立体声转环绕", "Stereo to surround"), Tx.t("将立体声扩展至中置与环绕扬声器，前左、前右保持不变。默认关闭。", "Extends stereo to the centre and surround speakers; front left and right stay unchanged. Off by default."),
                Ui.toggle(c, e.stereoSurround(), e::setStereoSurround), true);
        if (!stacked) {
            add(parent, c, false, Tx.t("立体声按 12 声道输出", "Stereo as 12 channels"), Tx.t("仅前左、前右发声，用于对比。默认按 2 声道输出。", "Only front left and right play; for comparison. Stereo is sent as 2 channels by default."),
                    Ui.toggle(c, e.stereoWide(), e::setStereoWide), true);
        }
    }

    private static void add(LinearLayout parent, Context c, boolean stacked, String title, String note, View control,
                            boolean divider) {
        if (divider) parent.addView(Ui.divider(c));
        if (!stacked) {
            parent.addView(Ui.settingRow(c, title, note, control));
            return;
        }
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 24), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 16));
        LinearLayout head = Ui.row(c);
        head.addView(Ui.text(c, title, Ui.BODY, Ui.TEXT), Ui.weight(1));
        boolean inline = control.getLayoutParams() != null && control.getLayoutParams().width > 0
                && control.getLayoutParams().width < Ui.dp(c, 120);
        if (inline) head.addView(control);
        box.addView(head, Ui.fillWidth());
        TextView n = Ui.text(c, note, Ui.MICRO, Ui.TEXT3);
        n.setPadding(0, Ui.dp(c, 4), 0, 0);
        box.addView(n);
        if (!inline) {
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.topMargin = Ui.dp(c, 12);
            box.addView(control, lp);
        }
        parent.addView(box, Ui.fillWidth());
    }
}
