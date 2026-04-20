import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Router, ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { AppComponent } from './app.component';
import { SdcViewerService } from './services/sdc-viewer.service';

describe('AppComponent router integration', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let router: Router;
  let queryParamsSubject: BehaviorSubject<Record<string, string>>;

  beforeEach(async () => {
    queryParamsSubject = new BehaviorSubject<Record<string, string>>({});

    const activatedRouteMock = {
      queryParams: queryParamsSubject.asObservable()
    };

    await TestBed.configureTestingModule({
      imports: [
        RouterTestingModule,
        FormsModule,
        HttpClientTestingModule
      ],
      declarations: [AppComponent],
      providers: [
        { provide: ActivatedRoute, useValue: activatedRouteMock },
        SdcViewerService
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should default to metadata tab', () => {
    expect(component.activeTab).toBe('metadata');
  });

  it('should read tab from queryParams on init', fakeAsync(() => {
    queryParamsSubject.next({ tab: 'trace' });
    tick();
    fixture.detectChanges();
    expect(component.activeTab).toBe('trace');
  }));

  it('should default to metadata for invalid tab param', fakeAsync(() => {
    queryParamsSubject.next({ tab: 'nonexistent' });
    tick();
    fixture.detectChanges();
    expect(component.activeTab).toBe('metadata');
  }));

  it('setTab should navigate with tab query param', () => {
    const navigateSpy = spyOn(router, 'navigate');
    component.setTab('trace');
    expect(navigateSpy).toHaveBeenCalledWith([], {
      relativeTo: jasmine.any(Object),
      queryParams: { tab: 'trace' },
      queryParamsHandling: 'merge'
    });
  });

  it('should read file from queryParams on init', fakeAsync(() => {
    spyOn(component, 'loadFileInfo');
    queryParamsSubject.next({ tab: 'metadata', file: 'basic90.sdc' });
    tick();
    fixture.detectChanges();
    expect(component.selectedFile).toBe('basic90.sdc');
  }));

  it('onFileSelected should navigate with file query param', () => {
    const navigateSpy = spyOn(router, 'navigate');
    spyOn(component, 'loadFileInfo');
    component.onFileSelected('basic90.sdc');
    expect(navigateSpy).toHaveBeenCalledWith([], {
      relativeTo: jasmine.any(Object),
      queryParams: { file: 'basic90.sdc' },
      queryParamsHandling: 'merge'
    });
  });
});
