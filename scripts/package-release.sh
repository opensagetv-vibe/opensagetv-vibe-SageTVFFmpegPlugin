#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

set -a
# shellcheck disable=SC1091
source "$ROOT/release.properties"
set +a

export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1789862400}"
"$ROOT/scripts/build.sh"
"$ROOT/scripts/build-windows-launcher.sh"

MIM_ROOT="${MIM_REPO_ROOT:-$ROOT/../opensagetv-vibe-ffmpeg-mim}"
LINUX_RUNTIME="${MIM_LINUX_RUNTIME:-$MIM_ROOT/output/plugin/OpenSageTVVibeMIMRuntimeLinux-$MIM_VERSION.zip}"
WINDOWS_RUNTIME="${MIM_WINDOWS_RUNTIME:-$MIM_ROOT/output/plugin/OpenSageTVVibeMIMRuntimeWindowsx64-$MIM_VERSION.zip}"

python3 "$ROOT/scripts/package_release.py" \
  --root "$ROOT" \
  --plugin-version "$PLUGIN_VERSION" \
  --mim-version "$MIM_VERSION" \
  --linux-runtime "$LINUX_RUNTIME" \
  --windows-runtime "$WINDOWS_RUNTIME" \
  --source-date-epoch "$SOURCE_DATE_EPOCH"

python3 "$ROOT/scripts/test_release_packages.py" \
  --root "$ROOT" \
  --plugin-version "$PLUGIN_VERSION" \
  --mim-version "$MIM_VERSION" \
  --linux-runtime "$LINUX_RUNTIME" \
  --windows-runtime "$WINDOWS_RUNTIME"
