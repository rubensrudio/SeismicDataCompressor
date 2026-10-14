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
| `neural-hybrid` | `NeuralHybridCodec.encode` (autoencoder prediction + quantized residual) + file write | file read + `NeuralHybridCodec.decode` |
| `uniform-quant` | same codec with zero prediction (same quantizer, same container) + file write | file read + decode |

The neural codecs run only when a model is available (`MODEL`, default `sdc-ai/models/sdc-ae-v1.sdcnn`). Because they are much slower, they use at most 1 warmup and 2 measured iterations. `uniform-quant` exists to isolate the network: at the same profile it has exactly the same step and error bound as `neural-hybrid`, so any size difference comes from the prediction.

The SDC codecs run with the three `CompressionProfile` profiles: `HIGH_QUALITY` (16 bits, Deflate 9), `BALANCED` (12 bits, Deflate 7) and `HIGH_COMPRESSION` (8 bits, Deflate 6). SDC timings include writing and reading the `.sdc` in a temporary directory (usually served from the page cache), because that is how the public API works. The baselines run in memory only, and their reconstruction is checked byte for byte.

**How it is measured.** `WARMUP` discarded iterations (so the JIT can compile), then `ITERATIONS` measured iterations with `System.nanoTime()`, with a `System.gc()` before each one. We report median ± standard deviation (and, in the JSON, min/max/mean and every sample). Throughput is computed over the **raw trace bytes** (`traces × samples × 4`), using the median. The classical codecs run on a single thread; the neural codec's convolutions use all available cores.

**Quality metrics**, computed on the original amplitudes with no normalization:

- **Ratio** = raw trace bytes / compressed file bytes; **bits/sample** = 8 × compressed bytes / number of samples
- **SNR (dB)** = 10·log10(Σx² / Σ(x − x̂)²), the usual metric in seismic compression
- **PSNR (dB)** = 20·log10((max − min) / RMSE), using the volume's dynamic range as the peak
- **NRMSE (%)** = RMSE / RMS(x) × 100, and **maximum absolute error**

**Rate–distortion sweep (`RD=1`).** Profiles compare codecs at different qualities, which can mislead. The sweep runs every bit depth from 4 to 16 (Deflate 9 everywhere) for `sdc-3d`, `uniform-quant` and `neural-hybrid`, plus one `neural-only` point (latents only, no residual), and writes `rd.csv`. `benchmark/rd_report.py` then adds two external, state-of-the-art scientific compressors on exactly the same samples — **ZFP** (fixed accuracy, `zfpy`) and **SZ3** (absolute error bound, `hdf5plugin`) — and reports:

- bits/sample needed to reach target SNRs (piecewise-linear interpolation between measured points);
- **BD-rate**: the average relative difference in bits/sample at equal SNR over the SNR range two curves share (piecewise-linear in log2(bits/sample); the classic cubic fit is not used because Deflate's efficiency changes in steps);
- a chart per dataset (`rd_<dataset>.png`).

```bash
pip install zfpy hdf5plugin h5py matplotlib numpy
python benchmark/rd_report.py --results benchmark/results/<run> --segy synthetic-medium=benchmark/data/synthetic-medium.sgy
```

> The project's `LinearQuantizer.psnr` assumes a signal in [-1, 1]. On real amplitudes it is not comparable across datasets, so the benchmark uses the metrics above.

## Reference results

Run [`results/2026-10-14_linux-x86_64-2cpu`](results/2026-10-14_linux-x86_64-2cpu/summary.md), commit `69fc8c8`, clean tree, `RD=1`. Rate–distortion report: [`rd_report.md`](results/2026-10-14_linux-x86_64-2cpu/rd_report.md).

**Hardware/software:** Intel Xeon @ 2.80 GHz, 2 vCPUs, 8 GiB RAM, container · OpenJDK 21.0.12 (G1, fixed 3 GiB heap). Full details in [`hardware.txt`](results/2026-10-14_linux-x86_64-2cpu/hardware.txt). Maven Central was not reachable from that machine, so the benchmark jar was compiled with `javac` from the same commit; the Maven build and all tests run in CI (`.github/workflows/build.yml`).

### `synthetic-medium` (128 × 128 × 1000, 62.5 MiB of samples) — profiles

| Codec | Profile | Ratio | bits/sample | Comp. (ms) | Decomp. (ms) | SNR (dB) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 2.06:1 | 15.57 | 1712 ± 69 | 396 ± 16 | 85.97 | 0.0030 |
| sdc-2d | BALANCED | 2.69:1 | 11.89 | 2394 ± 61 | 372 ± 19 | 61.88 | 0.048 |
| sdc-2d | HIGH_COMPRESSION | 4.97:1 | 6.44 | 937 ± 118 | 253 ± 14 | 37.74 | 0.77 |
| sdc-3d | HIGH_QUALITY | 2.38:1 | 13.46 | 1599 ± 31 | 374 ± 13 | 85.22 | 0.0031 |
| sdc-3d | BALANCED | 3.27:1 | 9.78 | 3479 ± 98 | 281 ± 10 | 61.13 | 0.049 |
| sdc-3d | HIGH_COMPRESSION | 6.06:1 | 5.28 | 1038 ± 54 | 316 ± 11 | 36.99 | 0.78 |
| neural-hybrid | HIGH_QUALITY | 2.46:1 | 13.03 | 87880 ± 2188 | 65684 ± 3158 | 84.36 | 0.0032 |
| neural-hybrid | BALANCED | 3.33:1 | 9.62 | 85512 ± 373 | 67475 ± 2184 | 60.27 | 0.050 |
| neural-hybrid | HIGH_COMPRESSION | 6.36:1 | 5.03 | 82619 ± 742 | 62227 ± 738 | 36.13 | 0.81 |
| uniform-quant | HIGH_QUALITY | 2.29:1 | 13.96 | 1351 ± 21 | 227 ± 6 | 84.36 | 0.0032 |
| uniform-quant | BALANCED | 3.27:1 | 9.80 | 2939 ± 5 | 172 ± 3 | 60.27 | 0.050 |
| uniform-quant | HIGH_COMPRESSION | 5.38:1 | 5.95 | 890 ± 32 | 189 ± 6 | 36.13 | 0.81 |
| deflate-9 | lossless | 1.06:1 | 30.08 | 2697 ± 62 | 337 ± 9 | ∞ | 0 |

Profiles reach slightly different SNRs per codec, so use the rate–distortion results below to compare compression at equal quality.

### `synthetic-medium` — rate–distortion

bits/sample needed to reach a target SNR (lower is better):

| Codec | 15 dB | 20 dB | 25 dB | 30 dB | 40 dB | 50 dB | 60 dB |
|---|---:|---:|---:|---:|---:|---:|---:|
| SDC 3D (closed-loop DPCM) | 1.49 | 2.47 | 3.37 | 4.22 | 6.27 | 8.46 | 9.63 |
| Uniform quantization (no prediction) | 1.63 | 2.80 | 3.86 | 4.85 | 7.12 | 8.70 | 9.67 |
| **Neural hybrid (autoencoder + residual)** | **0.93** | 2.21 | 3.06 | 3.95 | 6.23 | 8.06 | 9.51 |
| ZFP (fixed accuracy) | 2.20 | 3.09 | 3.92 | 4.75 | 6.43 | 8.09 | 9.75 |
| SZ3 (absolute error bound) | 1.12 | **1.92** | **2.67** | **3.48** | **5.11** | **6.79** | **8.47** |

Neural only (latents, no residual): **0.427 bits/sample (75:1) at 13.25 dB**.

| BD-rate (bits/sample at equal SNR) | vs. SDC 3D | vs. uniform quantization |
|---|---:|---:|
| Neural hybrid | **−5.7%** | **−12.2%** |
| Uniform quantization | +7.4% | — |
| ZFP | +7.1% | −0.4% |
| SZ3 | −16.3% | −22.4% |

![rate–distortion synthetic-medium](results/2026-10-14_linux-x86_64-2cpu/rd_synthetic-medium.png)

### What the numbers show

1. **The two codec defects found on 2026-10-12 are fixed.** `sdc-2d` `BALANCED`/`HIGH_COMPRESSION` now reach 61.9 / 37.7 dB (they were at about 0 dB), and `sdc-3d` `HIGH_COMPRESSION` reaches 37.0 dB at 6.06:1 (it was 9.6 dB at 4.79:1). Both codecs now use closed-loop DPCM and store the bit depth in each payload.
2. **The neural predictor measurably reduces size.** Against the same quantizer without the network (same step, same error bound, same container), the hybrid needs **12.2% fewer bits on average** at equal SNR, and **43% fewer at 15 dB** (0.93 vs 1.63 bits/sample). Against SDC 3D the average gain is 5.7%, and 38% at 15 dB.
3. **The gain concentrates at low rates.** At high fidelity the residual is dominated by the 2% Gaussian noise of the data, which no predictor can remove, so all codecs converge.
4. **SZ3 is the strongest codec overall** (−16.3% vs SDC 3D). The neural hybrid beats SZ3 only at the lowest rates (15 dB: 0.93 vs 1.12 bits/sample). ZFP is weaker than every predictive codec here.
5. **The neural codec is slow:** about 85 s to compress and 65 s to decompress 62.5 MiB in pure Java on 2 vCPUs — roughly 50–80× slower than SDC 3D to compress and about 200× slower to decompress.
6. **Neural only** stores just the latents: 0.427 bits/sample (75:1) at 13.25 dB. That SNR is close to the data's own signal-to-noise ratio (13.81 dB): the reconstruction keeps the reflectors and drops most of the noise. Against the noise-free version of the same volume it reaches **19.86 dB**, better than the noisy input itself (`java -cp <bench jar> com.sdc.bench.CleanSignalCheck medium`). Whether discarding noise is acceptable depends on the use; the hybrid modes keep it.

### Out-of-distribution check

A model trained on synthetic data must be checked on data it was not designed for. Run [`results/2026-10-14_linux-x86_64-2cpu-ood-noise5`](results/2026-10-14_linux-x86_64-2cpu-ood-noise5/rd_report.md): a 128 × 128 × 1000 volume with **5% noise instead of 2%** and seed 3000 (`TrainingDataGenerator /tmp/ood 1 3000 128 128 1000 0.05`).

| BD-rate (bits/sample at equal SNR) | vs. SDC 3D | vs. uniform quantization |
|---|---:|---:|
| Neural hybrid | −0.9% | −5.3% |
| SZ3 | −12.1% | −16.0% |

**The gain shrinks under this distribution shift**: the hybrid still beats plain quantization (−5.3%), but it is only at parity with SDC 3D, and the neural-only point drops to 7.65 dB. Real data, which differs much more from the training set than this, will need its own training and evaluation.

### Previous run (2026-10-12, before the fixes)

[`results/2026-10-12_linux-x86_64-4cpu`](results/2026-10-12_linux-x86_64-4cpu/summary.md) (commit `9952555`) is kept as history. It exposed the two defects fixed above (2D decoder assuming 16 bits; open-loop prediction), and showed that `HIGH_QUALITY` gained about 2× mostly from 16-bit quantization. Its sdc numbers are not comparable with the current codec.

## Comparing runs and publishing results

- Each run goes into its own directory. Commit the whole directory to keep the history.
- `hardware.txt` records `git_commit` and whether the tree was dirty (`git_dirty`). For an "official" result, run from a clean commit.
- To compare two machines or two commits, use the same `PRESETS`/`WARMUP`/`ITERATIONS`/`BLOCK`/`HEAP`.
- The SHA-256 hashes of the generated `.sdc` files are in `results.json` (`outputSha256`). They depend on the JDK's zlib version, so they are only comparable on the same JVM. The dataset hashes are comparable everywhere.

## Limitations

- **Synthetic data only so far.** The neural model was trained on volumes from the same generator (different seeds). Real data is less regular; run with `SEGY=...` on public volumes (F3 Netherlands, Volve) before drawing conclusions for production data.
- **Entropy coding is Deflate for every codec.** That keeps the comparison fair inside this project but penalizes all of them against SZ3, which uses its own Huffman + zstd stage.

- The synthetic data has 2% Gaussian noise, which limits lossless compression. Real data (especially stacked and filtered) usually compresses better. Confirm with `SEGY=...`.
- On a shared VM or container, the standard deviation is 2% to 10%. For fine-grained comparisons, increase `ITERATIONS` and close other processes. If you have host access, pin the CPU governor to `performance`.
- This is a simple end-to-end harness, not JMH. It suits operations from hundreds of ms to seconds, like these, but not microbenchmarks.
