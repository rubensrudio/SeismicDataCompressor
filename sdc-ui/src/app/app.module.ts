import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import { AppComponent } from './app.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { MetadataViewComponent } from './components/metadata-view/metadata-view.component';
import { TraceViewComponent } from './components/trace-view/trace-view.component';
import { VolumeViewComponent } from './components/volume-view/volume-view.component';
import { TraceHeadersViewComponent } from './components/trace-headers-view/trace-headers-view.component';
import { FileSelectorComponent } from './components/file-selector/file-selector.component';
import { ErrorDisplayComponent } from './components/error-display/error-display.component';

@NgModule({
  declarations: [
    AppComponent,
    FileUploadComponent,
    MetadataViewComponent,
    TraceViewComponent,
    VolumeViewComponent,
    TraceHeadersViewComponent,
    FileSelectorComponent,
    ErrorDisplayComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    RouterModule.forRoot([])
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
