package com.sdc.ai.nn;

/** Dense float tensor in HWC layout (height, width, channels), row-major. */
public final class Tensor3 {
    public final int h;
    public final int w;
    public final int c;
    public final float[] data;

    public Tensor3(int h, int w, int c) {
        this(h, w, c, new float[h * w * c]);
    }

    public Tensor3(int h, int w, int c, float[] data) {
        if (data.length != h * w * c) {
            throw new IllegalArgumentException("data length " + data.length + " != " + h + "x" + w + "x" + c);
        }
        this.h = h;
        this.w = w;
        this.c = c;
        this.data = data;
    }

    public float get(int y, int x, int ch) {
        return data[(y * w + x) * c + ch];
    }
}
