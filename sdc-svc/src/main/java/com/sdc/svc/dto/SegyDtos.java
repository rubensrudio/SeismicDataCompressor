package com.sdc.svc.dto;

public final class SegyDtos {

    private SegyDtos() {}

    public static final class CompressRequest {
        public String segyPath;
        public String sdcPath;
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
}
