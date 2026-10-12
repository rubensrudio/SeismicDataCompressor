package com.sdc.bench;

import java.util.List;

/**
 * Fidelity metrics computed on the original (non-normalized) amplitudes.
 *
 *  - SNR  (dB) = 10·log10( Σx² / Σ(x−x̂)² )        — the usual metric in seismic compression
 *  - PSNR (dB) = 20·log10( (max−min) / RMSE )      — peak = dynamic range of the volume
 *  - NRMSE (%) = RMSE / RMS(x) · 100
 *  - maxAbsError = max |x − x̂|
 *
 * Zero error (lossless) gives SNR/PSNR = +∞ and NRMSE = 0.
 */
public record Quality(double snrDb, double psnrDb, double nrmsePercent, double maxAbsError,
                      long samples, long nonFiniteSamples) {

    public boolean lossless() {
        return nonFiniteSamples == 0 && maxAbsError == 0.0;
    }

    public static Quality lossless(long samples) {
        return new Quality(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 0.0, samples, 0);
    }

    public static Quality compare(List<float[]> original, List<float[]> reconstructed) {
        if (original.size() != reconstructed.size()) {
            throw new IllegalArgumentException("trace count mismatch: " + original.size() + " vs " + reconstructed.size());
        }
        double sumSq = 0.0;
        double sumErrSq = 0.0;
        double maxAbs = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long n = 0;
        long nonFinite = 0;

        for (int t = 0; t < original.size(); t++) {
            float[] a = original.get(t);
            float[] b = reconstructed.get(t);
            if (a.length != b.length) {
                throw new IllegalArgumentException("sample count mismatch on trace " + t);
            }
            for (int i = 0; i < a.length; i++) {
                double x = a[i];
                double y = b[i];
                if (x < min) min = x;
                if (x > max) max = x;
                sumSq += x * x;
                n++;
                if (!Double.isFinite(y)) {
                    nonFinite++;
                    continue;
                }
                double d = x - y;
                sumErrSq += d * d;
                double ad = Math.abs(d);
                if (ad > maxAbs) maxAbs = ad;
            }
        }

        if (nonFinite > 0) {
            return new Quality(Double.NaN, Double.NaN, Double.NaN, Double.NaN, n, nonFinite);
        }
        if (sumErrSq == 0.0) {
            return lossless(n);
        }
        double rmse = Math.sqrt(sumErrSq / n);
        double rms = Math.sqrt(sumSq / n);
        double snr = 10.0 * Math.log10(sumSq / sumErrSq);
        double psnr = 20.0 * Math.log10((max - min) / rmse);
        double nrmse = rms > 0 ? rmse / rms * 100.0 : Double.POSITIVE_INFINITY;
        return new Quality(snr, psnr, nrmse, maxAbs, n, 0);
    }
}
