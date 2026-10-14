package com.sdc.ai.nn;

/** Nearest-neighbour 2x upsampling (Keras UpSampling2D(2), interpolation="nearest"). */
public final class UpSample2 implements Layer {
    @Override
    public Tensor3 apply(Tensor3 in) {
        Tensor3 out = new Tensor3(in.h * 2, in.w * 2, in.c);
        for (int y = 0; y < out.h; y++) {
            for (int x = 0; x < out.w; x++) {
                System.arraycopy(in.data, ((y / 2) * in.w + (x / 2)) * in.c, out.data, (y * out.w + x) * in.c, in.c);
            }
        }
        return out;
    }
}
