#!/usr/bin/env bash
# Benchmark reprodutível do SDC.
#
#   ./benchmark/run-benchmark.sh                     # presets small + medium, 2 warmups, 5 iterações
#   PRESETS=small ITERATIONS=3 ./benchmark/run-benchmark.sh
#   SEGY=/dados/F3.sgy PRESETS=none ./benchmark/run-benchmark.sh
#
# Variáveis: PRESETS, SEGY, WARMUP, ITERATIONS, BLOCK, HEAP, LABEL, OUT, SKIP_BUILD
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PRESETS="${PRESETS:-small,medium}"
WARMUP="${WARMUP:-2}"
ITERATIONS="${ITERATIONS:-5}"
BLOCK="${BLOCK:-8,8,0}"
HEAP="${HEAP:-3g}"
LABEL="${LABEL:-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)-$(nproc 2>/dev/null || echo 1)cpu}"
RUN_ID="$(date -u +%Y-%m-%d)_${LABEL}"
OUT="${OUT:-$ROOT/benchmark/results/$RUN_ID}"
DATA_DIR="$ROOT/benchmark/data"
JAR="$ROOT/sdc-bench/target/sdc-bench-0.1.0-SNAPSHOT-jar-with-dependencies.jar"

# A saída das ferramentas não deve carregar JAVA_TOOL_OPTIONS (pode conter proxies/segredos do ambiente).
strip_tool_opts() { grep -v '^Picked up JAVA_TOOL_OPTIONS' || true; }

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "[run] build sdc-core + sdc-bench"
  mvn -q -B -pl sdc-bench -am -DskipTests package 2>&1 | strip_tool_opts
fi

mkdir -p "$OUT" "$DATA_DIR"

# ---------------------------------------------------------------- hardware / software
HW="$OUT/hardware.txt"
{
  echo "# Hardware e software do run $RUN_ID"
  echo "date_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git_commit: $(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "git_dirty: $([[ -n "$(git status --porcelain -- . ':!benchmark/results' ':!benchmark/data' 2>/dev/null)" ]] && echo yes || echo no)"
  echo "kernel: $(uname -srmo 2>/dev/null || uname -a)"
  [[ -r /etc/os-release ]] && echo "distro: $(. /etc/os-release; echo "$PRETTY_NAME")"
  echo "virtualization: $(systemd-detect-virt 2>/dev/null || echo unknown)"
  echo
  echo "## CPU"
  if command -v lscpu >/dev/null; then
    lscpu | grep -E '^(Architecture|Model name|Vendor ID|CPU\(s\)|Thread\(s\) per core|Core\(s\) per socket|Socket\(s\)|CPU max MHz|CPU MHz|L1d cache|L2 cache|L3 cache|Flags)' \
      | sed -E 's/^(Flags:\s+).*/\1(omitted; avx2: '"$(grep -qw avx2 /proc/cpuinfo && echo yes || echo no)"', avx512f: '"$(grep -qw avx512f /proc/cpuinfo && echo yes || echo no)"')/'
  else
    sysctl -n machdep.cpu.brand_string hw.ncpu 2>/dev/null || true
  fi
  if [[ -r /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ]]; then
    echo "scaling_governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"
  fi
  echo "nproc (visible to process): $(nproc 2>/dev/null || echo unknown)"
  echo
  echo "## Memória"
  if [[ -r /proc/meminfo ]]; then grep -E '^(MemTotal|MemAvailable|SwapTotal)' /proc/meminfo; else sysctl -n hw.memsize 2>/dev/null || true; fi
  if [[ -r /sys/fs/cgroup/memory.max ]]; then echo "cgroup memory.max: $(cat /sys/fs/cgroup/memory.max)"; fi
  if [[ -r /sys/fs/cgroup/cpu.max ]]; then echo "cgroup cpu.max: $(cat /sys/fs/cgroup/cpu.max)"; fi
  echo
  echo "## Armazenamento (tmp, onde os .sdc temporários são escritos)"
  df -hT "${TMPDIR:-/tmp}" 2>/dev/null | tail -n +1 || true
  echo
  echo "## Java"
  java -version 2>&1 | strip_tool_opts
  echo
  echo "## Maven"
  mvn -v 2>&1 | strip_tool_opts | grep -E '^(Apache Maven|Java version|OS name)' || true
  echo
  echo "## Parâmetros"
  echo "presets: $PRESETS"
  echo "segy: ${SEGY:-none}"
  echo "warmup: $WARMUP  iterations: $ITERATIONS  block3d: $BLOCK  heap: $HEAP"
} > "$HW"
echo "[run] hardware -> $HW"

# ---------------------------------------------------------------- benchmark
ARGS=(--presets "$PRESETS" --data-dir "$DATA_DIR" --out "$OUT"
      --warmup "$WARMUP" --iterations "$ITERATIONS" --block "$BLOCK"
      --label "$LABEL" --git-commit "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)")
if [[ -n "${SEGY:-}" ]]; then
  IFS=',' read -ra FILES <<< "$SEGY"
  for f in "${FILES[@]}"; do ARGS+=(--segy "$f"); done
fi

# Heap fixo (Xms = Xmx) evita redimensionamento do heap durante as medições.
java -Xms"$HEAP" -Xmx"$HEAP" -XX:+AlwaysPreTouch -jar "$JAR" "${ARGS[@]}" 2>&1 | strip_tool_opts | tee "$OUT/run.log"

echo
echo "[run] pronto: $OUT"
ls -1 "$OUT"
