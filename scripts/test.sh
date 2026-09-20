#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh" >/dev/null
mkdir -p "$ROOT/build/test-classes"
javac --release 8 -cp "$ROOT/build/classes:$ROOT/build/stubs" -d "$ROOT/build/test-classes" "$ROOT/tests/org/opensagetv/vibe/ffmpeg/PluginSmokeTest.java"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
java -cp "$ROOT/build/classes:$ROOT/build/stubs:$ROOT/build/test-classes" org.opensagetv.vibe.ffmpeg.PluginSmokeTest "$TMP"

# Native bridge argument/exit-code passthrough.
mkdir -p "$TMP/plugins/SageTVFFmpegPlugin/runtime"
cat > "$TMP/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM" <<'SH'
#!/bin/sh
printf '%s\n' "$@" > "$(dirname "$0")/args.txt"
exit 23
SH
chmod +x "$TMP/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM"
cp "$ROOT/dist/dev/SageTVTranscoder" "$TMP/SageTVTranscoder"
set +e
"$TMP/SageTVTranscoder" -i "file with space.ts" -f mpegts >/dev/null 2>&1
RC=$?
set -e
[[ "$RC" == 23 ]]
grep -Fx -- '-i' "$TMP/plugins/SageTVFFmpegPlugin/runtime/args.txt" >/dev/null
grep -Fx -- 'file with space.ts' "$TMP/plugins/SageTVFFmpegPlugin/runtime/args.txt" >/dev/null
echo "LauncherSmokeTest PASS"
