# sdc-ai — neural (hybrid) seismic codec

This module adds a **learned predictor** to SDC and measures what it contributes. A small
convolutional autoencoder predicts each seismic section; the prediction error (residual) is
quantized and stored, so the reconstruction error is **bounded** (half a quantization step, up to
float32 rounding). The model is
trained with TensorFlow, then executed in **pure Java** — the codec and the benchmark do not
need a TensorFlow runtime.

## How the codec works

```
x_n   = x / s                       s = peak |x| of the volume (stored in the payload)
y     = round(encoder(x_n))         per inline section (crossline × time), int8 latents
p     = decoder(y)                  prediction, identical on the encoding and decoding side
q     = round((x_n − p) / step)     step = 1 / (2^(bits−1) − 1)
x̂     = s · (p + q · step)          |x − x̂| ≤ s · step / 2 (+ float32 rounding, ~1 ulp of x)
```

* **Payload** = Deflate(latents) + Deflate(residual indices stored as byte planes), plus the
  SHA-256 of the model. A file can only be decoded with the exact model that wrote it.
* **`Predictor.NONE`** runs the same quantizer with a zero prediction. Comparing the two modes
  at the same `bits` isolates exactly what the neural network buys (same step, same error bound,
  same container and entropy coder).
* **`residualBits = 0`** stores only the latents ("neural only"): the lowest rate, no error bound.

| Class | Role |
|---|---|
| `com.sdc.ai.nn.NeuralModel` | Loads an `.sdcnn` model and runs encoder/decoder |
| `com.sdc.ai.nn.Conv2D`, `LeakyRelu`, `UpSample2` | Inference layers with TensorFlow/Keras semantics (`same` padding, nearest upsampling) |
| `com.sdc.ai.NeuralHybridCodec` | Encode/decode volumes; `predict` + `reconstruct` for rate–distortion sweeps |
| `com.sdc.ai.AeRuntime` | TensorFlow Java runtime placeholder (not used by the codec) |

Inference is deterministic: rows are computed in parallel but every value is accumulated in a
fixed order, so the encoder and the decoder always build the same prediction. The test
`NeuralModelParityTest` checks the Java inference against TensorFlow outputs saved at training
time (measured differences: ≤ 2e-7 on the decoder output, ≤ 2e-6 on the encoder latents; the test
asserts 1e-4 on a 32 × 48 section).

## Model `models/sdc-ae-v1.sdcnn`

| | |
|---|---|
| Architecture | encoder conv3×3(16) → conv3×3/2(32) → conv3×3/2(32) → conv3×3/2(8); decoder mirrors it with nearest 2× upsampling; LeakyReLU(0.1); **32,713 parameters** |
| Latents | 8 channels at 1/8 resolution in both directions (1 latent value per 8 samples) |
| Training data | 10 synthetic volumes 64 × 64 × 1000, seeds **1000–1009** (`TrainingDataGenerator`) |
| Validation data | 2 volumes, seeds **2000–2001** (files named `train-2000/2001.sgy` by the generator) |
| Benchmark data | seed **20261012** — never used for training or validation |
| Training | 16,000 random 64 × 128 patches from inline and crossline sections, 10 epochs, Adam + cosine decay, uniform latent noise (quantization proxy) + L1 rate penalty 2e-5, TensorFlow 2.20 CPU, ~19 min |
| Metadata | `models/sdc-ae-v1.json` (includes the SHA-256), log in `models/sdc-ae-v1.train.log` |

### Reproduce the model

```bash
pip install -r sdc-ai/training/requirements.txt
mvn -q -pl sdc-bench -am -DskipTests package
CP=sdc-bench/target/sdc-bench-0.1.0-SNAPSHOT-jar-with-dependencies.jar
java -cp $CP com.sdc.bench.TrainingDataGenerator /tmp/train 10 1000 64 64 1000
java -cp $CP com.sdc.bench.TrainingDataGenerator /tmp/val 2 2000 64 64 1000
cd sdc-ai/training
python train_autoencoder.py --train /tmp/train --val /tmp/val --out ../models/sdc-ae-v1.sdcnn \
    --train-patches 16000 --epochs 10 --fixture ../src/test/resources/sdc-ae-v1-fixture.bin
```

TensorFlow training on CPU is not bit-reproducible across machines, so a retrained model will
have a different SHA-256 and slightly different results; the committed model is the one the
benchmark results refer to.

## Limitations (read before quoting results)

* **Synthetic data only.** The model was trained and evaluated on volumes from the same
  synthetic generator (different seeds). Real seismic data is less regular; results on real
  data are unknown until the benchmark is run with `SEGY=...` on public volumes (F3 Netherlands,
  Volve). A model trained on synthetic data may need retraining on real data.
* **2D model.** Each inline section is predicted independently; redundancy across inlines is
  not exploited.
* **Speed.** Pure-Java CPU inference is slow: on `synthetic-medium` (2 vCPUs) 25–90× slower to
  compress and 170–390× slower to decompress than the classical codecs.
* **Entropy coding** is Deflate for every codec in the comparison, which keeps the comparison
  fair but is far from optimal; a learned entropy model would reduce the latent cost further.
