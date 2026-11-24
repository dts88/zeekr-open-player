package io.github.dts88.openplayer.dovi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Rpu {
    public static final int EL_NONE = 0;
    public static final int EL_MEL = 1;
    public static final int EL_FEL = 2;

    public final Header header;
    public final Mapping mapping;
    public final boolean mappingReused;
    public final Dm dm;
    public final byte[] remaining;
    public final long crc32;

    public Rpu(Header header, Mapping mapping, boolean mappingReused, Dm dm, byte[] remaining, long crc32) {
        this.header = header;
        this.mapping = mapping;
        this.mappingReused = mappingReused;
        this.dm = dm;
        this.remaining = remaining;
        this.crc32 = crc32;
    }

    public static final class Header {
        public final int rpuType;
        public final int rpuFormat;
        public final int vdrRpuProfile;
        public final int vdrRpuLevel;
        public final boolean chromaResamplingExplicitFilter;
        public final int coefficientDataType;
        public final int coefficientLog2Denom;
        public final int vdrRpuNormalizedIdc;
        public final boolean blVideoFullRange;
        public final int blBitDepth;
        public final int elBitDepth;
        public final int vdrBitDepth;
        public final int extMappingIdc04;
        public final int extMappingIdc57;
        public final boolean spatialResamplingFilter;
        public final int reservedZero3Bits;
        public final boolean elSpatialResamplingFilter;
        public final boolean disableResidual;
        public final boolean vdrDmMetadataPresent;
        public final boolean usePrevVdrRpu;
        public final int prevVdrRpuId;

        Header(Builder b) {
            rpuType = b.rpuType;
            rpuFormat = b.rpuFormat;
            vdrRpuProfile = b.vdrRpuProfile;
            vdrRpuLevel = b.vdrRpuLevel;
            chromaResamplingExplicitFilter = b.chromaResamplingExplicitFilter;
            coefficientDataType = b.coefficientDataType;
            coefficientLog2Denom = b.coefficientLog2Denom;
            vdrRpuNormalizedIdc = b.vdrRpuNormalizedIdc;
            blVideoFullRange = b.blVideoFullRange;
            blBitDepth = b.blBitDepth;
            elBitDepth = b.elBitDepth;
            vdrBitDepth = b.vdrBitDepth;
            extMappingIdc04 = b.extMappingIdc04;
            extMappingIdc57 = b.extMappingIdc57;
            spatialResamplingFilter = b.spatialResamplingFilter;
            reservedZero3Bits = b.reservedZero3Bits;
            elSpatialResamplingFilter = b.elSpatialResamplingFilter;
            disableResidual = b.disableResidual;
            vdrDmMetadataPresent = b.vdrDmMetadataPresent;
            usePrevVdrRpu = b.usePrevVdrRpu;
            prevVdrRpuId = b.prevVdrRpuId;
        }

        public static final class Builder {
            public int rpuType = 2;
            public int rpuFormat = 18;
            public int vdrRpuProfile = 1;
            public int vdrRpuLevel;
            public boolean chromaResamplingExplicitFilter;
            public int coefficientDataType;
            public int coefficientLog2Denom = 23;
            public int vdrRpuNormalizedIdc = 1;
            public boolean blVideoFullRange;
            public int blBitDepth = 10;
            public int elBitDepth = 10;
            public int vdrBitDepth = 12;
            public int extMappingIdc04;
            public int extMappingIdc57;
            public boolean spatialResamplingFilter;
            public int reservedZero3Bits;
            public boolean elSpatialResamplingFilter;
            public boolean disableResidual = true;
            public boolean vdrDmMetadataPresent = true;
            public boolean usePrevVdrRpu;
            public int prevVdrRpuId;

            public Header build() {
                return new Header(this);
            }
        }

        public int coefficientBits() {
            return coefficientDataType == 0 ? coefficientLog2Denom : 32;
        }

        public double coefficient(long raw) {
            if (coefficientDataType == 0) {
                return raw / (double) (1L << coefficientLog2Denom);
            }
            return Float.intBitsToFloat((int) raw);
        }

        public int profile() {
            switch (vdrRpuProfile) {
                case 0:
                    return blVideoFullRange ? 5 : 0;
                case 1:
                    if (elSpatialResamplingFilter && !disableResidual) {
                        return vdrBitDepth == 12 ? 7 : 4;
                    }
                    return 8;
                default:
                    return 0;
            }
        }

        public boolean dmCompressed() {
            return reservedZero3Bits == 1;
        }
    }

    public static final class Mapping {
        public final int vdrRpuId;
        public final int mappingColorSpace;
        public final int mappingChromaFormatIdc;
        public final int numXPartitionsMinus1;
        public final int numYPartitionsMinus1;
        public final Curve[] curves;
        public final Nlq nlq;

        public Mapping(int vdrRpuId, int mappingColorSpace, int mappingChromaFormatIdc, int numXPartitionsMinus1,
                       int numYPartitionsMinus1, Curve[] curves, Nlq nlq) {
            this.vdrRpuId = vdrRpuId;
            this.mappingColorSpace = mappingColorSpace;
            this.mappingChromaFormatIdc = mappingChromaFormatIdc;
            this.numXPartitionsMinus1 = numXPartitionsMinus1;
            this.numYPartitionsMinus1 = numYPartitionsMinus1;
            this.curves = curves;
            this.nlq = nlq;
        }
    }

    public static final class Curve {
        public final int[] pivots;
        public final Piece[] pieces;

        public Curve(int[] pivots, Piece[] pieces) {
            this.pivots = pivots;
            this.pieces = pieces;
        }

        public int[] pivotCodes() {
            int[] codes = new int[pivots.length];
            int sum = 0;
            for (int i = 0; i < pivots.length; i++) {
                sum += pivots[i];
                codes[i] = Math.min(sum, 0xFFFF);
            }
            return codes;
        }
    }

    public static final class Piece {
        public static final int POLYNOMIAL = 0;
        public static final int MMR = 1;
        public static final int MMR_TERMS = 7;

        public final int mappingIdc;
        public final long[] polyCoef;
        public final long mmrConstant;
        public final long[][] mmrCoef;

        private Piece(int mappingIdc, long[] polyCoef, long mmrConstant, long[][] mmrCoef) {
            this.mappingIdc = mappingIdc;
            this.polyCoef = polyCoef;
            this.mmrConstant = mmrConstant;
            this.mmrCoef = mmrCoef;
        }

        public static Piece polynomial(long... coef) {
            return new Piece(POLYNOMIAL, coef, 0, new long[0][]);
        }

        public static Piece mmr(long constant, long[][] coef) {
            return new Piece(MMR, new long[0], constant, coef);
        }

        public int polyOrder() {
            return polyCoef.length - 1;
        }

        public int mmrOrder() {
            return mmrCoef.length;
        }
    }

    public static final class Nlq {
        public final int methodIdc;
        public final int[] predPivotValue;
        public final int[] offset;
        public final long[] vdrInMax;
        public final long[] linearDeadzoneSlope;
        public final long[] linearDeadzoneThreshold;

        public Nlq(int methodIdc, int[] predPivotValue, int[] offset, long[] vdrInMax, long[] linearDeadzoneSlope,
                   long[] linearDeadzoneThreshold) {
            this.methodIdc = methodIdc;
            this.predPivotValue = predPivotValue;
            this.offset = offset;
            this.vdrInMax = vdrInMax;
            this.linearDeadzoneSlope = linearDeadzoneSlope;
            this.linearDeadzoneThreshold = linearDeadzoneThreshold;
        }
    }

    public static final class Dm {
        public final int affectedDmMetadataId;
        public final int currentDmMetadataId;
        public final int sceneRefreshFlag;
        public final boolean compressed;
        public final Color color;
        public final boolean colorReused;
        public final List<ExtBlock> blocks;
        public final List<ExtBlock> blocksV40;

        public Dm(int affectedDmMetadataId, int currentDmMetadataId, int sceneRefreshFlag, boolean compressed,
                  Color color, boolean colorReused, List<ExtBlock> blocks, List<ExtBlock> blocksV40) {
            this.affectedDmMetadataId = affectedDmMetadataId;
            this.currentDmMetadataId = currentDmMetadataId;
            this.sceneRefreshFlag = sceneRefreshFlag;
            this.compressed = compressed;
            this.color = color;
            this.colorReused = colorReused;
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
            this.blocksV40 = blocksV40 == null ? null : Collections.unmodifiableList(new ArrayList<>(blocksV40));
        }
    }

    public static final class Color {
        public final int[] yccToRgb;
        public final long[] yccToRgbOffset;
        public final int[] rgbToLms;
        public final int signalEotf;
        public final int signalEotfParam0;
        public final int signalEotfParam1;
        public final long signalEotfParam2;
        public final int signalBitDepth;
        public final int signalColorSpace;
        public final int signalChromaFormat;
        public final int signalFullRangeFlag;
        public final int sourceMinPq;
        public final int sourceMaxPq;
        public final int sourceDiagonal;

        Color(Builder b) {
            yccToRgb = b.yccToRgb;
            yccToRgbOffset = b.yccToRgbOffset;
            rgbToLms = b.rgbToLms;
            signalEotf = b.signalEotf;
            signalEotfParam0 = b.signalEotfParam0;
            signalEotfParam1 = b.signalEotfParam1;
            signalEotfParam2 = b.signalEotfParam2;
            signalBitDepth = b.signalBitDepth;
            signalColorSpace = b.signalColorSpace;
            signalChromaFormat = b.signalChromaFormat;
            signalFullRangeFlag = b.signalFullRangeFlag;
            sourceMinPq = b.sourceMinPq;
            sourceMaxPq = b.sourceMaxPq;
            sourceDiagonal = b.sourceDiagonal;
        }

        public static final class Builder {
            public int[] yccToRgb = new int[9];
            public long[] yccToRgbOffset = new long[3];
            public int[] rgbToLms = new int[9];
            public int signalEotf = 65535;
            public int signalEotfParam0;
            public int signalEotfParam1;
            public long signalEotfParam2;
            public int signalBitDepth = 12;
            public int signalColorSpace;
            public int signalChromaFormat;
            public int signalFullRangeFlag = 1;
            public int sourceMinPq;
            public int sourceMaxPq;
            public int sourceDiagonal = 42;

            public Color build() {
                return new Color(this);
            }
        }
    }

    public int profile() {
        return header.profile();
    }

    public boolean hasEnhancementLayer() {
        return !header.disableResidual;
    }

    public int elType() {
        Nlq nlq = mapping == null ? null : mapping.nlq;
        if (nlq == null) return EL_NONE;
        for (int c = 0; c < 3; c++) {
            boolean nothing = nlq.offset[c] == 0
                    && header.coefficient(nlq.vdrInMax[c]) == 1.0
                    && nlq.linearDeadzoneSlope[c] == 0
                    && nlq.linearDeadzoneThreshold[c] == 0;
            if (!nothing) return EL_FEL;
        }
        return EL_MEL;
    }

    public ExtBlock block(int level) {
        if (dm == null) return null;
        for (ExtBlock b : dm.blocks) {
            if (b.level == level) return b;
        }
        if (dm.blocksV40 != null) {
            for (ExtBlock b : dm.blocksV40) {
                if (b.level == level) return b;
            }
        }
        return null;
    }

    public List<ExtBlock> blocks(int level) {
        List<ExtBlock> out = new ArrayList<>();
        if (dm == null) return out;
        for (ExtBlock b : dm.blocks) {
            if (b.level == level) out.add(b);
        }
        if (dm.blocksV40 != null) {
            for (ExtBlock b : dm.blocksV40) {
                if (b.level == level) out.add(b);
            }
        }
        return out;
    }

    public ExtBlock.Level1 level1() {
        ExtBlock b = block(1);
        return b instanceof ExtBlock.Level1 ? (ExtBlock.Level1) b : null;
    }

    public ExtBlock.Level5 level5() {
        ExtBlock b = block(5);
        return b instanceof ExtBlock.Level5 ? (ExtBlock.Level5) b : null;
    }

    public ExtBlock.Level6 level6() {
        ExtBlock b = block(6);
        return b instanceof ExtBlock.Level6 ? (ExtBlock.Level6) b : null;
    }

    public boolean hasCmV40() {
        return dm != null && dm.blocksV40 != null;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        int p = profile();
        sb.append(p == 0 ? "档次猜不出" : "P" + p);
        int el = elType();
        if (el == EL_MEL) sb.append("（MEL）");
        if (el == EL_FEL) sb.append("（FEL）");
        sb.append("，基础层 ").append(header.blBitDepth).append(" 位")
                .append(header.blVideoFullRange ? "全范围" : "有限范围");
        sb.append("，曲线");
        if (mappingReused) sb.append("（沿用 ").append(mapping.vdrRpuId).append(" 号）");
        String[] names = {" Y ", " Cb ", " Cr "};
        for (int c = 0; c < 3; c++) {
            Curve curve = mapping.curves[c];
            int mmr = 0;
            for (Piece piece : curve.pieces) {
                if (piece.mappingIdc == Piece.MMR) mmr++;
            }
            sb.append(names[c]).append(curve.pieces.length).append(" 段");
            if (mmr == 0) sb.append("多项式");
            else if (mmr == curve.pieces.length) sb.append(" MMR");
            else sb.append("（").append(mmr).append(" 段 MMR）");
        }
        if (dm == null) {
            sb.append("，没有显示管理数据");
        } else {
            sb.append("，扩展块");
            if (dm.blocks.isEmpty() && dm.blocksV40 == null) sb.append("没有");
            for (ExtBlock b : dm.blocks) sb.append(' ').append(b.level);
            if (dm.blocksV40 != null) {
                sb.append(" |");
                for (ExtBlock b : dm.blocksV40) sb.append(' ').append(b.level);
            }
            if (dm.sceneRefreshFlag != 0) sb.append("，换场景");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return summary();
    }

    public String strictCheck() {
        int profile = profile();
        if (header.vdrRpuLevel != 0) return "vdr_rpu_level should be 0";
        if (header.blBitDepth != 10) return "bl_bit_depth_minus8 should be 2";
        if (header.elBitDepth != 10) return "el_bit_depth_minus8 should be 2";
        if (header.vdrBitDepth > 14) return "vdr_bit_depth_minus8 should be <= 6";
        if (header.coefficientLog2Denom > 23) return "coefficient_log2_denom should be <= 23";
        if (!mappingReused) {
            if (profile == 7) {
                if (mapping.nlq == null) return "profile 7: nlq_pred_pivot_value should be defined";
                if (mapping.nlq.predPivotValue[0] + mapping.nlq.predPivotValue[1] != 1023) {
                    return "profile 7: nlq_pred_pivot_value elements should add up to the BL bit depth";
                }
            }
            if ((profile == 5 || profile == 8) && mapping.nlq != null) {
                return "profile " + profile + ": nlq_method_idc should be undefined";
            }
            if (mapping.mappingColorSpace != 0) return "mapping_color_space should be 0";
            if (mapping.mappingChromaFormatIdc != 0) return "mapping_chroma_format_idc should be 0";
        }
        if (dm == null) return null;
        if (dm.affectedDmMetadataId > 15) return "affected_dm_metadata_id should be <= 15";
        if (!dm.compressed && dm.color != null) {
            Color c = dm.color;
            if (c.signalBitDepth < 8 || c.signalBitDepth > 16) return "signal_bit_depth should be between 8 and 16";
            if (c.signalEotfParam0 == 0 && c.signalEotfParam1 == 0 && c.signalEotfParam2 == 0
                    && c.signalEotf != 65535) {
                return "signal_eotf should be 65535";
            }
        }
        String first = checkBlocks(dm.blocks, new int[]{1, 2, 4, 5, 6, 255}, new int[]{1, 8, 1, 1, 1, 1}, "CM v2.9");
        if (first != null) return first;
        if (dm.blocksV40 == null) return null;
        return checkBlocks(dm.blocksV40, new int[]{3, 8, 9, 10, 11, 253, 254}, new int[]{1, 5, 1, 4, 1, 1, 1},
                "CM v4.0");
    }

    private static String checkBlocks(List<ExtBlock> blocks, int[] levels, int[] max, String name) {
        int[] count = new int[levels.length];
        for (ExtBlock b : blocks) {
            int at = -1;
            for (int i = 0; i < levels.length; i++) {
                if (levels[i] == b.level) at = i;
            }
            if (at < 0) return name + ": block level " + b.level + " is not allowed";
            if (++count[at] > max[at]) {
                return name + ": there must be at most " + max[at] + " level " + b.level + " blocks";
            }
            String problem = checkBlock(b);
            if (problem != null) return name + ": level " + b.level + ": " + problem;
        }
        return null;
    }

    private static String checkBlock(ExtBlock b) {
        if (b instanceof ExtBlock.Unknown) {
            byte[] data = ((ExtBlock.Unknown) b).data;
            if (data.length > 66) return "should be at most 66 bytes";
            for (byte x : data) {
                if (x != 0x55) return "filler bytes are expected to equal 0x55";
            }
            return null;
        }
        int min = ExtBlock.minLength(b.level);
        if (b instanceof ExtBlock.Level2) {
            int w = ((ExtBlock.Level2) b).msWeight;
            if (w < -1 || w > 4095) return "ms_weight out of range";
        } else if (b instanceof ExtBlock.Level6) {
            ExtBlock.Level6 l = (ExtBlock.Level6) b;
            if (l.maxDisplayMasteringLuminance > 10000 || l.minDisplayMasteringLuminance > 10000
                    || l.maxContentLightLevel > 10000 || l.maxFrameAverageLightLevel > 10000) {
                return "luminance above 10000";
            }
        } else if (b instanceof ExtBlock.Level8) {
            if (b.length != 10 && b.length != 12 && b.length != 13 && b.length != 19 && b.length != 25) {
                return "invalid level 8 length";
            }
            return null;
        } else if (b instanceof ExtBlock.Level9) {
            ExtBlock.Level9 l = (ExtBlock.Level9) b;
            if (b.length != 1 && b.length != 17) return "invalid level 9 length";
            return checkPrimaries(l.sourcePrimaryIndex, l.primaries);
        } else if (b instanceof ExtBlock.Level10) {
            ExtBlock.Level10 l = (ExtBlock.Level10) b;
            if (b.length != 5 && b.length != 21) return "invalid level 10 length";
            for (int preset : new int[]{1, 16, 18, 21, 27, 28, 37, 38, 42, 48, 49}) {
                if (l.targetDisplayIndex == preset) return "target_display_index is a preset display";
            }
            return checkPrimaries(l.targetPrimaryIndex, l.primaries);
        } else if (b instanceof ExtBlock.Level11) {
            ExtBlock.Level11 l = (ExtBlock.Level11) b;
            if (l.contentType > 15) return "content_type should be <= 15";
            if (l.reservedByte3 != 0) return "reserved_byte3 should be 0";
        }
        if (b.length != min) return "should have length " + min;
        return null;
    }

    private static String checkPrimaries(int index, int[] primaries) {
        if (primaries == null) {
            return index == 255 ? "primary index 255 needs custom primaries" : null;
        }
        if (index != 255) return "custom primaries need primary index 255";
        for (int v : primaries) {
            if (v <= 0) return "custom primaries should be > 0";
        }
        return null;
    }
}
