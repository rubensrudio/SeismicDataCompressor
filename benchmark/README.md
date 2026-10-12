# Benchmark reprodutível do SDC

Este diretório contém tudo para medir o compressor de forma reprodutível: o **script**, o **dataset** (gerado de forma determinística e verificado por SHA-256), a **captura de hardware/software** e os **resultados** de cada execução.

```
benchmark/
├── run-benchmark.sh          # ponto de entrada: build + hardware + benchmark
├── README.md                 # este arquivo (metodologia + resultados de referência)
├── data/                     # datasets gerados (ignorado pelo git; recriado bit a bit)
└── results/
    └── <data>_<label>/       # um diretório por execução
        ├── hardware.txt      # CPU, memória, cgroup, disco, kernel, JDK, Maven, commit
        ├── results.json      # tudo, inclusive cada amostra de tempo
        ├── results.csv       # uma linha por (dataset, codec, profile)
        ├── summary.md        # tabelas prontas para leitura
        └── run.log           # saída do console
```

O código fica no módulo Maven [`sdc-bench`](../sdc-bench), que depende apenas do `sdc-core` (sem TensorFlow).

## Como rodar

Pré-requisitos: JDK 17+ e Maven 3.9 (os mesmos do projeto). Cerca de 3 GiB de RAM livre para o preset `medium`.

```bash
./benchmark/run-benchmark.sh
```

Isso compila `sdc-core` + `sdc-bench`, gera os datasets (`small` e `medium`) se necessário, confere o SHA-256 de cada um, roda o benchmark e grava em `benchmark/results/<AAAA-MM-DD>_<os>-<arch>-<N>cpu/`. Na máquina de referência leva cerca de 2 minutos.

Variáveis de ambiente:

| Variável | Default | Significado |
|---|---|---|
| `PRESETS` | `small,medium` | datasets sintéticos (`tiny`, `small`, `medium` ou `none`) |
| `SEGY` | — | SEG-Y(s) externo(s), separados por vírgula |
| `WARMUP` | `2` | iterações de aquecimento descartadas |
| `ITERATIONS` | `5` | iterações medidas |
| `BLOCK` | `8,8,0` | bloco 3D inline,xline,tempo (`0` = tempo inteiro, o default do `sdc-svc`) |
| `HEAP` | `3g` | heap da JVM (`-Xms` = `-Xmx`) |
| `LABEL` | `<os>-<arch>-<N>cpu` | rótulo do run (vira parte do nome do diretório) |
| `OUT` | `benchmark/results/<data>_<label>` | diretório de saída |
| `SKIP_BUILD` | `0` | `1` pula o `mvn package` |

Exemplos:

```bash
PRESETS=small ITERATIONS=3 ./benchmark/run-benchmark.sh           # rodada rápida
LABEL=macbook-m3 ./benchmark/run-benchmark.sh                     # rotular a máquina
SEGY=/dados/meu_volume.sgy PRESETS=none ./benchmark/run-benchmark.sh   # dado real
```

O jar também pode ser chamado direto: `java -jar sdc-bench/target/sdc-bench-0.1.0-SNAPSHOT-jar-with-dependencies.jar --help`.

## Dataset

Os dados sísmicos reais costumam ser grandes e ter licença restritiva, e não dá para versionar no git. Por isso o benchmark usa um **volume 3D sintético gerado de forma determinística** (`SyntheticSegyGenerator`):

- refletores com mergulho em inline e xline e um dobramento senoidal suave (um a cada ~25 amostras)
- uma falha normal planar (rejeito de 6,5 amostras)
- wavelet Ricker de 25 Hz, dt = 4 ms
- atenuação exponencial com o tempo e ruído gaussiano aditivo (2% da escala)
- SEG-Y rev1, formato 5 (IEEE float32), inline nos bytes 189-192 e xline nos bytes 193-196 do header de traço

| Preset | Grade (IL × XL × amostras) | Traços | Tamanho | SHA-256 |
|---|---|---:|---:|---|
| `tiny` | 8 × 8 × 128 | 64 | 50,5 KiB | `d18e727556b925d6edecfbd258b9b4b1990e427310a3a9e5959196e50717261c` |
| `small` | 32 × 32 × 500 | 1 024 | 2,19 MiB | `0e6036582f8db53226e33c9b4b2e87a72204f6c3b974415d304fe2e8dc7401e7` |
| `medium` | 128 × 128 × 1000 | 16 384 | 66,25 MiB | `0b6389d128a2a1a08ebd959fbb87ac583ed60beaf473fd3c3d122fa6fd139e26` |

Seed: `20261012` em todos os presets.

**Por que os bytes são idênticos em qualquer máquina:** `java.util.Random` tem algoritmo definido na especificação, `nextGaussian` usa `StrictMath` (Java 17+), e toda a matemática do gerador usa `StrictMath`, que dá o mesmo resultado bit a bit em qualquer JVM e CPU. O runner recalcula o SHA-256 e **aborta** se o arquivo não for o canônico. O teste `SyntheticSegyGeneratorTest` fixa o hash do preset `tiny`. O hash do `small` também foi conferido com a JVM no modo só interpretado (`-Xint`, sem intrínsecos do JIT).

Para conferir manualmente: `sha256sum benchmark/data/*.sgy`.

### Dados reais

`SEGY=...` aceita qualquer SEG-Y que o `SegyIO` lê: formato 1 (IBM) ou 5 (IEEE), traços de tamanho fixo, textual header de 3200 bytes, inline e xline nos bytes 189 e 193. Volumes públicos como o F3 Netherlands (dGB/OpendTect) ou o Volve (Equinor) servem, desde que os headers sigam esse layout. Nesse caso o SHA-256 do arquivo é registrado no relatório, mas não é verificado contra um valor esperado.

## Metodologia

**O que é medido.** Para cada dataset, o SEG-Y é lido **uma vez** (fora da medição). Depois:

| Codec | Compressão medida | Descompressão medida |
|---|---|---|
| `sdc-2d` (container v2) | `SdcFileWriter.writeCompressed` | `SdcFileReader.readAllCompressed` |
| `sdc-3d` (container v3) | `Sdc3DFileWriter.write3D` (bloco 8×8×tempo inteiro) | `Sdc3DFileReader.read3D` |
| `deflate-6`, `deflate-9` | `java.util.zip.Deflater` sobre os float32 brutos (big-endian) | `Inflater` |
| `shuffle+deflate-9` | byte-shuffle (estilo Blosc/HDF5) + Deflate 9 | Inflate + unshuffle |

Os codecs SDC são executados com os três profiles de `CompressionProfile`: `HIGH_QUALITY` (16 bits, Deflate 9), `BALANCED` (12 bits, Deflate 7) e `HIGH_COMPRESSION` (8 bits, Deflate 6). Os tempos SDC incluem a escrita e a leitura do `.sdc` em um diretório temporário (em geral servido pelo page cache), porque é assim que a API pública trabalha. Os baselines rodam só em memória. Para eles, a reconstrução é conferida byte a byte.

**Como é medido.** `WARMUP` iterações descartadas (para o JIT compilar) e depois `ITERATIONS` iterações medidas com `System.nanoTime()`, com um `System.gc()` antes de cada uma. Reportamos mediana ± desvio padrão (e, no JSON, mín/máx/média e cada amostra). O throughput é calculado sobre os **bytes brutos dos traços** (`traços × amostras × 4`), usando a mediana. Tudo roda em uma thread, porque é assim que o codec funciona hoje.

**Métricas de qualidade**, calculadas sobre as amplitudes originais, sem normalização:

- **Razão** = bytes brutos dos traços / bytes do arquivo comprimido; **bits/amostra** = 8 × bytes comprimidos / nº de amostras
- **SNR (dB)** = 10·log10(Σx² / Σ(x − x̂)²), a métrica usual em compressão sísmica
- **PSNR (dB)** = 20·log10((max − min) / RMSE), com a faixa dinâmica do volume como pico
- **NRMSE (%)** = RMSE / RMS(x) × 100, e **erro absoluto máximo**

> O `LinearQuantizer.psnr` do projeto assume sinal em [-1, 1]. Em amplitudes reais ele não é comparável entre datasets, por isso o benchmark usa as métricas acima.

## Resultados de referência

Run [`results/2026-10-12_linux-x86_64-4cpu`](results/2026-10-12_linux-x86_64-4cpu/summary.md), commit `9952555`, árvore limpa.

**Hardware/software:** Intel Xeon @ 2,10 GHz, 4 vCPUs (AVX2/AVX-512), 16 GiB RAM, contêiner Docker, ext4 · Ubuntu 24.04.5, kernel 6.18 · OpenJDK 21.0.12 (G1, heap fixo de 3 GiB) · Maven 3.9.11. Os detalhes completos estão em [`hardware.txt`](results/2026-10-12_linux-x86_64-4cpu/hardware.txt).

### `synthetic-medium` (128 × 128 × 1000, 62,5 MiB de amostras)

| Codec | Profile | Razão | bits/amostra | Comp. (ms) | Desc. (ms) | Comp. MiB/s | Desc. MiB/s | SNR (dB) | NRMSE (%) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| sdc-2d | HIGH_QUALITY | 2,06:1 | 15,51 | 1374 ± 58 | 419 ± 12 | 45,5 | 149,2 | 58,98 | 0,11 |
| sdc-2d | BALANCED | 2,51:1 | 12,75 | 1668 ± 159 | 419 ± 13 | 37,5 | 149,3 | **0,33** ⚠️ | 96,2 |
| sdc-2d | HIGH_COMPRESSION | 3,49:1 | 9,17 | 1387 ± 56 | 410 ± 25 | 45,1 | 152,5 | **−0,19** ⚠️ | 102,3 |
| sdc-3d | HIGH_QUALITY | 2,24:1 | 14,29 | 1351 ± 138 | 406 ± 6 | 46,3 | 154,1 | 58,00 | 0,13 |
| sdc-3d | BALANCED | 2,81:1 | 11,40 | 1945 ± 67 | 412 ± 13 | 32,1 | 151,6 | 33,63 | 2,08 |
| sdc-3d | HIGH_COMPRESSION | 4,79:1 | 6,68 | 3559 ± 282 | 374 ± 27 | 17,6 | 167,1 | 9,58 | 33,19 |
| deflate-6 | lossless | 1,06:1 | 30,08 | 2203 ± 75 | 400 ± 8 | 28,4 | 156,4 | ∞ | 0 |
| deflate-9 | lossless | 1,06:1 | 30,08 | 2206 ± 120 | 400 ± 9 | 28,3 | 156,3 | ∞ | 0 |
| shuffle+deflate-9 | lossless | 1,19:1 | 26,94 | 8601 ± 255 | 139 ± 4 | 7,3 | 450,1 | ∞ | 0 |

As tabelas do `synthetic-small` e o PSNR/erro máximo de todas as combinações estão no [`summary.md`](results/2026-10-12_linux-x86_64-4cpu/summary.md).

### O que os números mostram

1. **`sdc-2d` com `BALANCED` e `HIGH_COMPRESSION` não reconstrói o sinal** (SNR ≈ 0 dB, erro do tamanho do próprio sinal). A causa está no código: `TraceBlockCodec.compress` quantiza com `profile.effectiveBits()` (12 ou 8 bits), mas `TraceBlockCodec.decompress` chama `LinearQuantizer.decode(q)` ([TraceBlockCodec.java:64](../sdc-core/src/main/java/com/sdc/core/TraceBlockCodec.java#L64)), que sempre assume 16 bits. Além disso, o container v2 não grava quantos bits foram usados. Só o `HIGH_QUALITY` (16 bits) é consistente no 2D. O 3D não tem esse problema, porque `decompressBlock` recebe o profile.
2. **A qualidade do `sdc-3d` cai bastante com menos bits e com blocos maiores**: o SNR do `HIGH_COMPRESSION` vai de 14,1 dB (`small`, 500 amostras) para 9,6 dB (`medium`, 1000 amostras). Isso é compatível com predição em malha aberta: `residualEncode` prevê a partir das amostras **originais**, mas `residualDecode` prevê a partir das **reconstruídas** ([VolumeBlock3DCodec.java:23-25 e 49-51](../sdc-core/src/main/java/com/sdc/core/VolumeBlock3DCodec.java#L23)). Com isso, o erro de quantização se acumula ao longo do bloco. O delta 2D (`Preprocessing.deltaEncode`/`deltaDecode`) tem a mesma estrutura, e ainda satura deltas fora de [-1, 1] no `LinearQuantizer`. A forma usual de corrigir é DPCM em malha fechada: o encoder prevê usando os valores já quantizados.
3. **No `HIGH_QUALITY` o ganho é de cerca de 2×** (2,06–2,24:1, SNR ≈ 58–59 dB), contra 1,06:1 do Deflate lossless e 1,19:1 do shuffle+Deflate. Para float32 com ruído, isso vem quase todo da quantização para 16 bits: 15,5 bits/amostra no 2D é praticamente o mesmo que gravar `short` sem compressão.
4. **A descompressão (~150 MiB/s) é limitada pelo Inflate e pela leitura.** A compressão (~45 MiB/s) é limitada pelo Deflate 9. O `HIGH_COMPRESSION` 3D é o mais lento para comprimir: Deflate em dados de 8 bits com pouca redundância.

Esses resultados servem de **linha de base**. Depois de mudar um codec, rode de novo e compare o `results.csv` com este.

## Comparar execuções e publicar resultados

- Cada execução vai para um diretório próprio. Commite o diretório inteiro para manter o histórico.
- O `hardware.txt` registra o `git_commit` e se a árvore estava suja (`git_dirty`). Para um resultado "oficial", rode a partir de um commit limpo.
- Para comparar duas máquinas ou dois commits, use o mesmo `PRESETS`/`WARMUP`/`ITERATIONS`/`BLOCK`/`HEAP`.
- Os SHA-256 dos `.sdc` gerados ficam em `results.json` (`outputSha256`). Eles dependem da versão da zlib do JDK, então só são comparáveis na mesma JVM. Os do dataset são comparáveis em qualquer lugar.

## Limitações

- Os dados sintéticos têm ruído gaussiano de 2%, o que limita a compressão lossless. Dados reais (sobretudo empilhados e filtrados) costumam comprimir melhor. Confirme com `SEGY=...`.
- Em VM ou contêiner compartilhado o desvio padrão fica entre 2% e 10%. Para comparações finas, aumente `ITERATIONS` e feche outros processos. Se tiver acesso ao host, fixe o governor de CPU em `performance`.
- É um harness simples de ponta a ponta, não JMH. É adequado para operações de centenas de ms a segundos, como estas, mas não para microbenchmarks.
