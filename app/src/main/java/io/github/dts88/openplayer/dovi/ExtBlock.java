package io.github.dts88.openplayer.dovi;

public abstract class ExtBlock {
    public final int level;
    public final int length;

    ExtBlock(int level, int length) {
        this.level = level;
        this.length = length;
    }

    public static int minLength(int level) {
        switch (level) {
            case 1: return 5;
            case 2: return 11;
            case 3: return 5;
            case 4: return 3;
            case 5: return 7;
            case 6: return 8;
            case 8: return 10;
            case 9: return 1;
            case 10: return 5;
            case 11: return 4;
            case 254: return 2;
            case 255: return 6;
            default: return 0;
        }
    }

    public static final class Level1 extends ExtBlock {
        public final int minPq;
        public final int maxPq;
        public final int avgPq;

        public Level1(int length, int minPq, int maxPq, int avgPq) {
            super(1, length);
            this.minPq = minPq;
            this.maxPq = maxPq;
            this.avgPq = avgPq;
        }
    }

    public static final class Level2 extends ExtBlock {
        public final int targetMaxPq;
        public final int trimSlope;
        public final int trimOffset;
        public final int trimPower;
        public final int trimChromaWeight;
        public final int trimSaturationGain;
        public final int msWeight;

        public Level2(int length, int targetMaxPq, int trimSlope, int trimOffset, int trimPower,
                      int trimChromaWeight, int trimSaturationGain, int msWeight) {
            super(2, length);
            this.targetMaxPq = targetMaxPq;
            this.trimSlope = trimSlope;
            this.trimOffset = trimOffset;
            this.trimPower = trimPower;
            this.trimChromaWeight = trimChromaWeight;
            this.trimSaturationGain = trimSaturationGain;
            this.msWeight = msWeight;
        }
    }

    public static final class Level3 extends ExtBlock {
        public final int minPqOffset;
        public final int maxPqOffset;
        public final int avgPqOffset;

        public Level3(int length, int minPqOffset, int maxPqOffset, int avgPqOffset) {
            super(3, length);
            this.minPqOffset = minPqOffset;
            this.maxPqOffset = maxPqOffset;
            this.avgPqOffset = avgPqOffset;
        }
    }

    public static final class Level4 extends ExtBlock {
        public final int anchorPq;
        public final int anchorPower;

        public Level4(int length, int anchorPq, int anchorPower) {
            super(4, length);
            this.anchorPq = anchorPq;
            this.anchorPower = anchorPower;
        }
    }

    public static final class Level5 extends ExtBlock {
        public final int left;
        public final int right;
        public final int top;
        public final int bottom;

        public Level5(int length, int left, int right, int top, int bottom) {
            super(5, length);
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    public static final class Level6 extends ExtBlock {
        public final int maxDisplayMasteringLuminance;
        public final int minDisplayMasteringLuminance;
        public final int maxContentLightLevel;
        public final int maxFrameAverageLightLevel;

        public Level6(int length, int maxDisplayMasteringLuminance, int minDisplayMasteringLuminance,
                      int maxContentLightLevel, int maxFrameAverageLightLevel) {
            super(6, length);
            this.maxDisplayMasteringLuminance = maxDisplayMasteringLuminance;
            this.minDisplayMasteringLuminance = minDisplayMasteringLuminance;
            this.maxContentLightLevel = maxContentLightLevel;
            this.maxFrameAverageLightLevel = maxFrameAverageLightLevel;
        }
    }

    public static final class Level8 extends ExtBlock {
        public static final int NEUTRAL_TRIM = 2048;
        public static final int NEUTRAL_VECTOR = 128;

        public final int targetDisplayIndex;
        public final int trimSlope;
        public final int trimOffset;
        public final int trimPower;
        public final int trimChromaWeight;
        public final int trimSaturationGain;
        public final int msWeight;
        public final int targetMidContrast;
        public final int clipTrim;
        public final int[] saturationVectorField;
        public final int[] hueVectorField;

        public Level8(int length, int targetDisplayIndex, int trimSlope, int trimOffset, int trimPower,
                      int trimChromaWeight, int trimSaturationGain, int msWeight, int targetMidContrast,
                      int clipTrim, int[] saturationVectorField, int[] hueVectorField) {
            super(8, length);
            this.targetDisplayIndex = targetDisplayIndex;
            this.trimSlope = trimSlope;
            this.trimOffset = trimOffset;
            this.trimPower = trimPower;
            this.trimChromaWeight = trimChromaWeight;
            this.trimSaturationGain = trimSaturationGain;
            this.msWeight = msWeight;
            this.targetMidContrast = targetMidContrast;
            this.clipTrim = clipTrim;
            this.saturationVectorField = saturationVectorField;
            this.hueVectorField = hueVectorField;
        }
    }

    public static final class Level9 extends ExtBlock {
        public final int sourcePrimaryIndex;
        public final int[] primaries;

        public Level9(int length, int sourcePrimaryIndex, int[] primaries) {
            super(9, length);
            this.sourcePrimaryIndex = sourcePrimaryIndex;
            this.primaries = primaries;
        }
    }

    public static final class Level10 extends ExtBlock {
        public final int targetDisplayIndex;
        public final int targetMaxPq;
        public final int targetMinPq;
        public final int targetPrimaryIndex;
        public final int[] primaries;

        public Level10(int length, int targetDisplayIndex, int targetMaxPq, int targetMinPq,
                       int targetPrimaryIndex, int[] primaries) {
            super(10, length);
            this.targetDisplayIndex = targetDisplayIndex;
            this.targetMaxPq = targetMaxPq;
            this.targetMinPq = targetMinPq;
            this.targetPrimaryIndex = targetPrimaryIndex;
            this.primaries = primaries;
        }
    }

    public static final class Level11 extends ExtBlock {
        public final int contentType;
        public final int byte1;
        public final int reservedByte2;
        public final int reservedByte3;

        public Level11(int length, int contentType, int byte1, int reservedByte2, int reservedByte3) {
            super(11, length);
            this.contentType = contentType;
            this.byte1 = byte1;
            this.reservedByte2 = reservedByte2;
            this.reservedByte3 = reservedByte3;
        }

        public int whitepoint() {
            return byte1 & 0x0F;
        }

        public boolean referenceMode() {
            return ((byte1 >> 4) & 1) != 0;
        }
    }

    public static final class Level254 extends ExtBlock {
        public final int dmMode;
        public final int dmVersionIndex;

        public Level254(int length, int dmMode, int dmVersionIndex) {
            super(254, length);
            this.dmMode = dmMode;
            this.dmVersionIndex = dmVersionIndex;
        }
    }

    public static final class Level255 extends ExtBlock {
        public final int dmRunMode;
        public final int dmRunVersion;
        public final int[] dmDebug;

        public Level255(int length, int dmRunMode, int dmRunVersion, int[] dmDebug) {
            super(255, length);
            this.dmRunMode = dmRunMode;
            this.dmRunVersion = dmRunVersion;
            this.dmDebug = dmDebug;
        }
    }

    public static final class Unknown extends ExtBlock {
        public final byte[] data;

        public Unknown(int level, byte[] data) {
            super(level, data.length);
            this.data = data;
        }
    }
}
