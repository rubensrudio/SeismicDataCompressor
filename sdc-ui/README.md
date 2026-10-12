# SDC Viewer UI - Angular

Modern Angular interface for viewing `.sdc` files (v2 and v3) produced by the SDC compressor.

## Features

- **Metadata**: Shows header information, dimensions and block count.
- **2D traces**: Renders a heatmap of seismic traces (.sdc v2) with range controls.
- **3D volume**: Volume slices with INLINE/XLINE/TIME navigation (.sdc v3).
- **LRU cache**: Smooth navigation backed by a cache of decompressed blocks on the backend.
- **Responsive**: Clean, modern interface with intuitive controls.

## Prerequisites

- Node.js 20+
- npm 10+
- Angular CLI 18+

## Installation

```bash
cd sdc-ui
npm install
npm start
```

Open `http://localhost:4200`. The UI connects to the backend at `http://localhost:8080` automatically (configurable in `proxy.conf.json`).

## Folder Structure

```
src/
├── app/
│   ├── components/
│   │   ├── file-upload/        # .sdc file upload
│   │   ├── metadata-view/      # Shows metadata
│   │   ├── trace-view/         # 2D trace viewer (v2)
│   │   └── volume-view/        # 3D volume viewer (v3)
│   ├── services/
│   │   └── sdc-viewer.service.ts   # HTTP client for the API
│   └── app.component.ts        # Root component
├── index.html
├── main.ts
└── styles.scss
```

## Usage

1. **Open a file**: Enter the path to the .sdc file (e.g. `d:/data/volume.sdc`) and click "Open".
2. **View metadata**: The "Metadata" tab shows header information.
3. **2D traces**: The "Trace View" tab lets you browse and render seismic traces (.sdc v2 only).
4. **3D volume**: The "Volume" tab lets you slice and navigate a 3D volume (.sdc v3 only).

## Endpoints Used

- `POST /api/viewer/info` - File metadata
- `POST /api/viewer/trace-slice` - 2D trace slice
- `POST /api/viewer/volume-slice` - 3D volume slice
- `GET /api/viewer/cache-info` - Cache info
- `DELETE /api/viewer/cache-clear` - Clear cache

## CORS Configuration

If the backend runs on a different origin, configure CORS. Java example:

```java
@Configuration
public class CorsConfig {
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
          .allowedOrigins("http://localhost:4200")
          .allowedMethods("GET", "POST", "DELETE")
          .allowedHeaders("*");
      }
    };
  }
}
```

## Production Build

```bash
npm run build
# Output in dist/sdc-ui/
```

## Development

```bash
npm start
# Dev server at http://localhost:4200 with hot reload
```
