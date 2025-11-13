package com.sdc.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Leitor simples de arquivos .sdc v0.
 */
public final class SdcFileReader {

    private SdcFileReader() {
        // utilitário estático
    }

    public static SdcHeader readHeader(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            return SdcHeader.read(in);
        }
    }

    public static List<TraceBlock> readAll(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            SdcHeader header = SdcHeader.read(in);
            List<TraceBlock> traces = new ArrayList<>(header.traceCount());

            for (int t = 0; t < header.traceCount(); t++) {
                int traceId = in.readInt();
                float[] samples = new float[header.samplesPerTrace()];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = in.readFloat();
                }
                traces.add(new TraceBlock(traceId, samples));
            }
            return traces;
        }
    }
}
