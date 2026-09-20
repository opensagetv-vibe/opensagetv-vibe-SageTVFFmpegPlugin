#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/build" "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" "$ROOT/dist/dev/SageTVTranscoder"
mkdir -p "$ROOT/build/classes" "$ROOT/build/stubs" "$ROOT/dist/dev"
if [[ -n "${SAGETV_JAR:-}" ]]; then
  CP="$SAGETV_JAR"
else
  javac --release 8 -d "$ROOT/build/stubs" "$ROOT"/tools/sagetv-stubs/sage/*.java
  CP="$ROOT/build/stubs"
fi
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 8 -cp "$CP" -d "$ROOT/build/classes"
jar cf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" -C "$ROOT/build/classes" .
g++ -std=c++17 -O2 "$ROOT/launcher/SageTVTranscoderLauncher.cpp" -o "$ROOT/dist/dev/SageTVTranscoder"
chmod +x "$ROOT/dist/dev/SageTVTranscoder"
echo "Built dist/dev/SageTVFFmpegPlugin.jar and Linux SageTVTranscoder launcher"
