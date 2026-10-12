package com.sdc.bench;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Generates a synthetic, fully deterministic 3D SEG-Y volume.
 *
 * Geological model:
 *  - N reflectors dipping in inline/xline with a gentle sinusoidal fold
 *  - one planar normal fault (constant vertical throw)
 *  - fixed-frequency Ricker wavelet
 *  - exponential attenuation with time
 *  - additive Gaussian noise
 *
 * Reproducibility:
 *  - java.util.Random has a specified algorithm (48-bit LCG)
 *  - nextGaussian uses StrictMath since Java 17
 *  - all floating-point math uses StrictMath (bit-for-bit identical on any JVM/CPU)
 *  So the same Spec always produces exactly the same bytes, verifiable via SHA-256.
 *
 * SEG-Y layout (what SegyIO reads):
 *  - 3200-byte textual header (ASCII)
 *  - 400-byte binary header (dt, ns, format 5 = IEEE float32)
 *  - per trace: 240-byte header (inline at 189-192, xline at 193-196) + ns big-endian floats
 */
public final class SyntheticSegyGenerator {

    private SyntheticSegyGenerator() {}

    public static final int FIRST_INLINE = 1000;
    public static final int FIRST_XLINE = 2000;

    /**
     * @param expectedSha256 expected hash of the generated file (null = do not verify)
     */
    public record Spec(String name,
                       int inlines,
                       int xlines,
                       int samples,
                       int sampleIntervalUs,
                       long seed,
                       double noiseLevel,
                       String expectedSha256) {

        public int traceCount() {
            return inlines * xlines;
        }

        public long fileBytes() {
            return 3600L + (long) traceCount() * (240L + 4L * samples);
        }
    }

    public static final Spec TINY = new Spec("synthetic-tiny", 8, 8, 128, 4000, 20261012L, 0.02,
            "d18e727556b925d6edecfbd258b9b4b1990e427310a3a9e5959196e50717261c");

    public static final Spec SMALL = new Spec("synthetic-small", 32, 32, 500, 4000, 20261012L, 0.02,
            "0e6036582f8db53226e33c9b4b2e87a72204f6c3b974415d304fe2e8dc7401e7");

    public static final Spec MEDIUM = new Spec("synthetic-medium", 128, 128, 1000, 4000, 20261012L, 0.02,
            "0b6389d128a2a1a08ebd959fbb87ac583ed60beaf473fd3c3d122fa6fd139e26");

    public static Spec preset(String name) {
        return switch (name.trim().toLowerCase()) {
            case "tiny" -> TINY;
            case "small" -> SMALL;
            case "medium" -> MEDIUM;
            default -> throw new IllegalArgumentException("unknown preset: " + name + " (use tiny|small|medium)");
        };
    }

    private record Reflector(double t0, double rc, double dipI, double dipJ,
                             double foldAmp, double foldLenI, double foldLenJ, double foldPhase) {}

    public static void write(Spec spec, Path out) throws IOException {
        Random structure = new Random(spec.seed());
        Random noise = new Random(spec.seed() ^ 0x5DEECE66DL);

        int ns = spec.samples();
        double dtSec = spec.sampleIntervalUs() / 1_000_000.0;

        // ---- reflectors ----
        int reflectorCount = Math.max(8, ns / 25);
        Reflector[] reflectors = new Reflector[reflectorCount];
        double spacing = (double) ns / reflectorCount;
        for (int r = 0; r < reflectorCount; r++) {
            double t0 = (r + 0.5) * spacing + (structure.nextDouble() - 0.5) * spacing * 0.6;
            double rc = (structure.nextDouble() * 2.0 - 1.0) * 0.3;
            double dipI = (structure.nextDouble() * 2.0 - 1.0) * 0.15;   // samples per inline
            double dipJ = (structure.nextDouble() * 2.0 - 1.0) * 0.10;   // samples per xline
            double foldAmp = structure.nextDouble() * 4.0;               // samples
            double foldLenI = 20.0 + structure.nextDouble() * 60.0;
            double foldLenJ = 20.0 + structure.nextDouble() * 60.0;
            double foldPhase = structure.nextDouble() * 2.0 * StrictMath.PI;
            reflectors[r] = new Reflector(t0, rc, dipI, dipJ, foldAmp, foldLenI, foldLenJ, foldPhase);
        }

        // ---- Ricker wavelet (25 Hz), support half-width in samples ----
        double f = 25.0;
        double pf2 = StrictMath.PI * StrictMath.PI * f * f;
        int half = (int) StrictMath.ceil(0.08 / dtSec); // ±80 ms

        // ---- normal fault: plane i = a + b*j, block with i > plane is downthrown ----
        double faultA = spec.inlines() * 0.55;
        double faultB = 0.10;
        double faultThrow = 6.5; // samples

        double amplitudeScale = 1000.0;
        double noiseSigma = spec.noiseLevel() * amplitudeScale * 0.3;
        double attenuationTau = ns * 0.6; // samples

        Files.createDirectories(out.toAbsolutePath().getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 20))) {

            os.write(textualHeader(spec));
            os.write(binaryHeader(spec));

            double[] trace = new double[ns];
            byte[] th = new byte[240];
            int seq = 0;

            for (int i = 0; i < spec.inlines(); i++) {
                for (int j = 0; j < spec.xlines(); j++) {
                    Arrays.fill(trace, 0.0);
                    double shift = (i > faultA + faultB * j) ? faultThrow : 0.0;

                    for (Reflector r : reflectors) {
                        double tr = r.t0 + r.dipI * i + r.dipJ * j + shift
                                + r.foldAmp * StrictMath.sin(2.0 * StrictMath.PI * i / r.foldLenI + r.foldPhase)
                                            * StrictMath.cos(2.0 * StrictMath.PI * j / r.foldLenJ);
                        int center = (int) StrictMath.floor(tr);
                        int k0 = Math.max(0, center - half);
                        int k1 = Math.min(ns - 1, center + half);
                        for (int k = k0; k <= k1; k++) {
                            double tau = (k - tr) * dtSec;
                            double a = pf2 * tau * tau;
                            trace[k] += r.rc * (1.0 - 2.0 * a) * StrictMath.exp(-a);
                        }
                    }

                    seq++;
                    fillTraceHeader(th, seq, FIRST_INLINE + i, FIRST_XLINE + j, spec);
                    os.write(th);
                    for (int k = 0; k < ns; k++) {
                        double gain = amplitudeScale * StrictMath.exp(-k / attenuationTau);
                        double v = trace[k] * gain + noise.nextGaussian() * noiseSigma;
                        os.writeInt(Float.floatToIntBits((float) v));
                    }
                }
            }
        }
    }

    private static byte[] textualHeader(Spec spec) {
        String[] lines = {
                "SDC BENCHMARK - SYNTHETIC 3D SEISMIC VOLUME (DETERMINISTIC)",
                "DATASET " + spec.name(),
                "INLINES " + spec.inlines() + " FIRST " + FIRST_INLINE + "  XLINES " + spec.xlines() + " FIRST " + FIRST_XLINE,
                "SAMPLES " + spec.samples() + "  DT(US) " + spec.sampleIntervalUs() + "  FORMAT 5 IEEE FLOAT32",
                "SEED " + spec.seed() + "  NOISE " + spec.noiseLevel(),
                "MODEL: DIPPING FOLDED REFLECTORS + NORMAL FAULT + RICKER 25HZ + GAUSSIAN NOISE",
                "INLINE BYTES 189-192  XLINE BYTES 193-196",
                "GENERATOR com.sdc.bench.SyntheticSegyGenerator",
        };
        byte[] out = new byte[3200];
        Arrays.fill(out, (byte) ' ');
        for (int l = 0; l < 40; l++) {
            String body = l < lines.length ? lines[l] : "";
            String line = String.format("C%02d %-76s", l + 1, body);
            byte[] b = line.substring(0, 80).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, out, l * 80, 80);
        }
        return out;
    }

    private static byte[] binaryHeader(Spec spec) {
        byte[] b = new byte[400];
        putShort(b, 12, spec.xlines());          // traces per ensemble
        putShort(b, 16, spec.sampleIntervalUs()); // sample interval (us)
        putShort(b, 20, spec.samples());          // samples per trace
        putShort(b, 24, 5);                       // format 5 = IEEE float32
        putShort(b, 26, 1);                       // ensemble fold
        putShort(b, 28, 4);                       // sorting: horizontally stacked
        putShort(b, 54, 1);                       // units: meters
        putShort(b, 300, 0x0100);                 // SEG-Y rev 1
        putShort(b, 302, 1);                      // fixed-length traces
        return b;
    }

    private static void fillTraceHeader(byte[] th, int seq, int inline, int xline, Spec spec) {
        Arrays.fill(th, (byte) 0);
        putInt(th, 0, seq);                       // sequence within line
        putInt(th, 4, seq);                       // sequence within reel
        putInt(th, 8, inline);                    // field record
        putInt(th, 12, xline);                    // trace within field record
        putInt(th, 20, seq);                      // CDP
        putShort(th, 28, 1);                      // trace id code: seismic
        putShort(th, 70, 1);                      // coordinate scalar
        putInt(th, 72, xline * 25);               // source X
        putInt(th, 76, inline * 25);              // source Y
        putShort(th, 114, spec.samples());        // ns
        putShort(th, 116, spec.sampleIntervalUs());
        putInt(th, 180, xline * 25);              // CDP X
        putInt(th, 184, inline * 25);             // CDP Y
        putInt(th, 188, inline);                  // inline
        putInt(th, 192, xline);                   // crossline
    }

    private static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 8);
        b[off + 1] = (byte) v;
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
