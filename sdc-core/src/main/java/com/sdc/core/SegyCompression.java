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

        public final long segyBytes;      // tamanho total do arquivo SEG-Y
        public final long sdcBytes;       // tamanho total do .sdc
        public final long rawDataBytes;   // apenas dados dos traços (ns * nTraces * 4)

        public final int traceCount;
        public final int samplesPerTrace;

        public final double ratioFile;    // sdcBytes / segyBytes
        public final double ratioData;    // sdcBytes / rawDataBytes
        public final double savingsPercent; // (1 - ratioFile) * 100

        public final double psnrFirstTrace;
        public final double psnrMean;
        public final double psnrMin;
        public final double psnrMax;

        public CompressionResult(Path segyPath,
                                Path sdcPath,
                                long segyBytes,
                                long sdcBytes,
                                long rawDataBytes,
                                int traceCount,
                                int samplesPerTrace,
                                double ratioFile,
                                double ratioData,
                                double savingsPercent,
                                double psnrFirstTrace,
                                double psnrMean,
                                double psnrMin,
                                double psnrMax) {
            this.segyPath = segyPath;
            this.sdcPath = sdcPath;
            this.segyBytes = segyBytes;
            this.sdcBytes = sdcBytes;
            this.rawDataBytes = rawDataBytes;
            this.traceCount = traceCount;
            this.samplesPerTrace = samplesPerTrace;
            this.ratioFile = ratioFile;
            this.ratioData = ratioData;
            this.savingsPercent = savingsPercent;
            this.psnrFirstTrace = psnrFirstTrace;
            this.psnrMean = psnrMean;
            this.psnrMin = psnrMin;
            this.psnrMax = psnrMax;
        }
    }

    public static CompressionResult compressSegyToSdc(Path segyPath, Path sdcPath) throws IOException {
        return compressSegyToSdc(segyPath, sdcPath, CompressionProfile.defaultHighQuality());
    }

    /**
     * Lê um SEG-Y, comprime os traços para .sdc v2 e retorna métricas.
     */
    public static CompressionResult compressSegyToSdc(Path segyPath,
                                                      Path sdcPath,
                                                      CompressionProfile profile) throws IOException {
        SegyIO.SegyDataset dataset = SegyIO.read(segyPath);
        List<TraceBlock> traceBlocks = dataset.traces;

        // Dump do SEG-Y original
        SegyDump.dumpFromDataset(segyPath, dataset);

        int traceCount = dataset.traceCount();
        int samplesPerTrace = dataset.samplesPerTrace;

        long segyBytes = Files.size(segyPath);
        long rawDataBytes = (long) traceCount * samplesPerTrace * 4L;

        // Usa o profile aqui
        SdcFileWriter.writeCompressed(sdcPath, traceBlocks, profile);

        long sdcBytes = Files.size(sdcPath);

        double ratioFile = (double) sdcBytes / (double) segyBytes;
        double ratioData = (double) sdcBytes / (double) rawDataBytes;
        double savingsPercent = (1.0 - ratioFile) * 100.0;

        double psnrFirst = Double.NaN;
        double psnrMean = Double.NaN;
        double psnrMin = Double.NaN;
        double psnrMax = Double.NaN;

        if (!traceBlocks.isEmpty()) {
            List<TraceBlock> rec = SdcFileReader.readAllCompressed(sdcPath);
            int n = Math.min(traceBlocks.size(), rec.size());

            double sum = 0.0;

            for (int i = 0; i < n; i++) {
                float[] orig = traceBlocks.get(i).samples();
                float[] dec  = rec.get(i).samples();
                double psnr = LinearQuantizer.psnr(orig, dec);

                if (i == 0) {
                    psnrFirst = psnr;
                    psnrMin = psnr;
                    psnrMax = psnr;
                } else {
                    if (psnr < psnrMin) psnrMin = psnr;
                    if (psnr > psnrMax) psnrMax = psnr;
                }
                sum += psnr;
            }
            psnrMean = sum / n;
        }

        return new CompressionResult(
                segyPath,
                sdcPath,
                segyBytes,
                sdcBytes,
                rawDataBytes,
                traceCount,
                samplesPerTrace,
                ratioFile,
                ratioData,
                savingsPercent,
                psnrFirst,
                psnrMean,
                psnrMin,
                psnrMax
        );
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

        // Gera dump TXT + CSV do SEG-Y reconstruído
        SegyDump.dumpFromFile(outSegyPath);
    }
}
