package com.sdc.ai.nn;

/** One inference layer of a {@link NeuralModel}. Implementations are stateless and thread-safe. */
public interface Layer {
    Tensor3 apply(Tensor3 in);
}
