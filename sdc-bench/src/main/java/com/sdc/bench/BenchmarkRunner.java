package com.sdc.bench;

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
 * Benchmark reprodutível do SDC.
 *
 * Para cada dataset mede, com warmup + N iterações:
 *  - codec 2D  (SdcFileWriter.writeCompressed / SdcFileReader.readAllCompressed) × 3 profiles
 *  - codec 3D  (Sdc3DFileWriter.write3D / Sdc3DFileReader.read3D)               × 3 profiles
 *  - baselines lossless: Deflate 6, Deflate 9 e byte-shuffle + Deflate 9 sobre float32 brutos
 *
 * O parse do SEG-Y é feito uma única vez e fica fora das medições; os tempos cobrem
 * a codificação/decodificação do container .sdc (incluindo a escrita/leitura do arquivo).
 *
 * Saídas em --out: results.json, results.csv, summary.md.
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

        Files.createDirectories(outDir);
        Path work = Files.createTempDirectory("sdc-bench-");

        log("SDC benchmark | warmup=%d iterations=%d block=%s out=%s", warmup, iterations, Arrays.toString(block), outDir);

        List<DatasetInfo> datasets = new ArrayList<>();
        List<Result> results = new ArrayList<>();

        try {
            List<Object[]> inputs = new ArrayList<>();
            for (String p : presets) {
                if (p.isBlank() || p.equalsIgnoreCase("none")) continue;
                SyntheticSegyGenerator.Spec spec = SyntheticSegyGenerator.preset(p);
                Path file = dataDir.resolve(spec.name() + ".sgy");
                if (!Files.exists(file) || Files.size(file) != spec.fileBytes()) {
                    log("Gerando %s (%d x %d x %d) -> %s", spec.name(), spec.inlines(), spec.xlines(), spec.samples(), file);
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
                throw new IllegalArgumentException("nenhum dataset: use --presets e/ou --segy");
            }

            for (Object[] in : inputs) {
                String name = (String) in[0];
                Path file = (Path) in[1];
                String expected = (String) in[3];

                String sha = sha256(file);
                boolean verified = expected != null && expected.equals(sha);
                if (expected != null && !verified) {
                    throw new IllegalStateException("SHA-256 de " + file + " = " + sha + " difere do esperado " + expected
                            + ". O dataset não é o canônico; apague-o e rode novamente.");
                }
                log("Dataset %s  sha256=%s%s", name, sha, verified ? "  [verificado]" : "");

                long t0 = System.nanoTime();
                SegyIO.SegyDataset ds = SegyIO.read(file);
                double readMs = (System.nanoTime() - t0) / 1e6;

                DatasetInfo info = new DatasetInfo(name, file, (String) in[2], Files.size(file), sha, verified,
                        ds.traceCount(), ds.samplesPerTrace, ds.traceGrid.inlineCount(), ds.traceGrid.xlineCount(), readMs);
                datasets.add(info);

                results.addAll(benchDataset(info, ds, work, warmup, iterations, block));
            }
        } finally {
            deleteRecursively(work);
        }

        Map<String, String> env = environment(label, gitCommit, warmup, iterations, block);
        Files.writeString(outDir.resolve("results.json"), toJson(env, datasets, results));
        Files.writeString(outDir.resolve("results.csv"), toCsv(results));
        Files.writeString(outDir.resolve("summary.md"), toMarkdown(env, datasets, results));
        log("Resultados gravados em %s", outDir.toAbsolutePath());
    }

    // ----------------------------------------------------------------------------------------

    private static List<Result> benchDataset(DatasetInfo info, SegyIO.SegyDataset ds, Path work,
                                             int warmup, int iterations, int[] block) throws Exception {
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

        // ---- Baselines lossless sobre float32 big-endian ----
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
            throw new IllegalStateException(codec + " não reproduziu os bytes originais");
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
            if (traces[i] == null) throw new IllegalStateException("traço " + i + " ausente no volume 3D");
        }
        return Arrays.asList(traces);
    }

    // ---------------------------------- baselines helpers -----------------------------------

    private static byte[] rawSampleBytes(SegyIO.SegyDataset ds) {
        ByteBuffer bb = ByteBuffer.allocate(ds.traceCount() * ds.samplesPerTrace * 4); // big-endian
        for (TraceBlock tb : ds.traces) {
            for (float v : tb.samples()) bb.putFloat(v);
        }
        return bb.array();
    }

    /** Byte-shuffle (estilo Blosc/HDF5): agrupa o byte k de cada float32. */
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
        // só flags de VM relevantes (-X / -XX); evita vazar propriedades de ambiente (proxy, etc.)
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
            // cai no fallback
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
        sb.append("## Ambiente\n\n| Campo | Valor |\n|---|---|\n");
        env.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v.isEmpty() ? "—" : v.replace("|", "\\|")).append(" |\n"));

        sb.append("\n## Datasets\n\n| Dataset | Origem | Grade (IL×XL×amostras) | Traços | Tamanho SEG-Y | SHA-256 | Verificado |\n|---|---|---|---|---|---|---|\n");
        for (DatasetInfo d : datasets) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %d × %d × %d | %d | %.2f MiB | `%s` | %s |\n",
                    d.name(), d.source(), d.inlines(), d.xlines(), d.samplesPerTrace(), d.traces(),
                    d.fileBytes() / 1048576.0, d.sha256(), d.checksumVerified() ? "sim" : "n/a"));
        }

        for (DatasetInfo d : datasets) {
            sb.append("\n## Resultados — ").append(d.name()).append("\n\n");
            sb.append("Razão = bytes brutos dos traços (float32) / bytes do arquivo comprimido. Throughput sobre os bytes brutos, pela mediana.\n\n");
            sb.append("| Codec | Profile | Bits | Razão | bits/amostra | Compressão (ms) | Descompressão (ms) | Comp. MiB/s | Desc. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Erro abs. máx. |\n");
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
            if (!a.startsWith("--")) throw new IllegalArgumentException("argumento inesperado: " + a);
            String key = a.substring(2);
            String value = "true";
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            } else if (!key.equals("help") && i + 1 < args.length) {
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
                Uso: java -jar sdc-bench-jar-with-dependencies.jar [opções]

                  --presets tiny,small,medium   datasets sintéticos (default: small,medium; 'none' desativa)
                  --segy <arquivo.sgy>          SEG-Y externo adicional (repetível; formato 1 ou 5)
                  --data-dir <dir>              onde gerar/ler os datasets (default: benchmark/data)
                  --out <dir>                   diretório de saída (default: benchmark/results/local)
                  --warmup <n>                  iterações de aquecimento descartadas (default: 2)
                  --iterations <n>              iterações medidas (default: 5)
                  --block il,xl,t               bloco 3D (default: 8,8,0; 0 = tempo inteiro)
                  --label <texto>               rótulo do run
                  --git-commit <sha>            commit avaliado (preenchido pelo script)
                """);
    }
}
