import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { SdcViewerService, TraceSliceResponse } from '../../services/sdc-viewer.service';

@Component({
  selector: 'app-trace-view',
  templateUrl: './trace-view.component.html',
  styleUrls: ['./trace-view.component.scss']
})
export class TraceViewComponent implements OnInit {
  @ViewChild('heatmapCanvas') heatmapCanvas!: ElementRef<HTMLCanvasElement>;
  @Input() sdcPath!: string;

  traceStart = 0;
  traceCount = 50;
  sampleStart = 0;
  sampleCount: number | null = null;

  sliceData: TraceSliceResponse | null = null;
  loading = false;
  error: string | null = null;

  constructor(private viewerService: SdcViewerService) {}

  ngOnInit() {
    this.loadSlice();
  }

  loadSlice() {
    this.loading = true;
    this.error = null;

    this.viewerService.getTraceSlice({
      sdcPath: this.sdcPath,
      traceStart: this.traceStart,
      traceCount: this.traceCount,
      sampleStart: this.sampleStart,
      sampleCount: this.sampleCount ?? undefined
    }).subscribe({
      next: (data) => {
        this.sliceData = data;
        this.loading = false;
        setTimeout(() => this.renderCanvas(), 100);
      },
      error: (err) => {
        this.error = 'Error loading traces: ' + (err.message || err);
        this.loading = false;
      }
    });
  }

  private renderCanvas() {
    if (!this.sliceData) return;

    const canvas = this.heatmapCanvas?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d')!;
    const samples = this.sliceData.samples;
    const traceCount = samples.length;
    const sampleCount = samples[0]?.length || 0;

    if (traceCount === 0 || sampleCount === 0) return;

    canvas.width = traceCount * 2;
    canvas.height = sampleCount;

    // Simple heatmap rendering
    const imageData = ctx.createImageData(traceCount, sampleCount);
    const data = imageData.data;

    let min = Infinity;
    let max = -Infinity;
    for (let i = 0; i < traceCount; i++) {
      for (let j = 0; j < sampleCount; j++) {
        const v = samples[i][j];
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }

    const range = max - min || 1;

    for (let i = 0; i < traceCount; i++) {
      for (let j = 0; j < sampleCount; j++) {
        const v = (samples[i][j] - min) / range;
        const idx = (j * traceCount + i) * 4;
        const color = Math.floor(v * 255);

        data[idx] = color;
        data[idx + 1] = color;
        data[idx + 2] = color;
        data[idx + 3] = 255;
      }
    }

    ctx.putImageData(imageData, 0, 0);
  }
}
