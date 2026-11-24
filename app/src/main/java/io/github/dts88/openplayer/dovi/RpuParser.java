package io.github.dts88.openplayer.dovi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RpuParser {
    private static final int MAX_ID = 15;
    private static final int MAX_EXT_BLOCKS = 255;
    private static final long MAX_COEF_INT = 1L << 30;
    private static final int SECOND_LIST_MIN_BITS = 56;

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i << 24;
            for (int k = 0; k < 8; k++) {
                c = (c & 0x80000000) != 0 ? (c << 1) ^ 0x04C11DB7 : c << 1;
            }
            CRC_TABLE[i] = c;
        }
    }

    private final Rpu.Mapping[] seen = new Rpu.Mapping[MAX_ID + 1];
    private Rpu.Color lastColor;

    public void reset() {
        Arrays.fill(seen, null);
        lastColor = null;
    }

    public Rpu parse(RpuNal.Result found) throws RpuException {
        if (found == null || found.rpuLength <= 0) {
            throw new RpuException(RpuException.TRUNCATED, "这一帧没有 RPU");
        }
        return parse(found.rpu, 0, found.rpuLength);
    }

    public Rpu parse(byte[] data) throws RpuException {
        if (data == null) throw new RpuException(RpuException.INVALID, "没有数据");
        return parse(data, 0, data.length);
    }

    public Rpu parse(byte[] data, int offset, int length) throws RpuException {
        if (data == null || offset < 0 || length < 0 || offset > data.length - length) {
            throw new RpuException(RpuException.INVALID, "数据范围不对");
        }
        try {
            return read(data, offset, length);
        } catch (RuntimeException e) {
            throw new RpuException(RpuException.INTERNAL, "读 RPU 时出了意外：" + e, e);
        }
    }

    public Rpu parseNal(byte[] nal, int offset, int length) throws RpuException {
        if (nal == null || offset < 0 || length < 0 || offset > nal.length - length) {
            throw new RpuException(RpuException.INVALID, "数据范围不对");
        }
        int p = offset;
        int end = offset + length;
        while (p < end && nal[p] == 0) p++;
        if (p < end && nal[p] == 1) p++;
        if (end - p >= 2 && (nal[p] & 0xFF) == 0x7C && nal[p + 1] == 1) p += 2;
        byte[] clean = new byte[end - p];
        int n = RpuNal.unescape(ByteBuffer.wrap(nal), p, end, clean);
        return parse(clean, 0, n);
    }

    public static long crc32(byte[] data, int from, int to) {
        int crc = 0xFFFFFFFF;
        for (int i = from; i < to; i++) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ data[i]) & 0xFF];
        }
        return crc & 0xFFFFFFFFL;
    }

    private Rpu read(byte[] data, int offset, int length) throws RpuException {
        int end = offset + length;
        while (end > offset && data[end - 1] == 0) end--;
        if (end - offset < 7) {
            throw new RpuException(RpuException.TRUNCATED, "RPU 太短：" + (end - offset) + " 字节");
        }
        if ((data[end - 1] & 0xFF) != 0x80) {
            throw new RpuException(RpuException.INVALID, "RPU 的最后一个字节应该是 0x80");
        }
        int crcAt = end - 5;
        long computed = crc32(data, offset + 1, crcAt);
        long written = ((data[crcAt] & 0xFFL) << 24) | ((data[crcAt + 1] & 0xFFL) << 16)
                | ((data[crcAt + 2] & 0xFFL) << 8) | (data[crcAt + 3] & 0xFFL);
        if (computed != written) {
            throw new RpuException(RpuException.CRC, "RPU 的 CRC 不对：写的是 " + Long.toHexString(written)
                    + "，算出来是 " + Long.toHexString(computed));
        }

        BitReader r = new BitReader(data, offset, end - offset);
        if (r.u(8) != 0x19) {
            throw new RpuException(RpuException.INVALID, "RPU 应该从 0x19 开始");
        }
        Rpu.Header header = readHeader(r);

        Rpu.Mapping mapping;
        if (header.usePrevVdrRpu) {
            mapping = seen[header.prevVdrRpuId];
            if (mapping == null) mapping = seen[0];
            if (mapping == null) {
                throw new RpuException(RpuException.MISSING_REFERENCE,
                        "RPU 说曲线沿用 " + header.prevVdrRpuId + " 号，可是那个 RPU 没见过");
            }
        } else {
            mapping = readMapping(r, header);
        }

        Rpu.Dm dm = header.vdrDmMetadataPresent ? readDm(r, header) : null;

        r.alignZero("rpu_alignment_zero_bit");
        if (r.bitsLeft() < 40) {
            throw new RpuException(RpuException.INVALID, "RPU 的内容比它的长度多");
        }
        byte[] remaining = r.bytes((r.bitsLeft() - 40) / 8);

        if (!header.usePrevVdrRpu) seen[mapping.vdrRpuId] = mapping;
        if (dm != null && !dm.compressed) lastColor = dm.color;
        return new Rpu(header, mapping, header.usePrevVdrRpu, dm, remaining, written);
    }

    private static Rpu.Header readHeader(BitReader r) throws RpuException {
        Rpu.Header.Builder h = new Rpu.Header.Builder();
        h.rpuType = r.u(6);
        if (h.rpuType != 2) {
            throw new RpuException(RpuException.UNSUPPORTED, "rpu_type = " + h.rpuType + "，只认 2");
        }
        h.rpuFormat = r.u(11);
        h.vdrRpuProfile = r.u(4);
        h.vdrRpuLevel = r.u(4);
        if (!r.bit()) {
            throw new RpuException(RpuException.UNSUPPORTED, "RPU 没带 vdr_seq_info");
        }
        h.chromaResamplingExplicitFilter = r.bit();
        h.coefficientDataType = r.u(2);
        if (h.coefficientDataType == 0) {
            h.coefficientLog2Denom = r.ue("coefficient_log2_denom", 32);
        } else if (h.coefficientDataType == 1) {
            h.coefficientLog2Denom = 0;
        } else {
            throw new RpuException(RpuException.INVALID, "coefficient_data_type = " + h.coefficientDataType);
        }
        h.vdrRpuNormalizedIdc = r.u(2);
        h.blVideoFullRange = r.bit();
        if ((h.rpuFormat & 0x700) != 0) {
            throw new RpuException(RpuException.UNSUPPORTED, "rpu_format = " + h.rpuFormat + "，这种头不认识");
        }
        h.blBitDepth = 8 + r.ue("bl_bit_depth_minus8", 8);
        long el = r.ue();
        if ((el & 0xFF) > 8 || (el >> 8) > 0xFF) {
            throw new RpuException(RpuException.INVALID, "el_bit_depth_minus8 = " + el);
        }
        h.elBitDepth = 8 + (int) (el & 0xFF);
        h.extMappingIdc04 = (int) (el >> 8) & 0x1F;
        h.extMappingIdc57 = (int) (el >> 13);
        h.vdrBitDepth = 8 + r.ue("vdr_bit_depth_minus8", 8);
        h.spatialResamplingFilter = r.bit();
        h.reservedZero3Bits = r.u(3);
        h.elSpatialResamplingFilter = r.bit();
        h.disableResidual = r.bit();
        h.vdrDmMetadataPresent = r.bit();
        h.usePrevVdrRpu = r.bit();
        h.prevVdrRpuId = h.usePrevVdrRpu ? r.ue("prev_vdr_rpu_id", MAX_ID) : 0;
        if (h.reservedZero3Bits > 1) {
            throw new RpuException(RpuException.UNSUPPORTED,
                    "显示管理数据的压缩方式 " + h.reservedZero3Bits + " 不认识");
        }
        return h.build();
    }

    private static Rpu.Mapping readMapping(BitReader r, Rpu.Header h) throws RpuException {
        int vdrRpuId = r.ue("vdr_rpu_id", MAX_ID);
        int colorSpace = r.ue("mapping_color_space", Integer.MAX_VALUE);
        int chromaFormat = r.ue("mapping_chroma_format_idc", Integer.MAX_VALUE);

        int[][] pivots = new int[3][];
        for (int c = 0; c < 3; c++) {
            int count = r.ue("num_pivots_minus2", 7) + 2;
            pivots[c] = new int[count];
            for (int i = 0; i < count; i++) {
                pivots[c][i] = r.u(h.blBitDepth);
            }
        }

        boolean hasNlq = !h.disableResidual;
        int nlqMethod = 0;
        int[] nlqPivots = null;
        if (hasNlq) {
            nlqMethod = r.u(3);
            if (nlqMethod != 0) {
                throw new RpuException(RpuException.UNSUPPORTED, "nlq_method_idc = " + nlqMethod + "，只认 0");
            }
            nlqPivots = new int[]{r.u(h.blBitDepth), r.u(h.blBitDepth)};
        }

        int xPartitions = r.ue("num_x_partitions_minus1", Integer.MAX_VALUE);
        int yPartitions = r.ue("num_y_partitions_minus1", Integer.MAX_VALUE);

        Rpu.Curve[] curves = new Rpu.Curve[3];
        for (int c = 0; c < 3; c++) {
            Rpu.Piece[] pieces = new Rpu.Piece[pivots[c].length - 1];
            for (int i = 0; i < pieces.length; i++) {
                pieces[i] = readPiece(r, h);
            }
            curves[c] = new Rpu.Curve(pivots[c], pieces);
        }

        Rpu.Nlq nlq = null;
        if (hasNlq) {
            int[] offset = new int[3];
            long[] vdrInMax = new long[3];
            long[] slope = new long[3];
            long[] threshold = new long[3];
            for (int c = 0; c < 3; c++) {
                offset[c] = r.u(h.elBitDepth);
                vdrInMax[c] = unsignedCoef(r, h);
                slope[c] = unsignedCoef(r, h);
                threshold[c] = unsignedCoef(r, h);
            }
            nlq = new Rpu.Nlq(nlqMethod, nlqPivots, offset, vdrInMax, slope, threshold);
        }
        return new Rpu.Mapping(vdrRpuId, colorSpace, chromaFormat, xPartitions, yPartitions, curves, nlq);
    }

    private static Rpu.Piece readPiece(BitReader r, Rpu.Header h) throws RpuException {
        int idc = r.ue("mapping_idc", 1);
        if (idc == Rpu.Piece.POLYNOMIAL) {
            int orderMinus1 = r.ue("poly_order_minus1", 1);
            if (orderMinus1 == 0 && r.bit()) {
                throw new RpuException(RpuException.UNSUPPORTED, "多项式的线性插值写法（linear_interp_flag）没做");
            }
            long[] coef = new long[orderMinus1 + 2];
            for (int k = 0; k < coef.length; k++) {
                coef[k] = signedCoef(r, h);
            }
            return Rpu.Piece.polynomial(coef);
        }
        int order = r.u(2) + 1;
        if (order > 3) {
            throw new RpuException(RpuException.INVALID, "mmr_order_minus1 = " + (order - 1));
        }
        long constant = signedCoef(r, h);
        long[][] coef = new long[order][Rpu.Piece.MMR_TERMS];
        for (int j = 0; j < order; j++) {
            for (int k = 0; k < Rpu.Piece.MMR_TERMS; k++) {
                coef[j][k] = signedCoef(r, h);
            }
        }
        return Rpu.Piece.mmr(constant, coef);
    }

    private static long signedCoef(BitReader r, Rpu.Header h) throws RpuException {
        if (h.coefficientDataType != 0) return r.bits(32);
        long whole = r.se();
        if (whole > MAX_COEF_INT || whole < -MAX_COEF_INT) {
            throw new RpuException(RpuException.INVALID, "系数的整数部分太大：" + whole);
        }
        return whole * (1L << h.coefficientLog2Denom) + r.bits(h.coefficientLog2Denom);
    }

    private static long unsignedCoef(BitReader r, Rpu.Header h) throws RpuException {
        if (h.coefficientDataType != 0) return r.bits(32);
        long whole = r.ue();
        if (whole > MAX_COEF_INT) {
            throw new RpuException(RpuException.INVALID, "系数的整数部分太大：" + whole);
        }
        return whole * (1L << h.coefficientLog2Denom) + r.bits(h.coefficientLog2Denom);
    }

    private Rpu.Dm readDm(BitReader r, Rpu.Header h) throws RpuException {
        int affected = r.ue("affected_dm_metadata_id", Integer.MAX_VALUE);
        int current = r.ue("current_dm_metadata_id", Integer.MAX_VALUE);
        int sceneRefresh = r.ue("scene_refresh_flag", Integer.MAX_VALUE);

        boolean compressed = h.dmCompressed();
        Rpu.Color color = compressed ? lastColor : readColor(r);

        List<ExtBlock> blocks = readBlocks(r);
        List<ExtBlock> blocksV40 = r.bitsLeft() >= SECOND_LIST_MIN_BITS ? readBlocks(r) : null;
        return new Rpu.Dm(affected, current, sceneRefresh, compressed, color, compressed && color != null,
                blocks, blocksV40);
    }

    private static Rpu.Color readColor(BitReader r) throws RpuException {
        Rpu.Color.Builder c = new Rpu.Color.Builder();
        for (int i = 0; i < 9; i++) c.yccToRgb[i] = r.s(16);
        for (int i = 0; i < 3; i++) c.yccToRgbOffset[i] = r.bits(32);
        for (int i = 0; i < 9; i++) c.rgbToLms[i] = r.s(16);
        c.signalEotf = r.u(16);
        c.signalEotfParam0 = r.u(16);
        c.signalEotfParam1 = r.u(16);
        c.signalEotfParam2 = r.bits(32);
        c.signalBitDepth = r.u(5);
        c.signalColorSpace = r.u(2);
        c.signalChromaFormat = r.u(2);
        c.signalFullRangeFlag = r.u(2);
        c.sourceMinPq = r.u(12);
        c.sourceMaxPq = r.u(12);
        c.sourceDiagonal = r.u(10);
        return c.build();
    }

    private static List<ExtBlock> readBlocks(BitReader r) throws RpuException {
        int count = r.ue("num_ext_blocks", MAX_EXT_BLOCKS);
        r.alignZero("dm_alignment_zero_bit");
        List<ExtBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(readBlock(r));
        }
        return blocks;
    }

    private static ExtBlock readBlock(BitReader r) throws RpuException {
        long size = r.ue();
        int level = r.u(8);
        if (size * 8 > r.bitsLeft()) {
            throw new RpuException(RpuException.TRUNCATED, level + " 级扩展块写着 " + size + " 字节，数据没这么多");
        }
        int length = (int) size;
        if (length < ExtBlock.minLength(level)) {
            throw new RpuException(RpuException.INVALID, level + " 级扩展块只有 " + length + " 字节，不够");
        }
        int start = r.position();
        ExtBlock block;
        switch (level) {
            case 1:
                block = new ExtBlock.Level1(length, r.u(12), r.u(12), r.u(12));
                break;
            case 2:
                block = new ExtBlock.Level2(length, r.u(12), r.u(12), r.u(12), r.u(12), r.u(12), r.u(12), r.s(13));
                break;
            case 3:
                block = new ExtBlock.Level3(length, r.u(12), r.u(12), r.u(12));
                break;
            case 4:
                block = new ExtBlock.Level4(length, r.u(12), r.u(12));
                break;
            case 5:
                block = new ExtBlock.Level5(length, r.u(13), r.u(13), r.u(13), r.u(13));
                break;
            case 6:
                block = new ExtBlock.Level6(length, r.u(16), r.u(16), r.u(16), r.u(16));
                break;
            case 8:
                block = readLevel8(r, length);
                break;
            case 9:
                block = new ExtBlock.Level9(length, r.u(8), length >= 17 ? readPrimaries(r) : null);
                break;
            case 10:
                block = new ExtBlock.Level10(length, r.u(8), r.u(12), r.u(12), r.u(8),
                        length >= 21 ? readPrimaries(r) : null);
                break;
            case 11:
                block = new ExtBlock.Level11(length, r.u(8), r.u(8), r.u(8), r.u(8));
                break;
            case 254:
                block = new ExtBlock.Level254(length, r.u(8), r.u(8));
                break;
            case 255:
                block = new ExtBlock.Level255(length, r.u(8), r.u(8), new int[]{r.u(8), r.u(8), r.u(8), r.u(8)});
                break;
            default:
                block = new ExtBlock.Unknown(level, r.bytes(length));
                break;
        }
        r.skip(length * 8 - (r.position() - start));
        return block;
    }

    private static ExtBlock readLevel8(BitReader r, int length) throws RpuException {
        int targetDisplayIndex = r.u(8);
        int slope = r.u(12);
        int offset = r.u(12);
        int power = r.u(12);
        int chromaWeight = r.u(12);
        int saturationGain = r.u(12);
        int msWeight = r.u(12);
        int midContrast = length >= 12 ? r.u(12) : ExtBlock.Level8.NEUTRAL_TRIM;
        int clipTrim = length >= 13 ? r.u(12) : ExtBlock.Level8.NEUTRAL_TRIM;
        int[] saturation = new int[6];
        int[] hue = new int[6];
        for (int i = 0; i < 6; i++) {
            saturation[i] = length >= 19 ? r.u(8) : ExtBlock.Level8.NEUTRAL_VECTOR;
        }
        for (int i = 0; i < 6; i++) {
            hue[i] = length >= 25 ? r.u(8) : ExtBlock.Level8.NEUTRAL_VECTOR;
        }
        return new ExtBlock.Level8(length, targetDisplayIndex, slope, offset, power, chromaWeight, saturationGain,
                msWeight, midContrast, clipTrim, saturation, hue);
    }

    private static int[] readPrimaries(BitReader r) throws RpuException {
        int[] xy = new int[8];
        for (int i = 0; i < 8; i++) {
            xy[i] = r.s(16);
        }
        return xy;
    }
}
