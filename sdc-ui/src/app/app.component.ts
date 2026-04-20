import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SdcViewerService, ViewerInfoResponse } from './services/sdc-viewer.service';

const VALID_TABS = new Set(['metadata', 'trace', 'volume', 'headers']);

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'SDC Viewer - Seismic Data Compressor';
  selectedFile: string | null = null;
  fileInfo: ViewerInfoResponse | null = null;
  loading = false;
  error: string | null = null;
  activeTab = 'metadata';

  constructor(
    private sdcViewerService: SdcViewerService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const tab = params['tab'];
      this.activeTab = VALID_TABS.has(tab) ? tab : 'metadata';

      const file = params['file'];
      if (file && file !== this.selectedFile) {
        this.selectedFile = file;
        this.loadFileInfo();
      }
    });
  }

  setTab(tab: string) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge'
    });
  }

  onFileSelected(basename: string) {
    this.selectedFile = basename;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { file: basename },
      queryParamsHandling: 'merge'
    });
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
