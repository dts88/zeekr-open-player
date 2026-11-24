package io.github.dts88.openplayer;

public final class Credits {
    public static final String AUTHOR = "dts88";
    public static final String SOURCE = "github.com/dts88/zeekr-open-player";

    private Credits() {
    }

    public static String licenseNote() {
        return Tx.t("GNU 通用公共许可证第 3 版（GPL-3.0），并附第 7(b) 条署名条款："
                        + "使用本项目的代码、参数或实测结论，须在程序与网站的主界面注明本仓库地址与作者 " + AUTHOR + "。",
                "GNU General Public License v3 (GPL-3.0) with an attribution term under section 7(b): "
                        + "any program or website using code, parameters or measured findings from this project "
                        + "must credit this repository and the author " + AUTHOR + " on its main screen.");
    }

    public static String intro() {
        return Tx.t("在没有杜比认证的条件下，将多声道音轨逐声道映射到 Zeekr 7X 的 7.1.4 共 12 路输出，"
                        + "尽量用上车内已有的扬声器。",
                "Without any Dolby certification, multichannel audio tracks are mapped channel by channel onto the "
                        + "12 outputs (7.1.4) of the Zeekr 7X, to make the most of the speakers already in the car.");
    }

    public static String components() {
        return Tx.t("本应用内置 FFmpeg 解码库（Jellyfin 构建的 media3-ffmpeg-decoder），"
                        + "许可证为 GPL-3.0，因此本应用按 GPL-3.0 发布。\n\n"
                        + "使用的组件：\n",
                "This app bundles the FFmpeg decoder (media3-ffmpeg-decoder built by Jellyfin), licensed under "
                        + "GPL-3.0; the app is therefore released under GPL-3.0.\n\n"
                        + "Components:\n")
                + "· AndroidX Media3 1.9.0: Apache License 2.0\n"
                + "· Jellyfin media3-ffmpeg-decoder 1.9.0+1 (FFmpeg): GPL-3.0\n"
                + "· AndroidX RecyclerView: Apache License 2.0\n"
                + "· ass-media 0.5.1: MIT; libass: ISC; FreeType: FTL; FriBidi: LGPL-2.1; HarfBuzz: MIT\n\n"
                + Tx.t("DSD 转 PCM 的低通滤波器，以及蓝光 m2ts、图形字幕、AIFF、DSD、CUE 的解析为本项目自行实现。\n\n"
                        + "杜比、Dolby Vision、DTS 为各自所有者的商标，仅用于说明影片的音轨与画面格式；"
                        + "本应用未获上述公司认证。",
                "The DSD-to-PCM low-pass filter and the parsers for Blu-ray m2ts, image subtitles, AIFF, DSD and "
                        + "CUE are this project's own work.\n\n"
                        + "Dolby, Dolby Vision and DTS are trademarks of their respective owners, used here only to "
                        + "describe audio and picture formats; this app is not certified by those companies.");
    }
}
