package io.github.dts88.openplayer;

import java.util.Locale;

final class YuvMatrix {
    static final int BT601 = 0;
    static final int BT709 = 1;
    static final int BT2020 = 2;

    final float[] matrix;
    final float[] offset;
    final String name;

    private YuvMatrix(float[] matrix, float[] offset, String name) {
        this.matrix = matrix;
        this.offset = offset;
        this.name = name;
    }

    static YuvMatrix of(int standard, boolean fullRange, int bitDepth) {
        double kr;
        double kb;
        String std;
        switch (standard) {
            case BT601:
                kr = 0.299;
                kb = 0.114;
                std = "BT.601";
                break;
            case BT2020:
                kr = 0.2627;
                kb = 0.0593;
                std = "BT.2020";
                break;
            default:
                kr = 0.2126;
                kb = 0.0722;
                std = "BT.709";
        }
        double kg = 1 - kr - kb;
        int bits = bitDepth >= 10 ? 10 : 8;
        double max = (1 << bits) - 1;
        double unit = 1 << (bits - 8);
        double ys = fullRange ? 1 : max / (219 * unit);
        double cs = fullRange ? 1 : max / (224 * unit);
        double yo = fullRange ? 0 : 16 * unit / max;
        double co = 128 * unit / max;
        double rv = cs * 2 * (1 - kr);
        double bu = cs * 2 * (1 - kb);
        double gu = -cs * 2 * kb * (1 - kb) / kg;
        double gv = -cs * 2 * kr * (1 - kr) / kg;
        float[] m = {
                (float) ys, (float) ys, (float) ys,
                0f, (float) gu, (float) bu,
                (float) rv, (float) gv, 0f};
        float[] o = {(float) yo, (float) co, (float) co};
        return new YuvMatrix(m, o, std + (fullRange ? " 全范围" : " 有限范围") + "，" + bits + " 位");
    }

    float[] toRgb(float y, float u, float v) {
        float a = y - offset[0];
        float b = u - offset[1];
        float c = v - offset[2];
        return new float[]{
                matrix[0] * a + matrix[3] * b + matrix[6] * c,
                matrix[1] * a + matrix[4] * b + matrix[7] * c,
                matrix[2] * a + matrix[5] * b + matrix[8] * c};
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s（Y×%.4f，R+V×%.4f，G+U×%.4f+V×%.4f，B+U×%.4f）",
                name, matrix[0], matrix[6], matrix[4], matrix[7], matrix[5]);
    }
}
