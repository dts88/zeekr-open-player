package io.github.dts88.openplayer.bluray;

import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public final class M2tsDataSource implements DataSource {
    public static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstream;

        public Factory(DataSource.Factory upstream) {
            this.upstream = upstream;
        }

        @Override
        public DataSource createDataSource() {
            return new M2tsDataSource(upstream.createDataSource());
        }
    }

    private final DataSource upstream;
    private final M2tsPackets.Stripper stripper = new M2tsPackets.Stripper();
    private final Map<Uri, Boolean> checked = new HashMap<>();
    private final M2tsPackets.Upstream reader;
    private boolean stripping;

    M2tsDataSource(DataSource upstream) {
        this.upstream = upstream;
        reader = (buffer, offset, length) -> {
            int n = upstream.read(buffer, offset, length);
            return n == C.RESULT_END_OF_INPUT ? -1 : n;
        };
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        stripping = M2tsPackets.isM2tsName(dataSpec.uri.getPath()) && isM2ts(dataSpec);
        if (!stripping) {
            return upstream.open(dataSpec);
        }
        long fileStart = M2tsPackets.toFileStart(dataSpec.position);
        long fileLength = C.LENGTH_UNSET;
        if (dataSpec.length != C.LENGTH_UNSET) {
            long fileEnd = M2tsPackets.toFilePosition(dataSpec.position + dataSpec.length - 1) + 1;
            fileLength = fileEnd - fileStart;
        }
        long opened = upstream.open(dataSpec.buildUpon().setPosition(fileStart).setLength(fileLength).build());
        long tsLength = opened == C.LENGTH_UNSET
                ? C.LENGTH_UNSET
                : M2tsPackets.tsBytesBefore(fileStart + opened) - M2tsPackets.tsBytesBefore(fileStart);
        stripper.reset(fileStart, tsLength);
        return tsLength;
    }

    private boolean isM2ts(DataSpec dataSpec) throws IOException {
        Boolean known = checked.get(dataSpec.uri);
        if (known != null) return known;
        byte[] head = new byte[M2tsPackets.SNIFF_BYTES];
        int got = 0;
        try {
            upstream.open(dataSpec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET).build());
            while (got < head.length) {
                int n = upstream.read(head, got, head.length - got);
                if (n == C.RESULT_END_OF_INPUT) break;
                got += n;
            }
        } finally {
            upstream.close();
        }
        boolean yes = M2tsPackets.looksLikeM2ts(head, got);
        checked.put(dataSpec.uri, yes);
        return yes;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!stripping) return upstream.read(buffer, offset, length);
        if (length == 0) return 0;
        int n = stripper.read(reader, buffer, offset, length);
        return n < 0 ? C.RESULT_END_OF_INPUT : n;
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        upstream.close();
    }
}
