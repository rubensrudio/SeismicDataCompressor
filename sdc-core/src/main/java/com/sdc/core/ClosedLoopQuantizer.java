package com.sdc.core;

import java.io.ByteArrayOutputStream;

/**
 * Closed-loop (DPCM) quantization shared by the 2D and 3D codecs, plus the self-describing
 * payload format used since the "closed-loop" codec revision.
 *
 * <p>Why closed loop: the predictor must use the <em>reconstructed</em> samples on both sides.
 * The previous (open-loop) codecs predicted from the original samples while encoding and from
 * the reconstructed samples while decoding, so quantization error accumulated along the
 * prediction chain. Here the encoder runs the decoder's reconstruction internally, so the
 * error of every sample is bounded by half a quantization step (plus the rare overload case,
 * which the loop corrects on the following samples).</p>
 *
 * <p>Payload layout (all codecs): {@code [FORMAT_CLOSED_LOOP][bits][deflate(stream)]} where the
 * stream holds one signed byte per sample when {@code bits <= 8}; otherwise each sample is a
 * 16-bit value stored as two byte planes (all high bytes, then all low bytes), which Deflate
 * compresses much better than interleaved shorts because the high bytes are mostly 0x00/0xFF. Legacy payloads are raw zlib streams, whose first byte is always {@code 0x78}
 * for {@link java.util.zip.Deflater}, so the two layouts cannot be confused.</p>
 */
public final class ClosedLoopQuantizer {

    /** First payload byte of the closed-loop format. */
    public static final byte FORMAT_CLOSED_LOOP = 0x01;

    private ClosedLoopQuantizer() {}

    /** Largest quantization index for the given bit depth (e.g. 16 bits -> 32767). */
    public static int maxQ(int bits) {
        if (bits < 2 || bits > 16) {
            throw new IllegalArgumentException("bits must be between 2 and 16, got " + bits);
        }
        return (1 << (bits - 1)) - 1;
    }

    /** Quantizes a residual expressed in normalized units ([-1,1] is the full step range). */
    public static int quantize(float residual, int maxQ) {
        int q = Math.round(residual * maxQ);
        if (q > maxQ) q = maxQ;
        if (q < -maxQ) q = -maxQ;
        return q;
    }

    /** Reconstructs a sample from prediction + dequantized residual, clamped to [-1,1]. */
    public static float reconstruct(float prediction, int q, float invMaxQ) {
        float r = prediction + q * invMaxQ;
        if (r > 1f) r = 1f;
        if (r < -1f) r = -1f;
        return r;
    }

    /** True if the payload uses the closed-loop format. */
    public static boolean isClosedLoop(byte[] payload) {
        return payload != null && payload.length >= 2 && payload[0] == FORMAT_CLOSED_LOOP;
    }

    /** Bit depth stored in a closed-loop payload. */
    public static int storedBits(byte[] payload) {
        return payload[1] & 0xFF;
    }

    /** Serializes quantization indices into a self-describing, deflated payload. */
    public static byte[] pack(int[] q, int bits, int deflaterLevel) {
        byte[] stream;
        if (bits <= 8) {
            stream = new byte[q.length];
            for (int i = 0; i < q.length; i++) stream[i] = (byte) q[i];
        } else {
            int n = q.length;
            stream = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                stream[i] = (byte) (q[i] >>> 8);   // high-byte plane
                stream[n + i] = (byte) q[i];       // low-byte plane
            }
        }
        byte[] deflated = TraceBlockCodec.deflate(stream, deflaterLevel);
        ByteArrayOutputStream out = new ByteArrayOutputStream(deflated.length + 2);
        out.write(FORMAT_CLOSED_LOOP);
        out.write(bits);
        out.write(deflated, 0, deflated.length);
        return out.toByteArray();
    }

    /** Inverse of {@link #pack}: returns the quantization indices. */
    public static int[] unpack(byte[] payload, int expectedSamples) {
        if (!isClosedLoop(payload)) {
            throw new IllegalArgumentException("not a closed-loop payload");
        }
        int bits = storedBits(payload);
        maxQ(bits); // validates
        byte[] deflated = new byte[payload.length - 2];
        System.arraycopy(payload, 2, deflated, 0, deflated.length);
        byte[] stream = TraceBlockCodec.inflate(deflated);
        int[] q = new int[expectedSamples];
        if (bits <= 8) {
            if (stream.length != expectedSamples) {
                throw new IllegalStateException("expected " + expectedSamples + " samples but got " + stream.length);
            }
            for (int i = 0; i < q.length; i++) q[i] = stream[i];
        } else {
            if (stream.length != expectedSamples * 2) {
                throw new IllegalStateException("expected " + expectedSamples + " samples but got " + stream.length / 2);
            }
            int n = expectedSamples;
            for (int i = 0; i < n; i++) {
                int hi = stream[i];            // sign-extended
                int lo = stream[n + i] & 0xFF;
                q[i] = (short) ((hi << 8) | lo);
            }
        }
        return q;
    }
}
