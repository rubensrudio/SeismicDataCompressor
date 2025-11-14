package com.sdc.core;

import java.util.ArrayList;
import java.util.List;

public final class VolumeBlock3DCompressor {

    private VolumeBlock3DCompressor() {}

    public static final class CompressedVolumeBlock3D {
        public final VolumeBlock3D blockMeta;
        public final float min;
        public final float max;
        public final byte[] payload;

        public CompressedVolumeBlock3D(VolumeBlock3D blockMeta, float min, float max, byte[] payload) {
            this.blockMeta = blockMeta;
            this.min = min;
            this.max = max;
            this.payload = payload;
        }
    }

    public static CompressedVolumeBlock3D compressBlock(VolumeBlock3D block,
                                                        CompressionProfile profile) {
        float[][][] data = block.samples;

        // min/max do cubo inteiro
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int ni = data.length;
        int nj = data[0].length;
        int nt = data[0][0].length;

        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {
                    float v = data[i][j][k];
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
        }

        // normaliza para [-1,1]
        float range = max - min;
        float[][][] norm = new float[ni][nj][nt];
        if (range == 0f) {
            for (int i = 0; i < ni; i++) {
                for (int j = 0; j < nj; j++) {
                    java.util.Arrays.fill(norm[i][j], 0f);
                }
            }
        } else {
            for (int i = 0; i < ni; i++) {
                for (int j = 0; j < nj; j++) {
                    for (int k = 0; k < nt; k++) {
                        float v = data[i][j][k];
                        float zeroToOne = (v - min) / range;
                        norm[i][j][k] = zeroToOne * 2f - 1f;
                    }
                }
            }
        }

        // residual 3D
        float[][][] residual = VolumeBlock3DCodec.residualEncode(norm);

        // flatten
        float[] flat = VolumeBlock3DCodec.flatten3D(residual);

        // quantiza com profile
        short[] q = LinearQuantizer.encode(flat, profile);

        // short[] -> bytes
        byte[] rawBytes = TraceBlockCodec.shortsToBytes(q);

        // Deflate com nível do profile
        byte[] compressed = TraceBlockCodec.deflate(rawBytes, profile.deflaterLevel());

        return new CompressedVolumeBlock3D(block, min, max, compressed);
    }

    public static float[][][] decompressBlock(CompressedVolumeBlock3D cb,
                                              CompressionProfile profile) {
        VolumeBlock3D meta = cb.blockMeta;
        int ni = meta.inlineCount;
        int nj = meta.xlineCount;
        int nt = meta.timeCount;

        byte[] rawBytes = TraceBlockCodec.inflate(cb.payload);
        short[] q = TraceBlockCodec.bytesToShorts(rawBytes, ni * nj * nt);
        float[] flat = LinearQuantizer.decode(q);

        float[][][] residual = VolumeBlock3DCodec.unflatten3D(flat, ni, nj, nt);
        float[][][] norm = VolumeBlock3DCodec.residualDecode(residual);

        // denormaliza
        float[][][] data = new float[ni][nj][nt];
        float min = cb.min;
        float max = cb.max;
        float range = max - min;
        if (range == 0f) {
            for (int i = 0; i < ni; i++) {
                for (int j = 0; j < nj; j++) {
                    java.util.Arrays.fill(data[i][j], min);
                }
            }
        } else {
            for (int i = 0; i < ni; i++) {
                for (int j = 0; j < nj; j++) {
                    for (int k = 0; k < nt; k++) {
                        float v = norm[i][j][k]; // [-1,1]
                        float zeroToOne = (v + 1f) / 2f;
                        data[i][j][k] = min + zeroToOne * range;
                    }
                }
            }
        }
        return data;
    }
}
