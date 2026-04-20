import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { ErrorDisplayComponent } from './error-display.component';
import { AppError } from '../../models/app-error';

describe('ErrorDisplayComponent', () => {
  let component: ErrorDisplayComponent;
  let fixture: ComponentFixture<ErrorDisplayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ErrorDisplayComponent],
      imports: [
        NoopAnimationsModule,
        MatCardModule,
        MatIconModule,
        MatButtonModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorDisplayComponent);
    component = fixture.componentInstance;
  });

  it('should not render mat-card when error is null', () => {
    component.error = null;
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card).toBeNull();
  });

  it('should render amber card with message for client error', () => {
    const err: AppError = { kind: 'client', message: 'Bad request' };
    component.error = err;
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card).not.toBeNull();
    expect(card.classList).toContain('error-card--amber');

    const span = fixture.nativeElement.querySelector('mat-card-content span');
    expect(span.textContent.trim()).toBe('Bad request');
  });

  it('should render red card with "Server error" message for server error', () => {
    const err: AppError = { kind: 'server', message: 'Unexpected failure' };
    component.error = err;
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card).not.toBeNull();
    expect(card.classList).toContain('error-card--red');

    const span = fixture.nativeElement.querySelector('mat-card-content span');
    expect(span.textContent.trim()).toBe('Server error — check backend logs');
  });

  it('should render red card with baseUrl message for network error', () => {
    const err: AppError = { kind: 'network', baseUrl: 'http://localhost:8080' };
    component.error = err;
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card).not.toBeNull();
    expect(card.classList).toContain('error-card--red');

    const span = fixture.nativeElement.querySelector('mat-card-content span');
    expect(span.textContent.trim()).toContain('http://localhost:8080');
    expect(span.textContent.trim()).toContain('Is the backend running?');
  });

  it('should emit retry event when retry button is clicked', () => {
    component.error = { kind: 'client', message: 'Some error' };
    fixture.detectChanges();

    let emitted = false;
    component.retry.subscribe(() => (emitted = true));

    const button = fixture.debugElement.query(By.css('button[mat-button]'));
    button.nativeElement.click();

    expect(emitted).toBeTrue();
  });
});
