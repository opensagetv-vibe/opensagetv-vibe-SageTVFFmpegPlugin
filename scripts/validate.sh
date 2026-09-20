#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

test -f "$ROOT/AGENTS.md"
test -f "$ROOT/README.md"
test -f "$ROOT/HANDOFF.md"
test -f "$ROOT/TASKS.md"
test -f "$ROOT/WORKFLOW.md"
test -f "$ROOT/CHANGELOG.md"
test -f "$ROOT/THIRD_PARTY_NOTICES.md"
test -f "$ROOT/LICENSE"
test -f "$ROOT/VERSION"
test -f "$ROOT/release-deletions.lst"
test -f "$ROOT/artifacts/downloads/.gitkeep"
test "$(tr -d '\r\n' < "$ROOT/VERSION")" = "$(sed -n 's/^VERSION=//p' "$ROOT/release.properties" | tr -d '\r')"
test -x "$ROOT/dev.sh"
test -x "$ROOT/update.sh"
test -x "$ROOT/scripts/build.sh"
test -x "$ROOT/scripts/test.sh"
grep -Fq -- '-ProjectRoot "%~dp0."' "$ROOT/create_ai_handoff_zip.cmd"

if find "$ROOT" -type f \( -name 'Sage.jar' -o -name 'ffmpeg.real.ini' \) \
    -not -path "$ROOT/.deps/*" -not -path "$ROOT/build/*" -not -path "$ROOT/dist/*" \
    -print -quit | grep -q .; then
  echo 'ERROR: private/live runtime file present in publishable source' >&2
  exit 1
fi

if [[ -f "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" ]]; then
  ! jar tf "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" | grep -q '^sage/'
  if strings "$ROOT/dist/dev/SageTVFFmpegPlugin.jar" | grep -E 'VIBE_|MEDIA_STATE_URL|DVD_REMOTE_NAV|MiniDVDStreamTranscoder' >/dev/null; then
    echo 'ERROR: plugin references a Vibe-only Core protocol/symbol' >&2
    exit 1
  fi
fi

echo 'PASS: stock Sage.jar and repository source contracts'
