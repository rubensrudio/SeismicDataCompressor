package com.sdc.svc;

import com.sdc.core.SegyCompression;
import com.sdc.svc.dto.SegyDtos.*;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SegyCompressionService {

    public CompressResponse compress(CompressRequest req) throws Exception {
        Path segy = Path.of(req.segyPath);
        Path sdc  = Path.of(req.sdcPath);

        SegyCompression.CompressionResult result = SegyCompression.compressSegyToSdc(segy, sdc);

        CompressResponse resp = new CompressResponse();
        resp.segyPath = result.segyPath.toString();
        resp.sdcPath = result.sdcPath.toString();
        resp.segyBytes = result.segyBytes;
        resp.sdcBytes = result.sdcBytes;
        resp.ratio = result.ratio;
        resp.psnrFirstTrace = result.psnrFirstTrace;
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
