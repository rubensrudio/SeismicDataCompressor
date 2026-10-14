# SDC Benchmark — linux-x86_64-2cpu-ood-noise5

## Environment

| Field | Value |
|---|---|
| label | linux-x86_64-2cpu-ood-noise5 |
| timestampUtc | 2026-10-14T18:25:06.802512854Z |
| gitCommit | 5fb932d |
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
| noise5-3000 | external file | 128 × 128 × 1000 | 16384 | 66.25 MiB | `57e0bdcbe8eb24049b68d38b2502bb34916cf89f6d22e08f807fa255e4e6c88b` | n/a |

## Results — noise5-3000

Ratio = raw trace bytes (float32) / compressed file bytes. Throughput over the raw bytes, using the median.

| Codec | Profile | Bits | Ratio | bits/sample | Compression (ms) | Decompression (ms) | Comp. MiB/s | Decomp. MiB/s | SNR (dB) | PSNR (dB) | NRMSE (%) | Max abs. error |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 16 | 2.02:1 | 15.82 | 1608.2 ± 42.0 | 390.0 ± 8.0 | 38.9 | 160.2 | 63.86 | 88.31 | 0.064 | 36.85 |
| sdc-2d | BALANCED | 12 | 2.57:1 | 12.46 | 2037.7 ± 31.8 | 366.4 ± 4.8 | 30.7 | 170.6 | 59.53 | 83.98 | 0.106 | 36.85 |
| sdc-2d | HIGH_COMPRESSION | 8 | 4.72:1 | 6.78 | 931.8 ± 39.6 | 251.4 ± 17.8 | 67.1 | 248.6 | 37.36 | 61.81 | 1.355 | 36.78 |
| sdc-3d | HIGH_QUALITY | 16 | 2.33:1 | 13.70 | 1500.8 ± 75.9 | 329.9 ± 4.2 | 41.6 | 189.5 | 84.23 | 108.68 | 0.006 | 0.004547 |
| sdc-3d | BALANCED | 12 | 3.14:1 | 10.19 | 3611.3 ± 104.3 | 311.0 ± 12.1 | 17.3 | 201.0 | 60.15 | 84.59 | 0.098 | 0.07230 |
| sdc-3d | HIGH_COMPRESSION | 8 | 5.71:1 | 5.60 | 933.6 ± 51.3 | 295.8 ± 7.5 | 66.9 | 211.3 | 36.00 | 60.44 | 1.585 | 1.165 |
| neural-hybrid | HIGH_QUALITY | 16 | 2.37:1 | 13.48 | 79667.4 ± 1408.9 | 61398.5 ± 1951.6 | 0.8 | 1.0 | 81.62 | 106.06 | 0.008 | 0.005371 |
| neural-hybrid | BALANCED | 12 | 3.27:1 | 9.77 | 84449.0 ± 3807.6 | 63204.9 ± 2066.5 | 0.7 | 1.0 | 57.53 | 81.98 | 0.133 | 0.08566 |
| neural-hybrid | HIGH_COMPRESSION | 8 | 5.84:1 | 5.48 | 87950.8 ± 231.1 | 64441.5 ± 879.0 | 0.7 | 1.0 | 33.39 | 57.83 | 2.142 | 1.380 |
| uniform-quant | HIGH_QUALITY | 16 | 2.31:1 | 13.85 | 1490.5 ± 72.6 | 256.8 ± 34.0 | 41.9 | 243.4 | 81.62 | 106.06 | 0.008 | 0.005386 |
| uniform-quant | BALANCED | 12 | 3.25:1 | 9.83 | 3401.0 ± 62.1 | 162.7 ± 8.9 | 18.4 | 384.2 | 57.53 | 81.98 | 0.133 | 0.08566 |
| uniform-quant | HIGH_COMPRESSION | 8 | 5.48:1 | 5.84 | 885.0 ± 4.6 | 154.9 ± 3.8 | 70.6 | 403.5 | 33.39 | 57.83 | 2.142 | 1.380 |
| deflate-6 | LOSSLESS | 32 | 1.07:1 | 29.85 | 2798.4 ± 88.8 | 369.3 ± 15.1 | 22.3 | 169.3 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| deflate-9 | LOSSLESS | 32 | 1.07:1 | 29.85 | 2815.6 ± 91.8 | 334.6 ± 9.1 | 22.2 | 186.8 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |
| shuffle+deflate-9 | LOSSLESS | 32 | 1.18:1 | 27.03 | 11371.1 ± 126.5 | 128.0 ± 84.1 | 5.5 | 488.3 | ∞ (lossless) | ∞ (lossless) | 0 | 0 |

## Rate–distortion sweep (Deflate 9)

bits/sample over the raw float32 samples; SNR on the original amplitudes. neural-hybrid = autoencoder prediction + quantized residual; uniform-quant = same quantizer, zero prediction.

| Dataset | Codec | Bits | bits/sample | Ratio | SNR (dB) | Max abs. error | Latent bytes | Residual bytes |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| noise5-3000 | sdc-3d | 4 | 1.757 | 18.21:1 | 10.86 | 21.14 | 0 | 3598934 |
| noise5-3000 | sdc-3d | 5 | 2.831 | 11.30:1 | 17.45 | 9.866 | 0 | 5798283 |
| noise5-3000 | sdc-3d | 6 | 3.934 | 8.13:1 | 23.75 | 4.774 | 0 | 8056106 |
| noise5-3000 | sdc-3d | 7 | 4.899 | 6.53:1 | 29.91 | 2.349 | 0 | 10033297 |
| noise5-3000 | sdc-3d | 8 | 5.604 | 5.71:1 | 36.00 | 1.165 | 0 | 11477342 |
| noise5-3000 | sdc-3d | 9 | 7.914 | 4.04:1 | 42.05 | 0.5803 | 0 | 16207194 |
| noise5-3000 | sdc-3d | 10 | 8.875 | 3.61:1 | 48.09 | 0.2896 | 0 | 18176497 |
| noise5-3000 | sdc-3d | 11 | 9.535 | 3.36:1 | 54.12 | 0.1447 | 0 | 19528567 |
| noise5-3000 | sdc-3d | 12 | 10.136 | 3.16:1 | 60.15 | 0.07230 | 0 | 20757840 |
| noise5-3000 | sdc-3d | 14 | 12.144 | 2.63:1 | 72.19 | 0.01808 | 0 | 24871654 |
| noise5-3000 | sdc-3d | 16 | 13.705 | 2.33:1 | 84.23 | 0.004547 | 0 | 28067572 |
| noise5-3000 | uniform-quant | 4 | 1.385 | 23.11:1 | 8.71 | 25.05 | 0 | 2836226 |
| noise5-3000 | uniform-quant | 5 | 2.590 | 12.36:1 | 14.83 | 11.69 | 0 | 5303853 |
| noise5-3000 | uniform-quant | 6 | 3.765 | 8.50:1 | 21.14 | 5.655 | 0 | 7709981 |
| noise5-3000 | uniform-quant | 7 | 4.879 | 6.56:1 | 27.30 | 2.783 | 0 | 9991113 |
| noise5-3000 | uniform-quant | 8 | 5.841 | 5.48:1 | 33.39 | 1.380 | 0 | 11962184 |
| noise5-3000 | uniform-quant | 9 | 7.775 | 4.12:1 | 39.44 | 0.6875 | 0 | 15923556 |
| noise5-3000 | uniform-quant | 10 | 8.567 | 3.74:1 | 45.48 | 0.3431 | 0 | 17545912 |
| noise5-3000 | uniform-quant | 11 | 9.198 | 3.48:1 | 51.51 | 0.1714 | 0 | 18837469 |
| noise5-3000 | uniform-quant | 12 | 9.734 | 3.29:1 | 57.53 | 0.08566 | 0 | 19934223 |
| noise5-3000 | uniform-quant | 14 | 11.818 | 2.71:1 | 69.58 | 0.02144 | 0 | 24202472 |
| noise5-3000 | uniform-quant | 16 | 13.853 | 2.31:1 | 81.62 | 0.005386 | 0 | 28371350 |
| noise5-3000 | neural-hybrid | 4 | 1.211 | 26.43:1 | 9.20 | 25.05 | 858454 | 1620849 |
| noise5-3000 | neural-hybrid | 5 | 2.340 | 13.68:1 | 14.83 | 11.69 | 858454 | 3933639 |
| noise5-3000 | neural-hybrid | 6 | 3.393 | 9.43:1 | 21.14 | 5.655 | 858454 | 6091285 |
| noise5-3000 | neural-hybrid | 7 | 4.474 | 7.15:1 | 27.30 | 2.783 | 858454 | 8304387 |
| noise5-3000 | neural-hybrid | 8 | 5.474 | 5.85:1 | 33.39 | 1.380 | 858454 | 10351805 |
| noise5-3000 | neural-hybrid | 9 | 7.345 | 4.36:1 | 39.44 | 0.6875 | 858454 | 14184738 |
| noise5-3000 | neural-hybrid | 10 | 8.219 | 3.89:1 | 45.48 | 0.3431 | 858454 | 15973310 |
| noise5-3000 | neural-hybrid | 11 | 9.171 | 3.49:1 | 51.51 | 0.1714 | 858454 | 17923659 |
| noise5-3000 | neural-hybrid | 12 | 9.693 | 3.30:1 | 57.53 | 0.08566 | 858454 | 18993614 |
| noise5-3000 | neural-hybrid | 14 | 11.445 | 2.80:1 | 69.58 | 0.02142 | 858454 | 22580311 |
| noise5-3000 | neural-hybrid | 16 | 13.485 | 2.37:1 | 81.62 | 0.005371 | 858454 | 26758580 |
| noise5-3000 | neural-only | — | 0.419 | 76.34:1 | 7.65 | 201.3 | 858454 | 0 |
