import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';

import { VolumeViewComponent } from './volume-view.component';
import { SdcViewerService } from '../../services/sdc-viewer.service';

const MOCK_VOLUME_SLICE = {
  sdcPath: 'test.sdc',
  version: 1,
  axis: 'TIME',
  index: 0,
  inlineCount: 2,
  xlineCount: 3,
  timeCount: 4,
  dim0: 2,
  dim1: 3,
  data: [
    [0.1, 0.5, 0.9],
    [0.2, 0.6, 1.0]
  ],
  start0: 0,
  count0: 2,
  start1: 0,
  count1: 3,
  min: 0.1,
  max: 1.0
};

describe('VolumeViewComponent', () => {
  let component: VolumeViewComponent;
  let fixture: ComponentFixture<VolumeViewComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [VolumeViewComponent],
      imports: [
        HttpClientTestingModule,
        NoopAnimationsModule,
        FormsModule
      ],
      providers: [SdcViewerService]
    }).compileComponents();

    fixture = TestBed.createComponent(VolumeViewComponent);
    component = fixture.componentInstance;
    component.sdcPath = 'test.sdc';
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').flush(MOCK_VOLUME_SLICE);
    expect(component).toBeTruthy();
  });

  it('should use @ViewChild heatmapCanvas instead of document.querySelector', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').flush(MOCK_VOLUME_SLICE);
    tick(200);
    fixture.detectChanges();

    // After data loads, the canvas is rendered and @ViewChild resolves
    expect(component.heatmapCanvas).toBeDefined();
    expect(component.heatmapCanvas.nativeElement.tagName).toBe('CANVAS');
  }));

  it('should set sliceData after successful API call', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').flush(MOCK_VOLUME_SLICE);
    tick(200);
    fixture.detectChanges();

    expect(component.sliceData).toEqual(MOCK_VOLUME_SLICE);
    expect(component.loading).toBeFalse();
    expect(component.error).toBeNull();
  }));

  it('should set error on API failure', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').error(new ErrorEvent('network error'));
    tick();
    fixture.detectChanges();

    expect(component.error).toContain('Error loading slice');
    expect(component.loading).toBeFalse();
  }));

  it('should render canvas element with #heatmapCanvas template reference', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').flush(MOCK_VOLUME_SLICE);
    tick(200);
    fixture.detectChanges();

    const canvasEl = fixture.nativeElement.querySelector('canvas');
    expect(canvasEl).not.toBeNull();
    // Confirm @ViewChild resolved to the canvas element
    expect(component.heatmapCanvas.nativeElement).toBe(canvasEl);
  }));

  it('should reload slice when loadSlice() is called with different axis', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/volume-slice').flush(MOCK_VOLUME_SLICE);
    tick(200);
    fixture.detectChanges();

    component.axis = 'INLINE';
    component.loadSlice();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/viewer/volume-slice');
    expect(req.request.body.axis).toBe('INLINE');
    req.flush({ ...MOCK_VOLUME_SLICE, axis: 'INLINE' });
    tick(200);
  }));
});
