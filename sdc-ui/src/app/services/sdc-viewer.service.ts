import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ViewerInfoRequest {
  sdcPath: string;
}

export interface ViewerInfoResponse {
  sdcPath: string;
  version: number;
  traceCount: number;
  samplesPerTrace: number;
  sampleFormat?: string;
  inlineCount?: number;
  xlineCount?: number;
  timeCount?: number;
  blockInline?: number;
  blockXline?: number;
  blockTime?: number;
  blockCount?: number;
  compressionRatio?: number;
  avgPsnr?: number;
  effectiveBits?: number;
}

export interface TraceSliceRequest {
  sdcPath: string;
  traceStart?: number;
  traceCount?: number;
  sampleStart?: number;
  sampleCount?: number;
}

export interface TraceSliceResponse {
  sdcPath: string;
  version: number;
  traceStart: number;
  traceCount: number;
  sampleStart: number;
  sampleCount: number;
  traceIds: number[];
  samples: number[][];
}

export interface VolumeSliceRequest {
  sdcPath: string;
  axis?: string;
  index?: number;
  start0?: number;
  count0?: number;
  start1?: number;
  count1?: number;
  profile?: string;
  fidelityPercent?: number;
}

export interface VolumeSliceResponse {
  sdcPath: string;
  version: number;
  axis: string;
  index: number;
  inlineCount: number;
  xlineCount: number;
  timeCount: number;
  dim0: number;
  dim1: number;
  data: number[][];
  start0: number;
  count0: number;
  start1: number;
  count1: number;
  min: number;
  max: number;
}

export interface CompressResponse {
  segyPath?: string;
  sdcPath?: string;
  segyBytes: number;
  sdcBytes: number;
  traceCount: number;
  samplesPerTrace: number;
  ratio: number;
  ratioFile: number;
  savingsPercent: number;
  psnrMean: number;
  effectiveBits: number;
}

export interface CacheInfo {
  blocksInCache: number;
  blockCapacity: number;
}

export interface TraceHeader {
  traceNumber: number;
  inline: number;
  crossline: number;
}

export interface TraceHeadersRequest {
  sdcPath: string;
}

export interface TraceHeadersResponse {
  sdcPath: string;
  version: number;
  traceCount: number;
  headers: TraceHeader[];
}

@Injectable({
  providedIn: 'root'
})
export class SdcViewerService {

  private apiUrl = '/api/viewer';

  constructor(private http: HttpClient) { }

  getInfo(request: ViewerInfoRequest): Observable<ViewerInfoResponse> {
    return this.http.post<ViewerInfoResponse>(`${this.apiUrl}/info`, request);
  }

  getTraceSlice(request: TraceSliceRequest): Observable<TraceSliceResponse> {
    return this.http.post<TraceSliceResponse>(`${this.apiUrl}/trace-slice`, request);
  }

  getVolumeSlice(request: VolumeSliceRequest): Observable<VolumeSliceResponse> {
    return this.http.post<VolumeSliceResponse>(`${this.apiUrl}/volume-slice`, request);
  }

  getCacheInfo(sdcPath: string): Observable<CacheInfo> {
    return this.http.get<CacheInfo>(`${this.apiUrl}/cache-info`, {
      params: { sdcPath }
    });
  }

  clearCache(sdcPath: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/cache-clear`, {
      params: { sdcPath }
    });
  }

  getTraceHeaders(request: TraceHeadersRequest): Observable<TraceHeadersResponse> {
    return this.http.post<TraceHeadersResponse>(`${this.apiUrl}/trace-headers`, request);
  }

  getAvailableFiles(ext: 'sdc' | 'sgy'): Observable<{ files: string[] }> {
    const path = ext === 'sgy' ? '/api/viewer/sgy-files' : '/api/viewer/files';
    return this.http.get<{ files: string[] }>(path);
  }

  compress(req: { sgyFile: string; profile: string; fidelityPercent?: number }): Observable<CompressResponse> {
    return this.http.post<CompressResponse>('/api/segy/compress', req);
  }

  compress3d(req: { sgyFile: string; profile: string; fidelityPercent?: number }): Observable<CompressResponse> {
    return this.http.post<CompressResponse>('/api/segy/compress3d', req);
  }
}
