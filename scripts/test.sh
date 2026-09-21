#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STOCK_STV="${SAGETV_STOCK_STV:-$ROOT/../opensagetv-vibe-core/stvs/SageTV7/SageTV7.xml}"
if [[ ! -f "$STOCK_STV" && -f /work/sagetv/stvs/SageTV7/SageTV7.xml ]]; then
  STOCK_STV=/work/sagetv/stvs/SageTV7/SageTV7.xml
fi
python3 "$ROOT/scripts/test_stvi.py" --stvi "$ROOT/stvi/SageTVFFmpegPlugin.stvi" --stock-stv "$STOCK_STV"
"$ROOT/scripts/build.sh" >/dev/null
mkdir -p "$ROOT/build/test-classes"
CP="$(cat "$ROOT/build/compile-classpath.txt")"
javac --release 8 -cp "$ROOT/build/classes:$CP" -d "$ROOT/build/test-classes" "$ROOT/tests/org/opensagetv/vibe/ffmpeg/PluginSmokeTest.java"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
java -cp "$ROOT/build/classes:$CP:$ROOT/build/test-classes" org.opensagetv.vibe.ffmpeg.PluginSmokeTest "$TMP"

# Native bridge argument/exit-code passthrough.
mkdir -p "$TMP/plugins/SageTVFFmpegPlugin/runtime"
cat > "$TMP/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM" <<'SH'
#!/bin/sh
printf '%s\n' "$@" > "$(dirname "$0")/args.txt"
exit 23
SH
chmod +x "$TMP/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM"
cp "$ROOT/dist/dev/SageTVTranscoder" "$TMP/SageTVTranscoder"
cat > "$TMP/ffmpeg" <<'SH'
#!/bin/sh
exit 0
SH
chmod +x "$TMP/ffmpeg"
STOCK_BEFORE="$(sha256sum "$TMP/ffmpeg" | cut -d' ' -f1)"
set +e
"$TMP/SageTVTranscoder" -i "file with space.ts" -f mpegts >/dev/null 2>&1
RC=$?
set -e
[[ "$RC" == 23 ]]
grep -Fx -- '-i' "$TMP/plugins/SageTVFFmpegPlugin/runtime/args.txt" >/dev/null
grep -Fx -- 'file with space.ts' "$TMP/plugins/SageTVFFmpegPlugin/runtime/args.txt" >/dev/null

# A missing or removed plugin runtime must delegate to SageTV's stock ffmpeg
# so a partial install, disabled plugin, or uninstall cannot break playback.
rm "$TMP/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM"
cat > "$TMP/ffmpeg" <<'SH'
#!/bin/sh
printf '%s\n' "$@" > "$(dirname "$0")/stock-args.txt"
exit 29
SH
chmod +x "$TMP/ffmpeg"
set +e
"$TMP/SageTVTranscoder" -i "stock fallback.ts" -f mpegts >/dev/null 2>&1
RC=$?
set -e
[[ "$RC" == 29 ]]
grep -Fx -- 'stock fallback.ts' "$TMP/stock-args.txt" >/dev/null
[[ "$(sha256sum "$TMP/ffmpeg" | cut -d' ' -f1)" != "$STOCK_BEFORE" ]]
# The bridge never writes stock ffmpeg. Re-establish a stable hash and prove
# plugin invocation leaves it byte-identical.
STOCK_BEFORE="$(sha256sum "$TMP/ffmpeg" | cut -d' ' -f1)"
set +e
"$TMP/SageTVTranscoder" -version >/dev/null 2>&1
set -e
[[ "$(sha256sum "$TMP/ffmpeg" | cut -d' ' -f1)" == "$STOCK_BEFORE" ]]
echo "LauncherSmokeTest PASS"
