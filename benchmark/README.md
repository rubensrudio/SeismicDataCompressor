# SDC Reproducible Benchmark

This directory has everything needed to measure the compressor reproducibly: the **script**, the **dataset** (generated deterministically and verified by SHA-256), the **hardware/software capture** and the **results** of each run.

```
benchmark/
├── run-benchmark.sh          # entry point: build + hardware + benchmark
├── README.md                 # this file (methodology + reference results)
├── data/                     # generated datasets (git-ignored; recreated bit for bit)
└── results/
    └── <date>_<label>/       # one directory per run
        ├── hardware.txt      # CPU, memory, cgroup, disk, kernel, JDK, Maven, commit
        ├── results.json      # everything, including every timing sample
        ├── results.csv       # one row per (dataset, codec, profile)
        ├── summary.md        # ready-to-read tables
        └── run.log           # console output
```

The code lives in the [`sdc-bench`](../sdc-bench) Maven module, which depends only on `sdc-core` (no TensorFlow).

## How to run

Prerequisites: JDK 17+ and Maven 3.9 (same as the rest of the project). About 3 GiB of free RAM for the `medium` preset.

```bash
./benchmark/run-benchmark.sh
```

This builds `sdc-core` + `sdc-bench`, generates the datasets (`small` and `medium`) if needed, checks each one's SHA-256, runs the benchmark and writes to `benchmark/results/<YYYY-MM-DD>_<os>-<arch>-<N>cpu/`. It takes about 2 minutes on the reference machine.

Environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `PRESETS` | `small,medium` | synthetic datasets (`tiny`, `small`, `medium` or `none`) |
| `SEGY` | — | external SEG-Y file(s), comma-separated |
| `WARMUP` | `2` | discarded warmup iterations |
| `ITERATIONS` | `5` | measured iterations |
| `BLOCK` | `8,8,0` | 3D block inline,xline,time (`0` = full time axis, the `sdc-svc` default) |
| `HEAP` | `3g` | JVM heap (`-Xms` = `-Xmx`) |
| `LABEL` | `<os>-<arch>-<N>cpu` | run label (becomes part of the directory name) |
| `OUT` | `benchmark/results/<date>_<label>` | output directory |
| `SKIP_BUILD` | `0` | `1` skips `mvn package` |

Examples:

```bash
PRESETS=small ITERATIONS=3 ./benchmark/run-benchmark.sh               # quick run
LABEL=macbook-m3 ./benchmark/run-benchmark.sh                         # label the machine
SEGY=/data/my_volume.sgy PRESETS=none ./benchmark/run-benchmark.sh    # real data
```

The jar can also be called directly: `java -jar sdc-bench/target/sdc-bench-0.1.0-SNAPSHOT-jar-with-dependencies.jar --help`.

## Dataset

Real seismic data tends to be large and restrictively licensed, so it can't be versioned in git. The benchmark therefore uses a **deterministically generated synthetic 3D volume** (`SyntheticSegyGenerator`):

- reflectors dipping in inline and xline with a gentle sinusoidal fold (one every ~25 samples)
- one planar normal fault (6.5-sample throw)
- 25 Hz Ricker wavelet, dt = 4 ms
- exponential attenuation with time and additive Gaussian noise (2% of scale)
- SEG-Y rev1, format 5 (IEEE float32), inline at trace-header bytes 189-192 and xline at bytes 193-196

| Preset | Grid (IL × XL × samples) | Traces | Size | SHA-256 |
|---|---|---:|---:|---|
| `tiny` | 8 × 8 × 128 | 64 | 50.5 KiB | `d18e727556b925d6edecfbd258b9b4b1990e427310a3a9e5959196e50717261c` |
| `small` | 32 × 32 × 500 | 1,024 | 2.19 MiB | `0e6036582f8db53226e33c9b4b2e87a72204f6c3b974415d304fe2e8dc7401e7` |
| `medium` | 128 × 128 × 1000 | 16,384 | 66.25 MiB | `0b6389d128a2a1a08ebd959fbb87ac583ed60beaf473fd3c3d122fa6fd139e26` |

Seed: `20261012` for all presets.

**Why the bytes are identical on any machine:** `java.util.Random` has an algorithm fixed by its specification, `nextGaussian` uses `StrictMath` (Java 17+), and all of the generator's math uses `StrictMath`, which gives bit-for-bit identical results on any JVM and CPU. The runner recomputes the SHA-256 and **aborts** if the file is not the canonical one. The `SyntheticSegyGeneratorTest` test pins the hash of the `tiny` preset. The `small` hash was also checked with the JVM in interpreter-only mode (`-Xint`, no JIT intrinsics).

To check manually: `sha256sum benchmark/data/*.sgy`.

### Real data

`SEGY=...` accepts any SEG-Y that `SegyIO` can read: format 1 (IBM) or 5 (IEEE), fixed-length traces, a 3200-byte textual header, inline and xline at bytes 189 and 193. Public volumes such as F3 Netherlands (dGB/OpendTect) or Volve (Equinor) work as long as their headers follow that layout. In that case the file's SHA-256 is recorded in the report but not checked against an expected value.

## Methodology

**What is measured.** For each dataset, the SEG-Y is read **once** (outside the measurements). Then:

| Codec | Measured compression | Measured decompression |
|---|---|---|
| `sdc-2d` (v2 container) | `SdcFileWriter.writeCompressed` | `SdcFileReader.readAllCompressed` |
| `sdc-3d` (v3 container) | `Sdc3DFileWriter.write3D` (8×8×full-time block) | `Sdc3DFileReader.read3D` |
| `deflate-6`, `deflate-9` | `java.util.zip.Deflater` on the raw float32 samples (big-endian) | `Inflater` |
| `shuffle+deflate-9` | byte-shuffle (Blosc/HDF5 style) + Deflate 9 | Inflate + unshuffle |

The SDC codecs run with the three `CompressionProfile` profiles: `HIGH_QUALITY` (16 bits, Deflate 9), `BALANCED` (12 bits, Deflate 7) and `HIGH_COMPRESSION` (8 bits, Deflate 6). SDC timings include writing and reading the `.sdc` in a temporary directory (usually served from the page cache), because that is how the public API works. The baselines run in memory only, and their reconstruction is checked byte for byte.

**How it is measured.** `WARMUP` discarded iterations (so the JIT can compile), then `ITERATIONS` measured iterations with `System.nanoTime()`, with a `System.gc()` before each one. We report median ± standard deviation (and, in the JSON, min/max/mean and every sample). Throughput is computed over the **raw trace bytes** (`traces × samples × 4`), using the median. Everything runs on a single thread, because that is how the codec works today.

**Quality metrics**, computed on the original amplitudes with no normalization:

- **Ratio** = raw trace bytes / compressed file bytes; **bits/sample** = 8 × compressed bytes / number of samples
- **SNR (dB)** = 10·log10(Σx² / Σ(x − x̂)²), the usual metric in seismic compression
- **PSNR (dB)** = 20·log10((max − min) / RMSE), using the volume's dynamic range as the peak
- **NRMSE (%)** = RMSE / RMS(x) × 100, and **maximum absolute error**

> The project's `LinearQuantizer.psnr` assumes a signal in [-1, 1]. On real amplitudes it is not comparable across datasets, so the benchmark uses the metrics above.

## Reference results

Run [`results/2026-10-12_linux-x86_64-4cpu`](results/2026-10-12_linux-x86_64-4cpu/summary.md), commit `9952555`, clean tree.

**Hardware/software:** Intel Xeon @ 2.10 GHz, 4 vCPUs (AVX2/AVX-512), 16 GiB RAM, Docker container, ext4 · Ubuntu 24.04.5, kernel 6.18 · OpenJDK 21.0.12 (G1, fixed 3 GiB heap) · Maven 3.9.11. Full details in [`hardware.txt`](results/2026-10-12_linux-x86_64-4cpu/hardware.txt).

### `synthetic-medium` (128 × 128 × 1000, 62.5 MiB of samples)

| Codec | Profile | Ratio | bits/sample | Comp. (ms) | Decomp. (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | NRMSE (%) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 2.06:1 | 15.51 | 1374 ± 58 | 419 ± 12 | 45.5 | 149.2 | 58.98 | 0.11 |
| sdc-2d | BALANCED | 2.51:1 | 12.75 | 1668 ± 159 | 419 ± 13 | 37.5 | 149.3 | **0.33** ⚠️ | 96.2 |
| sdc-2d | HIGH_COMPRESSION | 3.49:1 | 9.17 | 1387 ± 56 | 410 ± 25 | 45.1 | 152.5 | **−0.19** ⚠️ | 102.3 |
| sdc-3d | HIGH_QUALITY | 2.24:1 | 14.29 | 1351 ± 138 | 406 ± 6 | 46.3 | 154.1 | 58.00 | 0.13 |
| sdc-3d | BALANCED | 2.81:1 | 11.40 | 1945 ± 67 | 412 ± 13 | 32.1 | 151.6 | 33.63 | 2.08 |
| sdc-3d | HIGH_COMPRESSION | 4.79:1 | 6.68 | 3559 ± 282 | 374 ± 27 | 17.6 | 167.1 | 9.58 | 33.19 |
| deflate-6 | lossless | 1.06:1 | 30.08 | 2203 ± 75 | 400 ± 8 | 28.4 | 156.4 | ∞ | 0 |
| deflate-9 | lossless | 1.06:1 | 30.08 | 2206 ± 120 | 400 ± 9 | 28.3 | 156.3 | ∞ | 0 |
| shuffle+deflate-9 | lossless | 1.19:1 | 26.94 | 8601 ± 255 | 139 ± 4 | 7.3 | 450.1 | ∞ | 0 |

The `synthetic-small` tables and the PSNR/max error for every combination are in [`summary.md`](results/2026-10-12_linux-x86_64-4cpu/summary.md).

### What the numbers show

1. **`sdc-2d` with `BALANCED` and `HIGH_COMPRESSION` does not reconstruct the signal** (SNR ≈ 0 dB, error as large as the signal itself). The cause is in the code: `TraceBlockCodec.compress` quantizes with `profile.effectiveBits()` (12 or 8 bits), but `TraceBlockCodec.decompress` calls `LinearQuantizer.decode(q)` ([TraceBlockCodec.java:64](../sdc-core/src/main/java/com/sdc/core/TraceBlockCodec.java#L64)), which always assumes 16 bits. On top of that, the v2 container does not record how many bits were used. Only `HIGH_QUALITY` (16 bits) is consistent in 2D. 3D does not have this problem, because `decompressBlock` receives the profile.
2. **`sdc-3d` quality drops sharply with fewer bits and with larger blocks**: `HIGH_COMPRESSION` SNR goes from 14.1 dB (`small`, 500 samples) to 9.6 dB (`medium`, 1000 samples). This is consistent with open-loop prediction: `residualEncode` predicts from the **original** samples, but `residualDecode` predicts from the **reconstructed** ones ([VolumeBlock3DCodec.java:23-25 and 49-51](../sdc-core/src/main/java/com/sdc/core/VolumeBlock3DCodec.java#L23)), so quantization error accumulates across the block. The 2D delta path (`Preprocessing.deltaEncode`/`deltaDecode`) has the same structure, and it also saturates deltas outside [-1, 1] in `LinearQuantizer`. The usual fix is closed-loop DPCM: the encoder predicts from the already-quantized values.
3. **With `HIGH_QUALITY` the gain is about 2×** (2.06–2.24:1, SNR ≈ 58–59 dB), versus 1.06:1 for lossless Deflate and 1.19:1 for shuffle+Deflate. For noisy float32, nearly all of that comes from quantizing to 16 bits: 15.5 bits/sample in 2D is practically the same as storing uncompressed `short` values.
4. **Decompression (~150 MiB/s) is bound by Inflate and reading.** Compression (~45 MiB/s) is bound by Deflate 9. 3D `HIGH_COMPRESSION` is the slowest to compress: Deflate on 8-bit data with little redundancy.

These results are a **baseline**. After changing a codec, run again and compare the new `results.csv` with this one.

## Comparing runs and publishing results

- Each run goes into its own directory. Commit the whole directory to keep the history.
- `hardware.txt` records `git_commit` and whether the tree was dirty (`git_dirty`). For an "official" result, run from a clean commit.
- To compare two machines or two commits, use the same `PRESETS`/`WARMUP`/`ITERATIONS`/`BLOCK`/`HEAP`.
- The SHA-256 hashes of the generated `.sdc` files are in `results.json` (`outputSha256`). They depend on the JDK's zlib version, so they are only comparable on the same JVM. The dataset hashes are comparable everywhere.

## Limitations

- The synthetic data has 2% Gaussian noise, which limits lossless compression. Real data (especially stacked and filtered) usually compresses better. Confirm with `SEGY=...`.
- On a shared VM or container, the standard deviation is 2% to 10%. For fine-grained comparisons, increase `ITERATIONS` and close other processes. If you have host access, pin the CPU governor to `performance`.
- This is a simple end-to-end harness, not JMH. It suits operations from hundreds of ms to seconds, like these, but not microbenchmarks.
