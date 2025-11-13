package com.sdc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Helpers de alto nível para:
 *  - SEG-Y -> .sdc (compressão)
 *  - .sdc -> SEG-Y (descompressão)
 *
 * Usa:
 *  - SegyIO (I/O SEG-Y)
 *  - TraceBlockCodec / SdcFileWriter / SdcFileReader
 */
public final class SegyCompression {

    private SegyCompression() {}

    public static final class CompressionResult {
        public final Path segyPath;
        public final Path sdcPath;
        public final long segyBytes;
        public final long sdcBytes;
        public final double ratio;
        public final double psnrFirstTrace;

        public CompressionResult(Path segyPath, Path sdcPath,
                                 long segyBytes, long sdcBytes,
                                 double ratio, double psnrFirstTrace) {
            this.segyPath = segyPath;
            this.sdcPath = sdcPath;
            this.segyBytes = segyBytes;
            this.sdcBytes = sdcBytes;
            this.ratio = ratio;
            this.psnrFirstTrace = psnrFirstTrace;
        }
    }

    /**
     * Lê um SEG-Y, comprime os traços para .sdc v2 e retorna métricas.
     */
    public static CompressionResult compressSegyToSdc(Path segyPath, Path sdcPath) throws IOException {
        SegyIO.SegyDataset dataset = SegyIO.read(segyPath);

        List<TraceBlock> traceBlocks = dataset.traces; // já está como TraceBlock

        long segyBytes = Files.size(segyPath);

        // Escreve .sdc v2 usando nosso codec
        SdcFileWriter.writeCompressed(sdcPath, traceBlocks);

        long sdcBytes = Files.size(sdcPath);
        double ratio = (double) sdcBytes / (double) segyBytes;

        // PSNR do primeiro traço (apenas como métrica de qualidade)
        double psnr = Double.NaN;
        if (!traceBlocks.isEmpty()) {
            List<TraceBlock> rec = SdcFileReader.readAllCompressed(sdcPath);
            float[] orig = traceBlocks.get(0).samples();
            float[] dec  = rec.get(0).samples();
            psnr = LinearQuantizer.psnr(orig, dec);
        }

        return new CompressionResult(segyPath, sdcPath, segyBytes, sdcBytes, ratio, psnr);
    }

    /**
     * Lê um .sdc v2, descomprime todos os traços e escreve um novo SEG-Y.
     *
     * ATENÇÃO: usa o SEG-Y template para copiar headers (textual, binary, trace).
     * Idealmente, templateSegyPath aponta para o SEG-Y original.
     */
    public static void decompressSdcToSegy(Path sdcPath, Path templateSegyPath, Path outSegyPath) throws IOException {
        // Lê headers e metadados do SEG-Y template
        SegyIO.SegyDataset template = SegyIO.read(templateSegyPath);

        // Descomprime todos os traços do .sdc
        List<TraceBlock> traces = SdcFileReader.readAllCompressed(sdcPath);

        if (traces.size() != template.traceHeaders.size()) {
            throw new IOException("Número de traços no .sdc (" + traces.size() +
                    ") difere do template SEG-Y (" + template.traceHeaders.size() + ")");
        }

        // Escreve novo SEG-Y com headers originais e samples reconstruídos
        SegyIO.write(outSegyPath, template, traces);
    }
}
