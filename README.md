# Open Player

Open Player (开源播放器) unlocks the 21 speakers and the 7.1.4 playback capability of the Zeekr 7X head unit. Without any Dolby certification, it maps the multichannel audio tracks in media files onto the matching hardware channels by hand, to make the most of the hardware already in the car.

Author: dts88 · Repository: <https://github.com/dts88/zeekr-open-player>

[中文说明](#中文说明)

## What it does

- **Multichannel output**: the head unit's media output is 12 channels of PCM (7.1.4). The player decodes AC-3, E-AC-3, DTS, TrueHD, FLAC, LPCM and similar tracks and places each channel onto those 12 outputs.
- **Channel placement**: the surrounds of a 5.1 track can go to the sides, the rear, or both; optional top upmix, stereo-to-surround, night mode and output headroom; downmix to stereo is available too.
- **Picture**: based on measurements of the Zeekr 7X centre display, every video goes through the GPU. Colours are converted to Display P3 and dithered against banding; HDR10, HDR10+, HLG and Dolby Vision are mapped to the display's white.
- **Subtitles**: embedded and same-name external subtitles (SRT, ASS / SSA, WebVTT, Blu-ray image subtitles), ASS effects rendered, adjustable delay, size and position.
- **Levels**: the Sound panel on the player page shows the level of each channel sent to the head unit, so it is clear at a glance which outputs carry sound.
- **Files**: browse USB drives and the head unit's storage by folder; MKV, MP4, Blu-ray M2TS, FLAC, WAV, AIFF, DSD, CUE sheets and more; resume from where playback stopped.

The app plays local files only.

The interface comes in Chinese and English: Chinese when the system language is Chinese, English otherwise.

## Status

- The app does not try to control the amplifier. The solutions published online so far appear to be software-side shaping: the audio is pre-processed before it reaches the amplifier, which is not an actual adjustment of what the amplifier hardware can output. Software shaping only makes the amplifier's result worse.
- No extra tuning is applied per channel: that work can get very complicated, and it can also end up sounding bad.

## Listening impressions, and a question

These are the author's subjective impressions in their own car:

- The 7.1 channels are clearly there, which suits films.
- Vocals sit too far forward; the system suits instrumental music better.
- Fine detail is lacking, and the mid-high range lacks attack.

The vocals can perhaps still be tuned. On the lack of detail, advice from people who know car audio would be welcome: **is this a hardware limit of the speakers and the amplifier that cannot be fixed, or is there still room to tune?** Please leave a note in Issues.

## Install and use

Apps on the head unit are installed in App Lab, where USB drives are already readable and writable, so this app asks for no storage permission. Copy films or music to a USB drive, open the app and pick them under Files.

## Build

JDK 17 and the Android SDK (compileSdk 36) are required.

```
./gradlew :app:assembleRelease
```

The release build contains arm64-v8a only; the debug build adds x86_64 for the emulator.

## License and attribution

This project bundles the FFmpeg decoder (`media3-ffmpeg-decoder` built by Jellyfin, GPL-3.0) and is released as a whole under the **GNU General Public License, version 3 (GPL-3.0)**. The full text is in [LICENSE](LICENSE).

An attribution term is added under section 7(b) of GPL-3.0, see [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md):

> Any program or website that uses code, parameters or measured findings from this project must state this repository, <https://github.com/dts88/zeekr-open-player>, and the author dts88 on its main screen.

Open-source components:

| Component | License |
| --- | --- |
| AndroidX Media3 1.9.0 | Apache License 2.0 |
| Jellyfin media3-ffmpeg-decoder 1.9.0+1 (FFmpeg) | GPL-3.0 |
| AndroidX RecyclerView | Apache License 2.0 |
| ass-media 0.5.1 (libass, FreeType, FriBidi, HarfBuzz) | MIT / ISC / FTL / LGPL-2.1 / MIT |

Dolby, Dolby Vision and DTS are trademarks of their respective owners and are used here only to describe the audio and picture formats of media files; this app is not certified by those companies and neither contains nor bypasses any licensing mechanism. Zeekr is a trademark of its owner; this project is not affiliated with it.

---

## 中文说明

开源播放器（Open Player）释放 Zeekr 7X 车机的 21 个扬声器和 7.1.4 声道的播放能力：在没有杜比认证的情况下，将媒体文件里的多声道音轨手动映射到对应的硬件声道，尽量用上车里已有的硬件。

**能做什么**

- **多声道直出**：车机的媒体通道是 12 路 PCM（7.1.4）。播放器把 AC-3、E-AC-3、DTS、TrueHD、FLAC、LPCM 等音轨解码后，按声道逐一放进这 12 路。
- **声道摆位**：5.1 的环绕声可放在侧面、后面或两组一起；可选顶部上混、立体声转环绕、夜间模式、输出余量；也可下混为立体声。
- **画面处理**：按 Zeekr 7X 中控屏的实测结果，所有影片经 GPU 处理——颜色按 Display P3 换算、加抖动消除色带；HDR10、HDR10+、HLG 与杜比视界映射到屏幕的白。
- **字幕**：内嵌与同名外挂字幕（SRT、ASS / SSA、WebVTT、蓝光图形字幕），ASS 特效渲染，字幕延迟、大小、位置可调。
- **电平**：播放页「声音」面板显示送往车机的各声道电平，哪一路有声一眼可见。
- **文件**：按文件夹浏览 U 盘与车机存储；MKV、MP4、蓝光 M2TS、FLAC、WAV、AIFF、DSD、CUE 分轨等；断点续播。

本应用只播放本地文件。系统语言是中文时界面显示中文，其余语言显示英文。

**现状**

- 并不试图控制功放。目前网上公布的方案似乎是软件方面的修饰，即对传入功放前的音频进行预处理，并非对功放硬件输出能力的实际调整。软件修饰只会让功放产生的效果变得更加糟糕。
- 目前没有对不同声道额外做调教：这件事可以很复杂，也可以很难听。

**主观听感，以及一个想请教的问题**

以下是作者在自己车上的主观感受：

- 7.1 声道确实被释放出来了，适合看电影。
- 人声过于靠前，更适合听器乐。
- 细节不足，中高音的爆发力不够。

人声的问题或许还有得调。但细节不足这一点，想请更懂行的朋友给些建议：**这是扬声器、功放的硬件问题，没得救；还是仍有调整的空间？** 欢迎在 Issues 里留言。

**安装与使用**

车机上的应用装在 App Lab 里，在那里本来就能读写 U 盘，本应用不申请任何存储权限。把影片或音乐拷进 U 盘，打开应用，在「文件」里选择即可。

**许可证与署名**

本项目整体按 GNU 通用公共许可证第 3 版（GPL-3.0）发布，并依据第 7 条 (b) 项附加署名条款，见 [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md)：

> 凡使用本项目的代码、参数或实测结论的程序或网站，必须在其主界面注明本仓库地址 <https://github.com/dts88/zeekr-open-player> 与作者 dts88。

Dolby、Dolby Vision、DTS 为各自所有者的商标，此处仅用于说明影片的音轨与画面格式；本应用未获上述公司认证，也不包含、不绕过任何授权机制。Zeekr 为其所有者的商标，本项目与其无隶属关系。
