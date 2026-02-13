# SDC Viewer UI - Angular

Interface moderna em Angular para visualizar arquivos `.sdc` (v2 e v3) gerados pelo compressor SDC.

## Recursos

- **Metadados**: Visualiza informações de header, dimensões, número de blocos.
- **Traços 2D**: Renderiza heatmap de traços sísmicos (.sdc v2) com controles de range.
- **Volume 3D**: Fatia volumétrica com navegação por INLINE/XLINE/TIME (.sdc v3).
- **Cache LRU**: Navegação fluida com cache de blocos decomprimidos no backend.
- **Responsivo**: Interface limpa e moderna com controles intuitivos.

## Pré-requisitos

- Node.js 20+
- npm 10+
- Angular CLI 18+

## Instalação

```bash
cd sdc-ui
npm install
npm start
```

Acessa `http://localhost:4200`. A UI conecta automaticamente ao backend em `http://localhost:8080` (configurável em `proxy.conf.json`).

## Estrutura de Pastas

```
src/
├── app/
│   ├── components/
│   │   ├── file-upload/        # Upload de arquivo .sdc
│   │   ├── metadata-view/      # Exibe metadados
│   │   ├── trace-view/         # Visualiza traços 2D (v2)
│   │   └── volume-view/        # Visualiza volume 3D (v3)
│   ├── services/
│   │   └── sdc-viewer.service.ts   # Cliente HTTP da API
│   └── app.component.ts        # Componente raiz
├── index.html
├── main.ts
└── styles.scss
```

## Uso

1. **Abrir arquivo**: Insira o caminho do .sdc (ex: `d:/dados/volume.sdc`) e clique "Abrir".
2. **Ver metadados**: Tab "Metadados" mostra info de header.
3. **Traços 2D**: Tab "Traços" permite navegar e renderizar traços sísmicos (apenas .sdc v2).
4. **Volume 3D**: Tab "Volume" permite fatiar e navegar volume 3D (apenas .sdc v3).

## Endpoints Consumidos

- `POST /api/viewer/info` - Metadados do arquivo
- `POST /api/viewer/trace-slice` - Slice de traços 2D
- `POST /api/viewer/volume-slice` - Slice de volume 3D
- `GET /api/viewer/cache-info` - Info de cache
- `DELETE /api/viewer/cache-clear` - Limpar cache

## Configuração de CORS

Se o backend está em origem diferente, configure CORS. Exemplo em Java:

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

## Build para Produção

```bash
npm run build
# Saída em dist/sdc-ui/
```

## Desenvolvimento

```bash
npm start
# Servidor em http://localhost:4200 com hot reload
```
