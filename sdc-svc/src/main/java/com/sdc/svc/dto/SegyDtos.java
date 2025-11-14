package com.sdc.svc.dto;

public final class SegyDtos {

    private SegyDtos() {}

    public static final class CompressRequest {
        public String segyPath;
        public String sdcPath;

        // NOVO: perfil de compressão
        // Ex.: "HIGH_QUALITY", "BALANCED", "HIGH_COMPRESSION"
        public String profile;

        // NOVO: percentual de fidelidade desejado (0..100).
        // Se informado, tem prioridade sobre "profile".
        public Double fidelityPercent;
    }


    public static final class CompressResponse {
        public String segyPath;
        public String sdcPath;

        public long segyBytes;
        public long sdcBytes;
        public long rawDataBytes;

        public int traceCount;
        public int samplesPerTrace;

        public double ratio;            // manter compatibilidade: igual a ratioFile
        public double ratioFile;        // sdcBytes / segyBytes
        public double ratioData;        // sdcBytes / rawDataBytes
        public double savingsPercent;   // (1 - ratioFile) * 100

        public double psnrFirstTrace;
        public double psnrMean;
        public double psnrMin;
        public double psnrMax;

        // NOVO: info sobre o profile usado
        public double fidelityPercentRequested;
        public int effectiveBits;
        public int deflaterLevel;
    }

    public static final class DecompressRequest {
        public String sdcPath;
        public String templateSegyPath;
        public String outSegyPath;
    }

    public static final class DecompressResponse {
        public String sdcPath;
        public String templateSegyPath;
        public String outSegyPath;
        public boolean success;
        public String message;
    }

    public static final class Compress3DRequest {
        public String segyPath;
        public String sdcPath;

        // Perfil de compressão (igual ao 1D)
        public String profile;
        public Double fidelityPercent;

        // Dimensões do bloco 3D (inline x xline x time)
        // Se null/0, caímos em defaults (ex.: 8x8xT)
        public Integer blockInline;
        public Integer blockXline;
        public Integer blockTime;
    }

    public static final class Decompress3DRequest {
        public String sdcPath;
        public String templateSegyPath;
        public String outSegyPath;

        // Mesmo profile/fidelity da compressão, para manter simetria
        public String profile;
        public Double fidelityPercent;
    }
}
