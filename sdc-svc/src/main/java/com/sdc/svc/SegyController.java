package com.sdc.svc;

import com.sdc.svc.dto.SegyDtos.CompressRequest;
import com.sdc.svc.dto.SegyDtos.CompressResponse;
import com.sdc.svc.dto.SegyDtos.DecompressRequest;
import com.sdc.svc.dto.SegyDtos.DecompressResponse;
import com.sdc.svc.dto.SegyDtos.Compress3DRequest;
import com.sdc.svc.dto.SegyDtos.Decompress3DRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/segy")
public class SegyController {

    private final SegyCompressionService service;

    public SegyController(SegyCompressionService service) {
        this.service = service;
    }

    @PostMapping("/compress")
    public CompressResponse compress(@RequestBody CompressRequest req) throws Exception {
        return service.compress(req);
    }

    @PostMapping("/decompress")
    public DecompressResponse decompress(@RequestBody DecompressRequest req) {
        return service.decompress(req);
    }

    @PostMapping("/compress3d")
    public CompressResponse compress3D(@RequestBody Compress3DRequest req) throws Exception {
        return service.compress3D(req);
    }

    @PostMapping("/decompress3d")
    public DecompressResponse decompress3D(@RequestBody Decompress3DRequest req) {
        return service.decompress3D(req);
    }
}
