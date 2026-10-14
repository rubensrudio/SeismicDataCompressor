package com.sdc.bench;

import com.sdc.ai.NeuralHybridCodec;
import com.sdc.ai.nn.NeuralModel;
import com.sdc.core.CompressionProfile;
import com.sdc.core.Sdc3DFileReader;
import com.sdc.core.Sdc3DFileWriter;
import com.sdc.core.SdcFileReader;
import com.sdc.core.SdcFileWriter;
import com.sdc.core.SegyIO;
import com.sdc.core.TraceBlock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SDC reproducible benchmark.
 *
 * For each dataset it measures, with warmup + N iterations:
 *  - codec 2D  (SdcFileWriter.writeCompressed / SdcFileReader.readAllCompressed) × 3 profiles
 *  - codec 3D  (Sdc3DFileWriter.write3D / Sdc3DFileReader.read3D)               × 3 profiles
 *  - lossless baselines: Deflate 6, Deflate 9 and byte-shuffle + Deflate 9 on raw float32
 *  - when a neural model is available (--model): neural-hybrid (autoencoder prediction + quantized
 *    residual) and uniform-quant (same residual quantizer with zero prediction) × 3 profiles
 *  - with --rd: a rate–distortion sweep over bit depths for sdc-3d, uniform-quant and neural-hybrid
 *
 * The SEG-Y is parsed only once and stays outside the measurements; timings cover
 * encoding/decoding of the .sdc container (including writing/reading the file).
 *
 * Outputs in --out: results.json, results.csv, summary.md.
 */
public final class BenchmarkRunner {

    private static final List<String> PROFILES = List.of("HIGH_QUALITY", "BALANCED", "HIGH_COMPRESSION");

    record Result(String dataset, String codec, String profile, int effectiveBits, int deflaterLevel,
                  long rawBytes, long segyBytes, long outputBytes, Timing compress, Timing decompress,
                  Quality quality, String outputSha256) {

        double ratio() {
            return (double) rawBytes / outputBytes;
        }

        double bitsPerSample() {
            return outputBytes * 8.0 / (rawBytes / 4.0);
        }
    }

    record RdPoint(String dataset, String codec, int bits, long rawBytes, long outputBytes,
                   long latentBytes, long residualBytes, Quality quality) {
        double bitsPerSample() {
            return outputBytes * 8.0 / (rawBytes / 4.0);
        }
    }

    record DatasetInfo(String name, Path path, String source, long fileBytes, String sha256, boolean checksumVerified,
                       int traces, int samplesPerTrace, int inlines, int xlines, double segyReadMs) {}

    // ----------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Map<String, List<String>> opts = parseArgs(args);
        if (opts.containsKey("help")) {
            printHelp();
            return;
        }

        List<String> presets = split(first(opts, "presets", "small,medium"));
        List<String> segyFiles = opts.getOrDefault("segy", List.of());
        Path dataDir = Path.of(first(opts, "data-dir", "benchmark/data"));
        Path outDir = Path.of(first(opts, "out", "benchmark/results/local"));
        int warmup = Integer.parseInt(first(opts, "warmup", "2"));
        int iterations = Integer.parseInt(first(opts, "iterations", "5"));
        int[] block = Arrays.stream(first(opts, "block", "8,8,0").split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        String label = first(opts, "label", "local");
        String gitCommit = first(opts, "git-commit", "unknown");
        NeuralModel model = loadModel(first(opts, "model", "auto"));
        boolean rd = opts.containsKey("rd");
        int[] rdBits = Arrays.stream(first(opts, "rd-bits", "4,5,6,7,8,9,10,11,12,14,16").split(","))
                .mapToInt(x -> Integer.parseInt(x.trim())).toArray();

        Files.createDirectories(outDir);
        Path work = Files.createTempDirectory("sdc-bench-");

        log("SDC benchmark | warmup=%d iterations=%d block=%s out=%s", warmup, iterations, Arrays.toString(block), outDir);

        List<DatasetInfo> datasets = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        List<RdPoint> rdPoints = new ArrayList<>();

        try {
            List<Object[]> inputs = new ArrayList<>();
            for (String p : presets) {
                if (p.isBlank() || p.equalsIgnoreCase("none")) continue;
                SyntheticSegyGenerator.Spec spec = SyntheticSegyGenerator.preset(p);
                Path file = dataDir.resolve(spec.name() + ".sgy");
                if (!Files.exists(file) || Files.size(file) != spec.fileBytes()) {
                    log("Generating %s (%d x %d x %d) -> %s", spec.name(), spec.inlines(), spec.xlines(), spec.samples(), file);
                    SyntheticSegyGenerator.write(spec, file);
                }
                inputs.add(new Object[]{spec.name(), file, "synthetic seed=" + spec.seed(), spec.expectedSha256()});
            }
            for (String s : segyFiles) {
                Path file = Path.of(s);
                String name = file.getFileName().toString().replaceFirst("\\.(sgy|segy)$", "");
                inputs.add(new Object[]{name, file, "external file", null});
            }
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("no datasets: use --presets and/or --segy");
            }

            for (Object[] in : inputs) {
                String name = (String) in[0];
                Path file = (Path) in[1];
                String expected = (String) in[3];

                String sha = sha256(file);
                boolean verified = expected != null && expected.equals(sha);
                if (expected != null && !verified) {
                    throw new IllegalStateException("SHA-256 of " + file + " = " + sha + " differs from expected " + expected
                            + ". The dataset is not the canonical one; delete it and run again.");
                }
                log("Dataset %s  sha256=%s%s", name, sha, verified ? "  [verified]" : "");

                long t0 = System.nanoTime();
                SegyIO.SegyDataset ds = SegyIO.read(file);
                double readMs = (System.nanoTime() - t0) / 1e6;

                DatasetInfo info = new DatasetInfo(name, file, (String) in[2], Files.size(file), sha, verified,
                        ds.traceCount(), ds.samplesPerTrace, ds.traceGrid.inlineCount(), ds.traceGrid.xlineCount(), readMs);
                datasets.add(info);

                results.addAll(benchDataset(info, ds, work, warmup, iterations, block, model));
                if (rd) {
                    rdPoints.addAll(rdSweep(info, ds, work, block, model, rdBits));
                }
            }
        } finally {
            deleteRecursively(work);
        }

        Map<String, String> env = environment(label, gitCommit, warmup, iterations, block);
        env.put("neuralModel", model == null ? "none" : model.sha256Hex());
        env.put("neuralModelMetadata", model == null ? "" : model.metadataJson());
        env.put("neuralTiming", "warmup=" + Math.min(warmup, 1) + " iterations=" + Math.min(iterations, 2)
                + " (neural codecs are slower; fewer repetitions)");
        Files.writeString(outDir.resolve("results.json"), toJson(env, datasets, results));
        Files.writeString(outDir.resolve("results.csv"), toCsv(results));
        if (!rdPoints.isEmpty()) {
            Files.writeString(outDir.resolve("rd.csv"), toRdCsv(rdPoints));
        }
        Files.writeString(outDir.resolve("summary.md"), toMarkdown(env, datasets, results) + rdMarkdown(rdPoints));
        log("Results written to %s", outDir.toAbsolutePath());
    }

    // ----------------------------------------------------------------------------------------

    private static List<Result> benchDataset(DatasetInfo info, SegyIO.SegyDataset ds, Path work,
                                             int warmup, int iterations, int[] block,
                                             NeuralModel model) throws Exception {
        List<Result> out = new ArrayList<>();
        long raw = (long) ds.traceCount() * ds.samplesPerTrace * 4L;
        List<float[]> original = ds.traces.stream().map(TraceBlock::samples).collect(Collectors.toList());

        // ---- SDC 2D (v2) ----
        for (String pname : PROFILES) {
            CompressionProfile profile = CompressionProfile.fromProfileName(pname);
            Path sdc = work.resolve(info.name() + "-2d-" + pname + ".sdc");
            Timing c = Timing.measure(warmup, iterations, () -> SdcFileWriter.writeCompressed(sdc, ds.traces, profile));
            List<List<TraceBlock>> holder = new ArrayList<>(1);
            holder.add(null);
            Timing d = Timing.measure(warmup, iterations, () -> holder.set(0, SdcFileReader.readAllCompressed(sdc)));
            List<float[]> rec = holder.get(0).stream().map(TraceBlock::samples).collect(Collectors.toList());
            Result r = new Result(info.name(), "sdc-2d", pname, profile.effectiveBits(), profile.deflaterLevel(),
                    raw, info.fileBytes(), Files.size(sdc), c, d, Quality.compare(original, rec), sha256(sdc));
            out.add(r);
            logResult(r);
        }

        // ---- SDC 3D (v3) ----
        for (String pname : PROFILES) {
            CompressionProfile profile = CompressionProfile.fromProfileName(pname);
            Path sdc = work.resolve(info.name() + "-3d-" + pname + ".sdc");
            Timing c = Timing.measure(warmup, iterations,
                    () -> Sdc3DFileWriter.write3D(sdc, ds, profile, block[0], block[1], block[2]));
            Sdc3DFileReader.Sdc3DVolume[] holder = new Sdc3DFileReader.Sdc3DVolume[1];
            Timing d = Timing.measure(warmup, iterations, () -> holder[0] = Sdc3DFileReader.read3D(sdc, profile));
            List<float[]> rec = tracesFromVolume(ds, holder[0]);
            Result r = new Result(info.name(), "sdc-3d", pname, profile.effectiveBits(), profile.deflaterLevel(),
                    raw, info.fileBytes(), Files.size(sdc), c, d, Quality.compare(original, rec), sha256(sdc));
            out.add(r);
            logResult(r);
        }

        // ---- Neural hybrid and its zero-prediction twin (same residual quantizer) ----
        if (model != null) {
            float[][][] vol = volumeOf(ds);
            int nw = Math.min(warmup, 1), nit = Math.min(iterations, 2);
            for (NeuralHybridCodec.Predictor pred : NeuralHybridCodec.Predictor.values()) {
                String codec = pred == NeuralHybridCodec.Predictor.NEURAL ? "neural-hybrid" : "uniform-quant";
                for (String pname : PROFILES) {
                    CompressionProfile profile = CompressionProfile.fromProfileName(pname);
                    NeuralHybridCodec.Settings st = new NeuralHybridCodec.Settings(
                            pred, profile.effectiveBits(), profile.deflaterLevel());
                    Path file = work.resolve(info.name() + "-" + codec + "-" + pname + ".sdcn");
                    Timing c = Timing.measure(nw, nit, () ->
                            Files.write(file, NeuralHybridCodec.encode(vol, model, st).payload()));
                    float[][][][] holder = new float[1][][][];
                    Timing d = Timing.measure(nw, nit, () ->
                            holder[0] = NeuralHybridCodec.decode(Files.readAllBytes(file), model));
                    Result r = new Result(info.name(), codec, pname, profile.effectiveBits(), profile.deflaterLevel(),
                            raw, info.fileBytes(), Files.size(file), c, d,
                            Quality.compare(original, tracesFromArray(ds, holder[0])), sha256(file));
                    out.add(r);
                    logResult(r);
                }
            }
        }

        // ---- Lossless baselines on big-endian float32 ----
        byte[] rawBytes = rawSampleBytes(ds);
        out.add(baseline(info, "deflate-6", rawBytes, false, 6, warmup, iterations));
        out.add(baseline(info, "deflate-9", rawBytes, false, 9, warmup, iterations));
        out.add(baseline(info, "shuffle+deflate-9", rawBytes, true, 9, warmup, iterations));
        return out;
    }

    private static Result baseline(DatasetInfo info, String codec, byte[] raw, boolean shuffle, int level,
                                   int warmup, int iterations) throws Exception {
        byte[][] comp = new byte[1][];
        Timing c = Timing.measure(warmup, iterations, () -> comp[0] = deflate(shuffle ? shuffle4(raw) : raw, level));
        byte[][] dec = new byte[1][];
        Timing d = Timing.measure(warmup, iterations, () -> {
            byte[] inflated = inflate(comp[0], raw.length);
            dec[0] = shuffle ? unshuffle4(inflated) : inflated;
        });
        if (!Arrays.equals(raw, dec[0])) {
            throw new IllegalStateException(codec + " did not reproduce the original bytes");
        }
        Result r = new Result(info.name(), codec, "LOSSLESS", 32, level, raw.length, info.fileBytes(), comp[0].length,
                c, d, Quality.lossless(raw.length / 4), sha256(comp[0]));
        logResult(r);
        return r;
    }

    private static List<float[]> tracesFromVolume(SegyIO.SegyDataset ds, Sdc3DFileReader.Sdc3DVolume vol) {
        float[][] traces = new float[ds.traceCount()][];
        for (int ii = 0; ii < ds.traceGrid.inlineCount(); ii++) {
            for (int jj = 0; jj < ds.traceGrid.xlineCount(); jj++) {
                int idx = ds.traceGrid.traceIndexAt(ii, jj);
                if (idx >= 0) traces[idx] = vol.data[ii][jj];
            }
        }
        for (int i = 0; i < traces.length; i++) {
            if (traces[i] == null) throw new IllegalStateException("trace " + i + " missing from the 3D volume");
        }
        return Arrays.asList(traces);
    }


    // ---------------------------------- neural / rate–distortion ---------------------------

    private static NeuralModel loadModel(String spec) throws IOException {
        if (spec.equalsIgnoreCase("none")) return null;
        Path p = spec.equalsIgnoreCase("auto") ? Path.of("sdc-ai/models/sdc-ae-v1.sdcnn") : Path.of(spec);
        if (!Files.exists(p)) {
            if (spec.equalsIgnoreCase("auto")) {
                log("No neural model at %s: neural codecs skipped (use --model <file.sdcnn>)", p);
                return null;
            }
            throw new IllegalArgumentException("model not found: " + p);
        }
        NeuralModel m = NeuralModel.load(p);
        log("Neural model %s sha256=%s", p, m.sha256Hex());
        return m;
    }

    /** Volume as [inline][xline][time]; absent traces stay zero. */
    static float[][][] volumeOf(SegyIO.SegyDataset ds) {
        int ni = ds.traceGrid.inlineCount(), nj = ds.traceGrid.xlineCount();
        float[][][] vol = new float[ni][nj][];
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                int idx = ds.traceGrid.traceIndexAt(i, j);
                vol[i][j] = idx >= 0 ? ds.traces.get(idx).samples().clone() : new float[ds.samplesPerTrace];
            }
        }
        return vol;
    }

    static List<float[]> tracesFromArray(SegyIO.SegyDataset ds, float[][][] vol) {
        float[][] traces = new float[ds.traceCount()][];
        for (int ii = 0; ii < ds.traceGrid.inlineCount(); ii++) {
            for (int jj = 0; jj < ds.traceGrid.xlineCount(); jj++) {
                int idx = ds.traceGrid.traceIndexAt(ii, jj);
                if (idx >= 0) traces[idx] = vol[ii][jj];
            }
        }
        return Arrays.asList(traces);
    }

    /**
     * Rate–distortion sweep (Deflate level 9 everywhere). sdc-3d points are real files written and
     * read back. For the neural codec the prediction is computed once; sizes come from real
     * encodes, and the reconstruction of the first bit depth is checked bit-for-bit against a
     * real decode before the cheaper {@link NeuralHybridCodec#reconstruct} is used for the rest.
     */
    private static List<RdPoint> rdSweep(DatasetInfo info, SegyIO.SegyDataset ds, Path work, int[] block,
                                         NeuralModel model, int[] bitsList) throws Exception {
        List<RdPoint> pts = new ArrayList<>();
        long raw = (long) ds.traceCount() * ds.samplesPerTrace * 4L;
        List<float[]> original = ds.traces.stream().map(TraceBlock::samples).collect(Collectors.toList());
        log("Rate-distortion sweep on %s, bits=%s", info.name(), Arrays.toString(bitsList));

        for (int bits : bitsList) {
            CompressionProfile profile = new CompressionProfile(bits, 9, 100.0);
            Path sdc = work.resolve(info.name() + "-rd-3d-" + bits + ".sdc");
            Sdc3DFileWriter.write3D(sdc, ds, profile, block[0], block[1], block[2]);
            Sdc3DFileReader.Sdc3DVolume v = Sdc3DFileReader.read3D(sdc, profile);
            long size = Files.size(sdc);
            pts.add(new RdPoint(info.name(), "sdc-3d", bits, raw, size, 0, size, Quality.compare(original, tracesFromVolume(ds, v))));
        }

        float[][][] vol = volumeOf(ds);
        List<NeuralHybridCodec.Prediction> preds = new ArrayList<>();
        preds.add(NeuralHybridCodec.predict(vol, null));
        if (model != null) {
            long t0 = System.nanoTime();
            preds.add(NeuralHybridCodec.predict(vol, model));
            log("  neural prediction computed in %.1f s", (System.nanoTime() - t0) / 1e9);
        }
        for (NeuralHybridCodec.Prediction p : preds) {
            String codec = p.predictor() == NeuralHybridCodec.Predictor.NEURAL ? "neural-hybrid" : "uniform-quant";
            boolean checked = false;
            for (int bits : bitsList) {
                NeuralHybridCodec.Settings st = new NeuralHybridCodec.Settings(p.predictor(), bits, 9);
                NeuralHybridCodec.Encoded e = NeuralHybridCodec.encode(vol, p, st);
                float[][][] rec = NeuralHybridCodec.reconstruct(vol, p, bits);
                if (!checked) {
                    float[][][] dec = NeuralHybridCodec.decode(e.payload(), model);
                    if (!Arrays.deepEquals(dec, rec)) {
                        throw new IllegalStateException(codec + ": reconstruct() differs from decode()");
                    }
                    checked = true;
                }
                pts.add(new RdPoint(info.name(), codec, bits, raw, e.payload().length, e.latentBytes(), e.residualBytes(),
                        Quality.compare(original, tracesFromArray(ds, rec))));
            }
            if (p.predictor() == NeuralHybridCodec.Predictor.NEURAL) {
                NeuralHybridCodec.Encoded e = NeuralHybridCodec.encode(vol, p,
                        new NeuralHybridCodec.Settings(p.predictor(), 0, 9));
                float[][][] dec = NeuralHybridCodec.decode(e.payload(), model);
                pts.add(new RdPoint(info.name(), "neural-only", 0, raw, e.payload().length, e.latentBytes(), 0,
                        Quality.compare(original, tracesFromArray(ds, dec))));
            }
        }
        for (RdPoint pt : pts) {
            log("  rd %-14s bits=%2d  %6.3f bits/sample  SNR=%6.2f dB", pt.codec(), pt.bits(), pt.bitsPerSample(), pt.quality().snrDb());
        }
        return pts;
    }

    private static String toRdCsv(List<RdPoint> pts) {
        StringBuilder sb = new StringBuilder("dataset,codec,bits,raw_bytes,output_bytes,latent_bytes,residual_bytes,"
                + "bits_per_sample,compression_ratio,snr_db,psnr_db,nrmse_percent,max_abs_error\n");
        for (RdPoint p : pts) {
            Quality q = p.quality();
            sb.append(String.join(",", p.dataset(), p.codec(), String.valueOf(p.bits()), String.valueOf(p.rawBytes()),
                    String.valueOf(p.outputBytes()), String.valueOf(p.latentBytes()), String.valueOf(p.residualBytes()),
                    csvNum(p.bitsPerSample()), csvNum((double) p.rawBytes() / p.outputBytes()),
                    csvNum(q.snrDb()), csvNum(q.psnrDb()), csvNum(q.nrmsePercent()), csvNum(q.maxAbsError()))).append('\n');
        }
        return sb.toString();
    }

    private static String rdMarkdown(List<RdPoint> pts) {
        if (pts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n## Rate–distortion sweep (Deflate 9)\n\n");
        sb.append("bits/sample over the raw float32 samples; SNR on the original amplitudes. ")
          .append("neural-hybrid = autoencoder prediction + quantized residual; uniform-quant = same quantizer, zero prediction.\n\n");
        sb.append("| Dataset | Codec | Bits | bits/sample | Ratio | SNR (dB) | Max abs. error | Latent bytes | Residual bytes |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (RdPoint p : pts) {
            Quality q = p.quality();
            sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %.3f | %.2f:1 | %s | %s | %d | %d |\n",
                    p.dataset(), p.codec(), p.bits() == 0 ? "—" : String.valueOf(p.bits()), p.bitsPerSample(),
                    (double) p.rawBytes() / p.outputBytes(), fmtDb(q.snrDb()), fmt(q.maxAbsError(), "%.4g"),
                    p.latentBytes(), p.residualBytes()));
        }
        return sb.toString();
    }

    // ---------------------------------- baselines helpers -----------------------------------

    private static byte[] rawSampleBytes(SegyIO.SegyDataset ds) {
        ByteBuffer bb = ByteBuffer.allocate(ds.traceCount() * ds.samplesPerTrace * 4); // big-endian
        for (TraceBlock tb : ds.traces) {
            for (float v : tb.samples()) bb.putFloat(v);
        }
        return bb.array();
    }

    /** Byte-shuffle (Blosc/HDF5 style): groups byte k of every float32 together. */
    static byte[] shuffle4(byte[] in) {
        int n = in.length / 4;
        byte[] out = new byte[in.length];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) out[k * n + i] = in[i * 4 + k];
        }
        return out;
    }

    static byte[] unshuffle4(byte[] in) {
        int n = in.length / 4;
        byte[] out = new byte[in.length];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) out[i * 4 + k] = in[k * n + i];
        }
        return out;
    }

    private static byte[] deflate(byte[] input, int level) {
        Deflater d = new Deflater(level);
        try {
            d.setInput(input);
            d.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length / 2);
            byte[] buf = new byte[1 << 16];
            while (!d.finished()) {
                int n = d.deflate(buf);
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            d.end();
        }
    }

    private static byte[] inflate(byte[] input, int expected) throws Exception {
        Inflater inf = new Inflater();
        try {
            inf.setInput(input);
            byte[] out = new byte[expected];
            int off = 0;
            while (!inf.finished() && off < expected) {
                off += inf.inflate(out, off, expected - off);
            }
            return out;
        } finally {
            inf.end();
        }
    }

    // ---------------------------------- environment ----------------------------------------

    private static Map<String, String> environment(String label, String gitCommit, int warmup, int iterations, int[] block) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("label", label);
        env.put("timestampUtc", Instant.now().toString());
        env.put("gitCommit", gitCommit);
        env.put("cpuModel", cpuModel());
        env.put("availableProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        env.put("maxHeapMiB", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        env.put("osName", System.getProperty("os.name"));
        env.put("osVersion", System.getProperty("os.version"));
        env.put("osArch", System.getProperty("os.arch"));
        env.put("javaVersion", System.getProperty("java.version"));
        env.put("javaVendor", System.getProperty("java.vendor"));
        env.put("jvmName", System.getProperty("java.vm.name"));
        // only relevant VM flags (-X / -XX); avoids leaking environment properties (proxy, etc.)
        env.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-X")).collect(Collectors.joining(" ")));
        env.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(b -> b.getName()).collect(Collectors.joining(", ")));
        env.put("warmupIterations", String.valueOf(warmup));
        env.put("measuredIterations", String.valueOf(iterations));
        env.put("block3D", block[0] + "x" + block[1] + "x" + (block[2] <= 0 ? "full" : String.valueOf(block[2])));
        return env;
    }

    private static String cpuModel() {
        Path cpuinfo = Path.of("/proc/cpuinfo");
        try {
            if (Files.isReadable(cpuinfo)) {
                for (String line : Files.readAllLines(cpuinfo)) {
                    if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException ignored) {
            // fall through to the fallback
        }
        String env = System.getenv("PROCESSOR_IDENTIFIER"); // Windows
        return env != null ? env : "unknown";
    }

    // ---------------------------------- output ----------------------------------------------

    private static String toJson(Map<String, String> env, List<DatasetInfo> datasets, List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"environment\": {\n");
        int i = 0;
        for (Map.Entry<String, String> e : env.entrySet()) {
            sb.append("    ").append(q(e.getKey())).append(": ").append(q(e.getValue()));
            sb.append(++i < env.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"datasets\": [\n");
        for (int d = 0; d < datasets.size(); d++) {
            DatasetInfo ds = datasets.get(d);
            sb.append("    {")
              .append("\"name\": ").append(q(ds.name()))
              .append(", \"source\": ").append(q(ds.source()))
              .append(", \"fileBytes\": ").append(ds.fileBytes())
              .append(", \"sha256\": ").append(q(ds.sha256()))
              .append(", \"checksumVerified\": ").append(ds.checksumVerified())
              .append(", \"traces\": ").append(ds.traces())
              .append(", \"samplesPerTrace\": ").append(ds.samplesPerTrace())
              .append(", \"inlines\": ").append(ds.inlines())
              .append(", \"xlines\": ").append(ds.xlines())
              .append(", \"segyReadMs\": ").append(num(ds.segyReadMs()))
              .append("}").append(d + 1 < datasets.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"results\": [\n");
        for (int r = 0; r < results.size(); r++) {
            Result x = results.get(r);
            Quality qa = x.quality();
            sb.append("    {")
              .append("\"dataset\": ").append(q(x.dataset()))
              .append(", \"codec\": ").append(q(x.codec()))
              .append(", \"profile\": ").append(q(x.profile()))
              .append(", \"effectiveBits\": ").append(x.effectiveBits())
              .append(", \"deflaterLevel\": ").append(x.deflaterLevel())
              .append(", \"rawBytes\": ").append(x.rawBytes())
              .append(", \"outputBytes\": ").append(x.outputBytes())
              .append(", \"compressionRatio\": ").append(num(x.ratio()))
              .append(", \"bitsPerSample\": ").append(num(x.bitsPerSample()))
              .append(", \"compressMs\": ").append(timingJson(x.compress()))
              .append(", \"decompressMs\": ").append(timingJson(x.decompress()))
              .append(", \"compressMiBs\": ").append(num(x.compress().throughputMiBs(x.rawBytes())))
              .append(", \"decompressMiBs\": ").append(num(x.decompress().throughputMiBs(x.rawBytes())))
              .append(", \"snrDb\": ").append(num(qa.snrDb()))
              .append(", \"psnrDb\": ").append(num(qa.psnrDb()))
              .append(", \"nrmsePercent\": ").append(num(qa.nrmsePercent()))
              .append(", \"maxAbsError\": ").append(num(qa.maxAbsError()))
              .append(", \"lossless\": ").append(qa.lossless())
              .append(", \"nonFiniteSamples\": ").append(qa.nonFiniteSamples())
              .append(", \"outputSha256\": ").append(q(x.outputSha256()))
              .append("}").append(r + 1 < results.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String timingJson(Timing t) {
        String samples = Arrays.stream(t.nanos()).mapToObj(n -> num(n / 1e6)).collect(Collectors.joining(", "));
        return "{\"median\": " + num(t.medianMs()) + ", \"mean\": " + num(t.meanMs()) + ", \"min\": " + num(t.minMs())
                + ", \"max\": " + num(t.maxMs()) + ", \"stdev\": " + num(t.stdevMs()) + ", \"samples\": [" + samples + "]}";
    }

    private static String toCsv(List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("dataset,codec,profile,effective_bits,deflater_level,raw_bytes,output_bytes,compression_ratio,bits_per_sample,"
                + "compress_median_ms,compress_stdev_ms,decompress_median_ms,decompress_stdev_ms,compress_mib_s,decompress_mib_s,"
                + "snr_db,psnr_db,nrmse_percent,max_abs_error,lossless\n");
        for (Result x : results) {
            Quality qa = x.quality();
            sb.append(String.join(",",
                    x.dataset(), x.codec(), x.profile(), String.valueOf(x.effectiveBits()), String.valueOf(x.deflaterLevel()),
                    String.valueOf(x.rawBytes()), String.valueOf(x.outputBytes()), csvNum(x.ratio()), csvNum(x.bitsPerSample()),
                    csvNum(x.compress().medianMs()), csvNum(x.compress().stdevMs()),
                    csvNum(x.decompress().medianMs()), csvNum(x.decompress().stdevMs()),
                    csvNum(x.compress().throughputMiBs(x.rawBytes())), csvNum(x.decompress().throughputMiBs(x.rawBytes())),
                    csvNum(qa.snrDb()), csvNum(qa.psnrDb()), csvNum(qa.nrmsePercent()), csvNum(qa.maxAbsError()),
                    String.valueOf(qa.lossless()))).append('\n');
        }
        return sb.toString();
    }

    private static String toMarkdown(Map<String, String> env, List<DatasetInfo> datasets, List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SDC Benchmark — ").append(env.get("label")).append("\n\n");
        sb.append("## Environment\n\n| Field | Value |\n|---|---|\n");
        env.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v.isEmpty() ? "—" : v.replace("|", "\\|")).append(" |\n"));

        sb.append("\n## Datasets\n\n| Dataset | Source | Grid (IL×XL×samples) | Traces | SEG-Y size | SHA-256 | Verified |\n|---|---|---|---|---|---|---|\n");
        for (DatasetInfo d : datasets) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %d × %d × %d | %d | %.2f MiB | `%s` | %s |\n",
                    d.name(), d.source(), d.inlines(), d.xlines(), d.samplesPerTrace(), d.traces(),
                    d.fileBytes() / 1048576.0, d.sha256(), d.checksumVerified() ? "yes" : "n/a"));
        }

        for (DatasetInfo d : datasets) {
            sb.append("\n## Results — ").append(d.name()).append("\n\n");
            sb.append("Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.\n\n");
            sb.append("| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |\n");
            sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (Result x : results) {
                if (!x.dataset().equals(d.name())) continue;
                Quality qa = x.quality();
                sb.append(String.format(Locale.ROOT,
                        "| %s | %s | %d | %.2f:1 | %.2f | %.1f ± %.1f | %.1f ± %.1f | %.1f | %.1f | %s | %s | %s | %s |\n",
                        x.codec(), x.profile(), x.effectiveBits(), x.ratio(), x.bitsPerSample(),
                        x.compress().medianMs(), x.compress().stdevMs(),
                        x.decompress().medianMs(), x.decompress().stdevMs(),
                        x.compress().throughputMiBs(x.rawBytes()), x.decompress().throughputMiBs(x.rawBytes()),
                        fmtDb(qa.snrDb()), fmtDb(qa.psnrDb()),
                        qa.lossless() ? "0" : fmt(qa.nrmsePercent(), "%.3f"),
                        qa.lossless() ? "0" : fmt(qa.maxAbsError(), "%.4g")));
            }
        }
        return sb.toString();
    }

    private static String fmtDb(double v) {
        if (v == Double.POSITIVE_INFINITY) return "∞ (lossless)";
        return fmt(v, "%.2f");
    }

    private static String fmt(double v, String pattern) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, pattern, v) : (Double.isNaN(v) ? "NaN" : "∞");
    }

    private static String num(double v) {
        if (!Double.isFinite(v)) return "null";
        return String.format(Locale.ROOT, "%.6g", v);
    }

    private static String csvNum(double v) {
        return Double.isFinite(v) ? num(v) : (Double.isNaN(v) ? "NaN" : "inf");
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void logResult(Result r) {
        double snr = r.quality().snrDb();
        log("  %-18s %-16s ratio=%7.2f:1  comp=%8.1f ms  decomp=%8.1f ms  SNR=%s",
                r.codec(), r.profile(), r.ratio(), r.compress().medianMs(), r.decompress().medianMs(),
                snr == Double.POSITIVE_INFINITY ? "inf (lossless)" : fmt(snr, "%.2f dB"));
    }

    private static void log(String fmt, Object... args) {
        System.out.println("[bench] " + String.format(Locale.ROOT, fmt, args));
    }

    // ---------------------------------- utils -----------------------------------------------

    static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static Map<String, List<String>> parseArgs(String[] args) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) throw new IllegalArgumentException("unexpected argument: " + a);
            String key = a.substring(2);
            String value = "true";
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            } else if (!key.equals("help") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            m.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return m;
    }

    private static String first(Map<String, List<String>> m, String key, String def) {
        List<String> v = m.get(key);
        return (v == null || v.isEmpty()) ? def : v.get(v.size() - 1);
    }

    private static List<String> split(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static void printHelp() {
        System.out.println("""
                Usage: java -jar sdc-bench-jar-with-dependencies.jar [options]

                  --presets tiny,small,medium   synthetic datasets (default: small,medium; 'none' disables)
                  --segy <file.sgy>             additional external SEG-Y (repeatable; format 1 or 5)
                  --data-dir <dir>              where to generate/read datasets (default: benchmark/data)
                  --out <dir>                   output directory (default: benchmark/results/local)
                  --warmup <n>                  discarded warmup iterations (default: 2)
                  --iterations <n>              measured iterations (default: 5)
                  --block il,xl,t               3D block (default: 8,8,0; 0 = full time axis)
                  --label <text>                run label
                  --git-commit <sha>            commit under test (filled in by the script)
                  --model <file.sdcnn|none>     neural model (default: sdc-ai/models/sdc-ae-v1.sdcnn if present)
                  --rd                          also run the rate–distortion sweep (writes rd.csv)
                  --rd-bits 4,6,8,...           bit depths of the sweep (default: 4..12,14,16)
                """);
    }
}
