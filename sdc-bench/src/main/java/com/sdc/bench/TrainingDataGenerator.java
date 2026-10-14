package com.sdc.bench;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes synthetic SEG-Y volumes for training the neural codec.
 *
 * Training seeds are {@code seedBase .. seedBase+count-1}. They must never include a benchmark
 * seed ({@link SyntheticSegyGenerator#MEDIUM} etc. use 20261012), so the model is always
 * evaluated on volumes it has not seen.
 *
 * Usage: java -cp sdc-bench.jar com.sdc.bench.TrainingDataGenerator out-dir [count] [seedBase] [inlines] [xlines] [samples] [noise]
 *
 * {@code noise} is the Gaussian noise level relative to the amplitude scale (benchmark presets use 0.02);
 * a different level gives an out-of-distribution test volume for the neural codec.
 */
public final class TrainingDataGenerator {

    private TrainingDataGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: TrainingDataGenerator out-dir [count=8] [seedBase=1000] [inlines=64] [xlines=64] [samples=1000] [noise=0.02]");
            return;
        }
        Path out = Path.of(args[0]);
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        long seedBase = args.length > 2 ? Long.parseLong(args[2]) : 1000L;
        int inlines = args.length > 3 ? Integer.parseInt(args[3]) : 64;
        int xlines = args.length > 4 ? Integer.parseInt(args[4]) : 64;
        int samples = args.length > 5 ? Integer.parseInt(args[5]) : 1000;
        double noise = args.length > 6 ? Double.parseDouble(args[6]) : 0.02;

        Files.createDirectories(out);
        for (int i = 0; i < count; i++) {
            long seed = seedBase + i;
            if (seed == SyntheticSegyGenerator.MEDIUM.seed()) {
                throw new IllegalArgumentException("seed " + seed + " is reserved for the benchmark");
            }
            SyntheticSegyGenerator.Spec spec = new SyntheticSegyGenerator.Spec(
                    (noise == 0.02 ? "train-" : "noise" + Math.round(noise * 100) + "-") + seed,
                    inlines, xlines, samples, 4000, seed, noise, null);
            Path file = out.resolve(spec.name() + ".sgy");
            SyntheticSegyGenerator.write(spec, file);
            System.out.println("wrote " + file + " (seed=" + seed + ")");
        }
    }
}
