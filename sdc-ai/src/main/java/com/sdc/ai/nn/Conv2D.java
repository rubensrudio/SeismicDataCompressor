package com.sdc.ai.nn;

import java.util.stream.IntStream;

/**
 * 3x3 (or any k x k) 2D convolution with TensorFlow/Keras "same" padding semantics.
 *
 * <p>Weights are in Keras HWIO order: {@code w[((ky*kw + kx)*cin + ci)*cout + co]}.
 * For stride s and input size n the output size is ceil(n/s); the total padding is
 * {@code max((out-1)*s + k - n, 0)} with {@code floor(total/2)} on the leading side, which is
 * exactly what TensorFlow does.</p>
 *
 * <p>Rows are computed in parallel, but every output value is accumulated in a fixed order, so
 * results are bit-identical regardless of the number of threads — the codec relies on this
 * because the encoder and the decoder must reconstruct exactly the same prediction.</p>
 */
public final class Conv2D implements Layer {
    final int kh, kw, cin, cout, stride;
    final float[] weights;
    final float[] bias;

    public Conv2D(int kh, int kw, int cin, int cout, int stride, float[] weights, float[] bias) {
        if (weights.length != kh * kw * cin * cout) throw new IllegalArgumentException("weights size");
        if (bias.length != cout) throw new IllegalArgumentException("bias size");
        this.kh = kh;
        this.kw = kw;
        this.cin = cin;
        this.cout = cout;
        this.stride = stride;
        this.weights = weights;
        this.bias = bias;
    }

    static int outSize(int n, int s) {
        return (n + s - 1) / s;
    }

    static int padBefore(int n, int k, int s) {
        int out = outSize(n, s);
        int total = Math.max((out - 1) * s + k - n, 0);
        return total / 2;
    }

    @Override
    public Tensor3 apply(Tensor3 in) {
        if (in.c != cin) throw new IllegalArgumentException("expected " + cin + " channels, got " + in.c);
        int oh = outSize(in.h, stride), ow = outSize(in.w, stride);
        int pt = padBefore(in.h, kh, stride), pl = padBefore(in.w, kw, stride);
        Tensor3 out = new Tensor3(oh, ow, cout);
        IntStream.range(0, oh).parallel().forEach(oy -> {
            float[] acc = new float[cout];
            for (int ox = 0; ox < ow; ox++) {
                System.arraycopy(bias, 0, acc, 0, cout);
                for (int ky = 0; ky < kh; ky++) {
                    int iy = oy * stride + ky - pt;
                    if (iy < 0 || iy >= in.h) continue;
                    for (int kx = 0; kx < kw; kx++) {
                        int ix = ox * stride + kx - pl;
                        if (ix < 0 || ix >= in.w) continue;
                        int inBase = (iy * in.w + ix) * cin;
                        int wBase = (ky * kw + kx) * cin * cout;
                        for (int ci = 0; ci < cin; ci++) {
                            float v = in.data[inBase + ci];
                            if (v == 0f) continue;
                            int wb = wBase + ci * cout;
                            for (int co = 0; co < cout; co++) {
                                acc[co] += v * weights[wb + co];
                            }
                        }
                    }
                }
                System.arraycopy(acc, 0, out.data, (oy * ow + ox) * cout, cout);
            }
        });
        return out;
    }
}
