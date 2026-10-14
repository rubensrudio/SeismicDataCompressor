package com.sdc.ai;

import com.sdc.ai.nn.NeuralModel;
import com.sdc.ai.nn.Tensor3;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pure-Java inference must reproduce TensorFlow. The fixture was written by
 * train_autoencoder.py --fixture with the same model: input section, raw encoder output,
 * rounded latents and decoder output computed by TensorFlow.
 */
class NeuralModelParityTest {

    static final Path MODEL = Path.of("models/sdc-ae-v1.sdcnn");

    private static Tensor3 read(ByteBuffer b) {
        int n = b.getInt(), h = b.getInt(), w = b.getInt(), c = b.getInt();
        assertEquals(1, n);
        float[] d = new float[h * w * c];
        for (int i = 0; i < d.length; i++) d[i] = b.getFloat();
        return new Tensor3(h, w, c, d);
    }

    @Test
    void javaInferenceMatchesTensorFlow() throws Exception {
        NeuralModel m = NeuralModel.load(MODEL);
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/sdc-ae-v1-fixture.bin")) {
            assertNotNull(in, "fixture missing");
            bytes = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Tensor3 x = read(b), yRaw = read(b), yRounded = read(b), out = read(b);

        Tensor3 y = m.encode(x);
        assertEquals(yRaw.data.length, y.data.length);
        for (int i = 0; i < y.data.length; i++) assertEquals(yRaw.data[i], y.data[i], 1e-4, "latent " + i);

        Tensor3 xh = m.decode(yRounded);
        assertEquals(out.data.length, xh.data.length);
        for (int i = 0; i < xh.data.length; i++) assertEquals(out.data[i], xh.data[i], 1e-4, "output " + i);
    }
}
