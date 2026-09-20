#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh"
rm -rf "$ROOT/build/package"
mkdir -p "$ROOT/build/package/jar" "$ROOT/build/package/system/plugins/SageTVFFmpegPlugin/launcher" "$ROOT/build/package/stvi"
cp "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" "$ROOT/build/package/jar/"
cp "$ROOT/stvi/SageTVFFmpegPlugin.stvi" "$ROOT/build/package/stvi/"
cp "$ROOT/dist/dev/SageTVTranscoder" "$ROOT/build/package/system/SageTVTranscoder"
cp "$ROOT/dist/dev/SageTVTranscoder" "$ROOT/build/package/system/plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder"
chmod +x "$ROOT/build/package/system/SageTVTranscoder" "$ROOT/build/package/system/plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder"
(cd "$ROOT/build/package/jar" && zip -qr "$ROOT/dist/dev/SageTVFFmpegPlugin-jar-dev.zip" .)
(cd "$ROOT/build/package/system" && zip -qr "$ROOT/dist/dev/SageTVFFmpegPlugin-system-linux-dev.zip" .)
(cd "$ROOT/build/package/stvi" && zip -qr "$ROOT/dist/dev/SageTVFFmpegPlugin-STVI-dev.zip" .)
echo "Development packages written to dist/dev"
