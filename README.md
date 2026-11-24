# 开源播放器

释放 Zeekr 7X 车机的 21 个扬声器和 7.1.4 声道的播放能力：在没有杜比认证的情况下，将媒体文件里的多声道音轨手动映射到对应的硬件声道，尽量用上车里已有的硬件。

作者：dts88　·　仓库：<https://github.com/dts88/zeekr-open-player>

## 能做什么

- **多声道直出**：车机的媒体通道是 12 路 PCM（7.1.4）。播放器把 AC-3、E-AC-3、DTS、TrueHD、FLAC、LPCM 等音轨解码后，按声道逐一放进这 12 路。
- **声道摆位**：5.1 的环绕声可放在侧面、后面或两组一起；可选顶部上混、立体声转环绕、夜间模式、输出余量；也可下混为立体声。
- **画面处理**：按 Zeekr 7X 中控屏的实测结果，所有影片经 GPU 处理——颜色按 Display P3 换算、加抖动消除色带；HDR10、HDR10+、HLG 与杜比视界映射到屏幕的白。
- **字幕**：内嵌与同名外挂字幕（SRT、ASS / SSA、WebVTT、蓝光图形字幕），ASS 特效渲染，字幕延迟、大小、位置可调。
- **电平**：播放页「声音」面板显示送往车机的各声道电平，哪一路有声一眼可见。
- **文件**：按文件夹浏览 U 盘与车机存储；MKV、MP4、蓝光 M2TS、FLAC、WAV、AIFF、DSD、CUE 分轨等；断点续播。

本应用只播放本地文件。

界面有中文和英文两套：系统语言是中文时显示中文，其余语言显示英文（英文应用名 Open Player）。

## 现状

- 目前还不能控制功放（预计极氪很快会提供 Z-Tuner）。
- 目前没有对不同声道额外做调教：这件事可以很复杂，也可以很难听。

## 主观听感，以及一个想请教的问题

以下是作者在自己车上的主观感受：

- 7.1 声道确实被释放出来了，适合看电影。
- 人声过于靠前，更适合听器乐。
- 细节不足，中高音的爆发力不够。

人声的问题或许还有得调。但细节不足这一点，想请更懂行的朋友给些建议：**这是扬声器、功放的硬件问题，没得救；还是仍有调整的空间？** 欢迎在 Issues 里留言。

## 安装与使用

车机上的应用装在 App Lab 里，在那里本来就能读写 U 盘，本应用不申请任何存储权限。把影片或音乐拷进 U 盘，打开应用，在「文件」里选择即可。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```
./gradlew :app:assembleRelease
```

发布包只含 arm64-v8a；调试包另带 x86_64，供模拟器使用。

## 许可证与署名

本项目内置 FFmpeg 解码库（Jellyfin 构建的 `media3-ffmpeg-decoder`，GPL-3.0），整体按 **GNU 通用公共许可证第 3 版（GPL-3.0）** 发布，全文见 [LICENSE](LICENSE)。

另依据 GPL-3.0 第 7 条 (b) 项附加署名条款，见 [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md)：

> 凡使用本项目的代码、参数或实测结论的程序或网站，必须在其主界面注明本仓库地址 <https://github.com/dts88/zeekr-open-player> 与作者 dts88。

使用的开源组件：

| 组件 | 许可证 |
| --- | --- |
| AndroidX Media3 1.9.0 | Apache License 2.0 |
| Jellyfin media3-ffmpeg-decoder 1.9.0+1（FFmpeg） | GPL-3.0 |
| AndroidX RecyclerView | Apache License 2.0 |
| ass-media 0.5.1（libass、FreeType、FriBidi、HarfBuzz） | MIT / ISC / FTL / LGPL-2.1 / MIT |

Dolby、Dolby Vision、DTS 为各自所有者的商标，此处仅用于说明影片的音轨与画面格式；本应用未获上述公司认证，也不包含、不绕过任何授权机制。Zeekr 为其所有者的商标，本项目与其无隶属关系。

---

## English summary

An open-source local media player for the Zeekr 7X head unit. Without any Dolby certification, it decodes multichannel audio tracks itself and places each channel onto the head unit's 12-channel (7.1.4) PCM media output, so the car's existing 21 speakers are actually used. Video goes through a GPU path tuned to the car's display (Display P3 conversion, dithering, HDR / Dolby Vision / HDR10+ tone mapping).

The interface is in Chinese when the system language is Chinese and in English otherwise; the app is named "Open Player" in English. The Sound panel shows live per-channel levels for the 12 outputs.

Status: the amplifier cannot be controlled yet, and no per-channel tuning is applied. The author's impression: 7.1 is clearly there (good for films); vocals sit too far forward; fine detail and mid-high attack are lacking. Advice from people who know car audio is welcome: is the lack of detail a hardware limit of the speakers and amplifier, or is there room to tune?

License: GPL-3.0 with an attribution term under section 7(b): any program or website using code, parameters or findings from this project must credit this repository and the author dts88 on its main screen. See [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md).
