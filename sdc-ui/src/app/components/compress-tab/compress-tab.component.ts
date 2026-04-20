import { Component, EventEmitter, OnInit, OnDestroy, Output } from '@angular/core';
import { Subject, EMPTY, Subscription } from 'rxjs';
import { switchMap, tap, catchError } from 'rxjs/operators';
import { SdcViewerService, CompressResponse } from '../../services/sdc-viewer.service';
import { AppError, parseHttpError } from '../../models/app-error';

export type CompressState = 'idle' | 'submitting' | 'success' | 'error';

@Component({
  selector: 'app-compress-tab',
  templateUrl: './compress-tab.component.html',
  styleUrls: ['./compress-tab.component.scss']
})
export class CompressTabComponent implements OnInit, OnDestroy {

  @Output() viewResult = new EventEmitter<string>();

  selectedSgy: string | null = null;
  selectedProfile: string = 'balanced';
  is3D: boolean = false;

  state: CompressState = 'idle';
  get submitting(): boolean { return this.state === 'submitting'; }

  result: CompressResponse | null = null;
  appError: AppError | null = null;
  elapsedMs: number = 0;

  private submitSubject = new Subject<void>();
  private subscription!: Subscription;

  constructor(private svc: SdcViewerService) {}

  ngOnInit(): void {
    this.subscription = this.submitSubject.pipe(
      switchMap(() => {
        this.state = 'submitting';
        this.result = null;
        this.appError = null;
        const start = Date.now();
        const req = { sgyFile: this.selectedSgy!, profile: this.selectedProfile };
        const obs = this.is3D ? this.svc.compress3d(req) : this.svc.compress(req);
        return obs.pipe(
          tap(res => {
            this.result = res;
            this.elapsedMs = Date.now() - start;
            this.state = 'success';
          }),
          catchError(err => {
            this.appError = parseHttpError(err, '/api/segy');
            this.state = 'error';
            return EMPTY;
          })
        );
      })
    ).subscribe();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  onSgySelected(basename: string): void {
    this.selectedSgy = basename;
  }

  submit(): void {
    if (!this.selectedSgy || this.submitting) return;
    this.submitSubject.next();
  }

  onRetry(): void {
    this.state = 'idle';
    this.appError = null;
    this.result = null;
  }

  viewResultClicked(): void {
    const outputBasename = this.result?.sdcPath ?? this.selectedSgy ?? '';
    this.viewResult.emit(outputBasename);
  }
}
