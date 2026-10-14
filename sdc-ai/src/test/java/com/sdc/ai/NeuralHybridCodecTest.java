package com.sdc.ai;

import com.sdc.ai.nn.NeuralModel;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NeuralHybridCodecTest {

    private static float[][][] volume(int ni, int nj, int nt, long seed) {
        Random r = new Random(seed);
        float[][][] v = new float[ni][nj][nt];
        for (int i = 0; i < ni; i++)
            for (int j = 0; j < nj; j++)
                for (int k = 0; k < nt; k++)
                    v[i][j][k] = (float) (150 * Math.sin((k + 0.3 * i - 0.2 * j) * 0.4) * Math.exp(-k / 300.0)
                            + r.nextGaussian() * 6);
        return v;
    }

    private static float peak(float[][][] v) {
        float s = 0;
        for (float[][] a : v) for (float[] t : a) for (float x : t) s = Math.max(s, Math.abs(x));
        return s;
    }

    @Test
    void errorIsBoundedByHalfStepForBothPredictors() throws Exception {
        NeuralModel m = NeuralModel.load(NeuralModelParityTest.MODEL);
        float[][][] v = volume(3, 20, 100, 1); // not multiples of 8: exercises padding/cropping
        for (NeuralHybridCodec.Predictor p : NeuralHybridCodec.Predictor.values()) {
            for (int bits : new int[]{6, 10, 16}) {
                NeuralHybridCodec.Settings st = new NeuralHybridCodec.Settings(p, bits, 6);
                byte[] payload = NeuralHybridCodec.encode(v, m, st).payload();
                float[][][] rec = NeuralHybridCodec.decode(payload, m);
                double tol = peak(v) * 0.5 / NeuralHybridCodec.maxQ(bits) * 1.001 + 1e-4;
                for (int i = 0; i < v.length; i++)
                    for (int j = 0; j < v[0].length; j++)
                        for (int k = 0; k < v[0][0].length; k++)
                            assertEquals(v[i][j][k], rec[i][j][k], tol, p + " bits=" + bits);
            }
        }
    }

    @Test
    void reconstructMatchesDecodeExactly() throws Exception {
        NeuralModel m = NeuralModel.load(NeuralModelParityTest.MODEL);
        float[][][] v = volume(2, 16, 64, 2);
        NeuralHybridCodec.Prediction p = NeuralHybridCodec.predict(v, m);
        NeuralHybridCodec.Settings st = new NeuralHybridCodec.Settings(NeuralHybridCodec.Predictor.NEURAL, 8, 6);
        float[][][] dec = NeuralHybridCodec.decode(NeuralHybridCodec.encode(v, p, st).payload(), m);
        assertTrue(java.util.Arrays.deepEquals(dec, NeuralHybridCodec.reconstruct(v, p, 8)));
    }

    @Test
    void payloadIsBoundToItsModel() throws Exception {
        NeuralModel m = NeuralModel.load(NeuralModelParityTest.MODEL);
        float[][][] v = volume(1, 8, 32, 3);
        byte[] payload = NeuralHybridCodec.encode(v, m,
                new NeuralHybridCodec.Settings(NeuralHybridCodec.Predictor.NEURAL, 8, 6)).payload();
        payload[4 + 4 + 12 + 4] ^= 0x01; // flip one bit of the stored model hash
        assertThrows(java.io.IOException.class, () -> NeuralHybridCodec.decode(payload, m));
    }
}
