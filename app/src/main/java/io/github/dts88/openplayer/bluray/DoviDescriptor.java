package io.github.dts88.openplayer.bluray;

import java.util.Locale;

final class DoviDescriptor {
    static final int TAG = 0xB0;

    final int versionMajor;
    final int versionMinor;
    final int profile;
    final int level;
    final boolean rpu;
    final boolean el;
    final boolean bl;
    final int compatibilityId;

    private DoviDescriptor(int versionMajor, int versionMinor, int profile, int level, boolean rpu, boolean el,
                           boolean bl, int compatibilityId) {
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.profile = profile;
        this.level = level;
        this.rpu = rpu;
        this.el = el;
        this.bl = bl;
        this.compatibilityId = compatibilityId;
    }

    static DoviDescriptor find(byte[] descriptors) {
        if (descriptors == null) return null;
        int p = 0;
        while (p + 2 <= descriptors.length) {
            int tag = descriptors[p] & 0xFF;
            int length = descriptors[p + 1] & 0xFF;
            int body = p + 2;
            if (body + length > descriptors.length) return null;
            if (tag == TAG && length >= 4) return parse(descriptors, body, length);
            p = body + length;
        }
        return null;
    }

    private static DoviDescriptor parse(byte[] b, int p, int length) {
        int major = b[p] & 0xFF;
        int minor = b[p + 1] & 0xFF;
        int bits = ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
        int profile = bits >> 9;
        int level = (bits >> 3) & 0x3F;
        boolean rpu = (bits & 0x4) != 0;
        boolean el = (bits & 0x2) != 0;
        boolean bl = (bits & 0x1) != 0;
        int q = p + 4 + (bl ? 0 : 2);
        int compat = q < p + length ? (b[q] & 0xFF) >> 4 : -1;
        return new DoviDescriptor(major, minor, profile, level, rpu, el, bl, compat);
    }

    String describe() {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "杜比视界 P%d，级别 %d（描述符 %d.%d）", profile, level,
                versionMajor, versionMinor));
        s.append("，").append(rpu ? "有" : "没有").append(" RPU");
        s.append("，").append(el ? "有" : "没有").append("增强层");
        s.append("，").append(bl ? "有" : "没有").append("基础层");
        if (compatibilityId >= 0) s.append("，兼容 ID ").append(compatibilityId).append(compatibilityText());
        return s.toString();
    }

    String playbackNote() {
        if (!bl) return "这一路只有增强层，单独放不出画面";
        switch (profile) {
            case 5: return "当普通 HEVC 放：P5 没有兼容层，颜色会偏绿偏紫";
            case 7: return "当普通 HEVC 放 HDR10 基础层（颜色对，少了逐帧映射）";
            case 4: return "当普通 HEVC 放 SDR 基础层（颜色对，少了逐帧映射）";
            case 8:
                if (compatibilityId == 0) return "当普通 HEVC 放：兼容 ID 0，没有兼容层，颜色会不对";
                return "当普通 HEVC 放基础层" + (compatibilityId > 0 ? compatibilityText() : "（HDR10 / SDR / HLG）");
            default: return "当普通 HEVC 放基础层";
        }
    }

    private String compatibilityText() {
        switch (compatibilityId) {
            case 0: return "（没有兼容层：不经杜比视界处理会偏绿偏紫）";
            case 1: return "（HDR10）";
            case 2: return "（SDR）";
            case 4: return "（HLG）";
            case 6: return "（蓝光 HDR10）";
            default: return "";
        }
    }
}
