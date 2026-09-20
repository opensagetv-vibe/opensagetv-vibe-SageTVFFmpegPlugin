#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/build" "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" "$ROOT/dist/dev/SageTVTranscoder"
mkdir -p "$ROOT/build/classes" "$ROOT/build/stubs" "$ROOT/dist/dev"
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
printf '%s\n' "$CP" > "$ROOT/build/compile-classpath.txt"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 8 -cp "$CP" -d "$ROOT/build/classes"
jar cf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" -C "$ROOT/build/classes" .
if jar tf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" | grep -q '^sage/'; then
  echo 'ERROR: SageTV compile-contract classes leaked into the plugin JAR' >&2
  exit 3
fi
g++ -std=c++17 -O2 "$ROOT/launcher/SageTVTranscoderLauncher.cpp" -o "$ROOT/dist/dev/SageTVTranscoder"
chmod +x "$ROOT/dist/dev/SageTVTranscoder"
echo "Built dist/dev/SageTVFFmpegPlugin.jar and Linux SageTVTranscoder launcher"
