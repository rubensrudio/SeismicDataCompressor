package com.sdc.svc;

import com.sdc.svc.dto.SegyDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/segy")
public class SegyController {

    private final SegyCompressionService service;

    public SegyController(SegyCompressionService service) {
        this.service = service;
    }

    @PostMapping("/compress")
    public ResponseEntity<?> compress(@RequestBody CompressRequest request) {
        try {
            CompressResponse resp = service.compress(request);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    "Erro ao comprimir SEG-Y: " + e.getMessage()
            );
        }
    }

    @PostMapping("/decompress")
    public ResponseEntity<DecompressResponse> decompress(@RequestBody DecompressRequest request) {
        DecompressResponse resp = service.decompress(request);
        return ResponseEntity.ok(resp);
    }
}
