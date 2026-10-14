"""
Rate–distortion report: combines the Java sweep (rd.csv) with two external, state-of-the-art
scientific lossy compressors run on exactly the same samples, and summarizes the comparison.

    ZFP  (fixed-accuracy mode, via zfpy)
    SZ3  (absolute error bound, via the hdf5plugin HDF5 filter)

Outputs, written next to rd.csv:
    rd_external.csv   external codec points
    rd_report.md      bits/sample needed to reach target SNRs, and BD-rate vs. sdc-3d
    rd_<dataset>.png  SNR vs bits/sample, and bits/sample relative to SDC 3D at equal SNR

BD-rate here is the average relative difference in bits/sample at equal SNR over the SNR range
both curves cover, using piecewise-linear interpolation of log2(bits/sample) (the curves are not
smooth enough for the classic cubic fit because Deflate's efficiency changes in steps).

Usage:
    python benchmark/rd_report.py --results benchmark/results/<run> \
        --segy synthetic-medium=benchmark/data/synthetic-medium.sgy [--segy name=path ...]
"""
import argparse
import csv
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "sdc-ai", "training"))
from segy_io import read_volume  # noqa: E402

TARGET_SNRS = [15, 20, 25, 30, 40, 50, 60]
# Reference categorical palette (validated, fixed order) — see the project's chart notes.
COLORS = {
    "sdc-3d": "#2a78d6",
    "uniform-quant": "#eb6834",
    "neural-hybrid": "#1baf7a",
    "neural-only": "#1baf7a",
    "zfp": "#eda100",
    "sz3": "#e87ba4",
}
LABELS = {
    "sdc-3d": "SDC 3D (closed-loop DPCM)",
    "uniform-quant": "Uniform quantization (no prediction)",
    "neural-hybrid": "Neural hybrid (autoencoder + residual)",
    "neural-only": "Neural only (latents)",
    "zfp": "ZFP (fixed accuracy)",
    "sz3": "SZ3 (absolute error bound)",
}


def quality(x, y):
    x = x.astype(np.float64)
    e = x - y.astype(np.float64)
    snr = 10 * np.log10(np.sum(x * x) / np.sum(e * e)) if np.any(e) else float("inf")
    return snr, float(np.max(np.abs(e)))


def zfp_points(vol):
    import zfpy
    peak = float(np.abs(vol).max())
    pts = []
    for k in np.arange(-2.0, 15.5, 1.0):
        tol = peak * 2.0 ** (-k)
        comp = zfpy.compress_numpy(vol, tolerance=tol)
        rec = zfpy.decompress_numpy(comp)
        snr, mx = quality(vol, rec)
        pts.append(("zfp", f"tol=peak*2^{-k:g}", len(comp), snr, mx))
    return pts


def sz3_points(vol):
    import tempfile
    import h5py
    import hdf5plugin
    peak = float(np.abs(vol).max())
    pts = []
    for k in np.arange(1.0, 15.5, 1.0):
        eb = peak * 2.0 ** (-k)
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "v.h5")
            with h5py.File(path, "w") as f:      # write, close, reopen: the read must decompress
                f.create_dataset("v", data=vol, chunks=vol.shape, **hdf5plugin.SZ3(absolute=eb))
            with h5py.File(path, "r") as f:
                d = f["v"]
                size = d.id.get_storage_size()
                rec = d[...]
        snr, mx = quality(vol, rec)
        pts.append(("sz3", f"eb=peak*2^{-k:g}", size, snr, mx))
    return pts


def interp_rate(points, snr):
    """bits/sample at the given SNR by piecewise-linear interpolation of log2(rate); None outside."""
    pts = sorted((p for p in points if np.isfinite(p[1])), key=lambda p: p[1])
    s = np.array([p[1] for p in pts])
    r = np.log2([p[0] for p in pts])
    if len(pts) < 2 or snr < s[0] or snr > s[-1]:
        return None
    # enforce monotone envelope (best rate achieving at least that SNR)
    return float(2 ** np.interp(snr, s, r))


def bd_rate(test, ref, step=0.25):
    lo = max(min(p[1] for p in test), min(p[1] for p in ref))
    hi = min(max(p[1] for p in test if np.isfinite(p[1])), max(p[1] for p in ref if np.isfinite(p[1])))
    if hi <= lo:
        return None, None
    grid = np.arange(lo, hi, step)
    diffs = [np.log2(interp_rate(test, g)) - np.log2(interp_rate(ref, g)) for g in grid]
    return float((2 ** np.mean(diffs) - 1) * 100), (lo, hi)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True)
    ap.add_argument("--segy", action="append", default=[], help="dataset=path.sgy")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(os.path.join(args.results, "rd.csv"))))
    ext_rows = []
    import importlib.metadata as md
    versions = ", ".join(f"{p} {md.version(p)}" for p in ("zfpy", "hdf5plugin", "h5py", "numpy"))
    report = ["# Rate–distortion report", "", f"External codec libraries: {versions}.", "",
              "bits/sample over raw float32 samples (32 = uncompressed); SNR in dB on the original amplitudes.",
              "External codecs (ZFP, SZ3) run in Python on exactly the same samples.", ""]

    for spec in args.segy:
        name, path = spec.split("=", 1)
        vol = read_volume(path)
        n = vol.size
        curves = {}
        for r in rows:
            if r["dataset"] != name:
                continue
            curves.setdefault(r["codec"], []).append((float(r["bits_per_sample"]), float(r["snr_db"]), int(r["bits"])))
        for codec, setting, size, snr, mx in zfp_points(vol) + sz3_points(vol):
            bps = size * 8.0 / n
            curves.setdefault(codec, []).append((bps, snr, setting))
            ext_rows.append([name, codec, setting, size, f"{bps:.6g}", f"{snr:.6g}", f"{mx:.6g}"])

        report += [f"## {name} ({vol.shape[0]} × {vol.shape[1]} × {vol.shape[2]})", "",
                   "### bits/sample needed to reach a target SNR (lower is better; — = outside the measured range)", "",
                   "| Codec | " + " | ".join(f"{t} dB" for t in TARGET_SNRS) + " |",
                   "|---|" + "---:|" * len(TARGET_SNRS)]
        for codec in ["sdc-3d", "uniform-quant", "neural-hybrid", "zfp", "sz3"]:
            if codec not in curves:
                continue
            cells = []
            for t in TARGET_SNRS:
                v = interp_rate(curves[codec], t)
                cells.append("—" if v is None else f"{v:.2f}")
            report.append(f"| {LABELS[codec]} | " + " | ".join(cells) + " |")
        if "neural-only" in curves:
            b, s, _ = curves["neural-only"][0]
            report += ["", f"Neural only (latents, no residual): **{b:.3f} bits/sample at {s:.2f} dB** "
                           f"(ratio {32 / b:.0f}:1)."]

        report += ["", "### BD-rate (average bits/sample difference at equal SNR; negative = fewer bits)", "",
                   "| Codec | vs. SDC 3D | vs. uniform quantization | SNR range compared |", "|---|---:|---:|---|"]
        for codec in ["neural-hybrid", "uniform-quant", "zfp", "sz3"]:
            if codec not in curves:
                continue
            a, rng = bd_rate(curves[codec], curves["sdc-3d"])
            b, _ = bd_rate(curves[codec], curves["uniform-quant"]) if codec != "uniform-quant" else (None, None)
            report.append(f"| {LABELS[codec]} | {'—' if a is None else f'{a:+.1f}%'} | "
                          f"{'—' if b is None else f'{b:+.1f}%'} | "
                          f"{'' if rng is None else f'{rng[0]:.1f}–{rng[1]:.1f} dB'} |")
        report.append("")
        plot(name, curves, os.path.join(args.results, f"rd_{name}.png"))
        report += [f"![rate–distortion {name}](rd_{name}.png)", ""]

    with open(os.path.join(args.results, "rd_external.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["dataset", "codec", "setting", "output_bytes", "bits_per_sample", "snr_db", "max_abs_error"])
        w.writerows(ext_rows)
    with open(os.path.join(args.results, "rd_report.md"), "w") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))


def plot(name, curves, path):
    """Left: SNR vs bits/sample. Right: bits/sample relative to SDC 3D at equal SNR."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    surface, ink, ink2, grid = "#fcfcfb", "#0b0b0b", "#52514e", "#e4e3df"
    order = ["sdc-3d", "uniform-quant", "neural-hybrid", "zfp", "sz3"]
    markers = {"sdc-3d": "o", "uniform-quant": "s", "neural-hybrid": "D", "zfp": "^", "sz3": "v"}
    fig, (ax, bx) = plt.subplots(1, 2, figsize=(12, 5), dpi=150, gridspec_kw={"width_ratios": [1.05, 1]})
    fig.patch.set_facecolor(surface)
    for a in (ax, bx):
        a.set_facecolor(surface)
        a.grid(True, color=grid, lw=0.8)
        for sp in ("top", "right"):
            a.spines[sp].set_visible(False)
        for sp in ("left", "bottom"):
            a.spines[sp].set_color("#b5b4ae")
        a.tick_params(colors=ink2)

    ref = curves["sdc-3d"]
    for codec in order:
        if codec not in curves:
            continue
        pts = sorted((p for p in curves[codec] if np.isfinite(p[1]) and p[0] <= 16), key=lambda p: p[0])
        ax.plot([p[0] for p in pts], [p[1] for p in pts], color=COLORS[codec], lw=2, marker=markers[codec], ms=5,
                markeredgecolor=surface, markeredgewidth=1, label=LABELS[codec])
        if codec == "sdc-3d":
            bx.axhline(0, color=COLORS[codec], lw=2, label=LABELS[codec] + " (reference)")
            continue
        grid_snr = np.arange(13.0, 80.0, 0.5)
        rel = []
        for g in grid_snr:
            t, r = interp_rate(curves[codec], g), interp_rate(ref, g)
            rel.append(np.nan if t is None or r is None else (t / r - 1) * 100)
        bx.plot(grid_snr, rel, color=COLORS[codec], lw=2, label=LABELS[codec])
    if "neural-only" in curves:
        b, s_, _ = curves["neural-only"][0]
        ax.plot([b], [s_], marker="*", ms=14, color=COLORS["neural-only"], markeredgecolor=surface, ls="none",
                label=LABELS["neural-only"])
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 90)
    ax.set_xlabel("bits per sample (32 = uncompressed float32)", color=ink2)
    ax.set_ylabel("SNR (dB, higher is better)", color=ink2)
    ax.set_title("Rate–distortion", color=ink, loc="left", fontsize=11)
    ax.legend(frameon=False, fontsize=8, loc="upper left", labelcolor=ink)
    bx.legend(frameon=False, fontsize=7.5, loc="upper right", labelcolor=ink)
    bx.set_xlabel("SNR (dB)", color=ink2)
    bx.set_ylabel("bits/sample vs. SDC 3D at equal SNR (%)", color=ink2)
    bx.set_title("Rate difference vs. SDC 3D (below 0 = smaller files)", color=ink, loc="left", fontsize=11)
    bx.set_ylim(-60, 60)
    fig.suptitle(f"{name}", x=0.01, ha="left", color=ink, fontsize=12)
    fig.tight_layout()
    fig.savefig(path, facecolor=fig.get_facecolor())
    plt.close(fig)


if __name__ == "__main__":
    main()
