import { Component, Input, OnInit } from '@angular/core';
import { SdcViewerService } from '../../services/sdc-viewer.service';

export interface TraceHeader {
  traceNumber: number;
  inline: number;
  crossline: number;
}

export interface TraceHeadersResponse {
  sdcPath: string;
  version: number;
  traceCount: number;
  headers: TraceHeader[];
}

@Component({
  selector: 'app-trace-headers-view',
  templateUrl: './trace-headers-view.component.html',
  styleUrls: ['./trace-headers-view.component.scss']
})
export class TraceHeadersViewComponent implements OnInit {
  @Input() sdcPath!: string;

  headers: TraceHeader[] = [];
  loading = false;
  error: string | null = null;
  displayedCount = 100;

  constructor(private viewerService: SdcViewerService) {}

  ngOnInit() {
    this.loadHeaders();
  }

  loadHeaders() {
    this.loading = true;
    this.error = null;

    this.viewerService.getTraceHeaders({ sdcPath: this.sdcPath }).subscribe({
      next: (response: any) => {
        this.headers = response.headers || [];
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Error loading trace headers: ' + (err.message || err);
        this.loading = false;
      }
    });
  }

  get displayedHeaders(): TraceHeader[] {
    return this.headers.slice(0, this.displayedCount);
  }

  loadMore() {
    this.displayedCount += 100;
  }

  hasMore(): boolean {
    return this.displayedCount < this.headers.length;
  }
}
