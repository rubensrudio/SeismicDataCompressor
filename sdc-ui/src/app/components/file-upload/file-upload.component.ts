import { Component, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-file-upload',
  templateUrl: './file-upload.component.html',
  styleUrls: ['./file-upload.component.scss']
})
export class FileUploadComponent {
  @Output() fileSelected = new EventEmitter<string>();

  onSubmit(filePath: string) {
    if (filePath.trim()) {
      this.fileSelected.emit(filePath.trim());
    }
  }
}
