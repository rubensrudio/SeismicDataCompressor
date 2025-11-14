package com.sdc.core;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Sdc3DFileReader {

    private Sdc3DFileReader() {}

    public static final class Sdc3DVolume {
        public final int inlineCount;
        public final int xlineCount;
        public final int timeCount;
        public final float[][][] data; // [inline][xline][time]

        public Sdc3DVolume(int inlineCount, int xlineCount, int timeCount, float[][][] data) {
            this.inlineCount = inlineCount;
            this.xlineCount = xlineCount;
            this.timeCount = timeCount;
            this.data = data;
        }
    }

    public static Sdc3DVolume read3D(Path path, CompressionProfile profile) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            SdcHeader header = SdcHeader.read(in);
            if (header.version() != 3) {
                throw new IOException("Expected SDC version 3 for 3D volume, got " + header.version());
            }

            int inlineCount = in.readInt();
            int xlineCount  = in.readInt();
            int timeCount   = in.readInt();

            int blockInline = in.readInt();
            int blockXline  = in.readInt();
            int blockTime   = in.readInt();

            int blockCount = in.readInt();

            List<CubeIndexEntry> index = new ArrayList<>(blockCount);
            for (int b = 0; b < blockCount; b++) {
                int ii = in.readInt();
                int jj = in.readInt();
                int tt = in.readInt();
                int bi = in.readInt();
                int bj = in.readInt();
                int bt = in.readInt();
                float min = in.readFloat();
                float max = in.readFloat();
                int payloadSize = in.readInt();
                index.add(new CubeIndexEntry(ii, jj, tt, bi, bj, bt, min, max, payloadSize));
            }

            float[][][] volume = new float[inlineCount][xlineCount][timeCount];

            for (CubeIndexEntry e : index) {
                byte[] payload = in.readNBytes(e.payloadSize);

                // monta metadados do bloco
                float[][][] dummy = new float[e.inlineCount][e.xlineCount][e.timeCount];
                VolumeBlock3D meta = new VolumeBlock3D(
                        e.inlineStartIndex,
                        e.xlineStartIndex,
                        e.timeStartIndex,
                        dummy
                );

                VolumeBlock3DCompressor.CompressedVolumeBlock3D cb =
                        new VolumeBlock3DCompressor.CompressedVolumeBlock3D(meta, e.min, e.max, payload);

                float[][][] cubeData = VolumeBlock3DCompressor.decompressBlock(cb, profile);

                // copia pro volume global
                for (int di = 0; di < e.inlineCount; di++) {
                    int gi = e.inlineStartIndex + di;
                    for (int dj = 0; dj < e.xlineCount; dj++) {
                        int gj = e.xlineStartIndex + dj;
                        System.arraycopy(cubeData[di][dj], 0,
                                volume[gi][gj], e.timeStartIndex, e.timeCount);
                    }
                }
            }

            return new Sdc3DVolume(inlineCount, xlineCount, timeCount, volume);
        }
    }
}
