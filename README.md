# AI-Enhanced Seismic Data Compressor (Maven Monorepo)

A comprehensive seismic data compression solution with AI enhancement, REST API, and modern web interface.

## Architecture

### Modules
- **`sdc-core`**: Core compression engine with preprocessing pipeline, quantization algorithms, and SDC container I/O (2D/3D support)
- **`sdc-ai`**: TensorFlow Java integration for AI-enhanced compression (SavedModel loading)
- **`sdc-cli`**: Command-line interface (Picocli) for compress/decompress/inspect operations
- **`sdc-svc`**: Spring Boot WebFlux microservice with RESTful API
- **`sdc-ui`**: Angular 18 web interface with Material Design components

## Prerequisites

- Java 17 or higher
- Maven 3.9.x
- Node.js 18+ and npm (for UI development)

## Build

```bash
mvn -v         # Verify Maven 3.9.x
java -version  # Verify JDK 17+
mvn clean install -T 1C
```

## Backend Services (sdc-svc)

### Run the Service
```bash
cd sdc-svc
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

### API Documentation (Swagger/OpenAPI)

Interactive API documentation is available via Swagger UI:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`

The Swagger interface provides:
- Complete API specification with request/response schemas
- Interactive endpoint testing
- Detailed descriptions for all operations
- Example request payloads

### REST API Endpoints

#### Compression/Decompression API (`/api/segy`)
- **POST** `/api/segy/compress` - Compress 2D SEG-Y file to SDC format
- **POST** `/api/segy/decompress` - Decompress SDC file to SEG-Y format
- **POST** `/api/segy/compress3d` - Compress 3D SEG-Y file with volume blocking
- **POST** `/api/segy/decompress3d` - Decompress 3D SDC file

#### Viewer API (`/api/viewer`)
- **POST** `/api/viewer/info` - Get SDC file metadata (dimensions, format, block structure)
- **POST** `/api/viewer/trace-slice` - Extract trace data slice (specified trace and sample ranges)
- **POST** `/api/viewer/volume-slice` - Extract 3D volume slice (inline/xline/time axis)
- **POST** `/api/viewer/trace-headers` - Retrieve trace header information
- **GET** `/api/viewer/cache-info?sdcPath={path}` - Get cache statistics
- **DELETE** `/api/viewer/cache-clear?sdcPath={path}` - Clear file cache

### Key Features
- Non-blocking reactive API using Spring WebFlux
- Intelligent volume block caching for fast 3D data access
- Support for both 2D and 3D seismic data
- Configurable compression profiles (quantization, preprocessing)

## Command-Line Interface (sdc-cli)

### Build CLI
```bash
cd sdc-cli
mvn -q -DskipTests package
```

### Usage
```bash
java -jar target/sdc-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar --help
```

Available commands:
- Compress SEG-Y to SDC
- Decompress SDC to SEG-Y
- Inspect SDC file metadata
- Batch processing support

## Frontend (sdc-ui)

### Setup
```bash
cd sdc-ui
npm install
```

### Run Development Server
```bash
npm start
```

The UI will open at `http://localhost:4200` with proxy to backend on port 8080.

### Features

#### Components
- **File Upload**: Select local SDC files for viewing
- **Metadata View**: Display file information (dimensions, format, block structure)
- **Trace View**: Visualize individual seismic traces
- **Trace Headers View**: Inspect trace header values
- **Volume View**: Interactive 3D volume slice visualization (inline/crossline/time)

#### Technology Stack
- Angular 18
- Angular Material for UI components
- RxJS for reactive data handling
- TypeScript 5.2
- Integration with backend REST API via `SdcViewerService`

### Build for Production
```bash
cd sdc-ui
npm run build
```

Output will be in `dist/sdc-ui/`

## Core Features

### sdc-core Module
- **Preprocessing**: Trace data normalization and conditioning
- **Quantization**: Linear quantizer with configurable bit depth
- **SDC Container**: Custom binary format with header and compressed blocks
- **2D Support**: Standard trace-by-trace compression
- **3D Support**: Volume-based blocking (inline/xline/time) for efficient random access
- **SEG-Y I/O**: Full SEG-Y Rev1 read/write support

### sdc-ai Module
- TensorFlow Java runtime integration
- Autoencoder model loading (SavedModel format)
- AI-enhanced compression pipeline (experimental)

## Project Structure

```
sdc-parent/
├── pom.xml                 # Parent POM
├── sdc-core/              # Core compression engine
├── sdc-ai/                # AI/ML integration
├── sdc-cli/               # Command-line tool
├── sdc-svc/               # Spring Boot REST service
└── sdc-ui/                # Angular web interface
    ├── src/app/
    │   ├── components/    # UI components
    │   └── services/      # HTTP services
    └── package.json
```

## Development

### Run Full Stack
1. Start backend: `cd sdc-svc && mvn spring-boot:run`
2. Start frontend: `cd sdc-ui && npm start`
3. Access UI at `http://localhost:4200`

### Testing
```bash
mvn test              # Run all Java tests
cd sdc-ui && npm test # Run Angular tests
```
