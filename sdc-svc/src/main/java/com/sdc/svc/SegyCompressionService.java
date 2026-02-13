package com.sdc.svc;

import com.sdc.core.CompressionProfile;
import com.sdc.core.SegyCompression;
import com.sdc.svc.dto.SegyDtos.CompressRequest;
import com.sdc.svc.dto.SegyDtos.CompressResponse;
import com.sdc.svc.dto.SegyDtos.DecompressRequest;
import com.sdc.svc.dto.SegyDtos.DecompressResponse;
import com.sdc.svc.dto.SegyDtos.Compress3DRequest;
import com.sdc.svc.dto.SegyDtos.Decompress3DRequest;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SegyCompressionService {

    public CompressResponse compress(CompressRequest req) throws Exception {
        Path segy = Path.of(req.segyPath);
        Path sdc  = Path.of(req.sdcPath);

        // Determina o profile:
        CompressionProfile profile;
        if (req.fidelityPercent != null) {
            profile = CompressionProfile.fromFidelityPercent(req.fidelityPercent);
        } else if (req.profile != null) {
            profile = CompressionProfile.fromProfileName(req.profile);
        } else {
            profile = CompressionProfile.defaultHighQuality();
        }

        SegyCompression.CompressionResult result =
                SegyCompression.compressSegyToSdc(segy, sdc, profile);

        CompressResponse resp = new CompressResponse();
        resp.segyPath = result.segyPath.toString();
        resp.sdcPath = result.sdcPath.toString();

        resp.segyBytes = result.segyBytes;
        resp.sdcBytes = result.sdcBytes;
        resp.rawDataBytes = result.rawDataBytes;

        resp.traceCount = result.traceCount;
        resp.samplesPerTrace = result.samplesPerTrace;

        resp.ratioFile = result.ratioFile;
        resp.ratioData = result.ratioData;
        resp.savingsPercent = result.savingsPercent;
        resp.ratio = result.ratioFile; // compatibilidade

        resp.psnrFirstTrace = result.psnrFirstTrace;
        resp.psnrMean = result.psnrMean;
        resp.psnrMin = result.psnrMin;
        resp.psnrMax = result.psnrMax;

        // info do profile
        resp.fidelityPercentRequested = profile.fidelityPercentRequested();
        resp.effectiveBits = profile.effectiveBits();
        resp.deflaterLevel = profile.deflaterLevel();

        return resp;
    }

    public CompressResponse compress3D(Compress3DRequest req) throws Exception {
        Path segy = Path.of(req.segyPath);
        Path sdc  = Path.of(req.sdcPath);

        // Determina o profile
        CompressionProfile profile;
        if (req.fidelityPercent != null) {
            profile = CompressionProfile.fromFidelityPercent(req.fidelityPercent);
        } else if (req.profile != null) {
            profile = CompressionProfile.fromProfileName(req.profile);
        } else {
            profile = CompressionProfile.defaultHighQuality();
        }

        int blockInline = (req.blockInline != null && req.blockInline > 0) ? req.blockInline : 8;
        int blockXline  = (req.blockXline  != null && req.blockXline  > 0) ? req.blockXline  : 8;
        int blockTime   = (req.blockTime   != null && req.blockTime   > 0) ? req.blockTime   : 0; // 0 = full time

        SegyCompression.CompressionResult result =
                SegyCompression.compressSegyToSdc3D(segy, sdc, profile, blockInline, blockXline, blockTime);

        CompressResponse resp = new CompressResponse();
        resp.segyPath = result.segyPath.toString();
        resp.sdcPath = result.sdcPath.toString();

        resp.segyBytes = result.segyBytes;
        resp.sdcBytes = result.sdcBytes;
        resp.rawDataBytes = result.rawDataBytes;

        resp.traceCount = result.traceCount;
        resp.samplesPerTrace = result.samplesPerTrace;

        resp.ratioFile = result.ratioFile;
        resp.ratioData = result.ratioData;
        resp.savingsPercent = result.savingsPercent;
        resp.ratio = result.ratioFile;

        resp.psnrFirstTrace = result.psnrFirstTrace;
        resp.psnrMean = result.psnrMean;
        resp.psnrMin = result.psnrMin;
        resp.psnrMax = result.psnrMax;

        // info do profile
        resp.fidelityPercentRequested = profile.fidelityPercentRequested();
        resp.effectiveBits = profile.effectiveBits();
        resp.deflaterLevel = profile.deflaterLevel();

        return resp;
    }


    public DecompressResponse decompress(DecompressRequest req) {
        DecompressResponse resp = new DecompressResponse();
        resp.sdcPath = req.sdcPath;
        resp.templateSegyPath = req.templateSegyPath;
        resp.outSegyPath = req.outSegyPath;

        try {
            SegyCompression.decompressSdcToSegy(
                    Path.of(req.sdcPath),
                    Path.of(req.templateSegyPath),
                    Path.of(req.outSegyPath)
            );
            resp.success = true;
            resp.message = "SEG-Y reconstruído com sucesso.";
        } catch (Exception e) {
            resp.success = false;
            resp.message = "Erro na descompressão: " + e.getMessage();
        }
        return resp;
    }

    public DecompressResponse decompress3D(Decompress3DRequest req) {
        DecompressResponse resp = new DecompressResponse();
        resp.sdcPath = req.sdcPath;
        resp.templateSegyPath = req.templateSegyPath;
        resp.outSegyPath = req.outSegyPath;

        // Determina o profile (deve ser compatível com o usado na compressão)
        CompressionProfile profile;
        if (req.fidelityPercent != null) {
            profile = CompressionProfile.fromFidelityPercent(req.fidelityPercent);
        } else if (req.profile != null) {
            profile = CompressionProfile.fromProfileName(req.profile);
        } else {
            profile = CompressionProfile.defaultHighQuality();
        }

        try {
            SegyCompression.decompressSdcToSegy3D(
                    Path.of(req.sdcPath),
                    Path.of(req.templateSegyPath),
                    Path.of(req.outSegyPath),
                    profile
            );
            resp.success = true;
            resp.message = "SEG-Y 3D reconstruído com sucesso.";
        } catch (Exception e) {
            resp.success = false;
            resp.message = "Erro na descompressão 3D: " + e.getMessage();
        }
        return resp;
    }
}
