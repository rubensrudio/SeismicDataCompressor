# SeismicDataCompressor (SDC)

Lossy and lossless compression of 3D seismic volumes (SEG-Y), with a classical closed-loop
DPCM codec, a **measured neural (autoencoder) hybrid codec**, a REST API, a web interface and a
**reproducible benchmark** that compares them with state-of-the-art scientific compressors
(ZFP, SZ3).

## Architecture

### Modules
- **`sdc-core`**: Core compression engine with preprocessing pipeline, quantization algorithms, and SDC container I/O (2D/3D support)
- **`sdc-ai`**: Neural hybrid codec — autoencoder prediction + bounded residual, trained with TensorFlow and executed in pure Java ([details](sdc-ai/README.md))
- **`sdc-cli`**: Command-line interface (Picocli) for compress/decompress/inspect operations
- **`sdc-svc`**: Spring Boot WebFlux microservice with RESTful API
- **`sdc-ui`**: Angular 18 web interface with Material Design components
- **`sdc-bench`**: Reproducible benchmark (deterministic synthetic SEG-Y dataset + measurement harness)

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
- **Quantization**: Closed-loop DPCM (the encoder predicts from reconstructed samples, so quantization error does not accumulate) with the bit depth stored in each payload
- **SDC Container**: Custom binary format with header and compressed blocks
- **2D Support**: Standard trace-by-trace compression
- **3D Support**: Volume-based blocking (inline/xline/time) for efficient random access
- **SEG-Y I/O**: Full SEG-Y Rev1 read/write support

### sdc-ai Module
- Convolutional autoencoder (32.7k parameters) trained with TensorFlow on synthetic sections; training script and model metadata in `sdc-ai/training` and `sdc-ai/models`
- Dependency-free `.sdcnn` model format and pure-Java inference, checked against TensorFlow outputs in the tests
- `NeuralHybridCodec`: int8 latents + uniformly quantized residual, so the maximum error is bounded; a zero-prediction mode isolates what the network contributes
- TensorFlow Java runtime placeholder (`AeRuntime`), not required by the codec
- Limitations: trained and evaluated on synthetic data only; 50–200× slower than the classical codecs (see [`sdc-ai/README.md`](sdc-ai/README.md))

## Project Structure

```
sdc-parent/
├── pom.xml                 # Parent POM
├── sdc-core/              # Core compression engine
├── sdc-ai/                # AI/ML integration
├── sdc-cli/               # Command-line tool
├── sdc-svc/               # Spring Boot REST service
├── sdc-bench/             # Benchmark harness + synthetic dataset generator
├── benchmark/             # Benchmark script, methodology and committed results
└── sdc-ui/                # Angular web interface
    ├── src/app/
    │   ├── components/    # UI components
    │   └── services/      # HTTP services
    └── package.json
```

## Benchmark

A reproducible benchmark (script, deterministic dataset with SHA-256 checksums, hardware capture,
committed results, rate–distortion sweep and external baselines) lives in
[`benchmark/`](benchmark/README.md):

```bash
./benchmark/run-benchmark.sh          # codec tables
RD=1 ./benchmark/run-benchmark.sh     # + rate–distortion sweep (rd.csv)
python benchmark/rd_report.py --results benchmark/results/<run> \
    --segy synthetic-medium=benchmark/data/synthetic-medium.sgy   # + ZFP/SZ3, BD-rate, charts
```

See [`benchmark/README.md`](benchmark/README.md) for the reference results, the methodology
and the limitations.

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

## License

Apache License 2.0 — see [`LICENSE`](LICENSE). To cite this software, see [`CITATION.cff`](CITATION.cff).

## Authorship

SDC is designed and maintained by **Rubens Rudio**, who defined its architecture and
specifications and reviews every change. AI coding assistants (Claude, by Anthropic) have been
used for code validation, benchmark generation and implementation work under the author's
direction; commits produced by the assistant are attributed to it in the Git history.
