import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { SdcViewerService } from '../../services/sdc-viewer.service';

const RECENT_KEY = 'sdc.recentFiles';
const MAX_RECENT = 5;

@Component({
  selector: 'app-file-selector',
  templateUrl: './file-selector.component.html',
  styleUrls: ['./file-selector.component.scss']
})
export class FileSelectorComponent implements OnInit {

  @Input() extension: 'sdc' | 'sgy' = 'sdc';
  @Output() fileSelected = new EventEmitter<string>();

  availableFiles: string[] = [];
  recentFiles: string[] = [];
  selected: string = '';
  manualPath: string = '';
  fallbackMode: boolean = false;

  constructor(private viewerService: SdcViewerService) {}

  ngOnInit(): void {
    this.viewerService.getAvailableFiles(this.extension).subscribe({
      next: (response) => {
        const files = response.files ?? [];
        if (files.length === 0) {
          this.fallbackMode = true;
        } else {
          this.fallbackMode = false;
          this.availableFiles = files;
          this.recentFiles = this.loadRecentFiles(files);
        }
      },
      error: () => {
        this.fallbackMode = true;
      }
    });
  }

  onSelect(event: { value: string }): void {
    const basename = event.value;
    this.saveToRecent(basename);
    this.fileSelected.emit(basename);
  }

  onManualSubmit(): void {
    const path = this.manualPath.trim();
    if (path) {
      this.fileSelected.emit(path);
    }
  }

  private loadRecentFiles(serverFiles: string[]): string[] {
    const stored = this.getStoredRecents();
    // Remove stale entries not in server list
    const valid = stored.filter(f => serverFiles.includes(f));
    this.persistRecents(valid);
    return valid.slice(0, MAX_RECENT);
  }

  private saveToRecent(basename: string): void {
    const recents = this.getStoredRecents().filter(f => f !== basename);
    recents.unshift(basename);
    const trimmed = recents.slice(0, MAX_RECENT);
    this.persistRecents(trimmed);
    this.recentFiles = trimmed;
  }

  private getStoredRecents(): string[] {
    try {
      const raw = localStorage.getItem(RECENT_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private persistRecents(recents: string[]): void {
    localStorage.setItem(RECENT_KEY, JSON.stringify(recents));
  }
}
