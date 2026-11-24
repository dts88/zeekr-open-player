package io.github.dts88.openplayer.bluray;

import android.util.SparseArray;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ts.Ac3Reader;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.PesReader;
import androidx.media3.extractor.ts.TsPayloadReader;


import java.util.Collections;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
final class BlurayPayloadReaderFactory implements TsPayloadReader.Factory {
    static final int LPCM = 0x80;
    static final int DTS = 0x82;
    static final int TRUEHD = 0x83;
    static final int EAC3 = 0x84;
    static final int DTS_HD_HIGH_RESOLUTION = 0x85;
    static final int DTS_HD_MASTER_AUDIO = 0x86;
    static final int EAC3_SECONDARY = 0xA1;
    static final int DTS_HD_SECONDARY = 0xA2;
    static final int TS_EAC3 = 0x87;
    static final int TS_H265 = 0x24;
    static final int PGS = 0x90;

    private final boolean bluray;
    private final DefaultTsPayloadReaderFactory fallback =
            new DefaultTsPayloadReaderFactory(0, Collections.emptyList());

    BlurayPayloadReaderFactory(boolean bluray) {
        this.bluray = bluray;
    }

    @Override
    public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
        return fallback.createInitialPayloadReaders();
    }

    @Override
    public TsPayloadReader createPayloadReader(int streamType, TsPayloadReader.EsInfo esInfo) {
        String language = esInfo.language;
        int roles = esInfo.getRoleFlags();
        if (streamType == TRUEHD) {
            return new BlurayPesReader(
                    new FramedAudioReader(FramedAudioReader.Kind.TRUEHD, language, roles),
                    new Ac3Reader(language, C.ROLE_FLAG_ALTERNATE, MimeTypes.VIDEO_MP2T),
                    "TrueHD 里带的 AC-3", language);
        }
        if (bluray || hdmvRegistered(esInfo.descriptorBytes)) {
            switch (streamType) {
                case LPCM:
                    return new PesReader(new LpcmReader(language, roles));
                case DTS:
                case DTS_HD_HIGH_RESOLUTION:
                case DTS_HD_MASTER_AUDIO:
                    return new PesReader(new FramedAudioReader(FramedAudioReader.Kind.DTS, language, roles));
                case DTS_HD_SECONDARY:
                    return new PesReader(new FramedAudioReader(
                            FramedAudioReader.Kind.DTS, language, roles | C.ROLE_FLAG_COMMENTARY));
                case EAC3:
                    return new PesReader(new FramedAudioReader(FramedAudioReader.Kind.EAC3, language, roles));
                case EAC3_SECONDARY:
                    return new PesReader(new FramedAudioReader(
                            FramedAudioReader.Kind.EAC3, language, roles | C.ROLE_FLAG_COMMENTARY));
                default:
                    break;
            }
        }
        if (streamType == TS_H265) {
            DoviDescriptor dv = DoviDescriptor.find(esInfo.descriptorBytes);
            if (dv != null) {
                boolean tag = DoviTaggingReader.wanted(dv.profile);
                TsPayloadReader plain = fallback.createPayloadReader(streamType, esInfo);
                return tag && plain != null ? new DoviTaggingReader(plain, dv.profile, dv.level) : plain;
            }
        }
        if (streamType == PGS) {
            return new PesReader(new PgsReader(language, roles));
        }
        if (streamType == TS_EAC3) {
            return new PesReader(new FramedAudioReader(FramedAudioReader.Kind.EAC3, language, roles));
        }
        return fallback.createPayloadReader(streamType, esInfo);
    }

    static boolean hdmvRegistered(byte[] descriptors) {
        if (descriptors == null) return false;
        int at = 0;
        while (at + 2 <= descriptors.length) {
            int tag = descriptors[at] & 0xFF;
            int length = descriptors[at + 1] & 0xFF;
            int body = at + 2;
            if (body + length > descriptors.length) return false;
            if (tag == 0x05 && length >= 4 && descriptors[body] == 'H' && descriptors[body + 1] == 'D'
                    && descriptors[body + 2] == 'M' && descriptors[body + 3] == 'V') {
                return true;
            }
            at = body + length;
        }
        return false;
    }
}
