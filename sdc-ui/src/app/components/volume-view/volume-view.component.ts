import { Component, Input, OnInit } from '@angular/core';
import { SdcViewerService, VolumeSliceResponse } from '../../services/sdc-viewer.service';

@Component({
  selector: 'app-volume-view',
  templateUrl: './volume-view.component.html',
  styleUrls: ['./volume-view.component.scss']
})
export class VolumeViewComponent implements OnInit {
  @Input() sdcPath!: string;

  axis = 'TIME';
  index = 0;
  profile = 'HIGH_QUALITY';

  sliceData: VolumeSliceResponse | null = null;
  loading = false;
  error: string | null = null;

  constructor(private viewerService: SdcViewerService) {}

  ngOnInit() {
    this.loadSlice();
  }

  loadSlice() {
    this.loading = true;
    this.error = null;

    this.viewerService.getVolumeSlice({
      sdcPath: this.sdcPath,
      axis: this.axis,
      index: this.index,
      profile: this.profile
    }).subscribe({
      next: (data) => {
        this.sliceData = data;
        this.loading = false;
        setTimeout(() => this.renderCanvas(), 100);
      },
      error: (err) => {
        this.error = 'Erro ao carregar slice: ' + (err.message || err);
        this.loading = false;
      }
    });
  }

  private renderCanvas() {
    if (!this.sliceData) return;

    const canvas = document.querySelector('canvas') as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d')!;
    const data = this.sliceData.data;
    const dim0 = this.sliceData.dim0;
    const dim1 = this.sliceData.dim1;

    canvas.width = dim1 * 2;
    canvas.height = dim0 * 2;

    const imageData = ctx.createImageData(dim1, dim0);
    const pixels = imageData.data;

    const min = this.sliceData.min;
    const max = this.sliceData.max;
    const range = max - min || 1;

    for (let i = 0; i < dim0; i++) {
      for (let j = 0; j < dim1; j++) {
        const v = data[i][j];
        const normalized = (v - min) / range;
        const color = Math.floor(Math.max(0, Math.min(255, normalized * 255)));

        const idx = (i * dim1 + j) * 4;
        pixels[idx] = color;
        pixels[idx + 1] = color;
        pixels[idx + 2] = color;
        pixels[idx + 3] = 255;
      }
    }

    ctx.putImageData(imageData, 0, 0);
  }
}
