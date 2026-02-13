import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { MetadataViewComponent } from './components/metadata-view/metadata-view.component';
import { TraceViewComponent } from './components/trace-view/trace-view.component';
import { VolumeViewComponent } from './components/volume-view/volume-view.component';
import { TraceHeadersViewComponent } from './components/trace-headers-view/trace-headers-view.component';

@NgModule({
  declarations: [
    AppComponent,
    FileUploadComponent,
    MetadataViewComponent,
    TraceViewComponent,
    VolumeViewComponent,
    TraceHeadersViewComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
