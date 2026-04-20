import { Component, Input, Output, EventEmitter } from '@angular/core';
import { AppError } from '../../models/app-error';

@Component({
  selector: 'app-error-display',
  templateUrl: './error-display.component.html',
  styleUrls: ['./error-display.component.scss']
})
export class ErrorDisplayComponent {
  @Input() error: AppError | null = null;
  @Output() retry = new EventEmitter<void>();

  get cardClass(): string {
    if (!this.error) return '';
    return this.error.kind === 'client' ? 'error-card--amber' : 'error-card--red';
  }

  get iconName(): string {
    if (!this.error) return '';
    switch (this.error.kind) {
      case 'client':  return 'warning';
      case 'server':  return 'error';
      case 'network': return 'wifi_off';
    }
  }

  get errorMessage(): string {
    if (!this.error) return '';
    switch (this.error.kind) {
      case 'client':  return this.error.message;
      case 'server':  return 'Server error — check backend logs';
      case 'network': return `Cannot reach server at ${this.error.baseUrl}. Is the backend running?`;
    }
  }
}
