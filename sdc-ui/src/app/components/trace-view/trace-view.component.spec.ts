import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';

import { TraceViewComponent } from './trace-view.component';
import { SdcViewerService } from '../../services/sdc-viewer.service';

const MOCK_SLICE = {
  sdcPath: 'test.sdc',
  version: 1,
  traceStart: 0,
  traceCount: 2,
  sampleStart: 0,
  sampleCount: 3,
  traceIds: [0, 1],
  samples: [
    [0.1, 0.5, 0.9],
    [0.2, 0.6, 1.0]
  ]
};

describe('TraceViewComponent', () => {
  let component: TraceViewComponent;
  let fixture: ComponentFixture<TraceViewComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TraceViewComponent],
      imports: [
        HttpClientTestingModule,
        NoopAnimationsModule,
        FormsModule
      ],
      providers: [SdcViewerService]
    }).compileComponents();

    fixture = TestBed.createComponent(TraceViewComponent);
    component = fixture.componentInstance;
    component.sdcPath = 'test.sdc';
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').flush(MOCK_SLICE);
    expect(component).toBeTruthy();
  });

  it('should use @ViewChild heatmapCanvas instead of document.querySelector', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').flush(MOCK_SLICE);
    tick(200);
    fixture.detectChanges();

    // After data loads, the canvas is rendered and @ViewChild resolves
    expect(component.heatmapCanvas).toBeDefined();
    expect(component.heatmapCanvas.nativeElement.tagName).toBe('CANVAS');
  }));

  it('should set sliceData after successful API call', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').flush(MOCK_SLICE);
    tick(200);
    fixture.detectChanges();

    expect(component.sliceData).toEqual(MOCK_SLICE);
    expect(component.loading).toBeFalse();
    expect(component.error).toBeNull();
  }));

  it('should set error on API failure', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').error(new ErrorEvent('network error'));
    tick();
    fixture.detectChanges();

    expect(component.error).toContain('Error loading traces');
    expect(component.loading).toBeFalse();
  }));

  it('should render canvas element with #heatmapCanvas template reference', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').flush(MOCK_SLICE);
    tick(200);
    fixture.detectChanges();

    const canvasEl = fixture.nativeElement.querySelector('canvas');
    expect(canvasEl).not.toBeNull();
    // Confirm @ViewChild resolved to the canvas element
    expect(component.heatmapCanvas.nativeElement).toBe(canvasEl);
  }));

  it('should reload slice when loadSlice() is called again', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/trace-slice').flush(MOCK_SLICE);
    tick(200);
    fixture.detectChanges();

    component.traceStart = 10;
    component.loadSlice();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/viewer/trace-slice');
    expect(req.request.body.traceStart).toBe(10);
    req.flush({ ...MOCK_SLICE, traceStart: 10 });
    tick(200);
  }));
});
