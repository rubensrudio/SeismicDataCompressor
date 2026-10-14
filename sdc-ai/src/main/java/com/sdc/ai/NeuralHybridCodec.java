package com.sdc.ai;

import com.sdc.ai.nn.NeuralModel;
import com.sdc.ai.nn.Tensor3;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Hybrid neural codec for 3D seismic volumes: a learned autoencoder predicts each section, and
 * the prediction residual is uniformly quantized, so the maximum error is guaranteed.
 *
 * <pre>
 *   x_n   = x / s                      s = peak |x| of the volume (stored)
 *   y     = round(encoder(x_n))        per inline section (crossline x time), int8 latents
 *   p     = decoder(y)                 prediction, identical on encoder and decoder side
 *   q     = round((x_n - p) / step)    step = 1 / (2^(bits-1) - 1)
 *   x_hat = s * (p + q * step)         |x - x_hat| <= s * step / 2 for every sample
 * </pre>
 *
 * <p>Payload = deflate(latents) + deflate(residual indices, stored as byte planes). With {@link Predictor#NONE} the
 * prediction is zero, which turns the codec into plain uniform quantization + Deflate with the
 * same step: the difference in size between the two modes is exactly what the neural predictor
 * buys. With {@code residualBits = 0} only the latents are stored (neural-only, lowest rate, no
 * error bound).</p>
 */
public final class NeuralHybridCodec {

    private static final byte[] MAGIC = {'S', 'D', 'C', 'N'};
    private static final int VERSION = 1;

    public enum Predictor { NEURAL, NONE }

    public record Settings(Predictor predictor, int residualBits, int deflaterLevel) {
        public Settings {
            if (residualBits != 0 && (residualBits < 2 || residualBits > 16)) {
                throw new IllegalArgumentException("residualBits must be 0 (none) or 2..16");
            }
            if (predictor == Predictor.NONE && residualBits == 0) {
                throw new IllegalArgumentException("predictor NONE needs a residual");
            }
        }
    }

    /** Encoded payload plus the split of its size, for reporting. */
    public record Encoded(byte[] payload, int latentBytes, int residualBytes) {}

    private NeuralHybridCodec() {}

    /**
     * The neural part of the encoding (latents + prediction). It does not depend on the residual
     * bit depth, so a rate–distortion sweep can compute it once and reuse it for every bit depth.
     * {@code model == null} gives the zero prediction ({@link Predictor#NONE}).
     */
    public record Prediction(Predictor predictor, float scale, int ni, int nj, int nt,
                             byte[] latents, float[][][] normalizedPrediction, byte[] modelSha256) {}

    public static Prediction predict(float[][][] vol, NeuralModel model) {
        int ni = vol.length, nj = vol[0].length, nt = vol[0][0].length;
        float s = 0f;
        for (float[][] a : vol) for (float[] t : a) for (float v : t) s = Math.max(s, Math.abs(v));
        if (s == 0f) s = 1f;
        if (model == null) {
            return new Prediction(Predictor.NONE, s, ni, nj, nt, new byte[0], null, new byte[32]);
        }
        float inv = 1f / s;
        int f = model.factor();
        int ph = roundUp(nj, f), pw = roundUp(nt, f);
        int lh = ph / f, lw = pw / f, lc = model.latentChannels();
        byte[] latents = new byte[ni * lh * lw * lc];
        float[][][] pred = new float[ni][][];
        for (int i = 0; i < ni; i++) {
            Tensor3 x = new Tensor3(ph, pw, 1);
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) x.data[j * pw + k] = vol[i][j][k] * inv;
            }
            Tensor3 y = model.encode(x);
            Tensor3 yq = new Tensor3(y.h, y.w, y.c);
            int base = i * lh * lw * lc;
            for (int n = 0; n < y.data.length; n++) {
                int v = Math.round(y.data[n]);
                if (v > 127) v = 127;
                if (v < -127) v = -127;
                latents[base + n] = (byte) v;
                yq.data[n] = v;
            }
            pred[i] = crop(model.decode(yq), nj, nt);
        }
        return new Prediction(Predictor.NEURAL, s, ni, nj, nt, latents, pred, model.sha256());
    }

    public static Encoded encode(float[][][] vol, NeuralModel model, Settings st) {
        return encode(vol, predict(vol, st.predictor() == Predictor.NEURAL ? model : null), st);
    }

    public static Encoded encode(float[][][] vol, Prediction p, Settings st) {
        if (p.predictor() != st.predictor()) throw new IllegalArgumentException("prediction/settings mismatch");
        int ni = p.ni(), nj = p.nj(), nt = p.nt();
        float inv = 1f / p.scale();
        int[] q = null;
        if (st.residualBits() > 0) {
            float step = 1f / maxQ(st.residualBits());
            q = new int[ni * nj * nt];
            for (int i = 0; i < ni; i++) {
                float[][] pred = p.normalizedPrediction() == null ? null : p.normalizedPrediction()[i];
                int base = i * nj * nt;
                for (int j = 0; j < nj; j++) {
                    for (int k = 0; k < nt; k++) {
                        float pv = pred == null ? 0f : pred[j][k];
                        q[base + j * nt + k] = Math.round((vol[i][j][k] * inv - pv) / step);
                    }
                }
            }
        }
        boolean neural = p.predictor() == Predictor.NEURAL;
        byte[] latentZ = neural ? deflate(p.latents(), st.deflaterLevel()) : new byte[0];
        int width = q == null ? 0 : widthFor(q);
        byte[] residualZ = q == null ? new byte[0] : deflate(toBytes(q, width), st.deflaterLevel());

        ByteArrayOutputStream bos = new ByteArrayOutputStream(latentZ.length + residualZ.length + 64);
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(st.predictor().ordinal());
            out.writeByte(st.residualBits());
            out.writeByte(width);
            out.writeInt(ni);
            out.writeInt(nj);
            out.writeInt(nt);
            out.writeFloat(p.scale());
            out.write(p.modelSha256());
            out.writeInt(latentZ.length);
            out.write(latentZ);
            out.writeInt(residualZ.length);
            out.write(residualZ);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new Encoded(bos.toByteArray(), latentZ.length, residualZ.length);
    }

    /**
     * Reconstruction that {@link #decode} would return for {@code encode(vol, p, residualBits)},
     * computed without the payload round trip (same arithmetic, so the result is bit-identical).
     * Used by rate–distortion sweeps; the benchmark checks the equality against a real decode.
     */
    public static float[][][] reconstruct(float[][][] vol, Prediction p, int residualBits) {
        int ni = p.ni(), nj = p.nj(), nt = p.nt();
        float s = p.scale(), inv = 1f / s;
        float step = residualBits == 0 ? 0f : 1f / maxQ(residualBits);
        float[][][] out = new float[ni][nj][nt];
        for (int i = 0; i < ni; i++) {
            float[][] pred = p.normalizedPrediction() == null ? null : p.normalizedPrediction()[i];
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {
                    float pv = pred == null ? 0f : pred[j][k];
                    float r = 0f;
                    if (residualBits > 0) {
                        int q = Math.round((vol[i][j][k] * inv - pv) / step);
                        r = q * step;
                    }
                    out[i][j][k] = (pv + r) * s;
                }
            }
        }
        return out;
    }

    public static float[][][] decode(byte[] payload, NeuralModel model) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(payload));
        byte[] magic = in.readNBytes(4);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("not an SDC neural payload");
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("unsupported neural payload version " + version);
        Predictor predictor = Predictor.values()[in.readUnsignedByte()];
        int bits = in.readUnsignedByte();
        int width = in.readUnsignedByte();
        int ni = in.readInt(), nj = in.readInt(), nt = in.readInt();
        float s = in.readFloat();
        byte[] sha = in.readNBytes(32);
        byte[] latentZ = in.readNBytes(in.readInt());
        byte[] residualZ = in.readNBytes(in.readInt());

        boolean neural = predictor == Predictor.NEURAL;
        if (neural) {
            if (model == null) throw new IOException("payload needs a neural model");
            if (!Arrays.equals(sha, model.sha256())) {
                throw new IOException("payload was encoded with a different model (sha256 mismatch)");
            }
        }
        int f = neural ? model.factor() : 1;
        int ph = roundUp(nj, f), pw = roundUp(nt, f);
        int lh = ph / f, lw = pw / f, lc = neural ? model.latentChannels() : 0;
        byte[] latents = neural ? inflate(latentZ, ni * lh * lw * lc) : null;
        int[] q = bits == 0 ? null : fromBytes(inflate(residualZ, ni * nj * nt * width), width);
        float step = bits == 0 ? 0f : 1f / maxQ(bits);

        float[][][] out = new float[ni][nj][nt];
        for (int i = 0; i < ni; i++) {
            float[][] pred = null;
            if (neural) {
                Tensor3 yq = new Tensor3(lh, lw, lc);
                int base = i * lh * lw * lc;
                for (int n = 0; n < yq.data.length; n++) yq.data[n] = latents[base + n];
                pred = crop(model.decode(yq), nj, nt);
            }
            int base = i * nj * nt;
            for (int j = 0; j < nj; j++) {
                for (int k = 0; k < nt; k++) {
                    float p = pred == null ? 0f : pred[j][k];
                    float r = q == null ? 0f : q[base + j * nt + k] * step;
                    out[i][j][k] = (p + r) * s;
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------

    static int maxQ(int bits) {
        return (1 << (bits - 1)) - 1;
    }

    private static int roundUp(int n, int f) {
        return (n + f - 1) / f * f;
    }

    private static float[][] crop(Tensor3 t, int h, int w) {
        float[][] o = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) o[y][x] = t.data[(y * t.w + x) * t.c];
        }
        return o;
    }

    private static int widthFor(int[] q) {
        int m = 0;
        for (int v : q) m = Math.max(m, Math.abs(v));
        return m <= Byte.MAX_VALUE ? 1 : (m <= Short.MAX_VALUE ? 2 : 4);
    }

    /** Big-endian values split into byte planes (most significant plane first). */
    private static byte[] toBytes(int[] q, int width) {
        int n = q.length;
        byte[] b = new byte[n * width];
        for (int p = 0; p < width; p++) {
            int shift = (width - 1 - p) * 8;
            for (int i = 0; i < n; i++) b[p * n + i] = (byte) (q[i] >>> shift);
        }
        return b;
    }

    private static int[] fromBytes(byte[] b, int width) {
        int n = b.length / width;
        int[] q = new int[n];
        for (int i = 0; i < n; i++) {
            int v = b[i];                          // sign-extended most significant byte
            for (int p = 1; p < width; p++) v = (v << 8) | (b[p * n + i] & 0xFF);
            q[i] = v;
        }
        return q;
    }

    private static byte[] deflate(byte[] in, int level) {
        Deflater d = new Deflater(level);
        try {
            d.setInput(in);
            d.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, in.length / 4));
            byte[] buf = new byte[1 << 16];
            while (!d.finished()) bos.write(buf, 0, d.deflate(buf));
            return bos.toByteArray();
        } finally {
            d.end();
        }
    }

    private static byte[] inflate(byte[] in, int expected) throws IOException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(in);
            byte[] out = new byte[expected];
            int off = 0;
            while (off < expected && !inf.finished()) {
                int n = inf.inflate(out, off, expected - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += n;
            }
            if (off != expected) throw new IOException("truncated stream: " + off + " of " + expected + " bytes");
            return out;
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inf.end();
        }
    }
}
