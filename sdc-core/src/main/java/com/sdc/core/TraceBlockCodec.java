package com.sdc.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Per-trace codec (2D container):
 *
 * Encode: min/max -> normalize to [-1,1] -> closed-loop DPCM (previous reconstructed sample as
 * predictor) -> quantization with the profile's bit depth -> Deflate. See {@link ClosedLoopQuantizer}
 * for the payload layout. Decode is the inverse and reads the bit depth from the payload.
 */
public final class TraceBlockCodec {

    private TraceBlockCodec() {}

    public static CompressedTraceBlock compress(TraceBlock tb) {
        return compress(tb, CompressionProfile.defaultHighQuality());
    }

    /**
     * Closed-loop DPCM along the time axis: each sample is predicted from the previous
     * <em>reconstructed</em> sample, and the residual is quantized with {@code profile.effectiveBits()}.
     * The bit depth is stored in the payload, so decoding needs no profile.
     */
    public static CompressedTraceBlock compress(TraceBlock tb, CompressionProfile profile) {
        float[] samples = tb.samples();
        int n = samples.length;

        float[] mm = Preprocessing.minMax(samples);
        float min = mm[0];
        float max = mm[1];
        float[] norm = Preprocessing.normalizeToMinusOneToOne(samples);

        int bits = profile.effectiveBits();
        int maxQ = ClosedLoopQuantizer.maxQ(bits);
        float inv = 1.0f / maxQ;
        int[] q = new int[n];
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            q[i] = ClosedLoopQuantizer.quantize(norm[i] - prev, maxQ);
            prev = ClosedLoopQuantizer.reconstruct(prev, q[i], inv);
        }
        byte[] payload = ClosedLoopQuantizer.pack(q, bits, profile.deflaterLevel());
        return new CompressedTraceBlock(tb.traceId(), min, max, n, payload);
    }

    public static TraceBlock decompress(CompressedTraceBlock cb) {
        if (!ClosedLoopQuantizer.isClosedLoop(cb.payload())) {
            return decompressLegacy(cb);
        }
        int n = cb.samplesPerTrace();
        int bits = ClosedLoopQuantizer.storedBits(cb.payload());
        int[] q = ClosedLoopQuantizer.unpack(cb.payload(), n);
        float inv = 1.0f / ClosedLoopQuantizer.maxQ(bits);
        float[] norm = new float[n];
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            prev = ClosedLoopQuantizer.reconstruct(prev, q[i], inv);
            norm[i] = prev;
        }
        float[] samples = Preprocessing.denormalizeFromMinusOneToOne(norm, cb.min(), cb.max());
        return new TraceBlock(cb.traceId(), samples);
    }

    /**
     * Decoder for payloads written before the closed-loop revision (raw zlib stream, open-loop
     * delta, 16-bit quantization). Kept so existing .sdc v2 files remain readable; files written
     * with fewer than 16 bits by that version cannot be decoded correctly because the bit depth
     * was never stored.
     */
    static TraceBlock decompressLegacy(CompressedTraceBlock cb) {
        byte[] rawBytes = inflate(cb.payload());
        short[] q = bytesToShorts(rawBytes, cb.samplesPerTrace());
        float[] deltasNorm = LinearQuantizer.decode(q);
        float[] norm = Preprocessing.deltaDecode(deltasNorm);
        float[] samples = Preprocessing.denormalizeFromMinusOneToOne(norm, cb.min(), cb.max());
        return new TraceBlock(cb.traceId(), samples);
    }

    // ---------- Helpers ----------

    static byte[] shortsToBytes(short[] data) {
        byte[] out = new byte[data.length * 2];
        int j = 0;
        for (short v : data) {
            out[j++] = (byte) (v >>> 8);
            out[j++] = (byte) (v);
        }
        return out;
    }

    static short[] bytesToShorts(byte[] data, int expectedSamples) {
        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("invalid short-encoded buffer length: " + data.length);
        }
        int n = data.length / 2;
        if (expectedSamples > 0 && n != expectedSamples) {
            // em produção, talvez só logar; aqui vamos ser estritos
            throw new IllegalStateException("expected " + expectedSamples + " samples but got " + n);
        }
        short[] out = new short[n];
        int j = 0;
        for (int i = 0; i < n; i++) {
            int hi = data[j++] & 0xFF;
            int lo = data[j++] & 0xFF;
            out[i] = (short) ((hi << 8) | lo);
        }
        return out;
    }

    static byte[] deflate(byte[] input) {
        return deflate(input, java.util.zip.Deflater.BEST_COMPRESSION);
    }

    static byte[] deflate(byte[] input, int level) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(level);
        deflater.setInput(input);
        deflater.finish();

        byte[] buffer = new byte[4096];
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(input.length)) {
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Unexpected IO error during deflate", e);
        } finally {
            deflater.end();
        }
    }


    static byte[] inflate(byte[] input) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);

        byte[] buffer = new byte[4096];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length * 2)) {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput()) break;
                    // evita loop infinito em caso de dados corrompidos
                }
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error during inflate", e);
        } finally {
            inflater.end();
        }
    }

    
}
