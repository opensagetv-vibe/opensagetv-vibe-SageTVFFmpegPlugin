#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${CXX:=x86_64-w64-mingw32-g++}"
if ! command -v "$CXX" >/dev/null 2>&1; then
  VIBE_MINGW=/opt/sagetv/targets/win64/ct-ng/bin/x86_64-w64-mingw32-g++
  if [[ -x "$VIBE_MINGW" ]]; then
    CXX="$VIBE_MINGW"
  else
    echo "ERROR: Windows x64 MinGW compiler not found: $CXX" >&2
    exit 2
  fi
fi
mkdir -p "$ROOT/dist/dev"
"$CXX" -std=c++17 -O2 -static -static-libgcc -static-libstdc++ "$ROOT/launcher/SageTVTranscoderLauncher.cpp" -lshell32 -o "$ROOT/dist/dev/SageTVTranscoder.exe"
echo "Built dist/dev/SageTVTranscoder.exe"
