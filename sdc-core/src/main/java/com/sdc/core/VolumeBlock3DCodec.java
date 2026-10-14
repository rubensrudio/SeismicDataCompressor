package com.sdc.core;

public final class VolumeBlock3DCodec {

    private VolumeBlock3DCodec() {}

    /**
     * Open-loop residual (kept for the legacy decoder): v - mean of the original neighbours
     * (i-1,j,k), (i,j-1,k), (i,j,k-1). Prefer {@link #closedLoopEncode}.
     */
    public static float[][][] residualEncode(float[][][] data) {
        int ni = data.length;
        int nj = data[0].length;
        int nt = data[0][0].length;

        float[][][] res = new float[ni][nj][nt];

        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {

                    float v = data[i][j][k];
                    int count = 0;
                    float sum = 0f;

                    if (i > 0) { sum += data[i-1][j][k]; count++; }
                    if (j > 0) { sum += data[i][j-1][k]; count++; }
                    if (k > 0) { sum += data[i][j][k-1]; count++; }

                    float pred = (count > 0) ? (sum / count) : 0f;
                    res[i][j][k] = v - pred;
                }
            }
        }
        return res;
    }

    public static float[][][] residualDecode(float[][][] residual) {
        int ni = residual.length;
        int nj = residual[0].length;
        int nt = residual[0][0].length;

        float[][][] data = new float[ni][nj][nt];

        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {

                    int count = 0;
                    float sum = 0f;

                    if (i > 0) { sum += data[i-1][j][k]; count++; }
                    if (j > 0) { sum += data[i][j-1][k]; count++; }
                    if (k > 0) { sum += data[i][j][k-1]; count++; }

                    float pred = (count > 0) ? (sum / count) : 0f;
                    data[i][j][k] = residual[i][j][k] + pred;
                }
            }
        }
        return data;
    }

    /**
     * Closed-loop 3D DPCM. Scan order is (i, j, k); the predictor is the mean of the already
     * <em>reconstructed</em> neighbours (i-1,j,k), (i,j-1,k), (i,j,k-1) — the same neighbourhood as
     * {@link #residualEncode}, but evaluated on reconstructed values so the encoder and decoder
     * stay in lock-step and quantization error does not accumulate.
     *
     * @return quantization indices in flatten order
     */
    public static int[] closedLoopEncode(float[][][] norm, int bits) {
        int ni = norm.length, nj = norm[0].length, nt = norm[0][0].length;
        int maxQ = ClosedLoopQuantizer.maxQ(bits);
        float inv = 1.0f / maxQ;
        float[][][] rec = new float[ni][nj][nt];
        int[] q = new int[ni * nj * nt];
        int idx = 0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {
                    float pred = predict(rec, i, j, k);
                    int qi = ClosedLoopQuantizer.quantize(norm[i][j][k] - pred, maxQ);
                    q[idx++] = qi;
                    rec[i][j][k] = ClosedLoopQuantizer.reconstruct(pred, qi, inv);
                }
            }
        }
        return q;
    }

    /** Inverse of {@link #closedLoopEncode}. Returns normalized samples in [-1,1]. */
    public static float[][][] closedLoopDecode(int[] q, int ni, int nj, int nt, int bits) {
        if (q.length != ni * nj * nt) {
            throw new IllegalArgumentException("index count mismatch");
        }
        float inv = 1.0f / ClosedLoopQuantizer.maxQ(bits);
        float[][][] rec = new float[ni][nj][nt];
        int idx = 0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {
                    float pred = predict(rec, i, j, k);
                    rec[i][j][k] = ClosedLoopQuantizer.reconstruct(pred, q[idx++], inv);
                }
            }
        }
        return rec;
    }

    private static float predict(float[][][] rec, int i, int j, int k) {
        int count = 0;
        float sum = 0f;
        if (i > 0) { sum += rec[i - 1][j][k]; count++; }
        if (j > 0) { sum += rec[i][j - 1][k]; count++; }
        if (k > 0) { sum += rec[i][j][k - 1]; count++; }
        return (count > 0) ? (sum / count) : 0f;
    }

    public static float[] flatten3D(float[][][] data) {
        int ni = data.length;
        int nj = data[0].length;
        int nt = data[0][0].length;
        float[] out = new float[ni * nj * nt];
        int idx = 0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                System.arraycopy(data[i][j], 0, out, idx, nt);
                idx += nt;
            }
        }
        return out;
    }

    public static float[][][] unflatten3D(float[] flat, int ni, int nj, int nt) {
        if (flat.length != ni * nj * nt) {
            throw new IllegalArgumentException("flat length mismatch");
        }
        float[][][] data = new float[ni][nj][nt];
        int idx = 0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                System.arraycopy(flat, idx, data[i][j], 0, nt);
                idx += nt;
            }
        }
        return data;
    }
}
