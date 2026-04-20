import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { FileSelectorComponent } from './file-selector.component';
import { SdcViewerService } from '../../services/sdc-viewer.service';

const RECENT_KEY = 'sdc.recentFiles';

describe('FileSelectorComponent', () => {
  let component: FileSelectorComponent;
  let fixture: ComponentFixture<FileSelectorComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(RECENT_KEY);

    await TestBed.configureTestingModule({
      declarations: [FileSelectorComponent],
      imports: [
        HttpClientTestingModule,
        NoopAnimationsModule,
        FormsModule,
        MatSelectModule,
        MatFormFieldModule,
        MatInputModule,
        MatIconModule,
        MatButtonModule
      ],
      providers: [SdcViewerService]
    }).compileComponents();

    fixture = TestBed.createComponent(FileSelectorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(RECENT_KEY);
  });

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: [] });
    expect(component).toBeTruthy();
  });

  it('1. should load files from API and display them in availableFiles', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/viewer/files');
    expect(req.request.method).toBe('GET');
    req.flush({ files: ['survey-a.sdc', 'survey-b.sdc', 'survey-c.sdc'] });
    fixture.detectChanges();

    expect(component.availableFiles).toEqual(['survey-a.sdc', 'survey-b.sdc', 'survey-c.sdc']);
    expect(component.fallbackMode).toBeFalse();
  });

  it('2. should show recent files from localStorage', () => {
    localStorage.setItem(RECENT_KEY, JSON.stringify(['survey-a.sdc', 'survey-b.sdc']));

    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: ['survey-a.sdc', 'survey-b.sdc', 'survey-c.sdc'] });
    fixture.detectChanges();

    expect(component.recentFiles).toEqual(['survey-a.sdc', 'survey-b.sdc']);
  });

  it('3. should set fallbackMode=true when API returns empty list', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: [] });
    fixture.detectChanges();

    expect(component.fallbackMode).toBeTrue();
    expect(component.availableFiles).toEqual([]);
  });

  it('4. should emit fileSelected on selection', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: ['survey-a.sdc'] });
    fixture.detectChanges();

    const emitted: string[] = [];
    component.fileSelected.subscribe((val: string) => emitted.push(val));

    component.onSelect({ value: 'survey-a.sdc' });

    expect(emitted).toEqual(['survey-a.sdc']);
  });

  it('should use sgy endpoint when extension is sgy', () => {
    component.extension = 'sgy';
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/viewer/sgy-files');
    expect(req.request.method).toBe('GET');
    req.flush({ files: ['trace-1.sgy'] });
    fixture.detectChanges();

    expect(component.availableFiles).toEqual(['trace-1.sgy']);
  });

  it('should save selection to localStorage LIFO and cap at 5', () => {
    localStorage.setItem(RECENT_KEY, JSON.stringify(['b.sdc', 'c.sdc', 'd.sdc', 'e.sdc', 'f.sdc']));
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: ['a.sdc', 'b.sdc', 'c.sdc', 'd.sdc', 'e.sdc', 'f.sdc'] });
    fixture.detectChanges();

    component.onSelect({ value: 'a.sdc' });

    const stored = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
    expect(stored[0]).toBe('a.sdc');
    expect(stored.length).toBeLessThanOrEqual(5);
  });

  it('should remove stale recent entries not in server list', () => {
    localStorage.setItem(RECENT_KEY, JSON.stringify(['stale.sdc', 'survey-a.sdc']));
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: ['survey-a.sdc'] });
    fixture.detectChanges();

    expect(component.recentFiles).not.toContain('stale.sdc');
    expect(component.recentFiles).toContain('survey-a.sdc');
  });

  it('should set fallbackMode=true when API errors', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').error(new ErrorEvent('network error'));
    fixture.detectChanges();

    expect(component.fallbackMode).toBeTrue();
  });

  it('should emit from onManualSubmit when in fallback mode', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/viewer/files').flush({ files: [] });
    fixture.detectChanges();

    const emitted: string[] = [];
    component.fileSelected.subscribe((val: string) => emitted.push(val));

    component.manualPath = '/data/my-file.sdc';
    component.onManualSubmit();

    expect(emitted).toEqual(['/data/my-file.sdc']);
  });
});
