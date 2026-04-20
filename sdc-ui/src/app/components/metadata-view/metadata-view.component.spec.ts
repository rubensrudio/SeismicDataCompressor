import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetadataViewComponent } from './metadata-view.component';
import { ViewerInfoResponse } from '../../services/sdc-viewer.service';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CommonModule } from '@angular/common';

const baseInfo: ViewerInfoResponse = {
  sdcPath: '/data/test.sdc',
  version: 2,
  traceCount: 100,
  samplesPerTrace: 500,
};

describe('MetadataViewComponent', () => {
  let component: MetadataViewComponent;
  let fixture: ComponentFixture<MetadataViewComponent>;

  function createComponent(extra: Partial<ViewerInfoResponse> = {}): void {
    component.fileInfo = { ...baseInfo, ...extra };
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MetadataViewComponent],
      imports: [
        CommonModule,
        BrowserAnimationsModule,
        MatCardModule,
        MatChipsModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataViewComponent);
    component = fixture.componentInstance;
  });

  it('should display compressionRatio, avgPsnr and effectiveBits when provided', () => {
    createComponent({ compressionRatio: 4.56, avgPsnr: 42.3, effectiveBits: 8 });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('4.56');
    expect(text).toContain('42.3');
    expect(text).toContain('8');
  });

  it('should show em-dash when compressionRatio, avgPsnr and effectiveBits are undefined', () => {
    createComponent();
    const items = fixture.nativeElement.querySelectorAll('.metric-item span');
    items.forEach((item: HTMLElement) => {
      expect(item.textContent?.trim()).toBe('—');
    });
  });

  it('should show warning chip when avgPsnr is below 30', () => {
    createComponent({ avgPsnr: 25 });
    const chip = fixture.nativeElement.querySelector('mat-chip');
    expect(chip).toBeTruthy();
    expect(chip.textContent).toContain('Low fidelity');
  });

  it('should not show warning chip when avgPsnr is 30 or above', () => {
    createComponent({ avgPsnr: 30 });
    const chip = fixture.nativeElement.querySelector('mat-chip');
    expect(chip).toBeNull();
  });

  it('should not show warning chip when avgPsnr is null/undefined', () => {
    createComponent();
    const chip = fixture.nativeElement.querySelector('mat-chip');
    expect(chip).toBeNull();
  });
});
