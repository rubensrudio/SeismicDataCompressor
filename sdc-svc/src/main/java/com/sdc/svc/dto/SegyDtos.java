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
        public double ratio;
        public double psnrFirstTrace;
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
