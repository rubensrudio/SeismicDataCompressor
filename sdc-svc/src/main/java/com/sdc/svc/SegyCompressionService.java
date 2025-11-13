package com.sdc.svc;

import com.sdc.core.CompressionProfile;
import com.sdc.core.SegyCompression;
import com.sdc.svc.dto.SegyDtos.CompressRequest;
import com.sdc.svc.dto.SegyDtos.CompressResponse;
import com.sdc.svc.dto.SegyDtos.DecompressRequest;
import com.sdc.svc.dto.SegyDtos.DecompressResponse;
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
}
