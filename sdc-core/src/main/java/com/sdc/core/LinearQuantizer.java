package com.sdc.core;

/**
 * Quantização linear simples de [-1,1] para short (16 bits).
 *
 * NÃO é a versão final de produção; serve como etapa inicial
 * para experimentos e benchmarks.
 */
public final class LinearQuantizer {

    private LinearQuantizer() {}

    // usamos todo o range de short, exceto os extremos absolutos
    private static final float MAX_ABS = 32766f;

    /**
     * Converte amostras normalizadas em [-1,1] para short.
     */
    public static short[] encode(float[] normalized) {
        short[] out = new short[normalized.length];
        for (int i = 0; i < normalized.length; i++) {
            float v = normalized[i];
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            int q = Math.round(v * MAX_ABS);
            out[i] = (short) q;
        }
        return out;
    }

    /**
     * Converte de volta de short para float aproximado em [-1,1].
     */
    public static float[] decode(short[] quantized) {
        float[] out = new float[quantized.length];
        for (int i = 0; i < quantized.length; i++) {
            out[i] = quantized[i] / MAX_ABS;
        }
        return out;
    }

    /**
     * Erro quadrático médio entre dois sinais.
     */
    public static double mse(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("arrays must have same length");
        }
        double acc = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            acc += d * d;
        }
        return acc / a.length;
    }

    /**
     * PSNR simples em dB, considerando valores em [-1,1].
     */
    public static double psnr(float[] original, float[] reconstructed) {
        double mse = mse(original, reconstructed);
        if (mse == 0.0) return Double.POSITIVE_INFINITY;
        double max = 1.0; // max amplitude após normalização
        return 10.0 * Math.log10((max * max) / mse);
    }
}
