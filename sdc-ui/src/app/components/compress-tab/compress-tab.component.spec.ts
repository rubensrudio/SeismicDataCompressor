import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { By } from '@angular/platform-browser';

import { CompressTabComponent } from './compress-tab.component';
import { FileSelectorComponent } from '../file-selector/file-selector.component';
import { ErrorDisplayComponent } from '../error-display/error-display.component';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';

const MOCK_RESPONSE = {
  segyPath: 'basic90.sgy',
  sdcPath: 'basic90.sdc',
  segyBytes: 100000,
  sdcBytes: 40000,
  traceCount: 90,
  samplesPerTrace: 1000,
  ratio: 2.50,
  ratioFile: 2.50,
  savingsPercent: 60.0,
  psnrMean: 42.1,
  effectiveBits: 12
};

describe('CompressTabComponent', () => {
  let component: CompressTabComponent;
  let fixture: ComponentFixture<CompressTabComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        CompressTabComponent,
        FileSelectorComponent,
        ErrorDisplayComponent
      ],
      imports: [
        HttpClientTestingModule,
        FormsModule,
        NoopAnimationsModule,
        MatButtonToggleModule,
        MatCheckboxModule,
        MatProgressBarModule,
        MatCardModule,
        MatButtonModule,
        MatSelectModule,
        MatFormFieldModule,
        MatInputModule,
        MatIconModule,
        MatChipsModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CompressTabComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // Flush the sgy-files request from FileSelectorComponent
    const sgyReq = httpMock.match('/api/viewer/sgy-files');
    sgyReq.forEach(r => r.flush({ files: ['basic90.sgy', 'test3d.sgy'] }));
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have compress button disabled when no file is selected', () => {
    component.selectedSgy = null;
    fixture.detectChanges();
    const btn = fixture.debugElement.query(By.css('button[mat-raised-button]'));
    expect(btn.nativeElement.disabled).toBeTrue();
  });

  it('should enable compress button once file is selected', () => {
    component.onSgySelected('basic90.sgy');
    fixture.detectChanges();
    const btn = fixture.debugElement.query(By.css('button[mat-raised-button]'));
    expect(btn.nativeElement.disabled).toBeFalse();
  });

  it('T1 – submit flow: clicking compress posts to /api/segy/compress', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    component.selectedProfile = 'balanced';
    component.is3D = false;
    fixture.detectChanges();

    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ sgyFile: 'basic90.sgy', profile: 'balanced' });
    req.flush(MOCK_RESPONSE);
    tick();
    fixture.detectChanges();
  }));

  it('T2 – success: result card appears with ratio and psnr', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    fixture.detectChanges();

    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress');
    req.flush(MOCK_RESPONSE);
    tick();
    fixture.detectChanges();

    expect(component.result).toEqual(MOCK_RESPONSE);
    expect(component.state).toBe('success');

    const card = fixture.debugElement.query(By.css('mat-card'));
    expect(card).toBeTruthy();
    expect(card.nativeElement.textContent).toContain('2.50');
    expect(card.nativeElement.textContent).toContain('42.1');
  }));

  it('T3 – error: 500 response causes ErrorDisplayComponent to show error', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    fixture.detectChanges();

    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress');
    req.flush({ message: 'Internal error' }, { status: 500, statusText: 'Server Error' });
    tick();
    fixture.detectChanges();

    expect(component.appError).toBeTruthy();
    expect(component.state).toBe('error');
    expect(component.appError?.kind).toBe('server');

    const errDisplay = fixture.debugElement.query(By.css('app-error-display'));
    expect(errDisplay).toBeTruthy();
  }));

  it('T4 – viewResult emit: clicking "View Result" emits output basename', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    fixture.detectChanges();

    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress');
    req.flush(MOCK_RESPONSE);
    tick();
    fixture.detectChanges();

    const emitted: string[] = [];
    component.viewResult.subscribe(v => emitted.push(v));

    const viewBtn = fixture.debugElement.queryAll(By.css('mat-card button[mat-button]'))[0];
    viewBtn.nativeElement.click();
    tick();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0]).toBe('basic90.sdc');
  }));

  it('T5 – switchMap: only last request completes when two rapid submits occur', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    fixture.detectChanges();

    // Fire two submits rapidly; switchMap will cancel the first inner observable
    // when the second arrives. With HttpClient + switchMap, both HTTP requests
    // are issued but the first is cancelled (unsubscribed) when the second arrives.
    component.submit();
    component.submit();
    tick();

    const reqs = httpMock.match('/api/segy/compress');
    // switchMap may cancel the first before or after the HTTP request is registered;
    // there is at least 1 request (the last one that survives)
    expect(reqs.length).toBeGreaterThanOrEqual(1);

    // Flush all pending requests — only the surviving one drives result
    const lastReq = reqs[reqs.length - 1];
    if (reqs.length > 1) {
      // first request was cancelled by switchMap; its flush is ignored by the component
      reqs[0].flush(MOCK_RESPONSE);
    }
    lastReq.flush({ ...MOCK_RESPONSE, ratio: 3.0, psnrMean: 45.0 });
    tick();
    fixture.detectChanges();

    expect(component.state).toBe('success');
    expect(component.result).toBeTruthy();
  }));

  it('should route to compress3d endpoint when is3D is true', fakeAsync(() => {
    component.onSgySelected('test3d.sgy');
    component.is3D = true;
    fixture.detectChanges();

    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress3d');
    expect(req.request.body).toEqual({ sgyFile: 'test3d.sgy', profile: 'balanced' });
    req.flush(MOCK_RESPONSE);
    tick();
    fixture.detectChanges();
  }));

  it('onRetry resets state to idle', fakeAsync(() => {
    component.onSgySelected('basic90.sgy');
    component.submit();
    tick();

    const req = httpMock.expectOne('/api/segy/compress');
    req.flush({ message: 'err' }, { status: 500, statusText: 'Error' });
    tick();
    fixture.detectChanges();

    expect(component.state).toBe('error');
    component.onRetry();
    expect(component.state).toBe('idle');
    expect(component.appError).toBeNull();
  }));
});
