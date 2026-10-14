package com.sdc.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ClosedLoopCodecTest {

    private static float[] signal(int n, long seed) {
        Random r = new Random(seed);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            s[i] = (float) (300 * Math.sin(i * 0.3) * Math.exp(-i / 400.0) + r.nextGaussian() * 5);
        }
        return s;
    }

    /** Max error allowed by quantization: half a step, in original units. */
    private static double halfStep(float[] x, int bits) {
        float[] mm = Preprocessing.minMax(x);
        double range = mm[1] - mm[0];
        return range / 2.0 * (0.5 / ClosedLoopQuantizer.maxQ(bits)) * 1.001 + 1e-4;
    }

    @Test
    void traceRoundTripRespectsHalfStepAtEveryBitDepth() {
        float[] x = signal(1000, 7);
        for (int bits : new int[]{4, 6, 8, 10, 12, 16}) {
            CompressionProfile p = new CompressionProfile(bits, 6, 100);
            CompressedTraceBlock cb = TraceBlockCodec.compress(new TraceBlock(1, x), p);
            assertTrue(ClosedLoopQuantizer.isClosedLoop(cb.payload()));
            assertEquals(bits, ClosedLoopQuantizer.storedBits(cb.payload()));
            float[] y = TraceBlockCodec.decompress(cb).samples();
            double tol = halfStep(x, bits);
            for (int i = 0; i < x.length; i++) {
                assertEquals(x[i], y[i], tol, "bits=" + bits + " sample " + i);
            }
        }
    }

    @Test
    void fewerBitsGiveSmallerPayloads() {
        float[] x = signal(2000, 3);
        int prev = Integer.MAX_VALUE;
        for (int bits : new int[]{16, 12, 8, 6}) {
            int size = TraceBlockCodec.compress(new TraceBlock(1, x), new CompressionProfile(bits, 9, 100)).payload().length;
            assertTrue(size < prev, "payload should shrink with fewer bits (bits=" + bits + ")");
            prev = size;
        }
    }

    @Test
    void legacyPayloadIsStillDecoded() {
        float[] x = signal(500, 11);
        // payload exactly as written before the closed-loop revision (16-bit, open-loop delta)
        float[] mm = Preprocessing.minMax(x);
        float[] norm = Preprocessing.normalizeToMinusOneToOne(x);
        short[] q = LinearQuantizer.encode(Preprocessing.deltaEncode(norm), CompressionProfile.defaultHighQuality());
        byte[] legacy = TraceBlockCodec.deflate(TraceBlockCodec.shortsToBytes(q), 9);
        assertEquals((byte) 0x78, legacy[0]);
        float[] y = TraceBlockCodec.decompress(new CompressedTraceBlock(1, mm[0], mm[1], x.length, legacy)).samples();
        double maxErr = 0;
        for (int i = 0; i < x.length; i++) maxErr = Math.max(maxErr, Math.abs(x[i] - y[i]));
        assertTrue(maxErr < 1.0, "legacy decode error " + maxErr);
    }

    @Test
    void volumeRoundTripRespectsHalfStep() {
        int ni = 5, nj = 6, nt = 200;
        float[][][] cube = new float[ni][nj][nt];
        Random r = new Random(5);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < ni; i++)
            for (int j = 0; j < nj; j++)
                for (int k = 0; k < nt; k++) {
                    float v = (float) (200 * Math.sin((k + 0.4 * i + 0.2 * j) * 0.25) + r.nextGaussian() * 4);
                    cube[i][j][k] = v;
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
        for (int bits : new int[]{4, 8, 12, 16}) {
            CompressionProfile p = new CompressionProfile(bits, 6, 100);
            VolumeBlock3DCompressor.CompressedVolumeBlock3D cb =
                    VolumeBlock3DCompressor.compressBlock(new VolumeBlock3D(0, 0, 0, cube), p);
            // decoding must not depend on the caller's profile any more
            float[][][] rec = VolumeBlock3DCompressor.decompressBlock(cb, CompressionProfile.defaultHighQuality());
            double tol = (max - min) / 2.0 * (0.5 / ClosedLoopQuantizer.maxQ(bits)) * 1.001 + 1e-4;
            for (int i = 0; i < ni; i++)
                for (int j = 0; j < nj; j++)
                    for (int k = 0; k < nt; k++)
                        assertEquals(cube[i][j][k], rec[i][j][k], tol, "bits=" + bits);
        }
    }
}
