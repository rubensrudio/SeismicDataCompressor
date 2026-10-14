"""Minimal SEG-Y reader for the training pipeline (fixed-length traces, format 1 or 5,
inline at bytes 189-192 and crossline at 193-196) - the same layout SegyIO (Java) reads."""
import struct
import numpy as np


def ibm_to_float(words: np.ndarray) -> np.ndarray:
    words = words.astype(np.uint32)
    sign = np.where(words >> 31, -1.0, 1.0)
    exponent = ((words >> 24) & 0x7F).astype(np.int32) - 64
    mantissa = (words & 0x00FFFFFF).astype(np.float64) / float(1 << 24)
    return (sign * mantissa * np.power(16.0, exponent)).astype(np.float32)


def read_volume(path):
    """Returns a float32 array shaped (inlines, xlines, samples)."""
    with open(path, "rb") as f:
        f.seek(3200)
        binhdr = f.read(400)
        ns = struct.unpack(">H", binhdr[20:22])[0]
        fmt = struct.unpack(">H", binhdr[24:26])[0]
        if fmt not in (1, 5):
            raise ValueError(f"unsupported SEG-Y sample format {fmt}")
        raw = np.frombuffer(f.read(), dtype=np.uint8)
    trace_bytes = 240 + 4 * ns
    n = raw.size // trace_bytes
    raw = raw[: n * trace_bytes].reshape(n, trace_bytes)
    headers = raw[:, :240]
    il = headers[:, 188:192].copy().view(">i4").ravel()
    xl = headers[:, 192:196].copy().view(">i4").ravel()
    words = raw[:, 240:].copy().view(">u4")
    samples = words.view(">f4").astype(np.float32) if fmt == 5 else ibm_to_float(words)
    ils, xls = np.unique(il), np.unique(xl)
    vol = np.zeros((ils.size, xls.size, ns), dtype=np.float32)
    vol[np.searchsorted(ils, il), np.searchsorted(xls, xl)] = samples
    return vol
