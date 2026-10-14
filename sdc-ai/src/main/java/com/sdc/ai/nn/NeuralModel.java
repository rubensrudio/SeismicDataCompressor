package com.sdc.ai.nn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * A convolutional autoencoder exported by {@code sdc-ai/training/train_autoencoder.py} in the
 * dependency-free ".sdcnn" format, executed in pure Java (no TensorFlow runtime needed).
 *
 * <p>Format (little-endian): magic {@code "SDCNN1\0\0"}, int version (=1), int latentChannels,
 * int downsampleFactor, int metaLength, UTF-8 JSON metadata, then the encoder and the decoder,
 * each as {@code int layerCount} followed by layers: {@code int type} (1 = conv, 2 = leaky ReLU,
 * 3 = 2x upsample); conv: {@code int kh, kw, cin, cout, stride, float[kh*kw*cin*cout] weights
 * (HWIO), float[cout] bias}; leaky ReLU: {@code float alpha}.</p>
 *
 * <p>{@link #sha256()} identifies the exact weights; the neural codec stores it in every payload
 * so a file can never be decoded with a different model.</p>
 */
public final class NeuralModel {

    private static final byte[] MAGIC = "SDCNN1\0\0".getBytes(StandardCharsets.US_ASCII);

    private final int latentChannels;
    private final int factor;
    private final String metadataJson;
    private final List<Layer> encoder;
    private final List<Layer> decoder;
    private final byte[] sha256;

    private NeuralModel(int latentChannels, int factor, String metadataJson,
                        List<Layer> encoder, List<Layer> decoder, byte[] sha256) {
        this.latentChannels = latentChannels;
        this.factor = factor;
        this.metadataJson = metadataJson;
        this.encoder = encoder;
        this.decoder = decoder;
        this.sha256 = sha256;
    }

    public static NeuralModel load(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    public static NeuralModel load(InputStream in) throws IOException {
        return parse(in.readAllBytes());
    }

    public static NeuralModel parse(byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        b.get(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("not an .sdcnn model");
        int version = b.getInt();
        if (version != 1) throw new IOException("unsupported .sdcnn version " + version);
        int latent = b.getInt();
        int factor = b.getInt();
        byte[] meta = new byte[b.getInt()];
        b.get(meta);
        List<Layer> enc = readLayers(b);
        List<Layer> dec = readLayers(b);
        if (b.hasRemaining()) throw new IOException("trailing bytes in model file");
        byte[] sha;
        try {
            sha = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new NeuralModel(latent, factor, new String(meta, StandardCharsets.UTF_8), enc, dec, sha);
    }

    private static List<Layer> readLayers(ByteBuffer b) throws IOException {
        int n = b.getInt();
        List<Layer> layers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int type = b.getInt();
            switch (type) {
                case 1 -> {
                    int kh = b.getInt(), kw = b.getInt(), cin = b.getInt(), cout = b.getInt(), stride = b.getInt();
                    float[] w = new float[kh * kw * cin * cout];
                    b.asFloatBuffer().get(w);
                    b.position(b.position() + w.length * 4);
                    float[] bias = new float[cout];
                    b.asFloatBuffer().get(bias);
                    b.position(b.position() + bias.length * 4);
                    layers.add(new Conv2D(kh, kw, cin, cout, stride, w, bias));
                }
                case 2 -> layers.add(new LeakyRelu(b.getFloat()));
                case 3 -> layers.add(new UpSample2());
                default -> throw new IOException("unknown layer type " + type);
            }
        }
        return layers;
    }

    /** (H, W, 1) normalized section -> (H/factor, W/factor, latentChannels) real-valued latents. */
    public Tensor3 encode(Tensor3 x) {
        if (x.h % factor != 0 || x.w % factor != 0) {
            throw new IllegalArgumentException("input must be a multiple of " + factor + ": " + x.h + "x" + x.w);
        }
        return run(encoder, x);
    }

    /** Latents -> reconstructed (H, W, 1) section. */
    public Tensor3 decode(Tensor3 y) {
        return run(decoder, y);
    }

    private static Tensor3 run(List<Layer> layers, Tensor3 t) {
        for (Layer l : layers) t = l.apply(t);
        return t;
    }

    public int latentChannels() {
        return latentChannels;
    }

    public int factor() {
        return factor;
    }

    public String metadataJson() {
        return metadataJson;
    }

    public byte[] sha256() {
        return sha256.clone();
    }

    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256);
    }
}
