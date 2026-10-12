package com.sdc.bench;

import java.util.Arrays;

/**
 * Estatísticas de tempo de N iterações medidas (warmups descartados).
 */
public record Timing(long[] nanos) {

    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }

    /**
     * Executa {@code warmup} iterações descartadas e {@code iterations} medidas.
     * Um System.gc() antes de cada iteração reduz a interferência de coletas pendentes.
     */
    public static Timing measure(int warmup, int iterations, Task task) throws Exception {
        for (int w = 0; w < warmup; w++) {
            task.run();
        }
        long[] ns = new long[iterations];
        for (int it = 0; it < iterations; it++) {
            System.gc();
            long t0 = System.nanoTime();
            task.run();
            ns[it] = System.nanoTime() - t0;
        }
        return new Timing(ns);
    }

    public double medianMs() {
        long[] s = nanos.clone();
        Arrays.sort(s);
        int n = s.length;
        double med = (n % 2 == 1) ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2.0;
        return med / 1e6;
    }

    public double minMs() {
        return Arrays.stream(nanos).min().orElse(0) / 1e6;
    }

    public double maxMs() {
        return Arrays.stream(nanos).max().orElse(0) / 1e6;
    }

    public double meanMs() {
        return Arrays.stream(nanos).average().orElse(0) / 1e6;
    }

    public double stdevMs() {
        if (nanos.length < 2) return 0.0;
        double mean = Arrays.stream(nanos).average().orElse(0);
        double acc = 0.0;
        for (long v : nanos) acc += (v - mean) * (v - mean);
        return Math.sqrt(acc / (nanos.length - 1)) / 1e6;
    }

    /** Throughput em MiB/s sobre {@code bytes} (dados brutos dos traços), usando a mediana. */
    public double throughputMiBs(long bytes) {
        double sec = medianMs() / 1e3;
        return sec > 0 ? (bytes / (1024.0 * 1024.0)) / sec : Double.POSITIVE_INFINITY;
    }
}
