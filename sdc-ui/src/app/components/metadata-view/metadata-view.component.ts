import { Component, Input } from '@angular/core';
import { ViewerInfoResponse } from '../../services/sdc-viewer.service';

@Component({
  selector: 'app-metadata-view',
  templateUrl: './metadata-view.component.html',
  styleUrls: ['./metadata-view.component.scss']
})
export class MetadataViewComponent {
  @Input() fileInfo!: ViewerInfoResponse;

  get info(): ViewerInfoResponse {
    return this.fileInfo;
  }
}
