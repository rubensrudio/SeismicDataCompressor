package com.sdc.bench;

import com.sdc.ai.NeuralHybridCodec;
import com.sdc.ai.nn.NeuralModel;
import com.sdc.core.SegyIO;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compares the neural-only reconstruction with the noise-free version of a synthetic preset.
 * The generator draws noise from its own random stream, so a spec with noiseLevel = 0 and the
 * same seed reproduces exactly the reflectors of the noisy volume without the noise.
 *
 * Usage: java -cp sdc-bench.jar com.sdc.bench.CleanSignalCheck [preset=medium] [model=sdc-ai/models/sdc-ae-v1.sdcnn]
 */
public final class CleanSignalCheck {

    private CleanSignalCheck() {}

    public static void main(String[] args) throws Exception {
        SyntheticSegyGenerator.Spec noisy = SyntheticSegyGenerator.preset(args.length > 0 ? args[0] : "medium");
        NeuralModel model = NeuralModel.load(Path.of(args.length > 1 ? args[1] : "sdc-ai/models/sdc-ae-v1.sdcnn"));
        SyntheticSegyGenerator.Spec clean = new SyntheticSegyGenerator.Spec(noisy.name() + "-clean", noisy.inlines(),
                noisy.xlines(), noisy.samples(), noisy.sampleIntervalUs(), noisy.seed(), 0.0, null);

        Path dir = Files.createTempDirectory("sdc-clean-");
        Path n = dir.resolve("noisy.sgy"), c = dir.resolve("clean.sgy");
        SyntheticSegyGenerator.write(noisy, n);
        SyntheticSegyGenerator.write(clean, c);
        float[][][] x = BenchmarkRunner.volumeOf(SegyIO.read(n));
        float[][][] s = BenchmarkRunner.volumeOf(SegyIO.read(c));
        float[][][] r = NeuralHybridCodec.reconstruct(x, NeuralHybridCodec.predict(x, model), 0);

        System.out.printf("noisy input   vs clean signal: %6.2f dB%n", snr(s, x));
        System.out.printf("neural-only   vs noisy input:  %6.2f dB%n", snr(x, r));
        System.out.printf("neural-only   vs clean signal: %6.2f dB%n", snr(s, r));
        Files.delete(n);
        Files.delete(c);
        Files.delete(dir);
    }

    static double snr(float[][][] ref, float[][][] x) {
        double sig = 0, err = 0;
        for (int i = 0; i < ref.length; i++)
            for (int j = 0; j < ref[0].length; j++)
                for (int k = 0; k < ref[0][0].length; k++) {
                    double a = ref[i][j][k], d = a - x[i][j][k];
                    sig += a * a;
                    err += d * d;
                }
        return 10 * Math.log10(sig / err);
    }
}
