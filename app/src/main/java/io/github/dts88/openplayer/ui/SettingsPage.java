package io.github.dts88.openplayer.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.dts88.openplayer.PlayerEngine;
import io.github.dts88.openplayer.Tx;

final class SettingsPage extends Page {

    private static final String[] AUDIO_LANGUAGES = {"", "zh", "en", "ja"};
    private final String[] audioLanguageLabels = {Tx.t("跟随影片", "Source default"), Tx.t("中文", "Chinese"), Tx.t("英文", "English"), Tx.t("日文", "Japanese")};
    private static final String[] TEXT_LANGUAGES = {"", "zh", "en"};
    private final String[] textLanguageLabels = {Tx.t("无", "None"), Tx.t("中文", "Chinese"), Tx.t("英文", "English")};
    static String pictureNote() {
        return Tx.t("按 Zeekr 7X 屏幕实测结果处理：颜色按 Display P3 换算，加抖动消除色带，", "Processed for the measured Zeekr 7X display: colours converted to Display P3, dithered against banding, ")
            + Tx.t("HDR 与杜比视界映射到屏幕最亮的白。", "HDR and Dolby Vision mapped to the display's brightest white.");
    }

    static String hdrLevelNote() {
        return Tx.t("HDR 影片映射后的整体亮度。推荐「标准」；画面偏暗时选「偏亮」，高光过曝时选「偏暗」。", "Overall brightness of HDR video after mapping. \"Standard\" is recommended; choose \"Brighter\" if the picture is dark, \"Darker\" if highlights clip.");
    }

    private LinearLayout column;

    SettingsPage(ShellActivity shell) {
        super(shell);
    }

    @Override
    String title() {
        return Tx.t("设置", "Settings");
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(c);
        column = Ui.column(c);
        column.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fill();
        return scroll;
    }

    private void fill() {
        column.removeAllViews();
        PlayerEngine e = shell.engine;

        LinearLayout sound = group(Tx.t("声音", "Sound"));
        SoundControls.addTo(sound, shell, false);

        LinearLayout picture = group(Tx.t("画面", "Picture"));
        picture.addView(Ui.settingRow(c, Tx.t("画面处理", "Processing"), pictureNote(), null));
        picture.addView(Ui.divider(c));
        picture.addView(Ui.settingRow(c, Tx.t("HDR 亮度", "HDR brightness"), hdrLevelNote(),
                Ui.segmented(c, PlayerEngine.hdrLevelLabels(), e.hdrLevelIndex(), e::setHdrLevelIndex)));

        LinearLayout language = group(Tx.t("音轨与字幕", "Audio and subtitles"));
        language.addView(Ui.settingRow(c, Tx.t("默认音轨语言", "Preferred audio language"), Tx.t("优先选择该语言的音轨；同语言时优先无损音轨。", "Tracks in this language are chosen first; among them, lossless tracks first."),
                Ui.segmented(c, audioLanguageLabels, indexOf(AUDIO_LANGUAGES, e.audioLanguage()),
                        i -> e.setAudioLanguage(AUDIO_LANGUAGES[i]))));
        language.addView(Ui.divider(c));
        language.addView(Ui.settingRow(c, Tx.t("默认字幕语言", "Preferred subtitle language"), Tx.t("同名外挂字幕不受此项影响，始终显示。", "External subtitles with the same file name are always shown."),
                Ui.segmented(c, textLanguageLabels, indexOf(TEXT_LANGUAGES, e.textLanguage()),
                        i -> e.setTextLanguage(TEXT_LANGUAGES[i]))));
    }

    private LinearLayout group(String name) {
        TextView t = Ui.section(c, name);
        t.setPadding(dp(4), dp(14), 0, dp(10));
        column.addView(t);
        LinearLayout card = Ui.card(c);
        column.addView(card, Ui.fillWidth());
        return card;
    }

    private static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) return i;
        }
        return 0;
    }

    @Override
    void onShow() {
        if (built()) fill();
    }
}
