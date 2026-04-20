import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatIconModule } from '@angular/material/icon';

import { ErrorDisplayComponent } from './error-display.component';
import { AppError } from '../../models/app-error';

describe('ErrorDisplayComponent', () => {
  let component: ErrorDisplayComponent;
  let fixture: ComponentFixture<ErrorDisplayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ErrorDisplayComponent],
      imports: [NoopAnimationsModule, MatIconModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorDisplayComponent);
    component = fixture.componentInstance;
  });

  it('should not render panel when error is null', () => {
    component.error = null;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.error-panel')).toBeNull();
  });

  it('should render client panel with message', () => {
    const err: AppError = { kind: 'client', message: 'Bad request' };
    component.error = err;
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.error-panel');
    expect(panel).not.toBeNull();
    expect(panel.classList).toContain('error-panel--client');
    expect(panel.querySelector('.error-panel__message').textContent.trim()).toBe('Bad request');
  });

  it('should render server panel with "Server error" message', () => {
    const err: AppError = { kind: 'server', message: 'Unexpected failure' };
    component.error = err;
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.error-panel');
    expect(panel).not.toBeNull();
    expect(panel.classList).toContain('error-panel--server');
    expect(panel.querySelector('.error-panel__message').textContent.trim())
      .toBe('Server error — check backend logs');
  });

  it('should render network panel with baseUrl message', () => {
    const err: AppError = { kind: 'network', baseUrl: 'http://localhost:8080' };
    component.error = err;
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.error-panel');
    expect(panel).not.toBeNull();
    expect(panel.classList).toContain('error-panel--network');
    const msg = panel.querySelector('.error-panel__message').textContent.trim();
    expect(msg).toContain('http://localhost:8080');
    expect(msg).toContain('Is the backend running?');
  });

  it('should emit retry when retry button clicked', () => {
    component.error = { kind: 'client', message: 'err' };
    fixture.detectChanges();

    let emitted = false;
    component.retry.subscribe(() => (emitted = true));

    fixture.nativeElement.querySelector('.error-panel__retry').click();
    expect(emitted).toBeTrue();
  });
});
