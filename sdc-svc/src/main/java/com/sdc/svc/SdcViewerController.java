package com.sdc.svc;

import com.sdc.svc.dto.SegyDtos.TraceSliceRequest;
import com.sdc.svc.dto.SegyDtos.TraceSliceResponse;
import com.sdc.svc.dto.SegyDtos.TraceHeadersRequest;
import com.sdc.svc.dto.SegyDtos.TraceHeadersResponse;
import com.sdc.svc.dto.SegyDtos.ViewerInfoRequest;
import com.sdc.svc.dto.SegyDtos.ViewerInfoResponse;
import com.sdc.svc.dto.SegyDtos.VolumeSliceRequest;
import com.sdc.svc.dto.SegyDtos.VolumeSliceResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/viewer")
public class SdcViewerController {

    private final SdcViewerService service;

    public SdcViewerController(SdcViewerService service) {
        this.service = service;
    }

    @PostMapping("/info")
    public ViewerInfoResponse info(@RequestBody ViewerInfoRequest req) throws Exception {
        return service.info(req);
    }

    @PostMapping("/trace-slice")
    public TraceSliceResponse traceSlice(@RequestBody TraceSliceRequest req) throws Exception {
        return service.traceSlice(req);
    }

    @PostMapping("/volume-slice")
    public VolumeSliceResponse volumeSlice(@RequestBody VolumeSliceRequest req) throws Exception {
        return service.volumeSlice(req);
    }

    @PostMapping("/trace-headers")
    public TraceHeadersResponse traceHeaders(@RequestBody TraceHeadersRequest req) throws Exception {
        return service.traceHeaders(req);
    }

    @GetMapping("/cache-info")
    public SdcViewerService.CacheInfo cacheInfo(@RequestParam String sdcPath) {
        return service.getCacheInfo(sdcPath);
    }

    @DeleteMapping("/cache-clear")
    public void cacheClear(@RequestParam String sdcPath) {
        service.clearCache(sdcPath);
    }
}
