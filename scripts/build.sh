#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/build" "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" "$ROOT/dist/dev/SageTVTranscoder"
mkdir -p "$ROOT/build/classes" "$ROOT/build/stubs" "$ROOT/build/dvd-spi-stubs" "$ROOT/dist/dev"
if [[ -n "${SAGETV_JAR:-}" ]]; then
  CP="$SAGETV_JAR"
elif [[ -f "$ROOT/.deps/stock/Sage.jar" ]]; then
  CP="$ROOT/.deps/stock/Sage.jar"
elif [[ "${ALLOW_SAGETV_STUBS:-false}" == true ]]; then
  javac --release 8 -d "$ROOT/build/stubs" "$ROOT"/tools/sagetv-stubs/sage/*.java
  CP="$ROOT/build/stubs"
else
  echo 'ERROR: unmodified stock Sage.jar is required (set SAGETV_JAR or populate .deps/stock/Sage.jar)' >&2
  exit 2
fi
test -f "$CP" || test -d "$CP" || { echo "ERROR: SageTV compile contract missing: $CP" >&2; exit 2; }
javac --release 8 -d "$ROOT/build/dvd-spi-stubs" "$ROOT"/tools/dvd-spi-stubs/sage/*.java
COMPILE_CP="$CP:$ROOT/build/dvd-spi-stubs"
printf '%s\n' "$COMPILE_CP" > "$ROOT/build/compile-classpath.txt"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 8 -cp "$COMPILE_CP" -d "$ROOT/build/classes"
jar cf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" -C "$ROOT/build/classes" . -C "$ROOT/src/main/resources" .
if jar tf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" | grep -q '^sage/'; then
  echo 'ERROR: SageTV compile-contract classes leaked into the plugin JAR' >&2
  exit 3
fi
cp "$ROOT/launcher/SageTVTranscoder" "$ROOT/dist/dev/SageTVTranscoder"
chmod +x "$ROOT/dist/dev/SageTVTranscoder"
echo "Built dist/dev/SageTVFFmpegPlugin.jar and ABI-neutral Linux SageTVTranscoder launcher"
