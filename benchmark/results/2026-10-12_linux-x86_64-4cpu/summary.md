# SDC Benchmark — linux-x86_64-4cpu

## Environment

| Field | Value |
|---|---|
| label | linux-x86_64-4cpu |
| timestampUtc | 2026-10-12T18:51:20.313026967Z |
| gitCommit | 9952555 |
| cpuModel | Intel(R) Xeon(R) Processor @ 2.10GHz |
| availableProcessors | 4 |
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

## Datasets

| Dataset | Source | Grid (IL×XL×samples) | Traces | SEG-Y size | SHA-256 | Verified |
|---|---|---|---|---|---|---|
| synthetic-small | synthetic seed=20261012 | 32 × 32 × 500 | 1024 | 2.19 MiB | `0e6036582f8db53226e33c9b4b2e87a72204f6c3b974415d304fe2e8dc7401e7` | yes |
| synthetic-medium | synthetic seed=20261012 | 128 × 128 × 1000 | 16384 | 66.25 MiB | `0b6389d128a2a1a08ebd959fbb87ac583ed60beaf473fd3c3d122fa6fd139e26` | yes |

## Results — synthetic-small

Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.

| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 16 | 1.96:1 | 16.37 | 65.1 ± 7.6 | 13.6 ± 1.1 | 30.0 | 143.9 | 63.44 | 84.59 | 0.067 | 0.09211 |
| sdc-2d | BALANCED | 12 | 2.34:1 | 13.65 | 65.2 ± 13.0 | 18.0 ± 1.7 | 30.0 | 108.3 | -2.04 | 19.10 | 126.525 | 149.3 |
| sdc-2d | HIGH_COMPRESSION | 8 | 3.15:1 | 10.14 | 51.2 ± 3.8 | 18.3 ± 0.1 | 38.2 | 106.6 | -2.57 | 18.58 | 134.435 | 158.7 |
| sdc-3d | HIGH_QUALITY | 16 | 2.21:1 | 14.48 | 34.3 ± 2.6 | 17.3 ± 2.4 | 57.0 | 112.7 | 62.91 | 84.06 | 0.072 | 0.07208 |
| sdc-3d | BALANCED | 12 | 2.76:1 | 11.61 | 54.4 ± 1.5 | 14.1 ± 0.6 | 35.9 | 138.3 | 38.58 | 59.73 | 1.178 | 1.228 |
| sdc-3d | HIGH_COMPRESSION | 8 | 4.50:1 | 7.11 | 96.7 ± 10.7 | 14.4 ± 1.0 | 20.2 | 135.2 | 14.06 | 35.21 | 19.811 | 17.56 |
| deflate-6 | LOSSLESS | 32 | 1.06:1 | 30.07 | 81.7 ± 12.4 | 12.0 ± 0.1 | 23.9 | 162.9 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| deflate-9 | LOSSLESS | 32 | 1.06:1 | 30.07 | 70.3 ± 11.4 | 12.1 ± 0.7 | 27.8 | 160.8 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| shuffle+deflate-9 | LOSSLESS | 32 | 1.19:1 | 26.92 | 266.5 ± 26.5 | 3.6 ± 2.1 | 7.3 | 535.9 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |

## Results — synthetic-medium

Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.

| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 16 | 2.06:1 | 15.51 | 1374.3 ± 57.5 | 418.8 ± 11.9 | 45.5 | 149.2 | 58.98 | 81.64 | 0.112 | 0.1866 |
| sdc-2d | BALANCED | 12 | 2.51:1 | 12.75 | 1667.9 ± 159.4 | 418.7 ± 13.3 | 37.5 | 149.3 | 0.33 | 23.00 | 96.242 | 183.1 |
| sdc-2d | HIGH_COMPRESSION | 8 | 3.49:1 | 9.17 | 1386.5 ± 56.0 | 410.0 ± 25.2 | 45.1 | 152.5 | -0.19 | 22.47 | 102.256 | 194.5 |
| sdc-3d | HIGH_QUALITY | 16 | 2.24:1 | 14.29 | 1351.1 ± 138.3 | 405.5 ± 5.9 | 46.3 | 154.1 | 58.00 | 80.67 | 0.126 | 0.1527 |
| sdc-3d | BALANCED | 12 | 2.81:1 | 11.40 | 1944.8 ± 66.7 | 412.3 ± 12.9 | 32.1 | 151.6 | 33.63 | 56.30 | 2.081 | 3.109 |
| sdc-3d | HIGH_COMPRESSION | 8 | 4.79:1 | 6.68 | 3558.5 ± 281.6 | 373.9 ± 27.3 | 17.6 | 167.1 | 9.58 | 32.24 | 33.189 | 46.13 |
| deflate-6 | LOSSLESS | 32 | 1.06:1 | 30.08 | 2203.2 ± 74.9 | 399.7 ± 8.0 | 28.4 | 156.4 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| deflate-9 | LOSSLESS | 32 | 1.06:1 | 30.08 | 2206.4 ± 119.5 | 399.9 ± 8.8 | 28.3 | 156.3 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| shuffle+deflate-9 | LOSSLESS | 32 | 1.19:1 | 26.94 | 8601.1 ± 255.0 | 138.8 ± 3.6 | 7.3 | 450.1 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
