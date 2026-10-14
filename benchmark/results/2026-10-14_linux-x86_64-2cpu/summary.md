# SDC Benchmark — linux-x86_64-2cpu

## Environment

| Field | Value |
|---|---|
| label | linux-x86_64-2cpu |
| timestampUtc | 2026-10-14T17:41:09.164232362Z |
| gitCommit | 69fc8c8 |
| cpuModel | Intel(R) Xeon(R) Processor @ 2.80GHz |
| availableProcessors | 2 |
| maxHeapMiB | 3072 |
| osName | Linux |
| osVersion | 6.18.44-fc-v114 |
| osArch | amd64 |
| javaVersion | 21.0.12.1 |
| javaVendor | Ubuntu |
| jvmName | OpenJDK 64-Bit Server VM |
| jvmArgs | -Xms3g -Xmx3g -XX:+AlwaysPreTouch |
| gc | G1 Young Generation, G1 Concurrent GC, G1 Old Generation |
| warmupIterations | 2 |
| measuredIterations | 5 |
| block3D | 8x8xfull |
| neuralModel | b2ba8ca4ae7d805a2c6496627d6db037b4d8dad18523e58884c89f6e9a611665 |
| neuralModelMetadata | {"architecture": "conv-ae c1=16 c2=32 latent=8 factor=8 leaky=0.1", "epochs": 10, "framework": "tensorflow 2.20.0", "l1": 2e-05, "params": 32713, "patch": [64, 128], "seed": 7, "train_dir": "train", "train_patches": 16000, "train_seconds": 1119.9, "train_volumes": 10, "val_mse_normalized": 0.0006112618139013648, "val_snr_db_rounded_latents": 12.99285430673644, "val_volumes": 2} |
| neuralTiming | warmup=1 iterations=2 (neural codecs are slower; fewer repetitions) |

## Datasets

| Dataset | Source | Grid (IL×XL×samples) | Traces | SEG-Y size | SHA-256 | Verified |
|---|---|---|---|---|---|---|
| synthetic-small | synthetic seed=20261012 | 32 × 32 × 500 | 1024 | 2.19 MiB | `0e6036582f8db53226e33c9b4b2e87a72204f6c3b974415d304fe2e8dc7401e7` | yes |
| synthetic-medium | synthetic seed=20261012 | 128 × 128 × 1000 | 16384 | 66.25 MiB | `0b6389d128a2a1a08ebd959fbb87ac583ed60beaf473fd3c3d122fa6fd139e26` | yes |

## Results — synthetic-small

Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.

| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 16 | 1.95:1 | 16.42 | 76.1 ± 4.3 | 14.6 ± 2.6 | 25.7 | 133.7 | 87.26 | 108.40 | 0.004 | 0.002426 |
| sdc-2d | BALANCED | 12 | 2.47:1 | 12.94 | 80.7 ± 3.4 | 14.0 ± 2.0 | 24.2 | 139.7 | 63.18 | 84.32 | 0.069 | 0.03887 |
| sdc-2d | HIGH_COMPRESSION | 8 | 4.45:1 | 7.18 | 42.1 ± 1.5 | 12.5 ± 1.2 | 46.4 | 156.5 | 39.02 | 60.17 | 1.120 | 0.6255 |
| sdc-3d | HIGH_QUALITY | 16 | 2.32:1 | 13.79 | 57.4 ± 5.0 | 17.0 ± 1.8 | 34.0 | 114.8 | 86.36 | 107.51 | 0.005 | 0.002510 |
| sdc-3d | BALANCED | 12 | 3.17:1 | 10.10 | 98.7 ± 4.7 | 13.1 ± 0.9 | 19.8 | 148.6 | 62.27 | 83.42 | 0.077 | 0.04000 |
| sdc-3d | HIGH_COMPRESSION | 8 | 5.85:1 | 5.47 | 35.3 ± 2.5 | 13.0 ± 0.4 | 55.3 | 150.1 | 38.13 | 59.28 | 1.240 | 0.6444 |
| neural-hybrid | HIGH_QUALITY | 16 | 2.43:1 | 13.17 | 3285.4 ± 575.7 | 2093.3 ± 10.6 | 0.6 | 0.9 | 84.69 | 105.84 | 0.006 | 0.002907 |
| neural-hybrid | BALANCED | 12 | 3.30:1 | 9.68 | 2847.1 ± 70.8 | 2022.9 ± 60.2 | 0.7 | 1.0 | 60.61 | 81.76 | 0.093 | 0.04646 |
| neural-hybrid | HIGH_COMPRESSION | 8 | 6.23:1 | 5.14 | 2707.3 ± 101.1 | 2089.3 ± 25.9 | 0.7 | 0.9 | 36.47 | 57.61 | 1.502 | 0.7488 |
| uniform-quant | HIGH_QUALITY | 16 | 2.27:1 | 14.10 | 60.2 ± 6.6 | 10.4 ± 0.2 | 32.5 | 188.1 | 84.69 | 105.84 | 0.006 | 0.002907 |
| uniform-quant | BALANCED | 12 | 3.26:1 | 9.82 | 90.4 ± 1.1 | 8.7 ± 0.3 | 21.6 | 224.7 | 60.60 | 81.75 | 0.093 | 0.04646 |
| uniform-quant | HIGH_COMPRESSION | 8 | 5.27:1 | 6.07 | 30.4 ± 2.9 | 8.7 ± 0.6 | 64.3 | 223.3 | 36.46 | 57.61 | 1.503 | 0.7488 |
| deflate-6 | LOSSLESS | 32 | 1.06:1 | 30.07 | 79.1 ± 2.5 | 10.0 ± 0.3 | 24.7 | 194.4 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| deflate-9 | LOSSLESS | 32 | 1.06:1 | 30.07 | 77.2 ± 3.1 | 10.2 ± 0.3 | 25.3 | 192.2 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| shuffle+deflate-9 | LOSSLESS | 32 | 1.19:1 | 26.92 | 266.2 ± 8.3 | 7.4 ± 0.5 | 7.3 | 263.5 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |

## Results — synthetic-medium

Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.

| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 16 | 2.06:1 | 15.57 | 1712.2 ± 68.8 | 396.1 ± 16.4 | 36.5 | 157.8 | 85.97 | 108.64 | 0.005 | 0.002993 |
| sdc-2d | BALANCED | 12 | 2.69:1 | 11.89 | 2394.2 ± 61.3 | 372.2 ± 18.7 | 26.1 | 167.9 | 61.88 | 84.55 | 0.080 | 0.04758 |
| sdc-2d | HIGH_COMPRESSION | 8 | 4.97:1 | 6.44 | 937.4 ± 118.0 | 253.1 ± 14.1 | 66.7 | 246.9 | 37.74 | 60.40 | 1.297 | 0.7680 |
| sdc-3d | HIGH_QUALITY | 16 | 2.38:1 | 13.46 | 1598.5 ± 31.3 | 373.5 ± 12.6 | 39.1 | 167.3 | 85.22 | 107.88 | 0.005 | 0.003059 |
| sdc-3d | BALANCED | 12 | 3.27:1 | 9.78 | 3479.2 ± 97.9 | 280.6 ± 9.6 | 18.0 | 222.7 | 61.13 | 83.80 | 0.088 | 0.04859 |
| sdc-3d | HIGH_COMPRESSION | 8 | 6.06:1 | 5.28 | 1038.0 ± 54.3 | 315.7 ± 11.2 | 60.2 | 198.0 | 36.99 | 59.65 | 1.415 | 0.7830 |
| neural-hybrid | HIGH_QUALITY | 16 | 2.46:1 | 13.03 | 87880.4 ± 2187.9 | 65684.3 ± 3157.5 | 0.7 | 1.0 | 84.36 | 107.02 | 0.006 | 0.003159 |
| neural-hybrid | BALANCED | 12 | 3.33:1 | 9.62 | 85512.0 ± 372.7 | 67474.9 ± 2184.2 | 0.7 | 0.9 | 60.27 | 82.94 | 0.097 | 0.05041 |
| neural-hybrid | HIGH_COMPRESSION | 8 | 6.36:1 | 5.03 | 82618.8 ± 741.7 | 62227.4 ± 738.4 | 0.8 | 1.0 | 36.13 | 58.79 | 1.562 | 0.8123 |
| uniform-quant | HIGH_QUALITY | 16 | 2.29:1 | 13.96 | 1351.2 ± 21.2 | 226.6 ± 5.7 | 46.3 | 275.8 | 84.36 | 107.02 | 0.006 | 0.003166 |
| uniform-quant | BALANCED | 12 | 3.27:1 | 9.80 | 2939.3 ± 5.1 | 171.6 ± 3.3 | 21.3 | 364.2 | 60.27 | 82.94 | 0.097 | 0.05041 |
| uniform-quant | HIGH_COMPRESSION | 8 | 5.38:1 | 5.95 | 889.7 ± 32.0 | 189.3 ± 6.4 | 70.2 | 330.2 | 36.13 | 58.79 | 1.562 | 0.8123 |
| deflate-6 | LOSSLESS | 32 | 1.06:1 | 30.08 | 2676.0 ± 126.6 | 339.8 ± 13.7 | 23.4 | 184.0 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| deflate-9 | LOSSLESS | 32 | 1.06:1 | 30.08 | 2697.1 ± 62.0 | 337.4 ± 9.3 | 23.2 | 185.2 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| shuffle+deflate-9 | LOSSLESS | 32 | 1.19:1 | 26.94 | 8893.7 ± 229.4 | 130.3 ± 9.2 | 7.0 | 479.8 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |

## Rate–distortion sweep (Deflate 9)

bits/sample over the raw float32 samples; SNR on the original amplitudes. neural-hybrid = autoencoder prediction + quantized residual; uniform-quant = same quantizer, zero prediction.

| Dataset | Codec | Bits | bits/sample | Ratio | SNR (dB) | Max abs. error | Latent bytes | Residual bytes |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| synthetic-small | sdc-3d | 4 | 1.433 | 22.33:1 | 13.32 | 11.69 | 0 | 91717 |
| synthetic-small | sdc-3d | 5 | 2.552 | 12.54:1 | 19.58 | 5.456 | 0 | 163353 |
| synthetic-small | sdc-3d | 6 | 3.669 | 8.72:1 | 25.89 | 2.640 | 0 | 234817 |
| synthetic-small | sdc-3d | 7 | 4.702 | 6.81:1 | 32.04 | 1.299 | 0 | 300930 |
| synthetic-small | sdc-3d | 8 | 5.465 | 5.85:1 | 38.13 | 0.6444 | 0 | 349789 |
| synthetic-small | sdc-3d | 9 | 7.795 | 4.11:1 | 44.18 | 0.3210 | 0 | 498879 |
| synthetic-small | sdc-3d | 10 | 8.717 | 3.67:1 | 50.22 | 0.1602 | 0 | 557885 |
| synthetic-small | sdc-3d | 11 | 9.501 | 3.37:1 | 56.26 | 0.08002 | 0 | 608057 |
| synthetic-small | sdc-3d | 12 | 10.068 | 3.18:1 | 62.27 | 0.04000 | 0 | 644320 |
| synthetic-small | sdc-3d | 14 | 12.148 | 2.63:1 | 74.32 | 0.01000 | 0 | 777458 |
| synthetic-small | sdc-3d | 16 | 13.793 | 2.32:1 | 86.36 | 0.002510 | 0 | 882746 |
| synthetic-small | uniform-quant | 4 | 1.155 | 27.71:1 | 12.31 | 13.58 | 0 | 73855 |
| synthetic-small | uniform-quant | 5 | 2.454 | 13.04:1 | 17.93 | 6.339 | 0 | 156974 |
| synthetic-small | uniform-quant | 6 | 3.769 | 8.49:1 | 24.21 | 3.068 | 0 | 241159 |
| synthetic-small | uniform-quant | 7 | 4.983 | 6.42:1 | 30.37 | 1.509 | 0 | 318820 |
| synthetic-small | uniform-quant | 8 | 6.074 | 5.27:1 | 36.46 | 0.7488 | 0 | 388650 |
| synthetic-small | uniform-quant | 9 | 7.991 | 4.00:1 | 42.52 | 0.3729 | 0 | 511381 |
| synthetic-small | uniform-quant | 10 | 8.639 | 3.70:1 | 48.55 | 0.1861 | 0 | 552818 |
| synthetic-small | uniform-quant | 11 | 9.157 | 3.49:1 | 54.59 | 0.09296 | 0 | 585979 |
| synthetic-small | uniform-quant | 12 | 9.723 | 3.29:1 | 60.60 | 0.04646 | 0 | 622217 |
| synthetic-small | uniform-quant | 14 | 11.850 | 2.70:1 | 72.65 | 0.01162 | 0 | 758352 |
| synthetic-small | uniform-quant | 16 | 14.099 | 2.27:1 | 84.69 | 0.002907 | 0 | 902274 |
| synthetic-small | neural-hybrid | 4 | 0.789 | 40.58:1 | 13.76 | 13.58 | 28714 | 21693 |
| synthetic-small | neural-hybrid | 5 | 1.998 | 16.01:1 | 17.96 | 6.340 | 28714 | 99125 |
| synthetic-small | neural-hybrid | 6 | 3.007 | 10.64:1 | 24.21 | 3.067 | 28714 | 163678 |
| synthetic-small | neural-hybrid | 7 | 4.086 | 7.83:1 | 30.38 | 1.509 | 28714 | 232726 |
| synthetic-small | neural-hybrid | 8 | 5.134 | 6.23:1 | 36.47 | 0.7488 | 28714 | 299808 |
| synthetic-small | neural-hybrid | 9 | 5.898 | 5.43:1 | 42.51 | 0.3729 | 28714 | 348720 |
| synthetic-small | neural-hybrid | 10 | 7.921 | 4.04:1 | 48.56 | 0.1861 | 28714 | 478195 |
| synthetic-small | neural-hybrid | 11 | 8.882 | 3.60:1 | 54.59 | 0.09296 | 28714 | 539647 |
| synthetic-small | neural-hybrid | 12 | 9.650 | 3.32:1 | 60.61 | 0.04646 | 28714 | 588848 |
| synthetic-small | neural-hybrid | 14 | 11.077 | 2.89:1 | 72.65 | 0.01161 | 28714 | 680148 |
| synthetic-small | neural-hybrid | 16 | 13.167 | 2.43:1 | 84.69 | 0.002907 | 28714 | 813890 |
| synthetic-small | neural-only | — | 0.450 | 71.17:1 | 13.13 | 83.93 | 28714 | 0 |
| synthetic-medium | sdc-3d | 4 | 1.122 | 28.52:1 | 12.60 | 14.21 | 0 | 2297936 |
| synthetic-medium | sdc-3d | 5 | 2.236 | 14.31:1 | 18.44 | 6.629 | 0 | 4580073 |
| synthetic-medium | sdc-3d | 6 | 3.327 | 9.62:1 | 24.74 | 3.208 | 0 | 6814137 |
| synthetic-medium | sdc-3d | 7 | 4.389 | 7.29:1 | 30.90 | 1.578 | 0 | 8987934 |
| synthetic-medium | sdc-3d | 8 | 5.285 | 6.06:1 | 36.99 | 0.7830 | 0 | 10823125 |
| synthetic-medium | sdc-3d | 9 | 7.449 | 4.30:1 | 43.04 | 0.3900 | 0 | 15256262 |
| synthetic-medium | sdc-3d | 10 | 8.335 | 3.84:1 | 49.08 | 0.1946 | 0 | 17071019 |
| synthetic-medium | sdc-3d | 11 | 9.170 | 3.49:1 | 55.11 | 0.09722 | 0 | 18779615 |
| synthetic-medium | sdc-3d | 12 | 9.737 | 3.29:1 | 61.13 | 0.04859 | 0 | 19942343 |
| synthetic-medium | sdc-3d | 14 | 11.396 | 2.81:1 | 73.18 | 0.01215 | 0 | 23338089 |
| synthetic-medium | sdc-3d | 16 | 13.456 | 2.38:1 | 85.22 | 0.003059 | 0 | 27558601 |
| synthetic-medium | uniform-quant | 4 | 1.095 | 29.23:1 | 12.24 | 14.74 | 0 | 2242096 |
| synthetic-medium | uniform-quant | 5 | 2.380 | 13.44:1 | 17.63 | 6.877 | 0 | 4874403 |
| synthetic-medium | uniform-quant | 6 | 3.662 | 8.74:1 | 23.88 | 3.328 | 0 | 7500559 |
| synthetic-medium | uniform-quant | 7 | 4.860 | 6.58:1 | 30.04 | 1.637 | 0 | 9952584 |
| synthetic-medium | uniform-quant | 8 | 5.950 | 5.38:1 | 36.13 | 0.8123 | 0 | 12185912 |
| synthetic-medium | uniform-quant | 9 | 7.885 | 4.06:1 | 42.18 | 0.4045 | 0 | 16147561 |
| synthetic-medium | uniform-quant | 10 | 8.542 | 3.75:1 | 48.22 | 0.2019 | 0 | 17494531 |
| synthetic-medium | uniform-quant | 11 | 9.095 | 3.52:1 | 54.25 | 0.1008 | 0 | 18625534 |
| synthetic-medium | uniform-quant | 12 | 9.699 | 3.30:1 | 60.27 | 0.05041 | 0 | 19862809 |
| synthetic-medium | uniform-quant | 14 | 11.721 | 2.73:1 | 72.32 | 0.01261 | 0 | 24005395 |
| synthetic-medium | uniform-quant | 16 | 13.964 | 2.29:1 | 84.36 | 0.003166 | 0 | 28598654 |
| synthetic-medium | neural-hybrid | 4 | 0.696 | 45.97:1 | 13.90 | 14.74 | 874174 | 551310 |
| synthetic-medium | neural-hybrid | 5 | 1.888 | 16.94:1 | 17.68 | 6.877 | 874174 | 2993386 |
| synthetic-medium | neural-hybrid | 6 | 2.889 | 11.08:1 | 23.88 | 3.328 | 874174 | 5042312 |
| synthetic-medium | neural-hybrid | 7 | 3.957 | 8.09:1 | 30.04 | 1.637 | 874174 | 7228773 |
| synthetic-medium | neural-hybrid | 8 | 5.018 | 6.38:1 | 36.13 | 0.8123 | 874174 | 9403626 |
| synthetic-medium | neural-hybrid | 9 | 7.029 | 4.55:1 | 42.18 | 0.4045 | 874174 | 13521843 |
| synthetic-medium | neural-hybrid | 10 | 7.794 | 4.11:1 | 48.22 | 0.2019 | 874174 | 15088068 |
| synthetic-medium | neural-hybrid | 11 | 8.736 | 3.66:1 | 54.25 | 0.1008 | 874174 | 17016380 |
| synthetic-medium | neural-hybrid | 12 | 9.548 | 3.35:1 | 60.27 | 0.05041 | 874174 | 18680694 |
| synthetic-medium | neural-hybrid | 14 | 10.939 | 2.93:1 | 72.32 | 0.01260 | 874174 | 21528535 |
| synthetic-medium | neural-hybrid | 16 | 13.033 | 2.46:1 | 84.36 | 0.003159 | 874174 | 25817416 |
| synthetic-medium | neural-only | — | 0.427 | 74.96:1 | 13.25 | 163.2 | 874174 | 0 |
