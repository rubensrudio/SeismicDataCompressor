import { Component } from '@angular/core';
import { SdcViewerService, ViewerInfoResponse } from './services/sdc-viewer.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  title = 'SDC Viewer - Seismic Data Compressor';
  selectedFile: string | null = null;
  fileInfo: ViewerInfoResponse | null = null;
  loading = false;
  error: string | null = null;
  activeTab = 'metadata';

  constructor(private sdcViewerService: SdcViewerService) {}

  onFileSelected(filePath: string) {
    this.selectedFile = filePath;
    this.loadFileInfo();
  }

  loadFileInfo() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.error = null;

    this.sdcViewerService.getInfo({ sdcPath: this.selectedFile })
      .subscribe({
        next: (info) => {
          this.fileInfo = info;
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Error loading file: ' + err.message;
          this.loading = false;
        }
      });
  }

  get isSdc2D(): boolean {
    return this.fileInfo ? this.fileInfo.version === 2 : false;
  }

  get isSdc3D(): boolean {
    return this.fileInfo ? this.fileInfo.version === 3 : false;
  }
}
