"""
Trains the SDC convolutional autoencoder used by the neural (hybrid) codec and exports it
to the dependency-free ".sdcnn" format that sdc-ai runs in pure Java.

The codec works on 2D seismic sections (crossline x time) of a volume normalized by its
peak absolute amplitude:

    encoder: conv3x3(c1) -> conv3x3/2(c2) -> conv3x3/2(c2) -> conv3x3/2(latent)   (factor 8)
    decoder: up2 -> conv3x3(c2) -> up2 -> conv3x3(c2) -> up2 -> conv3x3(c1) -> conv3x3(1)

Latents are rounded to integers by the codec, so training adds uniform noise U(-0.5, 0.5)
to the latents (the usual differentiable proxy for quantization) and penalizes their L1
norm, which keeps the integer latents small and cheap to entropy-code.

Data hygiene: the training/validation volumes come from SyntheticSegyGenerator with seeds
that never include the benchmark seed (20261012). See sdc-ai/README.md.

Usage:
    python train_autoencoder.py --train /data/train --val /data/val --out ../models/sdc-ae-v1.sdcnn
"""
import argparse
import glob
import hashlib
import json
import os
import struct
import time

import numpy as np
import tensorflow as tf

from segy_io import read_volume

MAGIC = b"SDCNN1\0\0"
LAYER_CONV, LAYER_LEAKY, LAYER_UP2 = 1, 2, 3
LEAKY_ALPHA = 0.1


def load_sections(folder):
    """Returns the list of normalized volumes found in the folder (*.sgy)."""
    vols = []
    for path in sorted(glob.glob(os.path.join(folder, "*.sgy"))):
        v = read_volume(path)
        v = v / max(float(np.abs(v).max()), 1e-12)
        vols.append(v.astype(np.float32))
        print(f"  {os.path.basename(path)} {v.shape}")
    return vols


def patch_dataset(vols, h, w, count, rng):
    """Random (h x w) crops from inline and crossline sections."""
    out = np.empty((count, h, w, 1), dtype=np.float32)
    for n in range(count):
        v = vols[rng.integers(len(vols))]
        ni, nj, nt = v.shape
        if rng.random() < 0.5:   # inline section: (xline, time)
            i = rng.integers(ni)
            j0, t0 = rng.integers(nj - h + 1), rng.integers(nt - w + 1)
            p = v[i, j0:j0 + h, t0:t0 + w]
        else:                    # crossline section: (inline, time)
            j = rng.integers(nj)
            i0, t0 = rng.integers(ni - h + 1), rng.integers(nt - w + 1)
            p = v[i0:i0 + h, j, t0:t0 + w]
        if rng.random() < 0.5:
            p = p[::-1, :]       # lateral flip (dips both ways)
        out[n, :, :, 0] = p
    return out


def conv(filters, stride=1, name=None):
    return tf.keras.layers.Conv2D(filters, 3, strides=stride, padding="same", name=name)


def build(c1, c2, latent):
    leaky = lambda: tf.keras.layers.LeakyReLU(negative_slope=LEAKY_ALPHA)
    enc = tf.keras.Sequential([
        tf.keras.Input((None, None, 1)),
        conv(c1), leaky(),
        conv(c2, 2), leaky(),
        conv(c2, 2), leaky(),
        conv(latent, 2),
    ], name="encoder")
    dec = tf.keras.Sequential([
        tf.keras.Input((None, None, latent)),
        tf.keras.layers.UpSampling2D(2), conv(c2), leaky(),
        tf.keras.layers.UpSampling2D(2), conv(c2), leaky(),
        tf.keras.layers.UpSampling2D(2), conv(c1), leaky(),
        conv(1),
    ], name="decoder")
    return enc, dec


class AE(tf.keras.Model):
    def __init__(self, enc, dec, l1):
        super().__init__()
        self.enc, self.dec, self.l1 = enc, dec, l1

    def call(self, x, training=False):
        y = self.enc(x)
        if training:
            y = y + tf.random.uniform(tf.shape(y), -0.5, 0.5)
        else:
            y = tf.round(y)
        return self.dec(y), y

    def train_step(self, x):
        with tf.GradientTape() as tape:
            xh, y = self(x, training=True)
            mse = tf.reduce_mean(tf.square(x - xh))
            rate = tf.reduce_mean(tf.abs(y))
            loss = mse + self.l1 * rate
        self.optimizer.apply_gradients(zip(tape.gradient(loss, self.trainable_variables), self.trainable_variables))
        return {"loss": loss, "mse": mse, "latent_l1": rate}

    def test_step(self, x):
        xh, y = self(x, training=False)
        return {"loss": tf.reduce_mean(tf.square(x - xh)), "latent_l1": tf.reduce_mean(tf.abs(y))}


def export_sdcnn(enc, dec, latent, path, meta):
    """Binary little-endian layout read by com.sdc.ai.nn.NeuralModel."""
    def layers_of(model):
        rows = []
        for layer in model.layers:
            if isinstance(layer, tf.keras.layers.Conv2D):
                k, b = layer.get_weights()          # k: (kh, kw, cin, cout) HWIO
                rows.append((LAYER_CONV, layer.strides[0], k, b))
            elif isinstance(layer, tf.keras.layers.LeakyReLU):
                rows.append((LAYER_LEAKY, LEAKY_ALPHA))
            elif isinstance(layer, tf.keras.layers.UpSampling2D):
                rows.append((LAYER_UP2,))
            else:
                raise ValueError(f"unsupported layer {layer}")
        return rows

    meta_json = json.dumps(meta, sort_keys=True).encode("utf-8")
    with open(path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<iii", 1, latent, 8))       # format version, latent channels, factor
        f.write(struct.pack("<i", len(meta_json)))
        f.write(meta_json)
        for model in (enc, dec):
            rows = layers_of(model)
            f.write(struct.pack("<i", len(rows)))
            for r in rows:
                f.write(struct.pack("<i", r[0]))
                if r[0] == LAYER_CONV:
                    _, stride, k, b = r
                    kh, kw, cin, cout = k.shape
                    f.write(struct.pack("<iiiii", kh, kw, cin, cout, stride))
                    f.write(np.ascontiguousarray(k, dtype="<f4").tobytes())
                    f.write(np.ascontiguousarray(b, dtype="<f4").tobytes())
                elif r[0] == LAYER_LEAKY:
                    f.write(struct.pack("<f", r[1]))
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train", required=True)
    ap.add_argument("--val", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--c1", type=int, default=16)
    ap.add_argument("--c2", type=int, default=32)
    ap.add_argument("--latent", type=int, default=8)
    ap.add_argument("--l1", type=float, default=2e-5)
    ap.add_argument("--patch", type=int, nargs=2, default=[64, 128])
    ap.add_argument("--train-patches", type=int, default=24000)
    ap.add_argument("--val-patches", type=int, default=2000)
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--fixture", help="write a parity fixture (input/latent/output) for the Java tests")
    args = ap.parse_args()

    tf.keras.utils.set_random_seed(args.seed)
    rng = np.random.default_rng(args.seed)
    print("train volumes:"); train_vols = load_sections(args.train)
    print("val volumes:"); val_vols = load_sections(args.val)
    h, w = args.patch
    xtr = patch_dataset(train_vols, h, w, args.train_patches, rng)
    xva = patch_dataset(val_vols, h, w, args.val_patches, rng)

    enc, dec = build(args.c1, args.c2, args.latent)
    model = AE(enc, dec, args.l1)
    sched = tf.keras.optimizers.schedules.CosineDecay(1e-3, args.epochs * (len(xtr) // args.batch))
    model.compile(optimizer=tf.keras.optimizers.Adam(sched))
    t0 = time.time()
    hist = model.fit(xtr, batch_size=args.batch, epochs=args.epochs, validation_data=(xva,), verbose=2, shuffle=True)
    train_seconds = time.time() - t0

    val_mse = float(hist.history["val_loss"][-1])
    signal = float(np.mean(np.square(xva)))
    meta = {
        "architecture": f"conv-ae c1={args.c1} c2={args.c2} latent={args.latent} factor=8 leaky={LEAKY_ALPHA}",
        "framework": f"tensorflow {tf.__version__}",
        "train_dir": os.path.basename(os.path.normpath(args.train)),
        "train_volumes": len(train_vols),
        "val_volumes": len(val_vols),
        "patch": [h, w],
        "train_patches": args.train_patches,
        "epochs": args.epochs,
        "l1": args.l1,
        "seed": args.seed,
        "val_mse_normalized": val_mse,
        "val_snr_db_rounded_latents": 10 * np.log10(signal / val_mse),
        "train_seconds": round(train_seconds, 1),
        "params": int(enc.count_params() + dec.count_params()),
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    sha = export_sdcnn(enc, dec, args.latent, args.out, meta)
    with open(os.path.splitext(args.out)[0] + ".json", "w") as f:
        json.dump({**meta, "sha256": sha}, f, indent=2)
    print(json.dumps({**meta, "sha256": sha}, indent=2))

    if args.fixture:
        x = xva[:1, :32, :48, :]
        y_raw = enc(x).numpy()
        y = np.round(y_raw)
        xh = dec(y).numpy()
        with open(args.fixture, "wb") as f:
            for arr in (x, y_raw, y, xh):
                f.write(struct.pack("<iiii", *arr.shape))
                f.write(np.ascontiguousarray(arr, dtype="<f4").tobytes())
        print("fixture written to", args.fixture)


if __name__ == "__main__":
    main()
