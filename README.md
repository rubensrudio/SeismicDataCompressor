# AI-Enhanced Seismic Data Compressor (Maven Monorepo)

Módulos:
- `sdc-core`: pipeline de pré-processamento, quantização e contêiner/IO
- `sdc-ai`: integração TensorFlow Java (carregamento de modelos SavedModel)
- `sdc-cli`: CLI (Picocli) para compress/decompress/inspect
- `sdc-svc`: microserviço Spring Boot (WebFlux) com endpoints REST
- `sdc-ui`: placeholder do front-end Angular (build separado)

## Build
mvn -v         # Maven 3.9.x
java -version  # JDK 17
mvn clean install -T 1C

## Executar serviço
cd sdc-svc
mvn spring-boot:run

## Executar CLI
cd sdc-cli
mvn -q -DskipTests package
java -jar target/sdc-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar --help
