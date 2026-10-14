package com.sdc.ai.nn;

/** f(x) = x for x >= 0, alpha*x otherwise (Keras LeakyReLU). */
public final class LeakyRelu implements Layer {
    final float alpha;

    public LeakyRelu(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public Tensor3 apply(Tensor3 in) {
        float[] o = new float[in.data.length];
        for (int i = 0; i < o.length; i++) {
            float v = in.data[i];
            o[i] = v >= 0f ? v : alpha * v;
        }
        return new Tensor3(in.h, in.w, in.c, o);
    }
}
